import { randomUUID } from 'node:crypto';
import { equal, hash } from '../shared/crypto.js';
import { audit, one, transaction } from '../shared/db.js';
import { AppError, ensure } from '../shared/errors.js';
import { balance } from './ledger.js';

export function createAccountDeletion(pool, config, { lockPhones, queueOtp, twoStep }) {
  async function lockAccount(db, actor) {
    await lockPhones(db, [actor.phone]);
    const user = ensure(await one(db, `SELECT * FROM users
      WHERE id=$1 AND phone=$2 AND NOT suspended AND deleted_at IS NULL FOR UPDATE`,
    [actor.id, actor.phone]), 401, 'SESSION_REVOKED');
    ensure(!user.roles.some(role => ['ADMIN', 'STAFF'].includes(role)), 403, 'MOBILE_ACCOUNT_REQUIRED');
    const session = ensure(await one(db, `SELECT family_id FROM sessions WHERE id=$1 AND user_id=$2
      AND revoked_at IS NULL AND expires_at>now() FOR UPDATE`, [actor.session_id, user.id]), 401, 'SESSION_REVOKED');
    return { user, session };
  }

  async function ensureEligible(db, id) {
    const driver = await one(db, 'SELECT availability FROM driver_profiles WHERE user_id=$1 FOR UPDATE', [id]);
    ensure(!['RESERVED', 'ON_TRIP'].includes(driver?.availability), 409, 'ACCOUNT_DELETION_RIDES_PENDING');
    const rides = await one(db, `SELECT 1 FROM rides WHERE (rider_id=$1 OR driver_id=$1)
      AND state NOT IN ('COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW') LIMIT 1`, [id]);
    ensure(!rides, 409, 'ACCOUNT_DELETION_RIDES_PENDING');
    const pending = await one(db, `SELECT 1 WHERE
      EXISTS(SELECT 1 FROM cancellation_charges WHERE rider_id=$1 AND status='DUE')
      OR EXISTS(SELECT 1 FROM payouts WHERE driver_id=$1 AND status IN ('PENDING','APPROVED'))
      OR EXISTS(SELECT 1 FROM rides r JOIN payments p ON p.ride_id=r.id
        WHERE (r.rider_id=$1 OR r.driver_id=$1) AND p.status<>'COLLECTED')
      OR EXISTS(SELECT 1 FROM rides r JOIN payment_disputes d ON d.ride_id=r.id
        WHERE (r.rider_id=$1 OR r.driver_id=$1) AND d.status='OPEN')`, [id]);
    ensure(!pending && (!driver || Number((await balance(db, id)).net_minor) === 0), 409, 'ACCOUNT_DELETION_PAYMENTS_PENDING');
  }

  async function eraseAccount(db, user) {
    const id = user.id;
    const refs = (await db.query(`SELECT id,false AS support FROM profile_photo_uploads WHERE user_id=$1
      UNION SELECT id,false FROM documents WHERE driver_id=$1
      UNION SELECT id,true FROM support_attachments WHERE user_id=$1`, [id])).rows;
    const prefixes = [`profiles/${id}/`, `drivers/${id}/`, ...(refs.some(ref => ref.support) ? [`support/${id}/`] : []), ...refs.map(ref => `verified/${ref.id}/`)];
    await db.query(`INSERT INTO jobs(kind,payload,run_at)
      SELECT 'purge_account_storage', jsonb_build_object('prefix',prefix), now()+interval '10 minutes'
      FROM unnest($1::text[]) AS prefix`, [prefixes]);

    // Keep an anonymous reference for rides, payouts and the immutable ledger.
    await db.query(`UPDATE users SET phone='deleted:' || id::text,name='',email='',permissions='{}',
      deleted_at=now(),suspended=true,password_hash=NULL,totp_secret=NULL,totp_counter=-1,
      recovery_password_hash=NULL,recovery_attempts=0,recovery_locked_until=NULL,mobile_totp_secret=NULL,
      mobile_totp_counter=-1,mobile_totp_pending=NULL,mobile_recovery_hashes='{}',
      profile_photo_key=NULL,profile_photo_version=NULL,profile_completed_at=NULL,
      rider_birth_date=NULL,rider_gender=NULL,rider_city=NULL WHERE id=$1`, [id]);
    await db.query(`UPDATE driver_profiles SET status='SUSPENDED',availability='OFFLINE',review_reason=NULL,
      application_encrypted=NULL,contact_encrypted=NULL,profile_photo_key=NULL,profile_photo_version=NULL,
      application_revision=NULL,application_vehicle_id=NULL,application_document_ids=NULL,submitted_at=NULL WHERE user_id=$1`, [id]);
    await db.query("UPDATE vehicles SET registration='deleted:' || id::text,status='REJECTED',make=NULL,model=NULL,model_year=NULL,color=NULL WHERE driver_id=$1", [id]);
    await db.query('DELETE FROM documents WHERE driver_id=$1', [id]);
    await db.query('DELETE FROM driver_locations WHERE driver_id=$1', [id]);
    await db.query('DELETE FROM profile_photo_uploads WHERE user_id=$1', [id]);
    await db.query('DELETE FROM sessions WHERE user_id=$1', [id]);
    await db.query('DELETE FROM mobile_login_steps WHERE user_id=$1', [id]);
    await db.query('DELETE FROM device_tokens WHERE user_id=$1', [id]);
    await db.query('DELETE FROM support_attachments WHERE user_id=$1', [id]);
    await db.query('DELETE FROM support_tickets WHERE user_id=$1', [id]);
    await db.query('DELETE FROM saved_addresses WHERE user_id=$1', [id]);
    await db.query('DELETE FROM trusted_contacts WHERE user_id=$1', [id]);
    await db.query('DELETE FROM notification_preferences WHERE user_id=$1', [id]);
    await db.query('DELETE FROM notifications WHERE user_id=$1', [id]);
    await db.query('DELETE FROM idempotency_records WHERE actor_id=$1', [id]);
    await db.query('DELETE FROM fare_quotes q WHERE q.rider_id=$1 AND NOT EXISTS(SELECT 1 FROM rides r WHERE r.quote_id=q.id)', [id]);
    await db.query(`UPDATE jobs SET payload='{}',done_at=now(),lease_until=NULL,lease_token=NULL
      WHERE kind='otp' AND (payload->>'phone'=$1 OR payload->>'challengeId' IN (
        SELECT id::text FROM otp_challenges WHERE owner_id=$2))`, [user.phone, id]);
    await db.query('DELETE FROM otp_challenges WHERE owner_id=$1 OR phone=$2', [id, user.phone]);
    await audit(db, id, 'ACCOUNT_DELETED', id);
  }

  return {
    async request(actor) {
      return transaction(pool, async db => {
        const { user, session } = await lockAccount(db, actor);
        await ensureEligible(db, user.id);
        const challenge = await queueOtp(db, user.phone, {
          purpose: 'ACCOUNT_DELETION', ownerId: user.id, changeId: randomUUID(), familyId: session.family_id,
        });
        return { ...challenge, twoStepRequired: Boolean(user.mobile_totp_secret) };
      });
    },

    async confirm(actor, { challengeId, code, factor }) {
      const result = await transaction(pool, async db => {
        const { user, session } = await lockAccount(db, actor);
        const challenge = await one(db, `SELECT * FROM otp_challenges
          WHERE id=$1 AND owner_id=$2 AND phone=$3 AND session_family_id=$4
          AND purpose='ACCOUNT_DELETION' AND expires_at>now() AND used_at IS NULL AND attempts<5 FOR UPDATE`,
        [challengeId, user.id, user.phone, session.family_id]);
        if (!challenge) return null;
        await db.query('UPDATE otp_challenges SET attempts=attempts+1 WHERE id=$1', [challenge.id]);
        if (!equal(challenge.code_hash, hash(code, config.OTP_PEPPER))) return null;
        if (user.mobile_totp_secret && (!factor || !await twoStep.consumeForAccountAction(db, user, factor))) return null;
        await ensureEligible(db, user.id);
        await eraseAccount(db, user);
        return { deleted: true };
      });
      if (!result) throw new AppError(400, 'ACCOUNT_DELETION_CODE_INVALID');
      return result;
    },
  };
}
