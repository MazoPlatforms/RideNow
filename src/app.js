import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import bcrypt from 'bcryptjs';
import * as OTPAuth from 'otpauth';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { one, transaction, audit, event } from './shared/db.js';
import { AppError, ensure, errorHandler } from './shared/errors.js';
import { decrypt, encrypt } from './shared/crypto.js';
import { idempotent } from './shared/idempotency.js';
import { rateLimiter } from './shared/rate-limit.js';
import { createRecaptchaVerifier } from './shared/recaptcha.js';
import { createAuth, permission } from './modules/auth.js';
import { quote } from './modules/pricing.js';
import * as rides from './modules/rides.js';
import * as drivers from './modules/drivers.js';
import * as ledger from './modules/ledger.js';
import { acceptOffer } from './modules/dispatch.js';
import { mountWebsite } from './website.js';
import { mountAdminSetup } from './admin-setup.js';
import { mountAdminWeb } from './admin-web.js';
export const coordinate = z.tuple([z.number().min(-180).max(180), z.number().min(-90).max(90)]);
export const locationSchema = z
  .object({
    coordinates: coordinate,
    accuracy: z.number().min(0).max(100),
    sequence: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
    recordedAt: z.iso.datetime(),
  })
  .strict();
const uuid = z.uuid(),
  category = z.enum(['TUK', 'CAR', 'VAN']),
  text = z.string().trim().min(1).max(500),
  money = z.number().int().positive().max(1000000000);
const phone = z.string().min(9).max(30),
  profileEmail = z.string().trim().max(254).email().or(z.literal('')).optional(),
  page = z.object({
    cursor: uuid.optional(),
    limit: z.coerce.number().int().min(1).max(100).default(30),
  });
const quoteSchema = z
  .object({
    waypoints: z.array(coordinate).length(2),
    category,
    tripType: z.enum(['ONE_WAY', 'RETURN']).default('ONE_WAY'),
    scheduledAt: z.iso.datetime().optional(),
  })
  .strict();
export function createApp({ pool, redis, config, maps, storage, logger, captchaVerifier = createRecaptchaVerifier(config) }) {
  const app = express(),
    auth = createAuth(pool, config),
    paths = {};
  const authenticate = async (req, res, next) => {
    try {
      const value = req.headers.authorization?.match(/^Bearer (.+)$/)?.[1];
      ensure(value, 401, 'AUTH_REQUIRED');
      req.user = await auth.authenticate(value);
      next();
    } catch (e) {
      next(e);
    }
  };
  async function profileView(user) {
    return {
      id: user.id,
      phone: user.phone,
      name: user.name,
      email: user.email ?? '',
      profilePhotoUrl: user.profile_photo_key
        ? await storage.download(user.profile_photo_key, user.profile_photo_version)
        : null,
      roles: user.roles,
      permissions: user.permissions,
    };
  }
  app.disable('x-powered-by');
  app.set('trust proxy', Number(config.TRUST_PROXY_HOPS ?? 0));
  app.use((req, res, next) => {
    req.id = randomUUID();
    res.set('X-Request-ID', req.id);
    const start = performance.now();
    res.on('finish', () =>
      logger.info(
        {
          requestId: req.id,
          method: req.method,
          path: req.route?.path ?? 'unmatched',
          status: res.statusCode,
          durationMs: Math.round(performance.now() - start),
        },
        'request',
      ),
    );
    next();
  });
  app.use(
    helmet({ contentSecurityPolicy: { directives: {
      scriptSrc: ["'self'", 'https://www.google.com/recaptcha/', 'https://www.gstatic.com/recaptcha/'],
      frameSrc: ["'self'", 'https://www.google.com/recaptcha/', 'https://recaptcha.google.com/recaptcha/'],
      connectSrc: ["'self'", 'https://www.google.com/recaptcha/', 'https://www.gstatic.com/recaptcha/'],
    } } }),
    cors({ origin: config.CORS_ORIGINS.split(',') }),
    express.json({ limit: '256kb' }),
  );
  app.use('/v1', rateLimiter(redis, config, { limit: 10000, seconds: 60, prefix: 'edge' }));
  app.use('/v1/auth', (req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });
  mountAdminWeb(app, { pool, redis, config, auth, captchaVerifier });
  const sensitive = rateLimiter(redis, config, { limit: 10, seconds: 60, prefix: 'auth' });
  function route(
    method,
    path,
    { body, access = 'user', idem = false, permission: perm, status = 200 } = {},
    fn,
  ) {
    const apiPath = path.replace(/:([A-Za-z]+)/g, '{$1}');
    paths[apiPath] ??= {};
    paths[apiPath][method] = {
      operationId: `${method}_${path.replace(/[^a-zA-Z0-9]/g, '_')}`,
      security: access === 'public' ? [] : [{ bearerAuth: [] }],
      ...(body
        ? {
            requestBody: {
              required: true,
              content: {
                'application/json': { schema: z.toJSONSchema(body, { unrepresentable: 'any' }) },
              },
            },
          }
        : {}),
      parameters: [
        ...Array.from(path.matchAll(/:([A-Za-z]+)/g), (m) => ({
          name: m[1],
          in: 'path',
          required: true,
          schema: { type: 'string', format: 'uuid' },
        })),
        ...(idem
          ? [
              {
                name: 'Idempotency-Key',
                in: 'header',
                required: true,
                schema: { type: 'string', minLength: 8, maxLength: 128 },
              },
            ]
          : []),
      ],
      responses: {
        [status]: { description: 'Success' },
        400: { description: 'Validation error' },
        401: { description: 'Authentication required' },
        403: { description: 'Forbidden' },
        409: { description: 'Conflict' },
        429: { description: 'Rate limited' },
      },
    };
    app[method](
      path,
      ...(access === 'public'
        ? []
        : [authenticate, rateLimiter(redis, config, { limit: 180, seconds: 60, prefix: 'user' })]),
      ...(path.startsWith('/v1/auth/') ? [sensitive] : []),
      async (req, res) => {
        for (const value of Object.values(req.params)) uuid.parse(value);
        if (perm) ensure(permission(req.user, perm), 403, 'FORBIDDEN');
        if (access === 'driver') ensure(req.user.roles.includes('DRIVER'), 403, 'DRIVER_REQUIRED');
        const value = body ? body.parse(req.body) : {};
        const result = idem
          ? await idempotent(
              pool,
              req.user.id,
              `${method}:${req.path}`,
              req.get('Idempotency-Key'),
              value,
              (db) => fn({ db, req, user: req.user, body: value }),
              config.ENCRYPTION_KEY,
            )
          : await fn({ db: pool, req, user: req.user, body: value });
        if (result?.error) throw new AppError(401, result.error);
        res.status(status).json(result ?? {});
      },
    );
  }
  app.get('/health/live', (req, res) => res.json({ status: 'ok' }));
  app.get('/health/ready', async (req, res) => {
    try {
      await pool.query('SELECT 1');
      await redis.ping();
      if (config.NODE_ENV === 'production') {
        const w = await one(
          pool,
          "SELECT updated_at FROM system_state WHERE key='worker.heartbeat'",
        );
        ensure(w && Date.now() - w.updated_at < 30000, 503, 'WORKER_UNAVAILABLE');
      }
      res.json({ status: 'ready', maps: maps.coverage().version });
    } catch {
      res.status(503).json({ status: 'unavailable' });
    }
  });
  route(
    'post',
    '/v1/auth/otp/request',
    { access: 'public', body: z.object({ phone }).strict(), status: 202 },
    ({ body }) => auth.requestOtp(body.phone),
  );
  route(
    'post',
    '/v1/auth/otp/verify',
    {
      access: 'public',
      body: z.object({ challengeId: uuid, code: z.string().regex(/^\d{6}$/) }).strict(),
    },
    ({ body }) => auth.verifyOtp(body.challengeId, body.code),
  );
  route(
    'post',
    '/v1/auth/staff/login',
    {
      access: 'public',
      body: z
        .object({ phone, password: z.string().min(12).max(128), totp: z.string().regex(/^\d{6}$/).optional(),
          challengeId: uuid.optional(), code: z.string().regex(/^\d{6}$/).optional() })
        .strict(),
    },
    ({ body }) => auth.staffLogin(body.phone, body.password, body.totp, body.challengeId, body.code),
  );
  route(
    'post',
    '/v1/auth/admin/recover',
    { access: 'public', body: z.object({ phone, password: z.string().min(12).max(128),
      recoveryPassword: z.string().min(20).max(128) }).strict() },
    ({ body }) => auth.recoverAdmin(body.phone, body.password, body.recoveryPassword),
  );
  route(
    'post',
    '/v1/auth/refresh',
    { access: 'public', body: z.object({ refreshToken: z.string().min(40).max(100) }).strict() },
    ({ body }) => auth.refresh(body.refreshToken),
  );
  route('post', '/v1/auth/logout', {}, async ({ db, user }) => {
    await db.query('UPDATE sessions SET revoked_at=now() WHERE id=$1', [user.session_id]);
    return { loggedOut: true };
  });
  route('get', '/v1/auth/sessions', {}, async ({ db, user }) => ({
    items: (
      await db.query(
        'SELECT id,created_at,expires_at,revoked_at FROM sessions WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100',
        [user.id],
      )
    ).rows,
  }));
  route('delete', '/v1/auth/sessions/:id', {}, async ({ db, user, req }) => {
    await db.query('UPDATE sessions SET revoked_at=now() WHERE id=$1 AND user_id=$2', [
      req.params.id,
      user.id,
    ]);
    return { revoked: true };
  });
  route('get', '/v1/me', {}, async ({ user }) => profileView(user));
  route(
    'patch',
    '/v1/me',
    { body: z.object({ name: text, email: profileEmail }).strict() },
    async ({ db, user, body }) => {
      const updated = await one(
        db,
        "UPDATE users SET name=$2,email=COALESCE(NULLIF($3,''),email) WHERE id=$1 RETURNING *",
        [user.id, body.name, body.email ?? null],
      );
      return profileView(updated);
    },
  );
  route(
    'post',
    '/v1/me/profile-photo',
    {
      idem: true,
      body: z.object({
        mime: z.enum(['image/jpeg', 'image/png']),
        bytes: z.number().int().positive().max(5 * 1024 * 1024),
      }).strict(),
    },
    async ({ db, user, body }) => {
      const id = randomUUID();
      const key = 'profiles/' + user.id + '/' + id;
      const upload = await one(
        db,
        "INSERT INTO profile_photo_uploads(id,user_id,object_key,mime,bytes,expires_at) VALUES($1,$2,$3,$4,$5,now()+interval '10 minutes') RETURNING id,expires_at",
        [id, user.id, key, body.mime, body.bytes],
      );
      return {
        photoId: upload.id,
        uploadUrl: await storage.upload(key, body.mime, body.bytes),
        expiresAt: upload.expires_at,
      };
    },
  );
  route(
    'post',
    '/v1/me/profile-photo/:id/complete',
    {},
    async ({ db, user, req }) => {
      const upload = await one(
        db,
        'SELECT * FROM profile_photo_uploads WHERE id=$1 AND user_id=$2',
        [req.params.id, user.id],
      );
      ensure(upload, 404, 'PROFILE_PHOTO_UPLOAD_NOT_FOUND');
      if (upload.status === 'CLEAN') return profileView(await one(db, 'SELECT * FROM users WHERE id=$1', [user.id]));
      ensure(upload.status === 'PENDING' && upload.expires_at > Date.now(), 409, 'PROFILE_PHOTO_UPLOAD_EXPIRED');
      const result = await storage.scan(upload);
      if (result.status !== 'CLEAN') {
        await db.query("UPDATE profile_photo_uploads SET status='REJECTED' WHERE id=$1", [upload.id]);
        throw new AppError(422, 'PROFILE_PHOTO_REJECTED');
      }
      const key = result.verifiedKey ?? upload.object_key;
      await db.query(
        "UPDATE profile_photo_uploads SET status='CLEAN',object_key=$2,object_version=$3 WHERE id=$1 AND status='PENDING'",
        [upload.id, key, result.version ?? null],
      );
      const updated = await one(
        db,
        'UPDATE users SET profile_photo_key=$2,profile_photo_version=$3 WHERE id=$1 RETURNING *',
        [user.id, key, result.version ?? null],
      );
      return profileView(updated);
    },
  );
  route(
    'put',
    '/v1/me/devices',
    { body: z.object({ token: z.string().min(20).max(4096) }).strict() },
    async ({ db, user, body }) => {
      await db.query(
        'INSERT INTO device_tokens(user_id,token) VALUES($1,$2) ON CONFLICT(token) DO UPDATE SET user_id=$1',
        [user.id, body.token],
      );
      return { registered: true };
    },
  );
  route(
    'delete',
    '/v1/me/devices',
    { body: z.object({ token: z.string() }).strict() },
    async ({ db, user, body }) => {
      await db.query('DELETE FROM device_tokens WHERE token=$1 AND user_id=$2', [
        body.token,
        user.id,
      ]);
      return { removed: true };
    },
  );
  route('get', '/v1/me/notifications', {}, async ({ db, user, req }) => {
    const p = page.parse(req.query);
    return {
      items: (
        await db.query(
          'SELECT * FROM notifications WHERE user_id=$1 AND ($2::uuid IS NULL OR id<$2) ORDER BY id DESC LIMIT $3',
          [user.id, p.cursor ?? null, p.limit],
        )
      ).rows,
    };
  });
  route('post', '/v1/me/notifications/:id/read', {}, async ({ db, user, req }) => {
    await db.query('UPDATE notifications SET read_at=now() WHERE id=$1 AND user_id=$2', [
      req.params.id,
      user.id,
    ]);
    return { read: true };
  });
  route(
    'post',
    '/v1/drivers/onboarding',
    { idem: true, body: z.object({ name: text }).strict() },
    ({ db, user, body }) => drivers.onboard(db, user, body),
  );
  route('get', '/v1/drivers/me', { access: 'driver' }, async ({ db, user }) => ({
    profile: await one(db, 'SELECT * FROM driver_profiles WHERE user_id=$1', [user.id]),
    vehicles: (await db.query('SELECT * FROM vehicles WHERE driver_id=$1', [user.id])).rows,
    documents: (
      await db.query(
        'SELECT id,kind,vehicle_id,expires_at,scan_status FROM documents WHERE driver_id=$1',
        [user.id],
      )
    ).rows,
  }));
  route(
    'post',
    '/v1/drivers/vehicles',
    {
      access: 'driver',
      idem: true,
      body: z
        .object({
          registration: z.string().min(3).max(30),
          category,
          capacity: z.number().int().min(1).max(15),
        })
        .strict(),
    },
    ({ db, user, body }) =>
      one(
        db,
        'INSERT INTO vehicles(driver_id,registration,category,capacity) VALUES($1,$2,$3,$4) RETURNING *',
        [user.id, body.registration, body.category, body.capacity],
      ),
  );
  route(
    'post',
    '/v1/drivers/documents',
    {
      access: 'driver',
      idem: true,
      body: z
        .object({
          vehicleId: uuid.optional(),
          kind: z.enum(['IDENTITY', 'LICENSE', 'REGISTRATION', 'INSURANCE']),
          mime: z.enum(['image/jpeg', 'image/png', 'application/pdf']),
          bytes: z.number().int().positive().max(10485760),
          expiresAt: z.iso.datetime(),
        })
        .strict(),
    },
    async ({ db, user, body }) => {
      ensure(new Date(body.expiresAt) > Date.now(), 400, 'DOCUMENT_EXPIRED');
      ensure(
        ['REGISTRATION', 'INSURANCE'].includes(body.kind) === !!body.vehicleId,
        400,
        'DOCUMENT_OWNER_INVALID',
      );
      if (body.vehicleId)
        ensure(
          await one(db, 'SELECT id FROM vehicles WHERE id=$1 AND driver_id=$2', [
            body.vehicleId,
            user.id,
          ]),
          403,
          'FORBIDDEN',
        );
      const key = `drivers/${user.id}/${randomUUID()}`;
      const doc = await one(
        db,
        'INSERT INTO documents(driver_id,vehicle_id,kind,object_key,mime,bytes,expires_at) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING id',
        [user.id, body.vehicleId ?? null, body.kind, key, body.mime, body.bytes, body.expiresAt],
      );
      return {
        documentId: doc.id,
        uploadUrl: await storage.upload(key, body.mime, body.bytes),
        expiresIn: 300,
      };
    },
  );
  route(
    'post',
    '/v1/drivers/documents/:id/submit',
    { access: 'driver', idem: true },
    async ({ db, user, req }) => {
      const d = await one(db, 'SELECT * FROM documents WHERE id=$1 AND driver_id=$2', [
        req.params.id,
        user.id,
      ]);
      ensure(d, 404, 'DOCUMENT_NOT_FOUND');
      await db.query(
        "INSERT INTO jobs(kind,payload,run_at) VALUES('scan',$1,now()+interval '6 minutes')",
        [{ documentId: d.id }],
      );
      return { queued: true };
    },
  );
  route('post', '/v1/drivers/submit', { access: 'driver', idem: true }, async ({ db, user }) => {
    const result = await one(
      db,
      "UPDATE driver_profiles SET status='PENDING' WHERE user_id=$1 AND status IN ('DRAFT','REJECTED') RETURNING *",
      [user.id],
    );
    return ensure(result, 409, 'INVALID_ONBOARDING_STATE');
  });
  route(
    'put',
    '/v1/drivers/availability',
    {
      access: 'driver',
      body: z.object({ availability: z.enum(['AVAILABLE', 'OFFLINE']) }).strict(),
    },
    ({ user, body }) =>
      transaction(pool, (db) => drivers.availability(db, user, body.availability)),
  );
  route('post', '/v1/locations', { access: 'driver', body: locationSchema }, ({ user, body }) =>
    transaction(pool, (db) => drivers.location(db, user, body)),
  );
  route('get', '/v1/maps/coverage', {}, () => maps.coverage());
  route('get', '/v1/maps/places', {}, ({ req }) => {
    const q = z.string().min(2).max(100).parse(req.query.q);
    return maps.searchPlaces({ query: q });
  });
  route(
    'post',
    '/v1/maps/route',
    { body: z.object({ waypoints: z.array(coordinate).min(2).max(3), category }).strict() },
    ({ body }) => maps.route({ waypoints: body.waypoints, vehicleCategory: body.category }),
  );
  route('post', '/v1/fare-quotes', { body: quoteSchema, status: 201 }, ({ user, body }) =>
    transaction(pool, (db) => quote(db, maps, user, body)),
  );
  route(
    'post',
    '/v1/rides',
    { idem: true, status: 201, body: z.object({ quoteId: uuid }).strict() },
    ({ db, user, body }) => rides.book(db, user, body.quoteId, config),
  );
  route('get', '/v1/rides', {}, async ({ db, user, req }) => {
    const p = page.parse(req.query);
    const items = (
      await db.query(
        'SELECT id,state,version,scheduled_at,final_minor,created_at FROM rides WHERE (rider_id=$1 OR driver_id=$1) AND ($2::uuid IS NULL OR id<$2) ORDER BY id DESC LIMIT $3',
        [user.id, p.cursor ?? null, p.limit],
      )
    ).rows;
    return { items, nextCursor: items.length === p.limit ? items.at(-1).id : null };
  });
  route('get', '/v1/rides/:id', {}, async ({ db, user, req }) => {
    const r = ensure(
      await one(
        db,
        'SELECT r.*,q.waypoints,q.route,q.category,q.trip_type,q.estimated_minor FROM rides r JOIN fare_quotes q ON q.id=r.quote_id WHERE r.id=$1',
        [req.params.id],
      ),
      404,
      'RIDE_NOT_FOUND',
    );
    ensure(
      r.rider_id === user.id || r.driver_id === user.id || permission(user, 'rides.monitor'),
      403,
      'FORBIDDEN',
    );
    const { pin_hash, pin_encrypted, pin_attempts, ...safe } = r;
    const position =
      r.driver_id && !rides.TERMINAL.includes(r.state)
        ? await one(
            db,
            'SELECT ST_X(point::geometry) AS longitude,ST_Y(point::geometry) AS latitude,received_at FROM driver_locations WHERE driver_id=$1',
            [r.driver_id],
          )
        : null;
    return {
      ...safe,
      ...(r.rider_id === user.id && !rides.TERMINAL.includes(r.state)
        ? { ridePin: decrypt(pin_encrypted, config.ENCRYPTION_KEY) }
        : {}),
      driverLocation: position,
      payment: await one(db, 'SELECT * FROM payments WHERE ride_id=$1', [r.id]),
      cancellationCharge: await one(db, 'SELECT * FROM cancellation_charges WHERE ride_id=$1', [
        r.id,
      ]),
    };
  });
  route(
    'post',
    '/v1/rides/:id/cancel',
    { idem: true, body: z.object({ reason: text }).strict() },
    ({ db, user, req, body }) => rides.cancel(db, user, req.params.id, body.reason),
  );
  route(
    'post',
    '/v1/rides/:id/reschedule',
    { idem: true, body: z.object({ quoteId: uuid }).strict() },
    ({ db, user, req, body }) => rides.reschedule(db, user, req.params.id, body.quoteId),
  );
  route(
    'post',
    '/v1/rides/:id/actions',
    {
      idem: true,
      access: 'driver',
      body: z
        .object({
          action: z.enum([
            'EN_ROUTE',
            'ARRIVE',
            'START',
            'AT_STOP',
            'WAIT',
            'RETURN',
            'COMPLETE',
            'NO_SHOW',
          ]),
          pin: z
            .string()
            .regex(/^\d{4}$/)
            .optional(),
        })
        .strict(),
    },
    ({ db, user, req, body }) => rides.action(db, user, req.params.id, body, config),
  );
  route(
    'post',
    '/v1/rides/:id/cash',
    { idem: true, access: 'driver' },
    async ({ db, user, req }) => {
      const r = ensure(
        await one(db, 'SELECT * FROM rides WHERE id=$1 FOR UPDATE', [req.params.id]),
        404,
        'RIDE_NOT_FOUND',
      );
      const p = await ledger.collectCash(db, r, user);
      await event(db, 'payment.updated', r.id, { rideId: r.id, status: p.status }, [
        r.rider_id,
        r.driver_id,
      ]);
      return p;
    },
  );
  route('get', '/v1/driver-offers', { access: 'driver' }, async ({ db, user }) => ({
    items: (
      await db.query(
        "SELECT o.*,q.waypoints,q.category,q.estimated_minor FROM dispatch_offers o JOIN rides r ON r.id=o.ride_id JOIN fare_quotes q ON q.id=r.quote_id WHERE o.driver_id=$1 AND o.status='PENDING' AND o.expires_at>now()",
        [user.id],
      )
    ).rows,
  }));
  route(
    'post',
    '/v1/driver-offers/:id/respond',
    { access: 'driver', idem: true, body: z.object({ accept: z.boolean() }).strict() },
    ({ db, user, req, body }) => acceptOffer(db, user, req.params.id, body.accept),
  );
  route('get', '/v1/earnings/balance', { access: 'driver' }, ({ db, user }) =>
    ledger.balance(db, user.id),
  );
  route('get', '/v1/earnings/transactions', { access: 'driver' }, async ({ db, user, req }) => {
    const p = page.parse(req.query);
    return {
      items: (
        await db.query(
          'SELECT t.*,e.debit,e.credit,a.code FROM ledger_entries e JOIN ledger_accounts a ON a.id=e.account_id JOIN ledger_transactions t ON t.id=e.transaction_id WHERE a.user_id=$1 AND ($2::uuid IS NULL OR t.id<$2) ORDER BY t.id DESC LIMIT $3',
          [user.id, p.cursor ?? null, p.limit],
        )
      ).rows,
    };
  });
  route('get', '/v1/admin/dashboard', { permission: 'rides.monitor' }, async ({ db }) => ({
    counts: await one(db, `SELECT
      (SELECT count(*)::int FROM users WHERE deleted_at IS NULL) AS users,
      (SELECT count(*)::int FROM driver_profiles WHERE status='PENDING') AS pending_drivers,
      (SELECT count(*)::int FROM rides WHERE state NOT IN ('SCHEDULED','COMPLETED','CANCELLED','NO_DRIVER','NO_SHOW')) AS active_rides,
      (SELECT count(*)::int FROM rides WHERE created_at>=date_trunc('day',now() AT TIME ZONE 'Asia/Colombo') AT TIME ZONE 'Asia/Colombo') AS rides_today,
      (SELECT count(*)::int FROM service_areas WHERE active) AS active_areas,
      (SELECT count(*)::int FROM fare_plans) AS fare_plans`),
    rides: (await db.query('SELECT state,count(*)::int AS count FROM rides GROUP BY state ORDER BY count(*) DESC')).rows,
    recent: (await db.query('SELECT id,state,created_at,final_minor FROM rides ORDER BY created_at DESC LIMIT 6')).rows,
  }));
  route('get', '/v1/admin/users', { permission: 'users.manage' }, async ({ db, req }) => {
    const p = page.parse(req.query);
    return {
      items: (
        await db.query(
          'SELECT id,phone,name,roles,permissions,suspended FROM users WHERE ($1::uuid IS NULL OR id<$1) ORDER BY id DESC LIMIT $2',
          [p.cursor ?? null, p.limit],
        )
      ).rows,
    };
  });
  route(
    'patch',
    '/v1/admin/users/:id',
    { permission: 'users.manage', idem: true, body: z.object({ suspended: z.boolean() }).strict() },
    async ({ db, user, req, body }) => {
      ensure(req.params.id !== user.id, 409, 'CANNOT_SUSPEND_SELF');
      const target = ensure(
        await one(db, 'SELECT * FROM users WHERE id=$1 FOR UPDATE', [req.params.id]),
        404,
        'USER_NOT_FOUND',
      );
      ensure(!target.roles.includes('ADMIN') || user.roles.includes('ADMIN'), 403, 'FORBIDDEN');
      await db.query('UPDATE users SET suspended=$2 WHERE id=$1', [target.id, body.suspended]);
      if (body.suspended) {
        await db.query('UPDATE sessions SET revoked_at=now() WHERE user_id=$1', [target.id]);
        await db.query(
          "UPDATE driver_profiles SET availability='OFFLINE' WHERE user_id=$1 AND availability='AVAILABLE'",
          [target.id],
        );
      }
      await audit(db, user.id, 'USER_SUSPENSION', target.id, body);
      return { updated: true };
    },
  );
  route('get', '/v1/admin/drivers', { permission: 'drivers.review' }, async ({ db, req }) => {
    const p = page.parse(req.query);
    return {
      items: (
        await db.query(
          'SELECT d.*,u.name,u.phone FROM driver_profiles d JOIN users u ON u.id=d.user_id WHERE ($1::uuid IS NULL OR d.user_id<$1) ORDER BY d.user_id DESC LIMIT $2',
          [p.cursor ?? null, p.limit],
        )
      ).rows,
    };
  });
  route(
    'post',
    '/v1/admin/drivers/:id/review',
    {
      permission: 'drivers.review',
      idem: true,
      body: z
        .object({
          status: z.enum(['APPROVED', 'REJECTED', 'SUSPENDED']),
          vehicleId: uuid.optional(),
          reason: text.optional(),
        })
        .strict(),
    },
    ({ db, user, req, body }) => drivers.review(db, user, req.params.id, body),
  );
  route('get', '/v1/admin/drivers/:id', { permission: 'drivers.review' }, async ({ db, req }) => ({
    driver: ensure(await one(db, 'SELECT d.*,u.name,u.phone FROM driver_profiles d JOIN users u ON u.id=d.user_id WHERE d.user_id=$1', [req.params.id]), 404, 'DRIVER_NOT_FOUND'),
    vehicles: (await db.query('SELECT * FROM vehicles WHERE driver_id=$1 ORDER BY created_at', [req.params.id])).rows,
    documents: (await db.query('SELECT id,kind,mime,bytes,scan_status,expires_at,created_at FROM documents WHERE driver_id=$1 ORDER BY created_at', [req.params.id])).rows,
  }));
  route(
    'get',
    '/v1/admin/documents/:id',
    { permission: 'drivers.review' },
    async ({ db, user, req }) => {
      const d = ensure(
        await one(db, "SELECT * FROM documents WHERE id=$1 AND scan_status='CLEAN'", [
          req.params.id,
        ]),
        404,
        'DOCUMENT_NOT_AVAILABLE',
      );
      await audit(db, user.id, 'DOCUMENT_VIEW', d.id);
      return { document: d, downloadUrl: await storage.download(d.object_key, d.object_version) };
    },
  );
  route('get', '/v1/admin/rides', { permission: 'rides.monitor' }, async ({ db, req }) => {
    const p = page.parse(req.query);
    return {
      items: (
        await db.query(
          'SELECT id,rider_id,driver_id,state,version,scheduled_at,final_minor,created_at FROM rides WHERE ($1::uuid IS NULL OR id<$1) ORDER BY id DESC LIMIT $2',
          [p.cursor ?? null, p.limit],
        )
      ).rows,
    };
  });
  route('get', '/v1/admin/service-areas', { permission: 'config.manage' }, async ({ db }) => ({
    items: (
      await db.query(
        'SELECT id,name,active,ST_AsGeoJSON(polygon)::json AS polygon FROM service_areas',
      )
    ).rows,
  }));
  route('get', '/v1/admin/service-areas/:id/fares', { permission: 'config.manage' }, async ({ db, req }) => ({
    items: (await db.query('SELECT DISTINCT ON(category) * FROM fare_plans WHERE area_id=$1 ORDER BY category,version DESC', [req.params.id])).rows,
    surge: (await db.query('SELECT * FROM surge_rules WHERE area_id=$1 ORDER BY category', [req.params.id])).rows,
  }));
  route(
    'post',
    '/v1/admin/service-areas',
    {
      permission: 'config.manage',
      idem: true,
      body: z
        .object({
          name: text,
          polygon: z.object({
            type: z.literal('Polygon'),
            coordinates: z.array(z.array(coordinate).min(4)).min(1),
          }),
        })
        .strict(),
    },
    async ({ db, user, body }) => {
      const a = await one(
        db,
        'INSERT INTO service_areas(name,polygon) VALUES($1,ST_SetSRID(ST_GeomFromGeoJSON($2),4326)) RETURNING id,name,active',
        [body.name, JSON.stringify(body.polygon)],
      );
      await audit(db, user.id, 'AREA_CREATED', a.id);
      return a;
    },
  );
  const fare = z
    .object({
      category,
      baseMinor: z.number().int().min(0).max(100000000),
      perKmMinor: z.number().int().min(0).max(100000000),
      perMinuteMinor: z.number().int().min(0).max(10000000),
      waitingMinor: z.number().int().min(0).max(10000000),
      minimumMinor: z.number().int().min(0).max(100000000),
      commissionBps: z.number().int().min(0).max(10000),
      cancellationMinor: z.number().int().min(0).max(100000000).default(0),
    })
    .strict();
  route(
    'post',
    '/v1/admin/service-areas/:id/fares',
    { permission: 'config.manage', idem: true, body: fare },
    async ({ db, user, req, body: b }) => {
      ensure(
        await one(db, 'SELECT id FROM service_areas WHERE id=$1 FOR UPDATE', [req.params.id]),
        404,
        'AREA_NOT_FOUND',
      );
      const p = await one(
        db,
        `INSERT INTO fare_plans(area_id,category,version,base_minor,per_km_minor,per_minute_minor,waiting_minor,minimum_minor,commission_bps,cancellation_minor)
    VALUES($1,$2,(SELECT COALESCE(max(version),0)+1 FROM fare_plans WHERE area_id=$1 AND category=$2),$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
        [
          req.params.id,
          b.category,
          b.baseMinor,
          b.perKmMinor,
          b.perMinuteMinor,
          b.waitingMinor,
          b.minimumMinor,
          b.commissionBps,
          b.cancellationMinor,
        ],
      );
      await audit(db, user.id, 'FARE_PUBLISHED', p.id);
      return p;
    },
  );
  route(
    'patch',
    '/v1/admin/service-areas/:id',
    { permission: 'config.manage', idem: true, body: z.object({ active: z.boolean() }).strict() },
    async ({ db, user, req, body }) => {
      if (body.active) {
        ensure(
          await one(db, 'SELECT 1 FROM fare_plans WHERE area_id=$1', [req.params.id]),
          409,
          'FARES_REQUIRED',
        );
        if (config.NODE_ENV === 'production')
          ensure(config.MAP_CATEGORY_APPROVALS, 409, 'MAP_APPROVAL_REQUIRED');
      }
      const r = ensure(
        await one(db, 'UPDATE service_areas SET active=$2 WHERE id=$1 RETURNING id,active', [
          req.params.id,
          body.active,
        ]),
        404,
        'AREA_NOT_FOUND',
      );
      await audit(db, user.id, 'AREA_ACTIVATION', r.id, body);
      return r;
    },
  );
  route(
    'get',
    '/v1/admin/finance/drivers/:id/balance',
    { permission: 'finance.manage' },
    ({ db, req }) => ledger.balance(db, req.params.id),
  );
  route('get', '/v1/admin/finance/drivers', { permission: 'finance.manage' }, async ({ db, req }) => {
    const search = z.string().trim().max(80).default('').parse(req.query.search);
    return { items: (await db.query(`SELECT d.user_id,u.name,u.phone FROM driver_profiles d JOIN users u ON u.id=d.user_id
      WHERE u.name ILIKE $1 OR u.phone ILIKE $1 ORDER BY u.name,d.user_id LIMIT 100`, ['%' + search + '%'])).rows };
  });
  route(
    'post',
    '/v1/admin/finance/adjustments',
    {
      permission: 'finance.manage',
      idem: true,
      body: z
        .object({
          driverId: uuid,
          kind: z.enum(['CREDIT', 'SETTLEMENT']),
          amountMinor: money,
          reference: text,
          reason: text,
        })
        .strict(),
    },
    ({ db, user, body }) => ledger.adjustment(db, user, body),
  );
  route(
    'post',
    '/v1/admin/finance/payouts',
    {
      permission: 'finance.manage',
      idem: true,
      body: z.object({ driverId: uuid, amountMinor: money, reason: text }).strict(),
    },
    ({ db, user, body }) => ledger.createPayout(db, user, body),
  );
  route(
    'post',
    '/v1/admin/finance/payouts/:id/actions',
    {
      permission: 'finance.manage',
      idem: true,
      body: z
        .object({ action: z.enum(['APPROVE', 'REJECT', 'PAID']), bankReference: text.optional() })
        .strict(),
    },
    ({ db, user, req, body }) => ledger.payoutAction(db, user, req.params.id, body),
  );
  route(
    'get',
    '/v1/admin/finance/payouts',
    { permission: 'finance.manage' },
    async ({ db, req }) => {
      const p = page.parse(req.query);
      return {
        items: (
          await db.query(
            'SELECT * FROM payouts WHERE ($1::uuid IS NULL OR id<$1) ORDER BY id DESC LIMIT $2',
            [p.cursor ?? null, p.limit],
          )
        ).rows,
      };
    },
  );
  route('get', '/v1/admin/audit', { permission: 'audit.read' }, async ({ db, req }) => {
    const cursor = z.coerce.number().int().positive().optional().parse(req.query.cursor);
    return {
      items: (
        await db.query(
          'SELECT * FROM audit_logs WHERE ($1::bigint IS NULL OR id<$1) ORDER BY id DESC LIMIT 100',
          [cursor ?? null],
        )
      ).rows,
    };
  });
  route('get', '/v1/admin/operations', { permission: 'rides.monitor' }, async ({ db }) => ({
    rides: (await db.query('SELECT state,count(*)::int FROM rides GROUP BY state')).rows,
    outbox: await one(
      db,
      'SELECT count(*)::int AS pending,min(created_at) AS oldest FROM outbox_events WHERE published_at IS NULL',
    ),
    jobs: await one(
      db,
      'SELECT count(*) FILTER(WHERE dead)::int AS dead,count(*) FILTER(WHERE NOT dead AND done_at IS NULL)::int AS pending FROM jobs',
    ),
    providers: (await db.query('SELECT key,value,updated_at FROM system_state')).rows,
  }));
  route(
    'post',
    '/v1/admin/staff',
    {
      permission: 'users.manage',
      idem: true,
      body: z
        .object({
          phone,
          name: text,
          password: z.string().min(12).max(128),
          permissions: z
            .array(
              z.enum([
                'users.manage',
                'drivers.review',
                'rides.monitor',
                'rides.support',
                'config.manage',
                'finance.manage',
                'audit.read',
              ]),
            )
            .max(7),
        })
        .strict(),
    },
    async ({ db, user, body }) => {
      ensure(user.roles.includes('ADMIN'), 403, 'ADMIN_REQUIRED');
      const { normalizePhone } = await import('./modules/auth.js');
      const secret = new OTPAuth.Secret({ size: 20 }),
        passwordHash = await bcrypt.hash(body.password, 12);
      const staff = await one(
        db,
        'INSERT INTO users(phone,name,roles,permissions,password_hash,totp_secret) VALUES($1,$2,$3,$4,$5,$6) RETURNING id,name,permissions',
        [
          normalizePhone(body.phone),
          body.name,
          ['STAFF'],
          body.permissions,
          passwordHash,
          encrypt(secret.base32, config.ENCRYPTION_KEY),
        ],
      );
      await audit(db, user.id, 'STAFF_CREATED', staff.id, { permissions: body.permissions });
      return { ...staff, totpEnrollmentSecret: secret.base32 };
    },
  );
  route(
    'patch',
    '/v1/admin/staff/:id',
    {
      permission: 'users.manage',
      idem: true,
      body: z
        .object({
          permissions: z
            .array(
              z.enum([
                'users.manage',
                'drivers.review',
                'rides.monitor',
                'rides.support',
                'config.manage',
                'finance.manage',
                'audit.read',
              ]),
            )
            .max(7),
        })
        .strict(),
    },
    async ({ db, user, req, body }) => {
      ensure(user.roles.includes('ADMIN'), 403, 'ADMIN_REQUIRED');
      const staff = ensure(
        await one(
          db,
          "UPDATE users SET permissions=$2 WHERE id=$1 AND 'STAFF'=ANY(roles) RETURNING id,permissions",
          [req.params.id, body.permissions],
        ),
        404,
        'STAFF_NOT_FOUND',
      );
      await audit(db, user.id, 'STAFF_PERMISSIONS', staff.id, body);
      return staff;
    },
  );
  route(
    'put',
    '/v1/admin/service-areas/:id/surge',
    {
      permission: 'config.manage',
      idem: true,
      body: z
        .object({ category, enabled: z.boolean(), maximum: z.number().min(1).max(2) })
        .strict(),
    },
    async ({ db, user, req, body }) => {
      const r = await one(
        db,
        'INSERT INTO surge_rules(area_id,category,enabled,maximum) VALUES($1,$2,$3,$4) ON CONFLICT(area_id,category) DO UPDATE SET enabled=$3,maximum=$4 RETURNING *',
        [req.params.id, body.category, body.enabled, body.maximum],
      );
      await audit(db, user.id, 'SURGE_RULE', req.params.id, body);
      return r;
    },
  );
  route(
    'post',
    '/v1/admin/rides/:id/interrupt',
    {
      permission: 'rides.support',
      idem: true,
      body: z
        .object({ amountMinor: z.number().int().min(0).max(1000000000), reason: text })
        .strict(),
    },
    async ({ db, user, req, body }) => {
      const r = await rides.interrupt(db, user, req.params.id, body);
      await audit(db, user.id, 'RIDE_INTERRUPTED', req.params.id, body);
      return r;
    },
  );
  route(
    'post',
    '/v1/admin/finance/rides/:id/cancellation-settlement',
    {
      permission: 'finance.manage',
      idem: true,
      body: z.object({ reference: text, reason: text }).strict(),
    },
    ({ db, user, req, body }) => ledger.settleCancellation(db, user, req.params.id, body),
  );
  route(
    'post',
    '/v1/admin/finance/rides/:id/disputes',
    { permission: 'finance.manage', idem: true, body: z.object({ reason: text }).strict() },
    ({ db, user, req, body }) => ledger.openDispute(db, user, req.params.id, body.reason),
  );
  route(
    'post',
    '/v1/admin/finance/disputes/:id/resolve',
    {
      permission: 'finance.manage',
      idem: true,
      body: z
        .object({ amountMinor: money, cashConfirmed: z.boolean(), reference: text, reason: text })
        .strict(),
    },
    ({ db, user, req, body }) => ledger.resolveDispute(db, user, req.params.id, body),
  );
  route(
    'get',
    '/v1/admin/finance/reconciliations',
    { permission: 'finance.manage' },
    async ({ db, req }) => {
      const p = page.parse(req.query);
      return {
        items: (
          await db.query(
            'SELECT * FROM reconciliations WHERE ($1::uuid IS NULL OR id<$1) ORDER BY id DESC LIMIT $2',
            [p.cursor ?? null, p.limit],
          )
        ).rows,
      };
    },
  );
  app.get('/openapi.json', (req, res) =>
    res.json({
      openapi: '3.1.0',
      info: { title: 'Mazo Ride API', version: '1.0.0' },
      components: {
        securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } },
      },
      paths,
    }),
  );
  mountAdminSetup(app, { pool, redis, config, auth });
  mountWebsite(app);
  app.use((req, res, next) => next(new AppError(404, 'NOT_FOUND')));
  app.use(errorHandler(logger));
  return { app, auth, paths };
}
