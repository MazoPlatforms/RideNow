import { z } from 'zod';
import { transaction, one } from '../shared/db.js';
import { decrypt } from '../shared/crypto.js';
import { ensure } from '../shared/errors.js';
import { driverContact, readApplication } from './driver-application.js';

const appSchema = z.enum(['RIDER', 'DRIVER']);
const common = ['PROFILE', 'RIDES', 'SUPPORT'];
const sections = {
  RIDER: [...common, 'PAYMENTS', 'SAVED_PLACES'],
  DRIVER: [...common, 'EARNINGS', 'VEHICLES'],
};
const input = z.strictObject({
  app: appSchema,
  sections: z.array(z.enum([...sections.RIDER, 'EARNINGS', 'VEHICLES'])).min(1).max(5),
}).refine(value => new Set(value.sections).size === value.sections.length &&
  value.sections.every(section => sections[value.app].includes(section)), {
  path: ['sections'], message: 'Select distinct sections available for this app',
});
const fileQuery = z.object({
  app: appSchema,
  type: z.enum(['avatar', 'document', 'support']),
});
const fileExtension = { 'image/jpeg': 'jpg', 'image/png': 'png', 'application/pdf': 'pdf' };
const rows = async (db, sql, values) => (await db.query(sql, values)).rows;

function authorize(user, app) {
  ensure(!user.roles.some(role => role === 'STAFF' || role === 'ADMIN') &&
    (user.session_app ? user.session_app === app : app === 'RIDER' || user.roles.includes('DRIVER')),
  403, 'DATA_EXPORT_FORBIDDEN');
}

function sessionRow(db, user) {
  return one(db, `SELECT u.id,u.phone,u.name,u.email,u.created_at,u.rider_birth_date::text,
    u.rider_gender,u.rider_city,u.profile_photo_key,u.profile_photo_version,s.device_app AS session_app,u.roles
    FROM users u JOIN sessions s ON s.user_id=u.id
    WHERE u.id=$1 AND s.id=$2 AND NOT u.suspended AND u.deleted_at IS NULL
      AND s.revoked_at IS NULL AND s.expires_at>now()`, [user.id, user.session_id]);
}

const driverProfile = (db, id) => one(db, `SELECT status,availability,created_at,submitted_at,
  application_encrypted,contact_encrypted,profile_photo_key,profile_photo_version FROM driver_profiles WHERE user_id=$1`, [id]);

function hasSafeCopy(storage, { object_key, object_version }) {
  return storage.mode === 'immutable' ? object_key.startsWith('verified/') :
    storage.mode === 'versioned' ? Boolean(object_version && object_version !== 'null') : true;
}

export function mountDataExport(route, { pool, redis, config, storage, rateLimiter }) {
  const path = '/v1/me/data-export';
  const limiter = rateLimiter(redis, config, {
    limit: 6, seconds: 600, prefix: 'data-export',
    identity: req => req.user.id,
  });
  const unpack = value => JSON.parse(decrypt(value, config.ENCRYPTION_KEY));
  const documentsFor = (db, id, vehicle) => rows(db, `SELECT id,vehicle_id,kind,mime,bytes,created_at,submitted_at,
    expires_at,scan_status,object_key,object_version FROM documents
    WHERE driver_id=$1 AND (vehicle_id IS NOT NULL)=$2 ORDER BY created_at,id`, [id, vehicle]);

  route('post', path, { body: input, noStore: true, middleware: [limiter] }, ({ user, body }) =>
    transaction(pool, async db => {
      await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY');
      const account = ensure(await sessionRow(db, user), 401, 'SESSION_REVOKED');
      authorize(account, body.app);
      const id = account.id, args = [id], isRider = body.app === 'RIDER';
      const selected = new Set(body.sections), data = {}, files = [];
      const driver = !isRider && (selected.has('PROFILE') || selected.has('EARNINGS')) ? await driverProfile(db, id) : null;
      const application = driver ? readApplication(driver, config.ENCRYPTION_KEY) : null;

      function addFile(file, type, folder, name) {
        if (!hasSafeCopy(storage, file)) return null;
        const archivePath = folder + '/' + file.id + '.' + fileExtension[file.mime];
        files.push({ id: file.id, type, archivePath, mime: file.mime, ...(name ? { originalName: name } : {}) });
        return archivePath;
      }

      const documentView = folder => ({ object_key, object_version, ...doc }) => ({
        ...doc, file: doc.scan_status === 'CLEAN'
          ? addFile({ ...doc, object_key, object_version }, 'document', folder) : null,
      });

      if (selected.has('PROFILE')) {
        const profile = { name: account.name, phone: account.phone, email: account.email, createdAt: account.created_at };
        if (isRider) Object.assign(profile, { dateOfBirth: account.rider_birth_date, gender: account.rider_gender, city: account.rider_city });
        if (driver) {
          const contact = driverContact(driver, application, account, config.ENCRYPTION_KEY);
          const onboarding = application && {
            dateOfBirth: application.dateOfBirth, gender: application.gender, city: application.city,
            consent: application.consent, identity: application.identity, license: application.license,
            schedule: application.schedule, serviceArea: application.serviceArea,
          };
          const identityDocuments = await documentsFor(db, id, false);
          profile.driver = { status: driver.status, availability: driver.availability, submittedAt: driver.submitted_at,
            contact, onboarding, documents: identityDocuments.map(documentView('identity-documents')) };
        }
        const photo = isRider ? account : driver;
        if (photo?.profile_photo_key) profile.photo = addFile({ id, mime: 'image/jpeg',
          object_key: photo.profile_photo_key, object_version: photo.profile_photo_version }, 'avatar', 'profile');
        profile.notificationSettings = (await one(db,
          'SELECT preferences FROM notification_preferences WHERE user_id=$1 AND app=$2', [id, body.app]))?.preferences ?? {};
        profile.devices = await rows(db, `SELECT DISTINCT ON (family_id) device_name AS name,created_at AS signed_in_at
          FROM sessions WHERE user_id=$1 AND device_app=$2 AND revoked_at IS NULL AND expires_at>now()
          ORDER BY family_id,created_at DESC`, [id, body.app]);
        data.profile = profile;
      }
      if (selected.has('RIDES')) {
        const ownership = isRider ? 'rider_id' : 'driver_id';
        const waypoints = isRider ? ',q.waypoints' : '';
        data.rides = await rows(db, `SELECT r.id,r.state,q.category,q.trip_type,r.scheduled_at,r.arrived_at,r.started_at,
          r.completed_at,r.created_at,r.final_minor,p.amount_minor AS payment_minor,p.status AS payment_status
          ${waypoints} FROM rides r JOIN fare_quotes q ON q.id=r.quote_id LEFT JOIN payments p ON p.ride_id=r.id
          WHERE r.${ownership}=$1 ORDER BY r.created_at,r.id`, args);
      }
      if (selected.has('PAYMENTS')) {
        data.payments = {
          currency: 'LKR',
          receipts: await rows(db, `SELECT p.ride_id,p.amount_minor,p.status,p.collected_at FROM payments p
            JOIN rides r ON r.id=p.ride_id WHERE r.rider_id=$1 ORDER BY r.created_at,r.id`, args),
          cancellationCharges: await rows(db, 'SELECT ride_id,amount_minor,status,created_at FROM cancellation_charges WHERE rider_id=$1 ORDER BY created_at,ride_id', args),
          disputes: await rows(db, 'SELECT id,ride_id,reason,status,created_at,resolved_at FROM payment_disputes WHERE opened_by=$1 ORDER BY created_at,id', args),
        };
      }
      if (selected.has('SAVED_PLACES')) {
        const addresses = await rows(db, 'SELECT id,kind,details_encrypted,updated_at FROM saved_addresses WHERE user_id=$1 ORDER BY updated_at,id', args);
        const contacts = await rows(db, 'SELECT id,details_encrypted,created_at FROM trusted_contacts WHERE user_id=$1 ORDER BY created_at,id', args);
        const reveal = ({ details_encrypted, ...row }) => ({ ...row, ...unpack(details_encrypted) });
        data.savedPlaces = { currentCity: account.rider_city, addresses: addresses.map(reveal), trustedContacts: contacts.map(reveal) };
      }
      if (selected.has('EARNINGS')) {
        data.earnings = {
          currency: 'LKR',
          bank: application?.bank ?? null,
          transactions: await rows(db, `SELECT t.id,t.kind,t.ride_id,t.created_at,a.code AS account,e.debit,e.credit
            FROM ledger_entries e JOIN ledger_accounts a ON a.id=e.account_id
            JOIN ledger_transactions t ON t.id=e.transaction_id WHERE a.user_id=$1 ORDER BY t.created_at,e.id`, args),
          payouts: await rows(db, 'SELECT id,amount_minor,status,created_at FROM payouts WHERE driver_id=$1 ORDER BY created_at,id', args),
        };
      }
      if (selected.has('VEHICLES')) {
        const documents = await documentsFor(db, id, true);
        data.vehicleRecords = {
          vehicles: await rows(db, `SELECT id,registration,category,capacity,status,created_at,make,model,model_year,color
            FROM vehicles WHERE driver_id=$1 ORDER BY created_at,id`, args),
          documents: documents.map(documentView('vehicle-documents')),
        };
      }
      if (selected.has('SUPPORT')) {
        const scope = [id, body.app];
        const tickets = await rows(db, `SELECT id,kind,status,subject_encrypted,created_at,updated_at FROM support_tickets
          WHERE user_id=$1 AND app=$2 ORDER BY created_at,id`, scope);
        const messages = await rows(db, `SELECT m.id,m.ticket_id,m.sender,m.body_encrypted,m.created_at
          FROM support_messages m JOIN support_tickets t ON t.id=m.ticket_id
          WHERE t.user_id=$1 AND t.app=$2 ORDER BY m.created_at,m.id`, scope);
        const attachments = await rows(db, `SELECT a.id,a.message_id,a.name,a.mime,a.bytes,a.scan_status,a.object_key,a.object_version
          FROM support_attachments a JOIN support_messages m ON m.id=a.message_id JOIN support_tickets t ON t.id=m.ticket_id
          WHERE t.user_id=$1 AND t.app=$2 AND a.user_id=$1 AND a.app=$2 ORDER BY a.created_at,a.id`, scope);
        const byMessage = Map.groupBy(attachments, file => file.message_id);
        const byTicket = Map.groupBy(messages, message => message.ticket_id);
        data.support = tickets.map(({ subject_encrypted, ...ticket }) => ({
          ...ticket, subject: decrypt(subject_encrypted, config.ENCRYPTION_KEY),
          messages: (byTicket.get(ticket.id) ?? []).map(({ body_encrypted, ...message }) => ({
            ...message, body: decrypt(body_encrypted, config.ENCRYPTION_KEY),
            attachments: (byMessage.get(message.id) ?? []).map(({ object_key, object_version, ...file }) => ({
              ...file, file: file.scan_status === 'CLEAN'
                ? addFile({ ...file, object_key, object_version }, 'support', 'support-attachments', file.name) : null,
            })),
          })),
        }));
      }
      return { schemaVersion: 1, app: body.app, exportedAt: new Date().toISOString(), data, files };
    }));

  route('get', path + '/files/:id', { query: fileQuery, noStore: true }, async ({ req, user, db }) => {
    const { app, type } = fileQuery.parse(req.query);
    const account = ensure(await sessionRow(db, user), 401, 'SESSION_REVOKED');
    authorize(account, app);
    const args = [req.params.id, account.id];
    let file;
    if (type === 'avatar' && req.params.id === account.id) {
      const profile = app === 'RIDER' ? account : await driverProfile(db, account.id);
      if (profile?.profile_photo_key)
        file = { object_key: profile.profile_photo_key, object_version: profile.profile_photo_version };
    }
    if (type === 'document' && app === 'DRIVER') {
      file = await one(db, `SELECT object_key,object_version FROM documents
        WHERE id=$1 AND driver_id=$2 AND scan_status='CLEAN'`, args);
    }
    if (type === 'support') {
      file = await one(db, `SELECT a.object_key,a.object_version FROM support_attachments a
        JOIN support_messages m ON m.id=a.message_id JOIN support_tickets t ON t.id=m.ticket_id
        WHERE a.id=$1 AND t.user_id=$2 AND a.user_id=$2 AND t.app=$3 AND a.app=$3 AND a.scan_status='CLEAN'`, [...args, app]);
    }
    ensure(file && hasSafeCopy(storage, file), 404, 'DATA_EXPORT_FILE_NOT_FOUND');
    return { downloadUrl: await storage.download(file.object_key, file.object_version) };
  });
}
