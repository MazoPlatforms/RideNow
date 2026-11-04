import { randomInt } from 'node:crypto';
import { SignJWT, jwtVerify } from 'jose';
import bcrypt from 'bcryptjs';
import * as OTPAuth from 'otpauth';
import { hash, token, equal, encrypt, decrypt } from '../shared/crypto.js';
import { transaction, one } from '../shared/db.js';
import { AppError, ensure } from '../shared/errors.js';
export function normalizePhone(input) {
  let s = input.replace(/[\s()-]/g, '');
  if (s.startsWith('0')) s = '+94' + s.slice(1);
  ensure(/^\+947\d{8}$/.test(s), 400, 'INVALID_SRI_LANKAN_MOBILE');
  return s;
}
export async function consumeOtp(db, config, id, code, expectedPhone) {
  const c = await one(db, 'SELECT * FROM otp_challenges WHERE id=$1 FOR UPDATE', [id]);
  if (!c || c.used_at || c.expires_at < Date.now() || c.attempts >= 5 ||
      (expectedPhone && c.phone !== expectedPhone)) return { error: 'OTP_INVALID' };
  await db.query('UPDATE otp_challenges SET attempts=attempts+1 WHERE id=$1', [id]);
  if (!equal(c.code_hash, hash(code, config.OTP_PEPPER))) return { error: 'OTP_INVALID' };
  await db.query('UPDATE otp_challenges SET used_at=now() WHERE id=$1', [id]);
  return c;
}
export function createAuth(pool, config) {
  const key = new TextEncoder().encode(config.JWT_SECRET);
  async function issue(db, user, family) {
    const refreshToken = token();
    const session = await one(
      db,
      `INSERT INTO sessions(user_id,family_id,token_hash,expires_at) VALUES($1,COALESCE($2,gen_random_uuid()),$3,now()+interval '30 days') RETURNING id`,
      [user.id, family ?? null, hash(refreshToken, config.JWT_SECRET)],
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
      user: { id: user.id, roles: user.roles, name: user.name },
    };
  }
  return {
    async requestOtp(input) {
      const phone = normalizePhone(input),
        code = String(randomInt(100000, 1000000));
      return transaction(pool, async (db) => {
        await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,1))', [phone]);
        const recent = await one(
          db,
          "SELECT count(*)::int AS n,max(created_at) AS latest FROM otp_challenges WHERE phone=$1 AND created_at>now()-interval '1 hour'",
          [phone],
        );
        ensure(
          recent.n < 10 && (!recent.latest || Date.now() - recent.latest >= 60000),
          429,
          'OTP_RATE_LIMIT',
        );
        if (config.OTP_PROVIDER === 'baileys') {
          const health = await one(
            db,
            "SELECT updated_at FROM system_state WHERE key='whatsapp.ready' AND value='true'::jsonb",
          );
          ensure(health && Date.now() - health.updated_at < 30000, 503, 'OTP_PROVIDER_UNAVAILABLE');
        }
        await db.query(
          'UPDATE otp_challenges SET used_at=now() WHERE phone=$1 AND used_at IS NULL',
          [phone],
        );
        const c = await one(
          db,
          "INSERT INTO otp_challenges(phone,code_hash,expires_at) VALUES($1,$2,now()+interval '5 minutes') RETURNING id,expires_at",
          [phone, hash(code, config.OTP_PEPPER)],
        );
        await db.query("INSERT INTO jobs(kind,payload) VALUES('otp',$1)", [
          { challengeId: c.id, phone, encryptedCode: encrypt(code, config.ENCRYPTION_KEY) },
        ]);
        return { challengeId: c.id, expiresAt: c.expires_at };
      });
    },
    async verifyOtp(id, code) {
      const result = await transaction(pool, async (db) => {
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
        return issue(db, user);
      });
      if (result.error) throw new AppError(401, result.error);
      return result;
    },
    async staffLogin(phone, password, otp, challengeId, code) {
      const user = await one(pool, 'SELECT * FROM users WHERE phone=$1', [normalizePhone(phone)]);
      ensure(
        user?.password_hash && user.roles.some((r) => ['ADMIN', 'STAFF'].includes(r)) &&
          !user.suspended && !user.deleted_at,
        401,
        'INVALID_CREDENTIALS',
      );
      ensure(await bcrypt.compare(password, user.password_hash), 401, 'INVALID_CREDENTIALS');
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
    async recoverAdmin(phone, password, recoveryPassword) {
      const initial = await one(pool, "SELECT * FROM users WHERE phone=$1 AND 'ADMIN'=ANY(roles)", [normalizePhone(phone)]);
      ensure(initial && !initial.suspended && !initial.deleted_at && initial.password_hash && initial.recovery_password_hash,
        401, 'INVALID_CREDENTIALS');
      ensure(await bcrypt.compare(password, initial.password_hash), 401, 'INVALID_CREDENTIALS');
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
        `SELECT u.*,s.id AS session_id FROM users u JOIN sessions s ON s.user_id=u.id WHERE u.id=$1 AND s.id=$2 AND s.revoked_at IS NULL AND s.expires_at>now() AND NOT u.suspended AND u.deleted_at IS NULL`,
        [payload.sub, payload.sid],
      );
      ensure(user, 401, 'SESSION_REVOKED');
      return user;
    },
    async refresh(value) {
      const result = await transaction(pool, async (db) => {
        const session = await one(db, 'SELECT * FROM sessions WHERE token_hash=$1 FOR UPDATE', [
          hash(value, config.JWT_SECRET),
        ]);
        if (!session) return { error: 'INVALID_REFRESH' };
        if (session.revoked_at) {
          await db.query(
            'UPDATE sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE family_id=$1',
            [session.family_id],
          );
          return { error: 'REFRESH_REUSE' };
        }
        if (session.expires_at < Date.now()) return { error: 'INVALID_REFRESH' };
        const user = await one(
          db,
          'SELECT * FROM users WHERE id=$1 AND NOT suspended AND deleted_at IS NULL',
          [session.user_id],
        );
        if (!user) return { error: 'ACCOUNT_UNAVAILABLE' };
        await db.query('UPDATE sessions SET revoked_at=now() WHERE id=$1', [session.id]);
        return issue(db, user, session.family_id);
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
