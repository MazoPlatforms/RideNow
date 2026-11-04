import express from 'express';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';
import { encrypt, decrypt, token, hash } from './shared/crypto.js';
import { ensure, AppError } from './shared/errors.js';
import { rateLimiter } from './shared/rate-limit.js';

const root = fileURLToPath(new URL('../admin/', import.meta.url));
const releaseLock = "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end";
const pages = ['overview', 'users', 'drivers', 'rides', 'areas', 'pricing', 'finance', 'staff', 'audit', 'security'];
const publicPages = ['login', 'verify', 'recovery'];
function landing(user) {
  if (user.roles.includes('ADMIN')) return '/admin/';
  return [['rides.monitor','/admin/'],['users.manage','/admin/users/'],['drivers.review','/admin/drivers/'],
    ['config.manage','/admin/areas/'],['finance.manage','/admin/finance/'],['audit.read','/admin/audit/']]
    .find(([permission]) => user.permissions.includes(permission))?.[1] ?? '/admin/security/';
}

export function mountAdminWeb(app, { pool, redis, config, auth, captchaVerifier }) {
  const secure = config.NODE_ENV === 'production';
  const cookie = secure ? '__Host-ridenow-admin' : 'ridenow-admin';
  const flowCookie = secure ? '__Host-ridenow-flow' : 'ridenow-flow';
  const options = { httpOnly: true, secure, sameSite: 'strict', path: '/' };
  const readCookie = (req, name) => {
    const value = (req.headers.cookie ?? '').split(';').map((v) => v.trim()).find((v) => v.startsWith(name + '='))?.slice(name.length + 1);
    return /^[\w-]{43}$/.test(value ?? '') ? value : null;
  };
  const key = (kind, value) => `admin-web:${kind}:${hash(value, config.OTP_PEPPER)}`;
  const seal = (value) => encrypt(JSON.stringify(value), config.ENCRYPTION_KEY);
  const open = (value) => JSON.parse(decrypt(value, config.ENCRYPTION_KEY));
  const noCache = (req, res, next) => {
    res.set({ 'Cache-Control': 'no-store', 'X-Robots-Tag': 'noindex, nofollow', 'Referrer-Policy': 'no-referrer' });
    next();
  };
  const sameOrigin = (req, res, next) => {
    const expected = `${secure ? 'https' : req.protocol}://${req.get('host')}`;
    ensure(req.get('X-Admin-Request') === '1' &&
      (!req.get('origin') || req.get('origin') === expected) &&
      !['cross-site', 'same-site'].includes(req.get('sec-fetch-site')), 403, 'ADMIN_ORIGIN_REJECTED');
    next();
  };
  async function rawSession(req) {
    const id = readCookie(req, cookie);
    const value = id && await redis.get(key('session', id));
    ensure(value, 401, 'ADMIN_SESSION_EXPIRED');
    const state = open(value);
    ensure(state.expiresAt > Date.now(), 401, 'ADMIN_SESSION_EXPIRED');
    return { id, state };
  }
  async function session(req) {
    let { id, state } = await rawSession(req);
    if (state.accessExpiresAt < Date.now() + 60000) {
      const lock = key('refresh', id), lease = token();
      const acquired = await redis.set(lock, lease, { NX: true, PX: 20000 });
      if (!acquired) {
        for (let i = 0; i < 40; i++) {
          await new Promise((r) => setTimeout(r, 100));
          ({ state } = await rawSession(req));
          if (state.accessExpiresAt >= Date.now() + 60000) break;
        }
        ensure(state.accessExpiresAt >= Date.now() + 60000, 503, 'ADMIN_SESSION_BUSY');
      } else {
        try {
          ({ state } = await rawSession(req));
          if (state.accessExpiresAt < Date.now() + 60000) {
            const next = await auth.refresh(state.refreshToken);
            const user = await auth.authenticate(next.accessToken);
            state = { ...state, accessToken: next.accessToken, refreshToken: next.refreshToken,
              accessExpiresAt: Date.now() + next.expiresIn * 1000, sessionId: user.session_id };
            await redis.set(key('session', id), seal(state), { EX: Math.max(1, Math.ceil((state.expiresAt - Date.now()) / 1000)) });
          }
        } finally { await redis.eval(releaseLock, { keys: [lock], arguments: [lease] }); }
      }
    }
    const user = await auth.authenticate(state.accessToken);
    ensure(user.roles.some((role) => ['ADMIN', 'STAFF'].includes(role)), 403, 'ADMIN_REQUIRED');
    return { id, state, user };
  }
  async function flow(req) {
    const id = readCookie(req, flowCookie);
    const value = id && await redis.get(key('flow', id));
    ensure(value, 401, 'ADMIN_FLOW_EXPIRED');
    return { id, state: open(value) };
  }
  const router = express.Router();
  router.use(noCache, sameOrigin, rateLimiter(redis, config, { limit: 60, seconds: 60, prefix: 'admin-web' }));
  router.get('/captcha-config', (req, res) => res.json({
    enabled: Boolean(config.RECAPTCHA_V2_SITE_KEY && config.RECAPTCHA_V2_SECRET_KEY),
    siteKey: config.RECAPTCHA_V2_SITE_KEY,
    testMode: config.NODE_ENV === 'test',
  }));
  const loginIpLimit = rateLimiter(redis, config, { limit: 10, seconds: 300, prefix: 'admin-web-login-ip' });
  const loginPhoneLimit = rateLimiter(redis, config, {
    limit: 5, seconds: 900, prefix: 'admin-web-login-phone',
    identity: (req) => String(req.body?.phone ?? '').replace(/\D/g, '').slice(-15) || 'missing-phone',
  });
  router.post('/begin', loginIpLimit, loginPhoneLimit, async (req, res) => {
    const body = z.object({ phone: z.string().min(9).max(30), password: z.string().min(12).max(128),
      captchaToken: z.string().min(20).max(4096).optional() }).strict().parse(req.body);
    ensure(typeof captchaVerifier === 'function', 503, 'CAPTCHA_UNAVAILABLE');
    await captchaVerifier({ token: body.captchaToken, hostname: req.hostname });
    const proof = await auth.adminPasswordProof(body.phone, body.password);
    const previous = readCookie(req, flowCookie);
    if (previous) await redis.del(key('flow', previous));
    const id = token();
    await redis.set(key('flow', id), seal({ proof }), { EX: 300 });
    res.cookie(flowCookie, id, { ...options, maxAge: 300000 });
    res.json({ next: '/admin/verify/' });
  });
  router.get('/flow', async (req, res) => {
    const { state } = await flow(req);
    res.json({ phone: '•••• ' + state.proof.phone.slice(-4), method: state.proof.totp ? 'totp' : 'whatsapp',
      otpRequested: Boolean(state.challengeId), recoveryAllowed: state.proof.roles.includes('ADMIN') });
  });
  router.post('/send-otp', async (req, res) => {
    const { id, state } = await flow(req);
    ensure(!state.proof.totp, 409, 'TOTP_REQUIRED');
    const result = await auth.requestOtp(state.proof.phone);
    const ttl = await redis.ttl(key('flow', id));
    ensure(ttl > 0, 401, 'ADMIN_FLOW_EXPIRED');
    state.challengeId = result.challengeId;
    await redis.set(key('flow', id), seal(state), { EX: ttl });
    res.json({ expiresAt: result.expiresAt });
  });
  router.post('/verify', async (req, res) => {
    const body = z.object({ code: z.string().regex(/^\d{6}$/).optional(), recoveryPassword: z.string().min(20).max(128).optional() })
      .strict().refine((b) => Boolean(b.code) !== Boolean(b.recoveryPassword)).parse(req.body);
    const { id, state } = await flow(req);
    const lock = key('verify', id), lease = token();
    ensure(await redis.set(lock, lease, { NX: true, PX: 30000 }), 409, 'VERIFICATION_IN_PROGRESS');
    try {
      ensure(await redis.exists(key('flow', id)), 401, 'ADMIN_FLOW_EXPIRED');
      const attempts = await redis.incr(key('attempts', id));
      if (attempts === 1) await redis.expire(key('attempts', id), 300);
      ensure(attempts <= 5, 429, 'VERIFICATION_ATTEMPTS_EXCEEDED');
      const result = body.recoveryPassword
        ? await auth.recoverAdmin(state.proof.phone, undefined, body.recoveryPassword, state.proof)
        : await auth.staffLogin(state.proof.phone, undefined, state.proof.totp ? body.code : undefined,
          state.challengeId, state.proof.totp ? undefined : body.code, state.proof);
      const user = await auth.authenticate(result.accessToken);
      ensure(user.roles.some((role) => ['ADMIN', 'STAFF'].includes(role)), 403, 'ADMIN_REQUIRED');
      const sessionId = token();
      await redis.set(key('session', sessionId), seal({ accessToken: result.accessToken, refreshToken: result.refreshToken,
        sessionId: user.session_id, expiresAt: Date.now() + 43200000, accessExpiresAt: Date.now() + result.expiresIn * 1000 }), { EX: 43200 });
      if (result.recoveryPassword) await redis.set(key('recovery', sessionId), seal({ recoveryPassword: result.recoveryPassword }), { EX: 300 });
      await redis.del([key('flow', id), key('attempts', id)]);
      res.clearCookie(flowCookie, options);
      res.cookie(cookie, sessionId, { ...options, maxAge: 43200000 });
      res.json({ next: result.recoveryPassword ? '/admin/recovery-saved/' : landing(user) });
    } finally { await redis.eval(releaseLock, { keys: [lock], arguments: [lease] }); }
  });
  router.get('/session', async (req, res) => {
    const { user } = await session(req);
    res.json({ id: user.id, name: user.name, phone: user.phone, roles: user.roles, permissions: user.permissions,
      environment: config.NODE_ENV, totpEnabled: Boolean(user.totp_secret), next: landing(user) });
  });
  router.get('/recovery-password', async (req, res) => {
    const { id } = await session(req);
    const value = await redis.getDel(key('recovery', id));
    ensure(value, 404, 'RECOVERY_PASSWORD_ALREADY_SHOWN');
    res.json(open(value));
  });
  router.post('/logout', async (req, res) => {
    try {
      const { id, state } = await rawSession(req);
      await pool.query('UPDATE sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE id=$1', [state.sessionId]);
      await redis.del([key('session', id), key('recovery', id)]);
    } catch (error) { if (error.status !== 401) throw error; }
    res.clearCookie(cookie, options); res.clearCookie(flowCookie, options);
    res.json({ loggedOut: true });
  });
  app.use('/v1/admin-web', router);
  app.use('/v1', async (req, res, next) => {
    const path = req.path;
    if (req.headers.authorization || !readCookie(req, cookie) ||
      !(/^\/admin(?:\/|$)/.test(path) || path === '/me' || /^\/auth\/sessions(?:\/|$)/.test(path))) return next();
    sameOrigin(req, res, () => {});
    const { state } = await session(req);
    req.headers.authorization = `Bearer ${state.accessToken}`;
    res.set('Cache-Control', 'no-store');
    next();
  });
  app.use('/admin', noCache);
  app.use('/admin/assets', express.static(root + 'assets', { dotfiles: 'deny', index: false,
    setHeaders: (res, path) => res.setHeader('Cache-Control', path.endsWith('.woff2')
      ? 'public, max-age=604800, immutable' : 'public, max-age=0, must-revalidate') }));
  for (const page of publicPages) app.get([`/admin/${page}`, `/admin/${page}/`], (req, res) => res.sendFile(`${page}.html`, { root }));
  for (const page of [...pages, 'recovery-saved']) {
    const paths = page === 'overview' ? ['/admin', '/admin/', '/admin/overview/'] : [`/admin/${page}`, `/admin/${page}/`];
    app.get(paths, async (req, res) => {
      try { await session(req); }
      catch (error) {
        if ([401, 403].includes(error.status)) return res.redirect(303, '/admin/login/');
        throw error;
      }
      res.sendFile(`${page}.html`, { root });
    });
  }
  return { session };
}
