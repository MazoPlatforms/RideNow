import { z } from 'zod';
import { decrypt, encrypt, hash } from '../shared/crypto.js';
import { one, transaction } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
import { normalizePhone } from './auth.js';

const cleanText = max => z.string().trim().min(1).max(max).regex(/^[^\p{C}]+$/u);
const location = z.tuple([z.number().min(-180).max(180), z.number().min(-90).max(90)]);
export const cityInput = z.strictObject({ city: cleanText(100).nullable() });
export const savedAddressInput = z.strictObject({
  kind: z.enum(['HOME', 'WORK', 'OTHER']),
  label: cleanText(80).optional(),
  address: cleanText(300),
  coordinates: location.nullable().optional(),
});
export const trustedContactInput = z.strictObject({ name: cleanText(80), phone: z.string().trim().min(5).max(32) });

export const notificationApp = z.enum(['RIDER', 'DRIVER']);
const defaults = {
  RIDER: { push: true, rides: true, promotions: false, scheduled: true, payments: true, account: true },
  DRIVER: { push: true, offers: true, rides: true, payments: true, promotions: false,
    scheduled: true, safety: true, account: false, inApp: true },
};
export const notificationChange = z.strictObject({
  app: notificationApp, key: z.enum([...new Set(Object.values(defaults).flatMap(Object.keys))]), enabled: z.boolean(),
}).refine(({ app, key }) => Object.hasOwn(defaults[app], key), { path: ['key'], message: 'Unknown notification preference for this app' });

export const notificationCategory = (type, payload = {}) => {
  if (type === 'driver.offer' || type === 'ride.offer') return 'offers';
  if (type.startsWith('payment.') || type.startsWith('payout.')) return 'payments';
  if (type.startsWith('safety.')) return 'safety';
  if (type.startsWith('promotion.')) return 'promotions';
  if (type.startsWith('ride.scheduled') || (type === 'ride.updated' && payload.state === 'SCHEDULED')) return 'scheduled';
  if (type.startsWith('ride.')) return 'rides';
  return 'account';
};

export function allowsNotification(preferences, app, type, payload, channel) {
  const options = { ...defaults[app], ...preferences };
  if (channel === 'push' && options.push === false) return false;
  if (channel === 'inApp' && options.inApp === false) return false;
  const category = notificationCategory(type, payload);
  if (app === 'RIDER' && (category === 'offers' || type.startsWith('driver.'))) return false;
  // Driver account notifications include optional app updates, not application reviews.
  if (app === 'DRIVER' && type.startsWith('driver.') && category === 'account') return true;
  return options[category] !== false;
}

async function lockUser(db, actor) {
  return ensure(await one(db, `SELECT id,roles FROM users WHERE id=$1 AND NOT suspended AND deleted_at IS NULL
    FOR UPDATE`, [actor.id]), 401, 'SESSION_REVOKED');
}
function checkNotificationApp(user, app) {
  ensure(app !== 'DRIVER' || user.roles.includes('DRIVER'), 403, 'DRIVER_REQUIRED');
}

export function createMobilePreferences(pool, config) {
  const pack = value => encrypt(JSON.stringify(value), config.ENCRYPTION_KEY);
  const unpack = value => JSON.parse(decrypt(value, config.ENCRYPTION_KEY));
  const addressView = row => ({ id: row.id, kind: row.kind, ...unpack(row.details_encrypted) });
  const contactView = row => ({ id: row.id, ...unpack(row.details_encrypted) });
  return {
    async riderSettings(actor) {
      const result = await one(pool, `SELECT u.rider_city AS city,
        ARRAY(SELECT to_jsonb(a) FROM saved_addresses a WHERE a.user_id=u.id ORDER BY a.updated_at DESC, a.id) AS addresses,
        ARRAY(SELECT to_jsonb(c) FROM trusted_contacts c WHERE c.user_id=u.id ORDER BY c.created_at, c.id) AS contacts
        FROM users u WHERE u.id=$1 AND NOT u.suspended AND u.deleted_at IS NULL`, [actor.id]);
      ensure(result, 401, 'SESSION_REVOKED');
      return {
        city: result.city,
        addresses: result.addresses.map(addressView),
        contacts: result.contacts.map(contactView),
      };
    },
    async setCity(actor, city) {
      return ensure(await one(pool, `UPDATE users SET rider_city=$2 WHERE id=$1 AND NOT suspended AND deleted_at IS NULL
        RETURNING rider_city AS city`, [actor.id, city]), 401, 'SESSION_REVOKED');
    },
    async saveAddress(actor, body, id = null) {
      const details = { label: body.label ?? null, address: body.address, coordinates: body.coordinates ?? null };
      const value = pack(details);
      return transaction(pool, async db => {
        await lockUser(db, actor);
        if (id) return addressView(ensure(await one(db, `UPDATE saved_addresses SET details_encrypted=$4,
          updated_at=now() WHERE user_id=$1 AND id=$2 AND kind=$3 RETURNING *`, [actor.id, id, body.kind, value]),
        404, 'SAVED_ADDRESS_NOT_FOUND'));
        if (body.kind === 'OTHER') {
          const saved = (await db.query("SELECT * FROM saved_addresses WHERE user_id=$1 AND kind='OTHER'", [actor.id])).rows;
          const existing = saved.find(row => JSON.stringify(unpack(row.details_encrypted)) === JSON.stringify(details));
          if (existing) return addressView(existing);
          ensure(saved.length < 50, 409, 'SAVED_ADDRESS_LIMIT');
          return addressView(await one(db,
            'INSERT INTO saved_addresses(user_id,kind,details_encrypted) VALUES($1,$2,$3) RETURNING *',
            [actor.id, body.kind, value]));
        }
        return addressView(await one(db, `INSERT INTO saved_addresses(user_id,kind,details_encrypted)
          VALUES($1,$2,$3) ON CONFLICT(user_id,kind) WHERE kind IN ('HOME','WORK')
          DO UPDATE SET details_encrypted=EXCLUDED.details_encrypted,updated_at=now() RETURNING *`,
        [actor.id, body.kind, value]));
      });
    },
    async removeAddress(actor, id) {
      return transaction(pool, async db => {
        await lockUser(db, actor);
        ensure(await one(db, 'DELETE FROM saved_addresses WHERE user_id=$1 AND id=$2 RETURNING id',
          [actor.id, id]), 404, 'SAVED_ADDRESS_NOT_FOUND');
        return { removed: true };
      });
    },
    async saveContact(actor, body) {
      const phone = normalizePhone(body.phone);
      const phoneKey = hash(`trusted-contact:${actor.id}:${phone}`, config.OTP_PEPPER);
      return transaction(pool, async db => {
        await lockUser(db, actor);
        const matched = await one(db, 'SELECT id FROM trusted_contacts WHERE user_id=$1 AND phone_key=$2', [actor.id, phoneKey]);
        if (!matched) {
          const count = await one(db, 'SELECT count(*)::int AS n FROM trusted_contacts WHERE user_id=$1', [actor.id]);
          ensure(count.n < 10, 409, 'TRUSTED_CONTACT_LIMIT');
        }
        return contactView(await one(db, `INSERT INTO trusted_contacts(user_id,phone_key,details_encrypted) VALUES($1,$2,$3)
          ON CONFLICT(user_id,phone_key) DO UPDATE SET details_encrypted=EXCLUDED.details_encrypted RETURNING *`,
        [actor.id, phoneKey, pack({ name: body.name, phone })]));
      });
    },
    async removeContact(actor, id) {
      return transaction(pool, async db => {
        await lockUser(db, actor);
        ensure(await one(db, 'DELETE FROM trusted_contacts WHERE user_id=$1 AND id=$2 RETURNING id',
          [actor.id, id]), 404, 'TRUSTED_CONTACT_NOT_FOUND');
        return { removed: true };
      });
    },
    async notifications(actor, app) {
      checkNotificationApp(actor, app);
      const row = await one(pool, 'SELECT preferences FROM notification_preferences WHERE user_id=$1 AND app=$2',
        [actor.id, app]);
      return { ...defaults[app], ...row?.preferences };
    },
    async clearNotifications(actor, app) {
      return transaction(pool, async db => {
        const user = await lockUser(db, actor);
        checkNotificationApp(user, app);
        await db.query(`INSERT INTO notification_preferences(user_id,app,inbox_cleared_at) VALUES($1,$2,now())
          ON CONFLICT(user_id,app) DO UPDATE SET inbox_cleared_at=EXCLUDED.inbox_cleared_at`, [actor.id, app]);
        return { cleared: true };
      });
    },
    async changeNotification(actor, { app, key, enabled }) {
      return transaction(pool, async db => {
        const user = await lockUser(db, actor);
        checkNotificationApp(user, app);
        const row = await one(db, `INSERT INTO notification_preferences(user_id,app,preferences) VALUES($1,$2,$3)
          ON CONFLICT(user_id,app) DO UPDATE SET preferences=notification_preferences.preferences || EXCLUDED.preferences
          RETURNING preferences`, [actor.id, app, { [key]: enabled }]);
        return { ...defaults[app], ...row.preferences };
      });
    },
  };
}
