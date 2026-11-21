import { randomBytes, randomUUID } from 'node:crypto';
import * as OTPAuth from 'otpauth';
import { decrypt, encrypt, equal, hash, token } from '../shared/crypto.js';
import { audit, one, transaction } from '../shared/db.js';
import { AppError, ensure } from '../shared/errors.js';

function authenticator(secret, phone) {
  return new OTPAuth.TOTP({
    issuer: 'RideNow', label: phone, algorithm: 'SHA1', digits: 6, period: 30,
    secret: OTPAuth.Secret.fromBase32(secret),
  });
}

function acceptedCounter(secret, phone, code) {
  if (!/^\d{6}$/.test(code)) return null;
  const timestamp = Date.now();
  const delta = authenticator(secret, phone).validate({ token: code, timestamp, window: 1 });
  return delta === null ? null : Math.floor(timestamp / 30000) + delta;
}

export function createMobileTwoStep(pool, config, { lockPhones, queueOtp, issueMobile }) {
  const isMobile = user => user && !user.suspended && !user.deleted_at &&
    !user.roles.some(role => ['ADMIN', 'STAFF'].includes(role));
  const ticketHash = value => hash(`mobile-login:${value}`, config.JWT_SECRET);
  const recoveryHash = (id, code) =>
    hash(`mobile-recovery:${id}:${code.toUpperCase().replaceAll('-', '')}`, config.OTP_PEPPER);

  async function withCurrent(db, actor) {
    const user = await one(db, 'SELECT * FROM users WHERE id=$1 FOR UPDATE', [actor.id]);
    ensure(isMobile(user), 403, 'MOBILE_ACCOUNT_REQUIRED');
    const session = ensure(await one(db, `SELECT id,family_id FROM sessions
      WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL AND expires_at>now() FOR UPDATE`,
    [actor.session_id, actor.id]), 401, 'SESSION_REVOKED');
    return { user, session };
  }

  async function consumeFactor(db, user, code) {
    const secret = decrypt(user.mobile_totp_secret, config.ENCRYPTION_KEY);
    const counter = acceptedCounter(secret, user.phone, code);
    if (counter !== null && counter > Number(user.mobile_totp_counter)) {
      await db.query('UPDATE users SET mobile_totp_counter=$2 WHERE id=$1', [user.id, counter]);
      return true;
    }
    const supplied = recoveryHash(user.id, code);
    const matched = user.mobile_recovery_hashes.find(value => equal(value, supplied));
    if (!matched) return false;
    await db.query('UPDATE users SET mobile_recovery_hashes=array_remove(mobile_recovery_hashes,$2) WHERE id=$1',
      [user.id, matched]);
    return true;
  }

  async function makeRecoveryCodes(db, id) {
    const recoveryCodes = Array.from({ length: 8 }, () =>
      randomBytes(12).toString('hex').toUpperCase().match(/.{4}/g).join('-'));
    await db.query('UPDATE users SET mobile_recovery_hashes=$2 WHERE id=$1',
      [id, recoveryCodes.map(code => recoveryHash(id, code))]);
    return recoveryCodes;
  }

  async function revokeOtherSessions(db, user, session) {
    await db.query('UPDATE sessions SET revoked_at=now() WHERE user_id=$1 AND id<>$2 AND revoked_at IS NULL',
      [user.id, session.id]);
    await db.query('DELETE FROM mobile_login_steps WHERE user_id=$1', [user.id]);
  }

  return {
    consumeForAccountAction: consumeFactor,

    status(actor) {
      ensure(isMobile(actor), 403, 'MOBILE_ACCOUNT_REQUIRED');
      return { enabled: Boolean(actor.mobile_totp_secret), recoveryRemaining: actor.mobile_recovery_hashes.length };
    },

    async start(actor) {
      return transaction(pool, async db => {
        await lockPhones(db, [actor.phone]);
        const { user, session } = await withCurrent(db, actor);
        ensure(user.phone === actor.phone, 409, 'PHONE_CHANGED');
        ensure(!user.mobile_totp_secret, 409, 'TWO_STEP_ALREADY_ENABLED');
        const secret = new OTPAuth.Secret({ size: 20 }).base32;
        const challenge = await queueOtp(db, user.phone,
          { purpose: 'TWO_STEP_SETUP', ownerId: user.id, changeId: randomUUID() });
        await db.query('UPDATE users SET mobile_totp_pending=$2 WHERE id=$1', [user.id, {
          encrypted: encrypt(secret, config.ENCRYPTION_KEY),
          challengeId: challenge.challengeId,
          family: session.family_id,
        }]);
        return { secret, expiresAt: challenge.expiresAt };
      });
    },

    async enable(actor, { whatsappCode, code }) {
      const result = await transaction(pool, async db => {
        const { user, session } = await withCurrent(db, actor);
        const pending = user.mobile_totp_pending;
        if (user.mobile_totp_secret || pending?.family !== session.family_id) return { error: 'TWO_STEP_SETUP_INVALID' };
        const challenge = await one(db, `SELECT * FROM otp_challenges WHERE id=$1 AND owner_id=$2
          AND phone=$3 AND purpose='TWO_STEP_SETUP' AND used_at IS NULL AND expires_at>now() FOR UPDATE`,
        [pending.challengeId, user.id, user.phone]);
        if (!challenge || challenge.attempts >= 5) return { error: 'TWO_STEP_SETUP_INVALID' };
        await db.query('UPDATE otp_challenges SET attempts=attempts+1 WHERE id=$1', [challenge.id]);
        const secret = decrypt(pending.encrypted, config.ENCRYPTION_KEY);
        const counter = acceptedCounter(secret, user.phone, code);
        if (!equal(challenge.code_hash, hash(whatsappCode, config.OTP_PEPPER)) || counter === null)
          return { error: 'TWO_STEP_SETUP_INVALID' };
        await db.query('UPDATE otp_challenges SET used_at=now() WHERE id=$1', [challenge.id]);
        await db.query(`UPDATE users SET mobile_totp_secret=$2,mobile_totp_counter=$3,mobile_totp_pending=NULL
          WHERE id=$1`, [user.id, pending.encrypted, counter]);
        const recoveryCodes = await makeRecoveryCodes(db, user.id);
        await revokeOtherSessions(db, user, session);
        await audit(db, user.id, 'MOBILE_TWO_STEP_ENABLED', user.id);
        return { enabled: true, recoveryCodes };
      });
      if (result.error) throw new AppError(400, result.error);
      return result;
    },

    async update(actor, code, disable) {
      return transaction(pool, async db => {
        const { user, session } = await withCurrent(db, actor);
        ensure(user.mobile_totp_secret, 409, 'TWO_STEP_NOT_ENABLED');
        ensure(await consumeFactor(db, user, code), 400, 'TWO_STEP_INVALID');
        if (disable) {
          await db.query(`UPDATE users SET mobile_totp_secret=NULL,mobile_totp_counter=-1,
            mobile_recovery_hashes='{}',mobile_totp_pending=NULL WHERE id=$1`, [user.id]);
          await revokeOtherSessions(db, user, session);
          await audit(db, user.id, 'MOBILE_TWO_STEP_DISABLED', user.id);
          return { enabled: false };
        }
        const recoveryCodes = await makeRecoveryCodes(db, user.id);
        await audit(db, user.id, 'MOBILE_RECOVERY_ROTATED', user.id);
        return { enabled: true, recoveryCodes };
      });
    },

    async beginLogin(db, user, device) {
      const twoStepToken = token();
      await db.query(`INSERT INTO mobile_login_steps(token_hash,user_id,phone,device,expires_at)
        VALUES($1,$2,$3,$4,now()+interval '5 minutes')`, [ticketHash(twoStepToken), user.id, user.phone, device ?? null]);
      return { twoStepToken };
    },

    async verifyLogin(value, code) {
      const result = await transaction(pool, async db => {
        const key = ticketHash(value);
        const pending = await one(db, 'SELECT user_id FROM mobile_login_steps WHERE token_hash=$1', [key]);
        if (!pending) return null;
        const user = await one(db, 'SELECT * FROM users WHERE id=$1 FOR UPDATE', [pending.user_id]);
        if (!isMobile(user) || !user.mobile_totp_secret) return null;
        const step = await one(db, `SELECT * FROM mobile_login_steps
          WHERE token_hash=$1 AND expires_at>now() AND attempts<5 FOR UPDATE`, [key]);
        if (!step || step.phone !== user.phone) return null;
        await db.query('UPDATE mobile_login_steps SET attempts=attempts+1 WHERE token_hash=$1', [key]);
        if (!await consumeFactor(db, user, code)) return null;
        await db.query('DELETE FROM mobile_login_steps WHERE token_hash=$1', [key]);
        return issueMobile(db, user, step.device);
      });
      ensure(result, 401, 'TWO_STEP_INVALID');
      return result;
    },
  };
}
