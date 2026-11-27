import { z } from 'zod';
import { randomUUID } from 'node:crypto';
import { decrypt, encrypt } from '../shared/crypto.js';
import { one, event, audit } from '../shared/db.js';
import { ensure } from '../shared/errors.js';

const short = z.string().trim().min(1).max(100);
const identifier = z.string().trim().regex(/^[\p{L}\p{N} ./-]{2,40}$/u);
const time = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/);
const distinct = (items) => new Set(items).size === items.length;
export const applicationSchema = z.strictObject({
  name: z.string().trim().min(2).max(100),
  email: z.email().max(254).optional(),
  dateOfBirth: z.iso.date(),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  city: short.optional(),
  consent: z.strictObject({
    termsVersion: z.literal('1'),
    termsAccepted: z.literal(true),
    locationAccepted: z.literal(true),
  }),
  identity: z.strictObject({
    number: identifier,
    issuedOn: z.iso.date().optional(),
    expiresOn: z.iso.date().optional(),
  }),
  license: z.strictObject({ number: identifier, class: z.string().trim().regex(/^[A-Z0-9]{1,12}$/), expiresOn: z.iso.date() }),
  schedule: z.strictObject({
    workType: z.enum(['FULL_TIME', 'PART_TIME']),
    days: z.array(z.number().int().min(1).max(7)).min(1).max(7).refine(distinct),
    start: time,
    end: time,
    remind: z.boolean(),
  }).refine(({ start, end }) => start !== end, 'Shift times must differ'),
  serviceArea: z.strictObject({
    city: short,
    radiusKm: z.number().min(5).max(50),
    zones: z.array(z.enum(['CENTRE', 'AIRPORT', 'HARBOUR', 'UNIVERSITY', 'STATION', 'SUBURBS']))
      .min(1).max(6).refine(distinct),
    acceptOutside: z.boolean(),
  }),
  bank: z.strictObject({
    holder: short,
    bank: short,
    branch: short,
    number: z.string().regex(/^\d{4,34}$/),
  }).optional(),
});
export const vehicleDetails = {
  make: short.optional(),
  model: short.optional(),
  modelYear: z.number().int().min(1900).max(new Date().getUTCFullYear() + 1).optional(),
  color: short.optional(),
};
export const requiredKinds = [
  'PROFILE_PHOTO', 'IDENTITY_FRONT', 'IDENTITY_BACK', 'LICENSE_FRONT', 'LICENSE_BACK',
  'REGISTRATION', 'REVENUE_LICENSE', 'INSURANCE',
  'VEHICLE_FRONT', 'VEHICLE_REAR', 'VEHICLE_LEFT', 'VEHICLE_RIGHT',
];
export const vehicleKinds = [
  'REGISTRATION', 'REVENUE_LICENSE', 'INSURANCE', 'EMISSION',
  'VEHICLE_FRONT', 'VEHICLE_REAR', 'VEHICLE_LEFT', 'VEHICLE_RIGHT', 'VEHICLE_INTERIOR', 'VEHICLE_EXTRA',
];
export const expiringKinds = ['LICENSE', 'LICENSE_FRONT', 'LICENSE_BACK', 'REGISTRATION', 'INSURANCE', 'REVENUE_LICENSE', 'EMISSION'];
export const documentKinds = [...new Set(['IDENTITY', 'LICENSE', ...requiredKinds, ...vehicleKinds])];
export const submitSchema = z.strictObject({
  vehicleId: z.uuid(),
  documentIds: z.array(z.uuid()).min(requiredKinds.length).max(documentKinds.length).refine(distinct),
  informationConfirmed: z.literal(true),
});
export const profileFields = 'user_id,status,availability,idle_since,review_reason,created_at,application_revision,application_vehicle_id,submitted_at';
export const documentFields = 'id,vehicle_id,kind,expires_at,scan_status,submitted_at,created_at';

export function readApplication(profile, key) {
  return profile.application_encrypted
    ? JSON.parse(decrypt(profile.application_encrypted, key))
    : null;
}

export function publicProfile(profile) {
  if (!profile) return null;
  const { application_encrypted, contact_encrypted, payout_bank_encrypted, profile_photo_key, profile_photo_version, ...safe } = profile;
  return safe;
}

export function driverContact(profile, saved, user, key) {
  const contact = profile.contact_encrypted ? JSON.parse(decrypt(profile.contact_encrypted, key)) : {};
  return {
    name: contact.name ?? saved?.name ?? user.name,
    email: contact.email ?? saved?.email ?? user.email ?? '',
    address: contact.address ?? '',
    preferredLanguage: contact.preferredLanguage ?? '',
    photoVisible: contact.photoVisible ?? true,
    dateOfBirth: saved?.dateOfBirth ?? null,
    identityNumber: saved?.identity?.number ?? null,
  };
}

export async function updateDriverContact(db, user, body, key) {
  const profile = ensure(await one(db, 'SELECT * FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]),
    404, 'DRIVER_NOT_FOUND');
  ensure(profile.status !== 'SUSPENDED', 409, 'DRIVER_SUSPENDED');
  const saved = readApplication(profile, key);
  const current = driverContact(profile, saved, user, key);
  const { dateOfBirth, identityNumber, ...contact } = body;
  ensure((dateOfBirth === undefined || dateOfBirth === current.dateOfBirth) &&
    (identityNumber === undefined || identityNumber === current.identityNumber),
    409, 'DRIVER_IDENTITY_REVIEW_REQUIRED');
  const updated = { ...current, ...contact };
  const { dateOfBirth: dob, identityNumber: nic, ...editable } = updated;
  await db.query('UPDATE driver_profiles SET contact_encrypted=$2 WHERE user_id=$1',
    [user.id, encrypt(JSON.stringify(editable), key)]);
  await audit(db, user.id, 'DRIVER_PROFILE_UPDATED', user.id);
  return updated;
}

export async function beginReapplication(db, id) {
  const profile = ensure(await one(db, 'SELECT * FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [id]),
    404, 'DRIVER_NOT_FOUND');
  ensure(['APPROVED','DRAFT','REJECTED'].includes(profile.status), 409, 'APPLICATION_NOT_EDITABLE');
  ensure(!['RESERVED','ON_TRIP'].includes(profile.availability), 409, 'DRIVER_BUSY');
  if (profile.status !== 'APPROVED') return { status: profile.status };
  ensure(!(await one(db, `SELECT id FROM rides WHERE driver_id=$1
    AND state NOT IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW') LIMIT 1`, [id])), 409, 'DRIVER_BUSY');
  await db.query("UPDATE driver_profiles SET status='DRAFT',availability='OFFLINE',review_reason=NULL WHERE user_id=$1", [id]);
  await audit(db, id, 'DRIVER_REAPPLICATION_STARTED', id);
  return { status: 'DRAFT' };
}

export async function editableProfile(db, id) {
  const profile = ensure(await one(db, 'SELECT * FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [id]),
    404, 'DRIVER_NOT_FOUND');
  ensure(['DRAFT', 'REJECTED'].includes(profile.status), 409, 'APPLICATION_NOT_EDITABLE');
  return profile;
}

export async function saveApplication(db, id, body, key) {
  const profile = await editableProfile(db, id);
  const today = new Date().toISOString().slice(0, 10);
  const { dateOfBirth, identity, license, consent } = body;
  const adultCutoff = (Number(today.slice(0, 4)) - 18) + today.slice(4);
  ensure(dateOfBirth <= adultCutoff && dateOfBirth >= '1900-01-01', 400, 'INVALID_BIRTH_DATE');
  ensure(!identity.issuedOn || identity.issuedOn <= today, 400, 'INVALID_IDENTITY_ISSUE_DATE');
  ensure(!identity.expiresOn || identity.expiresOn > today, 400, 'DOCUMENT_EXPIRED');
  ensure(!identity.issuedOn || !identity.expiresOn || identity.issuedOn < identity.expiresOn,
    400, 'INVALID_IDENTITY_DATE_RANGE');
  ensure(license.expiresOn > today, 400, 'DOCUMENT_EXPIRED');
  const acceptedAt = readApplication(profile, key)?.consent.acceptedAt ?? new Date().toISOString();
  await db.query('UPDATE driver_profiles SET application_encrypted=$2 WHERE user_id=$1', [
    id, encrypt(JSON.stringify({ ...body, consent: { ...consent, acceptedAt } }), key),
  ]);
  return { saved: true };
}

export async function selectedDocuments(db, driverId, vehicleId, ids) {
  return (await db.query(
    `SELECT ${documentFields},(expires_at IS NULL OR expires_at>now()) AS unexpired
     FROM documents WHERE driver_id=$1 AND id=ANY($2::uuid[]) AND (vehicle_id IS NULL OR vehicle_id=$3)`,
    [driverId, ids, vehicleId],
  )).rows;
}

export function checkDocuments(docs, ids, application) {
  const byKind = new Map(docs.map((doc) => [doc.kind, doc]));
  ensure(docs.length === ids.length && byKind.size === docs.length &&
    requiredKinds.every((kind) => byKind.has(kind)), 409, 'APPLICATION_DOCUMENTS_INCOMPLETE');
  ensure(docs.every((doc) => doc.scan_status === 'CLEAN' && doc.unexpired && doc.submitted_at),
    409, 'APPLICATION_DOCUMENTS_NOT_READY');
  for (const [kind, expected] of [
    ['LICENSE_FRONT', application.license.expiresOn], ['LICENSE_BACK', application.license.expiresOn],
    ['IDENTITY_FRONT', application.identity.expiresOn], ['IDENTITY_BACK', application.identity.expiresOn],
  ]) {
    const actual = byKind.get(kind).expires_at?.toISOString().slice(0, 10);
    ensure(actual === expected, 409, 'APPLICATION_DOCUMENT_EXPIRY_MISMATCH');
  }
}

export async function submitApplication(db, id, body, key) {
  const profile = await editableProfile(db, id);
  const application = ensure(readApplication(profile, key), 409, 'APPLICATION_DETAILS_REQUIRED');
  const vehicle = await one(db,
    'SELECT make,model,model_year,color FROM vehicles WHERE id=$1 AND driver_id=$2', [body.vehicleId, id]);
  ensure(vehicle?.make && vehicle.model && vehicle.model_year && vehicle.color, 409, 'APPLICATION_VEHICLE_DETAILS_REQUIRED');
  checkDocuments(await selectedDocuments(db, id, body.vehicleId, body.documentIds), body.documentIds, application);
  const result = await one(db,
    `UPDATE driver_profiles SET status='PENDING',review_reason=NULL,application_revision=$2,
     application_vehicle_id=$3,application_document_ids=$4,submitted_at=now()
     WHERE user_id=$1 RETURNING ${profileFields}`,
    [id, randomUUID(), body.vehicleId, body.documentIds]);
  await event(db, 'driver.application_submitted', id, { revision: result.application_revision }, [id]);
  return result;
}
