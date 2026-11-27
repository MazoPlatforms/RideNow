import { decrypt, encrypt } from '../shared/crypto.js';
import { one, audit } from '../shared/db.js';
import { ensure } from '../shared/errors.js';
import * as ledger from './ledger.js';
import { readApplication } from './driver-application.js';

const bankShape = (value) => {
  if (!value) return null;
  return {
    holder: value.holder,
    bank: value.bank,
    branch: value.branch,
    number: value.number,
    type: value.type ?? 'Savings',
    verified: true,
  };
};

export async function payoutMethod(db, user, key) {
  const profile = ensure(await one(db,
    'SELECT payout_bank_encrypted,application_encrypted FROM driver_profiles WHERE user_id=$1', [user.id]),
  404, 'DRIVER_NOT_FOUND');
  if (profile.payout_bank_encrypted) return bankShape(JSON.parse(decrypt(profile.payout_bank_encrypted, key)));
  if (profile.application_encrypted) {
    const application = JSON.parse(decrypt(profile.application_encrypted, key));
    if (application.bank) return bankShape(application.bank);
  }
  return null;
}

export async function savePayoutMethod(db, user, value, key) {
  const profile = ensure(await one(db,
    'SELECT status,availability FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]),
  404, 'DRIVER_NOT_FOUND');
  ensure(profile.status === 'APPROVED', 409, 'DRIVER_NOT_APPROVED');
  ensure(!['RESERVED', 'ON_TRIP'].includes(profile.availability), 409, 'DRIVER_BUSY');
  const method = bankShape(value);
  await db.query('UPDATE driver_profiles SET payout_bank_encrypted=$2 WHERE user_id=$1',
    [user.id, encrypt(JSON.stringify(method), key)]);
  await audit(db, user.id, 'DRIVER_PAYOUT_METHOD_UPDATED', user.id);
  return method;
}

export async function removePayoutMethod(db, user, key) {
  const profile = ensure(await one(db,
    'SELECT status,availability,payout_bank_encrypted,application_encrypted FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]),
  404, 'DRIVER_NOT_FOUND');
  ensure(profile.status === 'APPROVED', 409, 'DRIVER_NOT_APPROVED');
  ensure(!['RESERVED', 'ON_TRIP'].includes(profile.availability), 409, 'DRIVER_BUSY');
  ensure(!(await one(db, "SELECT id FROM payouts WHERE driver_id=$1 AND status IN ('PENDING','APPROVED') LIMIT 1", [user.id])),
    409, 'PAYOUT_PENDING');
  let applicationEncrypted = profile.application_encrypted;
  const application = profile.application_encrypted ? readApplication(profile, key) : null;
  if (application?.bank) {
    delete application.bank;
    applicationEncrypted = encrypt(JSON.stringify(application), key);
  }
  await db.query('UPDATE driver_profiles SET payout_bank_encrypted=NULL,application_encrypted=$2 WHERE user_id=$1',
    [user.id, applicationEncrypted]);
  await audit(db, user.id, 'DRIVER_PAYOUT_METHOD_REMOVED', user.id);
  return { removed: true };
}

export async function createDriverPayout(db, user, amountMinor, key) {
    const profile = ensure(await one(db,
      'SELECT payout_bank_encrypted,application_encrypted FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [user.id]),
    404, 'DRIVER_NOT_FOUND');
    const bank = profile.payout_bank_encrypted
      ? JSON.parse(decrypt(profile.payout_bank_encrypted, key))
      : readApplication(profile, key)?.bank;
    ensure(Boolean(bank), 409, 'PAYOUT_METHOD_REQUIRED');
    return ledger.createPayout(db, user, {
      driverId: user.id,
      amountMinor,
      reason: 'Driver withdrawal request',
    });
}

export async function myPayouts(db, user, limit = 30) {
  const [balance, payouts] = await Promise.all([
    ledger.balance(db, user.id),
    db.query('SELECT id,amount_minor,status,created_at FROM payouts WHERE driver_id=$1 ORDER BY created_at DESC,id DESC LIMIT $2', [user.id, limit]),
  ]);
  return { balance, items: payouts.rows };
}
