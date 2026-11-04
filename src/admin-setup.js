import express from 'express';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';
import bcrypt from 'bcryptjs';
import * as OTPAuth from 'otpauth';
import { one, transaction, audit } from './shared/db.js';
import { hash, equal, encrypt, decrypt, token } from './shared/crypto.js';
import { ensure, AppError } from './shared/errors.js';
import { rateLimiter } from './shared/rate-limit.js';
import { normalizePhone, consumeOtp } from './modules/auth.js';

const root = fileURLToPath(new URL('../admin-setup/', import.meta.url));
const phone = z.string().min(9).max(30).transform(normalizePhone);
const complete = z.object({
  phone, name: z.string().trim().min(1).max(100),
  password: z.string().min(12).max(128),
  verification: z.enum(['whatsapp', 'setup-key']).default('whatsapp'),
  challengeId: z.uuid().optional(), code: z.string().regex(/^\d{6}$/).optional(),
  enrollment: z.string().max(2048).optional(), totp: z.string().regex(/^\d{6}$/).optional(),
}).strict().refine((body) => body.verification === 'setup-key' || (body.challengeId && body.code),
  { message: 'WhatsApp OTP is required unless operator setup-key recovery is selected' });

export function mountAdminSetup(app, { pool, redis, config, auth }) {
  const enabled = () => Boolean(config.ADMIN_SETUP_KEY?.length >= 32 &&
    Date.parse(config.ADMIN_SETUP_EXPIRES_AT) > Date.now());
  const unused = async (db = pool) => !(await one(db,
    "SELECT EXISTS(SELECT 1 FROM users WHERE 'ADMIN'=ANY(roles)) OR EXISTS(SELECT 1 FROM system_state WHERE key='admin.bootstrap.completed') AS closed")).closed;
  const noCache = (req, res, next) => {
    res.set({ 'Cache-Control': 'no-store', 'X-Robots-Tag': 'noindex, nofollow', 'Referrer-Policy': 'no-referrer' });
    next();
  };
  app.get(['/admin-access', '/admin-access/'], noCache, (req, res) => res.redirect(303, '/admin/login/'));
  app.use('/admin-access-assets', noCache, express.static(root, { dotfiles: 'deny', index: false }));
  app.use('/admin-setup', noCache, async (req, res, next) => {
    if (!enabled() || !await unused()) throw new AppError(404, 'SETUP_UNAVAILABLE');
    next();
  }, express.static(root, { dotfiles: 'deny', maxAge: 0 }));
  const router = express.Router();
  router.use(noCache, rateLimiter(redis, config, { limit: 20, seconds: 60, prefix: 'admin-setup' }));
  router.use(async (req, res, next) => {
    const key = req.headers['x-admin-setup-key'];
    ensure(enabled() && typeof key === 'string' && key.length <= 256 &&
      equal(hash(key, config.OTP_PEPPER), hash(config.ADMIN_SETUP_KEY, config.OTP_PEPPER)),
    404, 'SETUP_UNAVAILABLE');
    ensure(await unused(), 404, 'SETUP_UNAVAILABLE');
    next();
  });
  router.post('/otp', async (req, res) => {
    const body = z.object({ phone }).strict().parse(req.body);
    res.status(202).json(await auth.requestOtp(body.phone));
  });
  router.post('/totp', (req, res) => {
    const body = z.object({ phone }).strict().parse(req.body);
    const secret = new OTPAuth.Secret({ size: 20 });
    const totp = new OTPAuth.TOTP({ issuer: 'RideNow', label: body.phone, secret, digits: 6, period: 30 });
    res.json({ secret: secret.base32, uri: totp.toString(),
      enrollment: encrypt(JSON.stringify({ secret: secret.base32, phone: body.phone,
        expiresAt: Date.now() + 15 * 60 * 1000 }), config.ENCRYPTION_KEY) });
  });
  router.post('/complete', async (req, res) => {
    const body = complete.parse(req.body);
    let totpSecret = null, counter = -1;
    if (body.enrollment || body.totp) {
      let enrollment;
      try { enrollment = JSON.parse(decrypt(body.enrollment, config.ENCRYPTION_KEY)); }
      catch { throw new AppError(400, 'INVALID_TOTP_ENROLLMENT'); }
      ensure(enrollment.phone === body.phone && enrollment.expiresAt > Date.now(), 400, 'INVALID_TOTP_ENROLLMENT');
      const totp = new OTPAuth.TOTP({ secret: OTPAuth.Secret.fromBase32(enrollment.secret), digits: 6, period: 30 });
      const delta = body.totp ? totp.validate({ token: body.totp, window: 1 }) : null;
      ensure(delta !== null, 400, 'INVALID_TOTP');
      counter = Math.floor(Date.now() / 30000) + delta;
      totpSecret = encrypt(enrollment.secret, config.ENCRYPTION_KEY);
    }
    const recoveryPassword = token();
    const [passwordHash, recoveryHash] = await Promise.all([
      bcrypt.hash(body.password, 12), bcrypt.hash(recoveryPassword, 12),
    ]);
    const result = await transaction(pool, async (db) => {
      await db.query('SELECT pg_advisory_xact_lock(927565)');
      ensure(enabled() && await unused(db), 404, 'SETUP_UNAVAILABLE');
      const existing = await one(db, 'SELECT * FROM users WHERE phone=$1 FOR UPDATE', [body.phone]);
      ensure(!existing || (!existing.suspended && !existing.deleted_at &&
        existing.roles.length === 1 && existing.roles[0] === 'RIDER'), 409, 'ACCOUNT_NOT_ELIGIBLE');
      if (body.verification === 'whatsapp') {
        const challenge = await consumeOtp(db, config, body.challengeId, body.code, body.phone);
        if (challenge.error) return challenge;
      }
      const user = existing
        ? await one(db, "UPDATE users SET name=$2,roles=ARRAY['ADMIN'],permissions='{}',password_hash=$3,totp_secret=$4,totp_counter=$5,recovery_password_hash=$6 WHERE id=$1 RETURNING id",
          [existing.id, body.name, passwordHash, totpSecret, counter, recoveryHash])
        : await one(db, "INSERT INTO users(phone,name,roles,password_hash,totp_secret,totp_counter,recovery_password_hash) VALUES($1,$2,ARRAY['ADMIN'],$3,$4,$5,$6) RETURNING id",
          [body.phone, body.name, passwordHash, totpSecret, counter, recoveryHash]);
      await db.query('UPDATE sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=$1', [user.id]);
      await audit(db, user.id, 'ADMIN_WEB_BOOTSTRAP', user.id, { totpEnabled: Boolean(totpSecret), verification: body.verification });
      await db.query("INSERT INTO system_state(key,value) VALUES('admin.bootstrap.completed','true')");
      return { id: user.id, created: true, totpEnabled: Boolean(totpSecret), recoveryPassword };
    });
    if (result.error) throw new AppError(401, result.error);
    res.status(201).json(result);
  });
  app.use('/v1/admin-setup', router);
}
