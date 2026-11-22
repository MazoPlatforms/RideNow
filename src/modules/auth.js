import { randomInt, randomUUID } from 'node:crypto';
import { SignJWT, jwtVerify } from 'jose';
import bcrypt from 'bcryptjs';
import { parsePhoneNumberFromString } from 'libphonenumber-js/max';
import * as OTPAuth from 'otpauth';
import { hash, token, equal, encrypt, decrypt } from '../shared/crypto.js';
import { transaction, one, audit } from '../shared/db.js';
import { AppError, ensure } from '../shared/errors.js';
import { createAccountDeletion } from './account-deletion.js';
import { createMobileTwoStep } from './mobile-two-step.js';
import { riderProfileComplete } from './rider-profile.js';
export function normalizePhone(input) {
  ensure(typeof input === 'string', 400, 'INVALID_PHONE_NUMBER');
  const compact = input.trim().replace(/[\s().-]/g, '');
  let candidate = compact;
  let defaultCountry;
  if (compact.startsWith('00')) candidate = '+' + compact.slice(2);
  else if (!compact.startsWith('+')) {
    if (compact.startsWith('0')) defaultCountry = 'LK';
    else candidate = '+' + compact;
  }
  const parsed = parsePhoneNumberFromString(candidate, defaultCountry);
  ensure(parsed?.isValid() && !parsed.ext, 400, 'INVALID_PHONE_NUMBER');
  return parsed.number;
}
export async function consumeOtp(db, config, id, code, expectedPhone) {
  const c = await one(db, 'SELECT * FROM otp_challenges WHERE id=$1 FOR UPDATE', [id]);
  if (!c || c.purpose !== 'LOGIN' || c.used_at || c.expires_at < Date.now() || c.attempts >= 5 ||
      (expectedPhone && c.phone !== expectedPhone)) return { error: 'OTP_INVALID' };
  await db.query('UPDATE otp_challenges SET attempts=attempts+1 WHERE id=$1', [id]);
  if (!equal(c.code_hash, hash(code, config.OTP_PEPPER))) return { error: 'OTP_INVALID' };
  await db.query('UPDATE otp_challenges SET used_at=now() WHERE id=$1', [id]);
  return c;
}
export function createAuth(pool, config) {
  const key = new TextEncoder().encode(config.JWT_SECRET);
  async function passwordMatches(user, password, proof) {
    if (proof) return proof.userId === user.id && typeof proof.digest === 'string' &&
      equal(proof.digest, hash(user.password_hash, config.JWT_SECRET));
    return typeof password === 'string' && await bcrypt.compare(password, user.password_hash);
  }
  async function issue(db, user, family, device) {
    const refreshToken = token();
    const session = await one(
      db,
      `INSERT INTO sessions(user_id,family_id,token_hash,expires_at,device_key,device_name,device_app)
       VALUES($1,COALESCE($2,gen_random_uuid()),$3,now()+interval '30 days',$4,$5,$6) RETURNING id`,
      [user.id, family ?? null, hash(refreshToken, config.JWT_SECRET),
        device?.device_key ?? null, device?.device_name ?? null, device?.device_app ?? null],
    );
    const accessToken = await new SignJWT({ sid: session.id })
      .setProtectedHeader({ alg: 'HS256' })
      .setIssuer('mazo-rides')
      .setAudience('mazo-clients')
      .setSubject(user.id)
      .setIssuedAt()
      .setExpirationTime('15m')
      .sign(key);
    return {
      accessToken,
      refreshToken,
      expiresIn: 900,
      user: {
        id: user.id,
        phone: user.phone,
        roles: user.roles,
        name: user.name,
        email: user.email ?? '',
        profileComplete: await riderProfileComplete(db, user),
      },
    };
  }
  function installation(userId, device) {
    return {
      device_key: hash(`installation:${userId}:${device.app}:${device.installationId.toLowerCase()}`, config.JWT_SECRET),
      device_name: device.name,
      device_app: device.app,
    };
  }
  async function currentDeviceSession(db, actor) {
    const user = ensure(await one(db, 'SELECT roles FROM users WHERE id=$1 AND NOT suspended AND deleted_at IS NULL FOR UPDATE',
      [actor.id]), 401, 'SESSION_REVOKED');
    ensure(!user.roles.some(role => ['ADMIN','STAFF'].includes(role)), 403, 'MOBILE_ACCOUNT_REQUIRED');
    const current = ensure(await one(db, `SELECT * FROM sessions
      WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL AND expires_at>now() FOR UPDATE`,
    [actor.session_id, actor.id]), 401, 'SESSION_REVOKED');
    return current;
  }
  async function lockPhones(db, phones) {
    for (const phone of [...new Set(phones)].sort()) {
      await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,1))', [phone]);
    }
  }
  async function queueOtp(db, phone, { purpose = 'LOGIN', ownerId = null, changeId = null, familyId = null } = {}) {
    const recent = await one(db,
      "SELECT count(*)::int AS n,max(created_at) AS latest FROM otp_challenges WHERE phone=$1 AND created_at>now()-interval '1 hour'",
      [phone]);
    ensure(recent.n < 10 && (!recent.latest || Date.now() - recent.latest >= 60000), 429, 'OTP_RATE_LIMIT');
    if (config.OTP_PROVIDER === 'baileys') {
      const health = await one(db, "SELECT updated_at FROM system_state WHERE key='whatsapp.ready' AND value='true'::jsonb");
      ensure(health && Date.now() - health.updated_at < 30000, 503, 'OTP_PROVIDER_UNAVAILABLE');
    }
    await db.query(
      `UPDATE otp_challenges SET used_at=now()
       WHERE phone=$1 AND used_at IS NULL AND purpose=$2 AND owner_id IS NOT DISTINCT FROM $3`,
      [phone, purpose, ownerId]);
    const code = String(randomInt(100000, 1000000));
    const challenge = await one(db,
      `INSERT INTO otp_challenges(phone,code_hash,expires_at,purpose,owner_id,change_id,session_family_id)
       VALUES($1,$2,now()+interval '5 minutes',$3,$4,$5,$6) RETURNING id,expires_at`,
      [phone, hash(code, config.OTP_PEPPER), purpose, ownerId, changeId, familyId]);
    await db.query("INSERT INTO jobs(kind,payload) VALUES('otp',$1)", [
      { challengeId: challenge.id, phone, encryptedCode: encrypt(code, config.ENCRYPTION_KEY), purpose },
    ]);
    return { challengeId: challenge.id, expiresAt: challenge.expires_at };
  }
  async function currentPhoneAccount(db, actor) {
    const user = ensure(await one(db, `SELECT * FROM users WHERE id=$1 AND NOT suspended AND deleted_at IS NULL
      FOR UPDATE`, [actor.id]), 401, 'SESSION_REVOKED');
    ensure(!user.roles.some(role => ['ADMIN','STAFF'].includes(role)), 403, 'STAFF_PHONE_CHANGE_NOT_ALLOWED');
    ensure(await one(db, `SELECT id FROM sessions WHERE id=$1 AND user_id=$2
      AND revoked_at IS NULL AND expires_at>now() FOR UPDATE`, [actor.session_id, user.id]), 401, 'SESSION_REVOKED');
    return user;
  }
  async function issueMobile(db, user, device) {
    if (device) await db.query(`UPDATE sessions SET revoked_at=now() WHERE user_id=$1
      AND device_app=$2 AND device_key=$3 AND revoked_at IS NULL`, [user.id, device.device_app, device.device_key]);
    return issue(db, user, undefined, device);
  }
  const twoStep = createMobileTwoStep(pool, config, { lockPhones, queueOtp, issueMobile });
  return {
    twoStep,
    accountDeletion: createAccountDeletion(pool, config, { lockPhones, queueOtp, twoStep }),
    async adminPasswordProof(phone, password) {
      const user = await one(pool, "SELECT * FROM users WHERE phone=$1 AND roles && ARRAY['ADMIN','STAFF']::text[]", [normalizePhone(phone)]);
      ensure(user?.password_hash && !user.suspended && !user.deleted_at &&
        await passwordMatches(user, password), 401, 'INVALID_CREDENTIALS');
      return { userId: user.id, phone: user.phone, digest: hash(user.password_hash, config.JWT_SECRET),
        totp: Boolean(user.totp_secret), roles: user.roles };
    },
    async requestOtp(input) {
      const phone = normalizePhone(input);
      return transaction(pool, async (db) => {
        await lockPhones(db, [phone]);
        return queueOtp(db, phone);
      });
    },
    async requestPhoneChange(actor, input) {
      const next = normalizePhone(input);
      return transaction(pool, async (db) => {
        await lockPhones(db, [actor.phone, next]);
        const user = await currentPhoneAccount(db, actor);
        ensure(user.phone === actor.phone && next !== user.phone, 400, 'PHONE_CHANGE_INVALID');
        ensure(!(await one(db, 'SELECT id FROM users WHERE phone=$1', [next])),
          409, 'PHONE_UNAVAILABLE');
        const changeId = randomUUID();
        const scope = { changeId, ownerId: user.id };
        const current = await queueOtp(db, user.phone, { ...scope, purpose: 'PHONE_CURRENT' });
        await queueOtp(db, next, { ...scope, purpose: 'PHONE_NEW' });
        return { changeId, phone: next, expiresAt: current.expiresAt };
      });
    },
    async verifyPhoneChange(actor, { changeId, currentCode, newCode }) {
      const result = await transaction(pool, async (db) => {
        const proposed = await one(db, `SELECT phone FROM otp_challenges
          WHERE change_id=$1 AND owner_id=$2 AND purpose='PHONE_NEW'`, [changeId, actor.id]);
        if (!proposed) return { error: 'OTP_INVALID' };
        await lockPhones(db, [actor.phone, proposed.phone]);
        const user = await currentPhoneAccount(db, actor);
        const rows = (await db.query(`SELECT * FROM otp_challenges WHERE owner_id=$1 AND change_id=$2
          ORDER BY purpose FOR UPDATE`, [user.id, changeId])).rows;
        const current = rows.find(row => row.purpose === 'PHONE_CURRENT');
        const next = rows.find(row => row.purpose === 'PHONE_NEW');
        if (rows.length !== 2 || current?.phone !== user.phone || next?.phone !== proposed.phone ||
          rows.some(row => row.used_at || row.expires_at <= Date.now() || row.attempts >= 5)) {
          return { error: 'OTP_INVALID' };
        }
        await db.query('UPDATE otp_challenges SET attempts=attempts+1 WHERE change_id=$1', [changeId]);
        const currentOk = equal(current.code_hash, hash(currentCode, config.OTP_PEPPER));
        const nextOk = equal(next.code_hash, hash(newCode, config.OTP_PEPPER));
        if (!currentOk || !nextOk) return { error: 'OTP_INVALID' };
        ensure(!(await one(db, 'SELECT id FROM users WHERE phone=$1', [next.phone])),
          409, 'PHONE_UNAVAILABLE');
        const updated = await one(db, 'UPDATE users SET phone=$2 WHERE id=$1 RETURNING *', [user.id, next.phone]);
        await db.query('UPDATE otp_challenges SET used_at=now() WHERE phone=ANY($1) AND used_at IS NULL',
          [[current.phone, next.phone]]);
        await db.query('UPDATE sessions SET revoked_at=now() WHERE user_id=$1 AND revoked_at IS NULL', [user.id]);
        await db.query('DELETE FROM mobile_login_steps WHERE user_id=$1', [user.id]);
        await db.query('UPDATE users SET mobile_totp_pending=NULL WHERE id=$1', [user.id]);
        await audit(db, user.id, 'ACCOUNT_PHONE_CHANGED', user.id);
        const device = await one(db, 'SELECT device_key,device_name,device_app FROM sessions WHERE id=$1', [actor.session_id]);
        return issue(db, updated, undefined, device);
      });
      if (result.error) throw new AppError(400, result.error);
      return result;
    },
    async verifyOtp(id, code, inputDevice) {
      const result = await transaction(pool, async (db) => {
        const challenge = await one(db, "SELECT phone FROM otp_challenges WHERE id=$1 AND purpose='LOGIN'", [id]);
        if (!challenge) return { error: 'OTP_INVALID' };
        await lockPhones(db, [challenge.phone]);
        const c = await consumeOtp(db, config, id, code);
        if (c.error) return c;
        const user = await one(
          db,
          'INSERT INTO users(phone) VALUES($1) ON CONFLICT(phone) DO UPDATE SET phone=EXCLUDED.phone RETURNING *',
          [c.phone],
        );
        if (user.suspended || user.deleted_at) return { error: 'ACCOUNT_UNAVAILABLE' };
        if (user.roles.some((r) => ['ADMIN', 'STAFF'].includes(r)))
          return { error: 'STAFF_LOGIN_REQUIRED' };
        const device = inputDevice ? installation(user.id, inputDevice) : undefined;
        if (user.mobile_totp_secret) return twoStep.beginLogin(db, user, device);
        return issueMobile(db, user, device);
      });
      if (result.error) throw new AppError(401, result.error);
      return result;
    },
    async staffLogin(phone, password, otp, challengeId, code, proof) {
      const user = await one(pool, 'SELECT * FROM users WHERE phone=$1', [normalizePhone(phone)]);
      ensure(
        user?.password_hash && user.roles.some((r) => ['ADMIN', 'STAFF'].includes(r)) &&
          !user.suspended && !user.deleted_at,
        401,
        'INVALID_CREDENTIALS',
      );
      ensure(await passwordMatches(user, password, proof), 401, 'INVALID_CREDENTIALS');
      if (!user.totp_secret) {
        ensure(challengeId && code, 401, 'WHATSAPP_OTP_REQUIRED');
        const result = await transaction(pool, async (db) => {
          const current = await one(db, 'SELECT * FROM users WHERE id=$1 FOR UPDATE', [user.id]);
          if (!current || current.suspended || current.deleted_at || current.totp_secret ||
              current.password_hash !== user.password_hash ||
              !current.roles.some((r) => ['ADMIN', 'STAFF'].includes(r)))
            return { error: 'INVALID_CREDENTIALS' };
          const challenge = await consumeOtp(db, config, challengeId, code, current.phone);
          if (challenge.error) return challenge;
          return issue(db, current);
        });
        if (result.error) throw new AppError(401, result.error);
        return result;
      }
      ensure(/^\d{6}$/.test(otp ?? ''), 401, 'INVALID_CREDENTIALS');
      const totp = new OTPAuth.TOTP({
        secret: OTPAuth.Secret.fromBase32(decrypt(user.totp_secret, config.ENCRYPTION_KEY)),
        period: 30,
        digits: 6,
      });
      const delta = totp.validate({ token: otp, window: 1 });
      ensure(delta !== null, 401, 'INVALID_CREDENTIALS');
      const counter = Math.floor(Date.now() / 30000) + delta;
      return transaction(pool, async (db) => {
        const updated = await one(
          db,
          'UPDATE users SET totp_counter=$2 WHERE id=$1 AND totp_counter<$2 AND NOT suspended AND deleted_at IS NULL RETURNING *',
          [user.id, counter],
        );
        ensure(updated, 401, 'TOTP_REUSED');
        return issue(db, updated);
      });
    },
    async recoverAdmin(phone, password, recoveryPassword, proof) {
      const initial = await one(pool, "SELECT * FROM users WHERE phone=$1 AND 'ADMIN'=ANY(roles)", [normalizePhone(phone)]);
      ensure(initial && !initial.suspended && !initial.deleted_at && initial.password_hash && initial.recovery_password_hash,
        401, 'INVALID_CREDENTIALS');
      ensure(await passwordMatches(initial, password, proof), 401, 'INVALID_CREDENTIALS');
      const nextRecovery = token();
      const nextHash = await bcrypt.hash(nextRecovery, 12);
      const result = await transaction(pool, async (db) => {
        const user = await one(db, 'SELECT * FROM users WHERE id=$1 FOR UPDATE', [initial.id]);
        if (!user || user.suspended || user.deleted_at || !user.roles.includes('ADMIN') ||
            user.password_hash !== initial.password_hash || !user.recovery_password_hash)
          return { error: 'INVALID_CREDENTIALS' };
        if (user.recovery_locked_until && user.recovery_locked_until > Date.now())
          return { error: 'RECOVERY_LOCKED' };
        const attempts = user.recovery_locked_until ? 0 : user.recovery_attempts;
        if (!await bcrypt.compare(recoveryPassword, user.recovery_password_hash)) {
          await db.query("UPDATE users SET recovery_attempts=$2,recovery_locked_until=CASE WHEN $2>=5 THEN now()+interval '15 minutes' ELSE NULL END WHERE id=$1", [user.id, attempts + 1]);
          return { error: 'INVALID_CREDENTIALS' };
        }
        await db.query('UPDATE users SET recovery_password_hash=$2,recovery_attempts=0,recovery_locked_until=NULL WHERE id=$1', [user.id, nextHash]);
        await db.query('UPDATE sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=$1', [user.id]);
        await db.query("INSERT INTO audit_logs(actor_id,action,target_id) VALUES($1,'ADMIN_RECOVERY',$1)", [user.id]);
        return { ...await issue(db, user), recoveryPassword: nextRecovery };
      });
      if (result.error) throw new AppError(401, result.error);
      return result;
    },
    async authenticate(access) {
      let payload;
      try {
        ({ payload } = await jwtVerify(access, key, {
          algorithms: ['HS256'],
          issuer: 'mazo-rides',
          audience: 'mazo-clients',
        }));
      } catch {
        throw new AppError(401, 'INVALID_TOKEN');
      }
      const user = await one(
        pool,
        `SELECT u.*,s.id AS session_id,s.device_app AS session_app FROM users u JOIN sessions s ON s.user_id=u.id WHERE u.id=$1 AND s.id=$2 AND s.revoked_at IS NULL AND s.expires_at>now() AND NOT u.suspended AND u.deleted_at IS NULL`,
        [payload.sub, payload.sid],
      );
      ensure(user, 401, 'SESSION_REVOKED');
      return user;
    },
    async listDevices(actor) {
      ensure(!actor.roles.some(role => ['ADMIN','STAFF'].includes(role)), 403, 'MOBILE_ACCOUNT_REQUIRED');
      const items = (await pool.query(`SELECT family_id AS id,device_name AS name,device_app AS app,
          created_at AS "updatedAt",id=$2 AS current
        FROM sessions WHERE user_id=$1 AND revoked_at IS NULL AND expires_at>now()
        ORDER BY (id=$2) DESC,created_at DESC,id DESC`, [actor.id, actor.session_id])).rows;
      ensure(items.some(item => item.current), 401, 'SESSION_REVOKED');
      return { items };
    },
    async registerDevice(actor, inputDevice) {
      return transaction(pool, async db => {
        const current = await currentDeviceSession(db, actor);
        const device = installation(actor.id, inputDevice);
        ensure(!current.device_key || current.device_key === device.device_key, 409, 'DEVICE_ID_MISMATCH');
        await db.query('UPDATE sessions SET device_key=$2, device_name=$3, device_app=$4 WHERE id=$1',
          [current.id, device.device_key, device.device_name, device.device_app]);
        return { registered: true };
      });
    },
    async revokeDevices(actor, target) {
      return transaction(pool, async db => {
        const current = await currentDeviceSession(db, actor);
        ensure(target?.toLowerCase() !== current.family_id, 409, 'CURRENT_DEVICE');
        const result = await db.query(`UPDATE sessions SET revoked_at=now()
          WHERE user_id=$1 AND family_id<>$2 AND ($3::uuid IS NULL OR family_id=$3) AND revoked_at IS NULL`,
        [actor.id, current.family_id, target ?? null]);
        if (result.rowCount) await audit(db, actor.id, target ? 'DEVICE_REVOKED' : 'OTHER_DEVICES_REVOKED', actor.id);
        return { revoked: true };
      });
    },
    async refresh(value) {
      const result = await transaction(pool, async (db) => {
        const tokenHash = hash(value, config.JWT_SECRET);
        const owner = await one(db, 'SELECT user_id FROM sessions WHERE token_hash=$1', [tokenHash]);
        if (!owner) return { error: 'INVALID_REFRESH' };
        const user = await one(db, 'SELECT * FROM users WHERE id=$1 FOR UPDATE', [owner.user_id]);
        const session = await one(db, 'SELECT * FROM sessions WHERE token_hash=$1 FOR UPDATE', [tokenHash]);
        if (!session) return { error: 'INVALID_REFRESH' };
        if (session.revoked_at) {
          await db.query(
            'UPDATE sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE family_id=$1',
            [session.family_id],
          );
          return { error: 'REFRESH_REUSE' };
        }
        if (session.expires_at < Date.now()) return { error: 'INVALID_REFRESH' };
        if (!user || user.suspended || user.deleted_at) return { error: 'ACCOUNT_UNAVAILABLE' };
        await db.query('UPDATE sessions SET revoked_at=now() WHERE id=$1', [session.id]);
        return issue(db, user, session.family_id, session);
      });
      if (result.error) throw new AppError(401, result.error);
      return result;
    },
  };
}
export function permission(user, name) {
  return (
    user.roles.includes('ADMIN') ||
    (user.roles.includes('STAFF') && user.permissions.includes(name))
  );
}
