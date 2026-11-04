import test, { before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { io as socketClient } from 'socket.io-client';
import supertest from 'supertest';
import { createClient } from 'redis';
import pino from 'pino';
import { runner } from 'node-pg-migrate';
import { database, one, transaction } from '../src/shared/db.js';
import { loadConfig } from '../src/shared/config.js';
import { decrypt, encrypt, hash } from '../src/shared/crypto.js';
import { createApp } from '../src/app.js';
import { createMaps } from '../src/adapters/maps.js';
import { attachRealtime } from '../src/realtime.js';
import { dispatchTick } from '../src/modules/dispatch.js';
import { publishOutbox, maintenance, claimJob, finishJob, processJobs } from '../src/worker.js';
import { idempotent } from '../src/shared/idempotency.js';
import { AppError } from '../src/shared/errors.js';
import { post, adjustment, createPayout, payoutAction } from '../src/modules/ledger.js';
const dbUrl = process.env.TEST_DATABASE_URL ?? 'postgres://ride:ride@localhost:5432/ride_test';
if (!new URL(dbUrl).pathname.endsWith('_test'))
  throw new Error('Test database name must end in _test');
const config = loadConfig({
  NODE_ENV: 'test',
  DATABASE_URL: dbUrl,
  REDIS_URL: process.env.TEST_REDIS_URL ?? 'redis://localhost:6379/15',
  JWT_SECRET: 't'.repeat(64),
  OTP_PEPPER: 'p'.repeat(64),
  ENCRYPTION_KEY: 'ab'.repeat(32),
  RECAPTCHA_V2_SITE_KEY: 'test-site-key',
  RECAPTCHA_V2_SECRET_KEY: 'test-secret-key',
});
const logger = pino({ level: 'silent' }),
  pool = database(dbUrl),
  redis = createClient({ url: config.REDIS_URL });
let app, auth, api, maps, server, realtime, area;
const pickup = [81.6973617, 7.7060005],
  dropoff = [81.7005996, 7.7099703];
const storage = {
  upload: async () => 'http://storage.test/upload',
  download: async () => 'http://storage.test/download',
  scan: async () => 'CLEAN',
};
before(async () => {
  const admin = database(new URL('/postgres', dbUrl).href);
  try {
    const exists = await one(admin, 'SELECT 1 FROM pg_database WHERE datname=$1', [
      new URL(dbUrl).pathname.slice(1),
    ]);
    if (!exists)
      await admin.query(
        `CREATE DATABASE "${new URL(dbUrl).pathname.slice(1).replaceAll('"', '""')}"`,
      );
  } finally {
    await admin.end();
  }
  await runner({
    databaseUrl: dbUrl,
    dir: 'migrations',
    direction: 'up',
    migrationsTable: 'schema_migrations',
    count: Infinity,
    checkOrder: true,
    log: () => {},
  });
  await redis.connect();
  maps = await createMaps(config);
  ({ app, auth } = createApp({ pool, redis, maps, storage, config, logger,
    captchaVerifier: async ({ token }) => { if (token !== 'captcha-test-token-value') throw new AppError(403,'CAPTCHA_INVALID'); return true; } }));
  api = supertest(app);
  server = createServer(app);
  realtime = await attachRealtime(server, { pool, redis, config, logger, auth });
  server.listen(0);
  await once(server, 'listening');
});
after(async () => {
  if (realtime) await realtime.close();
  if (server?.listening) await new Promise((r) => server.close(r));
  await pool.end();
  if (redis.isOpen) await redis.quit();
});
beforeEach(async () => {
  await pool.query(
    'TRUNCATE users,otp_challenges,service_areas,jobs,outbox_events,system_state,gateway_credentials RESTART IDENTITY CASCADE',
  );
  await redis.flushDb();
  area = await one(
    pool,
    "INSERT INTO service_areas(name,polygon,active) VALUES('Test area',ST_MakeEnvelope(81.69,7.50,81.80,7.73,4326),true) RETURNING *",
  );
  for (const c of ['TUK', 'CAR', 'VAN'])
    await pool.query(
      'INSERT INTO fare_plans(area_id,category,version,base_minor,per_km_minor,per_minute_minor,waiting_minor,minimum_minor,commission_bps) VALUES($1,$2,1,10000,8000,1000,2000,10000,2000)',
      [area.id, c],
    );
});
let phoneIndex = 0;
async function login() {
  const phone = `+9477${String(++phoneIndex).padStart(7, '0')}`,
    challenge = await auth.requestOtp(phone);
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [
    challenge.challengeId,
  ]);
  const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  return auth.verifyOtp(challenge.challengeId, code);
}
const request = (method, path, session, body, key = randomUUID()) =>
  api[method](path)
    .set('Authorization', `Bearer ${session.accessToken}`)
    .set('Idempotency-Key', key)
    .send(body);
async function driver() {
  const s = await login();
  await request('post', '/v1/drivers/onboarding', s, { name: 'Test driver' }).expect(200);
  const v = (
    await request('post', '/v1/drivers/vehicles', s, {
      registration: randomUUID().slice(0, 10),
      category: 'TUK',
      capacity: 3,
    }).expect(200)
  ).body;
  for (const [kind, vehicleId] of [
    ['IDENTITY', null],
    ['LICENSE', null],
    ['REGISTRATION', v.id],
    ['INSURANCE', v.id],
  ])
    await pool.query(
      "INSERT INTO documents(driver_id,vehicle_id,kind,object_key,mime,bytes,expires_at,scan_status) VALUES($1,$2,$3,$4,'application/pdf',100,now()+interval '1 year','CLEAN')",
      [s.user.id, vehicleId, kind, randomUUID()],
    );
  await pool.query("UPDATE driver_profiles SET status='APPROVED' WHERE user_id=$1", [s.user.id]);
  await pool.query("UPDATE vehicles SET status='APPROVED' WHERE id=$1", [v.id]);
  await putLocation(s, pickup, 1);
  await request('put', '/v1/drivers/availability', s, { availability: 'AVAILABLE' }).expect(200);
  return s;
}
async function putLocation(s, coordinates, sequence) {
  await request('post', '/v1/locations', s, {
    coordinates,
    accuracy: 10,
    sequence,
    recordedAt: new Date().toISOString(),
  }).expect(200);
}
async function makeQuote(s, extra = {}) {
  return (
    await request('post', '/v1/fare-quotes', s, {
      waypoints: [pickup, dropoff],
      category: 'TUK',
      tripType: 'ONE_WAY',
      ...extra,
    }).expect(201)
  ).body;
}
async function booking(s, extra = {}) {
  const q = await makeQuote(s, extra);
  return (await request('post', '/v1/rides', s, { quoteId: q.id }).expect(201)).body;
}
async function assigned(s, d, extra = {}) {
  const r = await booking(s, extra);
  await dispatchTick(pool, maps);
  const offer = await one(
    pool,
    "SELECT * FROM dispatch_offers WHERE ride_id=$1 AND status='PENDING'",
    [r.rideId],
  );
  assert(offer);
  await request('post', `/v1/driver-offers/${offer.id}/respond`, d, { accept: true }).expect(200);
  return r;
}
async function act(d, r, action, extra = {}) {
  return request('post', `/v1/rides/${r.rideId}/actions`, d, { action, ...extra });
}
async function teleport(d, point, seq) {
  await pool.query(
    "UPDATE driver_locations SET recorded_at=now()-interval '1 minute' WHERE driver_id=$1",
    [d.user.id],
  );
  await putLocation(d, point, seq);
}

test('PostGIS and real API authentication; OTP failures persist and single use holds', async () => {
  assert((await one(pool, 'SELECT PostGIS_Version() AS version')).version);
  const c = (await api.post('/v1/auth/otp/request').send({ phone: '0771234567' }).expect(202)).body;
  await api
    .post('/v1/auth/otp/verify')
    .send({ challengeId: c.challengeId, code: '000000' })
    .expect(401);
  assert.equal(
    (await one(pool, 'SELECT attempts FROM otp_challenges WHERE id=$1', [c.challengeId])).attempts,
    1,
  );
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [
    c.challengeId,
  ]);
  const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  const s = (
    await api.post('/v1/auth/otp/verify').send({ challengeId: c.challengeId, code }).expect(200)
  ).body;
  await request('get', '/v1/me', s).expect(200);
  await api.post('/v1/auth/otp/verify').send({ challengeId: c.challengeId, code }).expect(401);
  await api.post('/v1/auth/otp/request').send({ phone: '0771234567' }).expect(429);
});
test('refresh token reuse revokes the entire family', async () => {
  const s = await login(),
    next = await auth.refresh(s.refreshToken);
  await assert.rejects(auth.refresh(s.refreshToken), { code: 'REFRESH_REUSE' });
  await assert.rejects(auth.authenticate(next.accessToken), { code: 'SESSION_REVOKED' });
});
test('rate limit and validation are structured; RBAC blocks rider admin calls', async () => {
  const s = await login();
  await request('get', '/v1/admin/users', s).expect(403);
  const bad = await request('post', '/v1/fare-quotes', s, {
    waypoints: [[0, 0]],
    category: 'BUS',
  }).expect(400);
  assert.equal(bad.body.code, 'VALIDATION_ERROR');
  assert(bad.body.requestId);
  for (let n = 0; n < 11; n++)
    await api.post('/v1/auth/refresh').send({ refreshToken: 'x'.repeat(43) });
  await api
    .post('/v1/auth/refresh')
    .send({ refreshToken: 'x'.repeat(43) })
    .expect(429);
});
test('driver cannot go online without approval or fresh location', async () => {
  const s = await login();
  await request('post', '/v1/drivers/onboarding', s, { name: 'Unapproved' }).expect(200);
  await request('put', '/v1/drivers/availability', s, { availability: 'AVAILABLE' }).expect(409);
});
test('simultaneous idempotent bookings return one ride; mismatched payload conflicts', async () => {
  const s = await login(),
    q = await makeQuote(s),
    key = randomUUID();
  const results = await Promise.all(
    Array.from({ length: 8 }, () => request('post', '/v1/rides', s, { quoteId: q.id }, key)),
  );
  for (const r of results) assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(new Set(results.map((r) => r.body.rideId)).size, 1);
  assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM rides')).n, 1);
  await request('post', '/v1/rides', s, { quoteId: randomUUID() }, key).expect(409);
});
test('concurrent different bookings enforce one active rider ride', async () => {
  const s = await login(),
    q1 = await makeQuote(s),
    q2 = await makeQuote(s);
  const results = await Promise.all([
    request('post', '/v1/rides', s, { quoteId: q1.id }),
    request('post', '/v1/rides', s, { quoteId: q2.id }),
  ]);
  assert.deepEqual(results.map((r) => r.status).sort(), [201, 409]);
});
test('instant ride completes, cash is idempotent and driver owes commission', async () => {
  const s = await login(),
    d = await driver(),
    r = await assigned(s, d);
  await act(d, r, 'ARRIVE').then((x) => assert.equal(x.status, 200, JSON.stringify(x.body)));
  await act(d, r, 'START', { pin: '0000' }).then((x) => assert.equal(x.status, 401));
  assert.equal(
    (await one(pool, 'SELECT pin_attempts FROM rides WHERE id=$1', [r.rideId])).pin_attempts,
    1,
  );
  await act(d, r, 'START', { pin: r.ridePin }).then((x) => assert.equal(x.status, 200));
  await pool.query("UPDATE rides SET started_at=now()-interval '10 minutes' WHERE id=$1", [
    r.rideId,
  ]);
  await teleport(d, dropoff, 2);
  const key = randomUUID(),
    finished = await request(
      'post',
      `/v1/rides/${r.rideId}/actions`,
      d,
      { action: 'COMPLETE' },
      key,
    ).expect(200);
  const repeated = await request(
    'post',
    `/v1/rides/${r.rideId}/actions`,
    d,
    { action: 'COMPLETE' },
    key,
  ).expect(200);
  assert.deepEqual(repeated.body, finished.body);
  await request('post', `/v1/rides/${r.rideId}/cash`, d).expect(200);
  await request('post', `/v1/rides/${r.rideId}/cash`, d).expect(200);
  const b = (await request('get', '/v1/earnings/balance', d).expect(200)).body;
  assert(Number(b.net_minor) < 0);
  assert.equal(-Number(b.net_minor), Math.round(finished.body.finalMinor * 0.2));
  assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM ledger_transactions')).n, 2);
  assert.equal(Number((await one(pool, 'SELECT sum(debit-credit) AS n FROM ledger_entries')).n), 0);
});
test('return trip bills both legs, excludes stop waiting from driving, and returns to pickup', async () => {
  const s = await login(),
    d = await driver(),
    r = await assigned(s, d, { tripType: 'RETURN' });
  assert.equal((await act(d, r, 'ARRIVE')).status, 200);
  assert.equal((await act(d, r, 'START', { pin: r.ridePin })).status, 200);
  await teleport(d, dropoff, 2);
  assert.equal((await act(d, r, 'AT_STOP')).status, 200);
  assert.equal((await act(d, r, 'WAIT')).status, 200);
  await pool.query(
    "UPDATE rides SET started_at=now()-interval '20 minutes',stop_at=now()-interval '10 minutes' WHERE id=$1",
    [r.rideId],
  );
  assert.equal((await act(d, r, 'RETURN')).status, 200);
  await teleport(d, pickup, 3);
  const result = await act(d, r, 'COMPLETE');
  assert.equal(result.status, 200, JSON.stringify(result.body));
  const ev = await one(
    pool,
    "SELECT details FROM ride_events WHERE ride_id=$1 AND state='COMPLETED'",
    [r.rideId],
  );
  assert(ev.details.waitingSeconds >= 600);
  assert(ev.details.drivingSeconds < 605);
});
test('two dispatch workers issue one offer and only one acceptance wins', async () => {
  const s = await login(),
    d = await driver(),
    r = await booking(s);
  await Promise.all([dispatchTick(pool, maps), dispatchTick(pool, maps)]);
  const offers = (await pool.query('SELECT * FROM dispatch_offers WHERE ride_id=$1', [r.rideId]))
    .rows;
  assert.equal(offers.length, 1);
  const results = await Promise.all(
    [1, 2].map(() =>
      request('post', `/v1/driver-offers/${offers[0].id}/respond`, d, { accept: true }),
    ),
  );
  assert.deepEqual(results.map((r) => r.status).sort(), [200, 409]);
});
test('scheduled rides wait for dispatch window; busy rider cancels after grace period', async () => {
  const s = await login(),
    future = new Date(Date.now() + 3600000).toISOString(),
    r = await booking(s, { scheduledAt: future });
  await dispatchTick(pool, maps);
  assert.equal(
    (await one(pool, 'SELECT state FROM rides WHERE id=$1', [r.rideId])).state,
    'SCHEDULED',
  );
  await booking(s);
  await pool.query("UPDATE rides SET scheduled_at=now()-interval '6 minutes' WHERE id=$1", [
    r.rideId,
  ]);
  await dispatchTick(pool, maps);
  const row = await one(pool, 'SELECT state,cancel_reason FROM rides WHERE id=$1', [r.rideId]);
  assert.equal(row.state, 'CANCELLED');
  assert.equal(row.cancel_reason, 'RIDER_BUSY');
});
test('scheduled dispatch can recover after worker restart; no driver deadline is persisted', async () => {
  const s = await login(),
    r = await booking(s, { scheduledAt: new Date(Date.now() + 3600000).toISOString() });
  await pool.query("UPDATE rides SET scheduled_at=now()+interval '14 minutes' WHERE id=$1", [
    r.rideId,
  ]);
  await dispatchTick(pool, maps);
  assert.equal(
    (await one(pool, 'SELECT state FROM rides WHERE id=$1', [r.rideId])).state,
    'SEARCHING',
  );
  await pool.query("UPDATE rides SET search_deadline=now()-interval '1 second' WHERE id=$1", [
    r.rideId,
  ]);
  await dispatchTick(pool, maps);
  assert.equal(
    (await one(pool, 'SELECT state FROM rides WHERE id=$1', [r.rideId])).state,
    'NO_DRIVER',
  );
});
test('GPS rejects old sequence and implausible jumps; expiry removes driver from supply', async () => {
  const d = await driver();
  await request('post', '/v1/locations', d, {
    coordinates: pickup,
    accuracy: 10,
    sequence: 1,
    recordedAt: new Date().toISOString(),
  }).expect(409);
  await request('post', '/v1/locations', d, {
    coordinates: [82, 8],
    accuracy: 10,
    sequence: 2,
    recordedAt: new Date().toISOString(),
  }).expect(422);
  await pool.query("UPDATE driver_locations SET received_at=now()-interval '1 minute'");
  await maintenance(pool);
  assert.equal(
    (await one(pool, 'SELECT availability FROM driver_profiles WHERE user_id=$1', [d.user.id]))
      .availability,
    'OFFLINE',
  );
});
test('database rejects unbalanced and edited ledger records', async () => {
  const s = await login();
  await assert.rejects(
    transaction(pool, (db) =>
      post(db, {
        reference: 'bad',
        kind: 'TEST',
        entries: [
          { code: 'A', userId: s.user.id, debit: 100, credit: 0 },
          { code: 'B', userId: null, debit: 0, credit: 99 },
        ],
      }),
    ),
    { code: 'LEDGER_UNBALANCED' },
  );
  await assert.rejects(
    transaction(pool, async (db) => {
      const a = await one(db, "INSERT INTO ledger_accounts(code) VALUES('TEST') RETURNING id"),
        t = await one(
          db,
          "INSERT INTO ledger_transactions(reference,kind) VALUES('sql-bad','TEST') RETURNING id",
        );
      await db.query(
        'INSERT INTO ledger_entries(transaction_id,account_id,debit) VALUES($1,$2,100)',
        [t.id, a.id],
      );
    }),
    { code: '23514' },
  );
});
test('finance credit payout enforces available credit, two people and bank reference uniqueness', async () => {
  const d = await driver(),
    maker = (await login()).user,
    checker = (await login()).user;
  await transaction(pool, (db) =>
    adjustment(db, maker, {
      driverId: d.user.id,
      kind: 'CREDIT',
      amountMinor: 10000,
      reference: 'bonus-1',
      reason: 'Test bonus',
    }),
  );
  const p = await transaction(pool, (db) =>
    createPayout(db, maker, { driverId: d.user.id, amountMinor: 8000, reason: 'Weekly payout' }),
  );
  await assert.rejects(
    transaction(pool, (db) =>
      createPayout(db, maker, { driverId: d.user.id, amountMinor: 3000, reason: 'overdraft' }),
    ),
    { code: 'INSUFFICIENT_DRIVER_CREDIT' },
  );
  await assert.rejects(
    transaction(pool, (db) => payoutAction(db, maker, p.id, { action: 'APPROVE' })),
    { code: 'PAYOUT_APPROVAL_INVALID' },
  );
  await transaction(pool, (db) => payoutAction(db, checker, p.id, { action: 'APPROVE' }));
  await transaction(pool, (db) =>
    payoutAction(db, maker, p.id, { action: 'PAID', bankReference: 'bank-001' }),
  );
  assert.equal(Number((await request('get', '/v1/earnings/balance', d)).body.net_minor), 2000);
  await assert.rejects(pool.query('UPDATE ledger_entries SET debit=1'));
});
test('job leases are unique and stale workers cannot mark another lease complete', async () => {
  await pool.query("INSERT INTO jobs(kind,payload) VALUES('scan','{}')");
  const results = await Promise.all([claimJob(pool, ['scan']), claimJob(pool, ['scan'])]);
  assert.equal(results.filter(Boolean).length, 1);
  const job = results.find(Boolean);
  await finishJob(pool, { ...job, lease_token: randomUUID() });
  assert.equal((await one(pool, 'SELECT done_at FROM jobs WHERE id=$1', [job.id])).done_at, null);
  await finishJob(pool, job);
  assert((await one(pool, 'SELECT done_at FROM jobs WHERE id=$1', [job.id])).done_at);
});
test('outbox is durable and notifications deduplicate on retry', async () => {
  const s = await login();
  await booking(s);
  await publishOutbox({ pool, redis });
  assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM notifications')).n, 1);
  await pool.query('UPDATE outbox_events SET published_at=NULL');
  await publishOutbox({ pool, redis });
  assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM notifications')).n, 1);
});
test('self-hosted notifications survive offline use without external push jobs', async () => {
  const s = await login(), other = await login();
  await booking(s);
  await publishOutbox({ pool, redis, config: { FCM_ENABLED: 'false' } });
  const inbox = (await request('get', '/v1/me/notifications', s).expect(200)).body.items;
  assert.equal(inbox.length, 1);
  assert.equal(inbox[0].read_at, null);
  assert.equal((await request('get', '/v1/me/notifications', other).expect(200)).body.items.length, 0);
  assert.equal((await one(pool, "SELECT count(*)::int AS n FROM jobs WHERE kind='push'")).n, 0);
  await request('post', `/v1/me/notifications/${inbox[0].id}/read`, s).expect(200);
  assert((await request('get', '/v1/me/notifications', s).expect(200)).body.items[0].read_at);
});
test('optional external push queues once and preserves pending jobs while disabled', async () => {
  const s = await login();
  await booking(s);
  const enabled = { FCM_ENABLED: 'true' };
  await publishOutbox({ pool, redis, config: enabled });
  await pool.query('UPDATE outbox_events SET published_at=NULL');
  await publishOutbox({ pool, redis, config: enabled });
  assert.equal((await one(pool, "SELECT count(*)::int AS n FROM jobs WHERE kind='push'")).n, 1);
  await processJobs({ pool, storage, logger, push: null });
  const job = await one(pool, "SELECT * FROM jobs WHERE kind='push'");
  assert.equal(job.attempts, 0);
  assert.equal(job.done_at, null);
  let delivered = 0;
  await processJobs({ pool, storage, logger, push: async () => { delivered++; } });
  assert.equal(delivered, 1);
  assert((await one(pool, 'SELECT done_at FROM jobs WHERE id=$1', [job.id])).done_at);
});
test('WebSocket authenticates, checks ride ownership, delivers events and revokes sessions', async () => {
  const s = await login(),
    other = await login(),
    r = await booking(s),
    url = `http://localhost:${server.address().port}`;
  const socket = socketClient(url, {
    auth: { token: s.accessToken },
    transports: ['websocket'],
    reconnection: false,
  });
  await once(socket, 'connect');
  try {
    assert.equal(
      (await socket.timeout(2000).emitWithAck('ride.subscribe', { rideId: r.rideId })).ok,
      true,
    );
    const denied = socketClient(url, {
      auth: { token: other.accessToken },
      transports: ['websocket'],
      reconnection: false,
    });
    await once(denied, 'connect');
    try {
      assert.equal(
        (await denied.timeout(2000).emitWithAck('ride.subscribe', { rideId: r.rideId })).code,
        'FORBIDDEN',
      );
    } finally {
      denied.disconnect();
    }
    const received = once(socket, 'ride.updated');
    await publishOutbox({ pool, redis });
    assert.equal((await received)[0].payload.rideId, r.rideId);
    await request('post', '/v1/auth/logout', s).expect(200);
    const result = await socket.timeout(2000).emitWithAck('ride.subscribe', { rideId: r.rideId });
    assert.equal(result.code, 'SESSION_REVOKED');
  } finally {
    socket.disconnect();
  }
  const bad = socketClient(url, {
    auth: { token: 'bad' },
    transports: ['websocket'],
    reconnection: false,
  });
  try {
    await once(bad, 'connect_error');
  } finally {
    bad.disconnect();
  }
});

test('scheduled reschedule consumes a new quote and preserves old quote consumption', async () => {
  const s = await login(),
    r = await booking(s, { scheduledAt: new Date(Date.now() + 3600000).toISOString() });
  const original = await one(pool, 'SELECT quote_id FROM rides WHERE id=$1', [r.rideId]);
  const next = await makeQuote(s, { scheduledAt: new Date(Date.now() + 7200000).toISOString() });
  await request('post', `/v1/rides/${r.rideId}/reschedule`, s, { quoteId: next.id }).expect(200);
  await request('post', '/v1/rides', s, { quoteId: original.quote_id }).expect(409);
  assert.equal(
    (await one(pool, 'SELECT quote_id FROM rides WHERE id=$1', [r.rideId])).quote_id,
    next.id,
  );
});
test('cancellation fees create receivable instead of pretending cash was collected', async () => {
  const s = await login(),
    d = await driver();
  await pool.query(
    "INSERT INTO fare_plans(area_id,category,version,base_minor,per_km_minor,per_minute_minor,waiting_minor,minimum_minor,commission_bps,cancellation_minor) VALUES($1,'TUK',2,10000,8000,1000,2000,10000,2000,5000)",
    [area.id],
  );
  const r = await assigned(s, d);
  await request('post', `/v1/rides/${r.rideId}/cancel`, s, { reason: 'Changed plans' }).expect(200);
  const c = await one(pool, 'SELECT * FROM cancellation_charges WHERE ride_id=$1', [r.rideId]);
  assert.equal(c.amount_minor, 5000);
  assert.equal(c.status, 'DUE');
  assert.equal(await one(pool, 'SELECT * FROM payments WHERE ride_id=$1', [r.rideId]), undefined);
  const finance = (await login()).user;
  const { settleCancellation } = await import('../src/modules/ledger.js');
  await transaction(pool, (db) =>
    settleCancellation(db, finance, r.rideId, {
      reference: 'receipt-1',
      reason: 'Bank receipt checked',
    }),
  );
  assert.equal(
    Number(
      (
        await one(
          pool,
          "SELECT sum(e.debit-e.credit) AS n FROM ledger_entries e JOIN ledger_accounts a ON a.id=e.account_id WHERE a.code='RIDER_RECEIVABLE' AND a.user_id=$1",
          [s.user.id],
        )
      ).n,
    ),
    0,
  );
});
test('dispute correction reverses immutable entries and posts the reconciled amount', async () => {
  const s = await login(),
    d = await driver(),
    r = await assigned(s, d);
  assert.equal((await act(d, r, 'ARRIVE')).status, 200);
  assert.equal((await act(d, r, 'START', { pin: r.ridePin })).status, 200);
  await teleport(d, dropoff, 2);
  assert.equal((await act(d, r, 'COMPLETE')).status, 200);
  await request('post', `/v1/rides/${r.rideId}/cash`, d).expect(200);
  const finance = (await login()).user;
  const { openDispute, resolveDispute } = await import('../src/modules/ledger.js');
  const dispute = await transaction(pool, (db) =>
    openDispute(db, finance, r.rideId, 'Fare dispute'),
  );
  await request('post', `/v1/rides/${r.rideId}/cash`, d).expect(409);
  await transaction(pool, (db) =>
    resolveDispute(db, finance, dispute.id, {
      amountMinor: 15000,
      cashConfirmed: true,
      reference: 'dispute-bank-1',
      reason: 'Driver and rider confirmed corrected cash',
    }),
  );
  assert.equal(
    (await one(pool, 'SELECT amount_minor FROM payments WHERE ride_id=$1', [r.rideId]))
      .amount_minor,
    15000,
  );
  assert.equal(
    (await one(pool, "SELECT count(*)::int AS n FROM ledger_transactions WHERE kind='REVERSAL'")).n,
    2,
  );
  assert.equal(Number((await request('get', '/v1/earnings/balance', d)).body.net_minor), -3000);
  const history = (await request('get', `/v1/rides/${r.rideId}`, s).expect(200)).body;
  assert.equal(history.driverLocation, null);
});
test('malware scanner retries fail closed and saves exact object version on success', async () => {
  const d = await driver();
  const doc = await one(
    pool,
    "INSERT INTO documents(driver_id,kind,object_key,mime,bytes,expires_at) VALUES($1,'IDENTITY',$2,'application/pdf',100,now()+interval '1 year') RETURNING *",
    [d.user.id, randomUUID()],
  );
  const job = await one(pool, "INSERT INTO jobs(kind,payload) VALUES('scan',$1) RETURNING *", [
    { documentId: doc.id },
  ]);
  await processJobs({
    pool,
    storage: {
      scan: async () => {
        throw new Error('Scanner offline');
      },
    },
    logger,
  });
  assert.equal(
    (await one(pool, 'SELECT scan_status FROM documents WHERE id=$1', [doc.id])).scan_status,
    'PENDING',
  );
  await pool.query('UPDATE jobs SET run_at=now() WHERE id=$1', [job.id]);
  await processJobs({
    pool,
    storage: { scan: async () => ({ status: 'CLEAN', version: 'immutable-version-1' }) },
    logger,
  });
  assert.equal(
    (await one(pool, 'SELECT object_version FROM documents WHERE id=$1', [doc.id])).object_version,
    'immutable-version-1',
  );
});
test('staff TOTP is single-use and staff cannot grant permissions', async () => {
  const bcrypt = await import('bcryptjs'),
    OTPAuth = await import('otpauth'),
    { encrypt } = await import('../src/shared/crypto.js');
  const secret = new OTPAuth.Secret({ size: 20 }),
    password = 'test-passphrase-1234',
    ph = '+94779999999';
  await pool.query(
    "INSERT INTO users(phone,roles,password_hash,totp_secret,permissions) VALUES($1,'{STAFF}',$2,$3,'{users.manage}')",
    [ph, await bcrypt.hash(password, 4), encrypt(secret.base32, config.ENCRYPTION_KEY)],
  );
  const otp = new OTPAuth.TOTP({ secret }).generate();
  const session = await auth.staffLogin(ph, password, otp);
  await assert.rejects(auth.staffLogin(ph, password, otp), { code: 'TOTP_REUSED' });
  await request('post', '/v1/admin/staff', session, {
    phone: '0771111111',
    name: 'Forbidden',
    password,
    permissions: [],
  }).expect(403);
});

async function setupTest(fn) {
  config.ADMIN_SETUP_KEY = 'local-test-setup-key-'.repeat(3);
  config.ADMIN_SETUP_EXPIRES_AT = new Date(Date.now() + 3600000).toISOString();
  const call = (path, body, key = config.ADMIN_SETUP_KEY) => api.post(`/v1/admin-setup/${path}`)
    .set('X-Admin-Setup-Key', key).send(body);
  try { await fn(call); }
  finally { delete config.ADMIN_SETUP_KEY; delete config.ADMIN_SETUP_EXPIRES_AT; }
}
async function adminOtp(phone) {
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '61 seconds' WHERE phone=$1", [phone]);
  const result = await auth.requestOtp(phone);
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [result.challengeId]);
  return { challengeId: result.challengeId, code: decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY) };
}
const adminBody = (phone) => ({ phone, name: 'Test administrator', password: 'temporary-admin-password-1234' });

test('temporary admin setup is disabled by default, key protected and expires', async () => {
  await api.get('/admin-setup/').expect(404).expect('Cache-Control', 'no-store');
  await setupTest(async (call) => {
    await api.get('/admin-setup/').expect(200).expect('X-Robots-Tag', 'noindex, nofollow');
    await call('otp', { phone: '+94770000101' }, 'wrong-key').expect(404);
    await call('complete', { ...adminBody('+94770000101'), verification: 'setup-key' }, 'wrong-key').expect(404);
    assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM users')).n, 0);
    config.ADMIN_SETUP_EXPIRES_AT = new Date(Date.now() - 1000).toISOString();
    await call('otp', { phone: '+94770000101' }).expect(404);
  });
});

test('OTP-verified setup persists failed attempts, closes once and supports password plus OTP login', async () => {
  await setupTest(async (call) => {
    const phone = '+94770000102';
    const otp = await adminOtp(phone);
    const wrong = otp.code === '000000' ? '111111' : '000000';
    await call('complete', { ...adminBody(phone), ...otp, code: wrong }).expect(401);
    assert.equal((await one(pool, 'SELECT attempts FROM otp_challenges WHERE id=$1', [otp.challengeId])).attempts, 1);
    const result = (await call('complete', { ...adminBody(phone), ...otp }).expect(201)).body;
    assert.equal(result.totpEnabled, false);
    assert(result.recoveryPassword.length >= 32);
    const user = await one(pool, 'SELECT * FROM users WHERE id=$1', [result.id]);
    assert.deepEqual(user.roles, ['ADMIN']);
    assert.notEqual(user.password_hash, adminBody(phone).password);
    assert.notEqual(user.recovery_password_hash, result.recoveryPassword);
    await api.get('/admin-setup/').expect(404);
    await call('complete', { ...adminBody('+94770000103'), verification: 'setup-key' }).expect(404);
    await pool.query("UPDATE users SET roles=ARRAY['RIDER'] WHERE id=$1", [result.id]);
    await call('otp', { phone: '+94770000103' }).expect(404);
    await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1", [result.id]);
    await assert.rejects(auth.staffLogin(phone, adminBody(phone).password), { code: 'WHATSAPP_OTP_REQUIRED' });
    const next = await adminOtp(phone);
    const session = await auth.staffLogin(phone, adminBody(phone).password, undefined, next.challengeId, next.code);
    assert(session.accessToken);
    await assert.rejects(auth.staffLogin(phone, adminBody(phone).password, undefined, next.challengeId, next.code), { code: 'OTP_INVALID' });
  });
});

test('setup-key fallback creates only one first admin under concurrent requests', async () => {
  await setupTest(async (call) => {
    const results = await Promise.all([
      call('complete', { ...adminBody('+94770000104'), verification: 'setup-key' }),
      call('complete', { ...adminBody('+94770000105'), verification: 'setup-key' }),
    ]);
    assert.deepEqual(results.map((r) => r.status).sort(), [201, 404]);
    assert.equal((await one(pool, "SELECT count(*)::int AS n FROM users WHERE 'ADMIN'=ANY(roles)")).n, 1);
    assert.equal((await one(pool, "SELECT details->>'verification' AS mode FROM audit_logs WHERE action='ADMIN_WEB_BOOTSTRAP'")).mode, 'setup-key');
  });
});

test('optional TOTP is confirmed before storage; OTP cannot bypass enrolled TOTP', async () => {
  await setupTest(async (call) => {
    const OTPAuth = await import('otpauth');
    const phone = '+94770000106';
    const enrollment = (await call('totp', { phone }).expect(200)).body;
    await call('complete', { ...adminBody(phone), verification: 'setup-key', enrollment: enrollment.enrollment.slice(0, -4) + 'AAAA', totp: '000000' }).expect(400);
    await call('complete', { ...adminBody(phone), verification: 'setup-key', enrollment: enrollment.enrollment, totp: 'invalid' }).expect(400);
    const totp = new OTPAuth.TOTP({ secret: OTPAuth.Secret.fromBase32(enrollment.secret), digits: 6, period: 30 });
    const created = (await call('complete', { ...adminBody(phone), verification: 'setup-key', enrollment: enrollment.enrollment, totp: totp.generate() }).expect(201)).body;
    assert.equal(created.totpEnabled, true);
    const user = await one(pool, 'SELECT * FROM users WHERE id=$1', [created.id]);
    assert.notEqual(user.totp_secret, enrollment.secret);
    const otp = await adminOtp(phone);
    await assert.rejects(auth.staffLogin(phone, adminBody(phone).password, undefined, otp.challengeId, otp.code), { code: 'INVALID_CREDENTIALS' });
  });
});

test('admin recovery requires two passwords, rotates recovery secret and revokes older sessions', async () => {
  await setupTest(async (call) => {
    const phone = '+94770000107', password = adminBody(phone).password;
    const created = (await call('complete', { ...adminBody(phone), verification: 'setup-key' }).expect(201)).body;
    await assert.rejects(auth.recoverAdmin(phone, 'incorrect-password', created.recoveryPassword), { code: 'INVALID_CREDENTIALS' });
    await assert.rejects(auth.recoverAdmin(phone, password, 'incorrect-recovery-password'), { code: 'INVALID_CREDENTIALS' });
    const otp = await adminOtp(phone);
    const older = await auth.staffLogin(phone, password, undefined, otp.challengeId, otp.code);
    const recovered = (await api.post('/v1/auth/admin/recover').send({ phone, password, recoveryPassword: created.recoveryPassword })
      .expect(200).expect('Cache-Control', 'no-store')).body;
    assert(recovered.accessToken);
    assert.notEqual(recovered.recoveryPassword, created.recoveryPassword);
    await assert.rejects(auth.authenticate(older.accessToken), { code: 'SESSION_REVOKED' });
    await assert.rejects(auth.recoverAdmin(phone, password, created.recoveryPassword), { code: 'INVALID_CREDENTIALS' });
    assert.equal((await auth.authenticate(recovered.accessToken)).id, created.id);
    await pool.query('UPDATE users SET recovery_attempts=4 WHERE id=$1', [created.id]);
    await assert.rejects(auth.recoverAdmin(phone, password, 'incorrect-recovery-password'), { code: 'INVALID_CREDENTIALS' });
    await assert.rejects(auth.recoverAdmin(phone, password, recovered.recoveryPassword), { code: 'RECOVERY_LOCKED' });
  });
});
test('admin provisions staff; idempotency responses containing enrollment secrets are encrypted', async () => {
  const admin = await login();
  await pool.query("UPDATE users SET roles='{ADMIN}' WHERE id=$1", [admin.user.id]);
  const body = {
      phone: '0773333333',
      name: 'Reviewer',
      password: 'long-staff-password-123',
      permissions: ['drivers.review'],
    },
    key = randomUUID();
  const a = await request('post', '/v1/admin/staff', admin, body, key).expect(200),
    b = await request('post', '/v1/admin/staff', admin, body, key).expect(200);
  assert.equal(a.body.id, b.body.id);
  assert.equal(a.body.totpEnrollmentSecret, b.body.totpEnrollmentSecret);
  const record = await one(
    pool,
    "SELECT response FROM idempotency_records WHERE actor_id=$1 AND operation='post:/v1/admin/staff'",
    [admin.user.id],
  );
  assert(record.response.encryptedResponse);
  assert(!JSON.stringify(record.response).includes(a.body.totpEnrollmentSecret));
});
test('support interruption produces explicit partial fare and audit', async () => {
  const s = await login(),
    d = await driver(),
    r = await assigned(s, d);
  assert.equal((await act(d, r, 'ARRIVE')).status, 200);
  assert.equal((await act(d, r, 'START', { pin: r.ridePin })).status, 200);
  const staff = await login();
  await pool.query("UPDATE users SET roles='{STAFF}',permissions='{rides.support}' WHERE id=$1", [
    staff.user.id,
  ]);
  await request('post', `/v1/admin/rides/${r.rideId}/interrupt`, staff, {
    amountMinor: 12000,
    reason: 'Vehicle breakdown; partial trip confirmed',
  }).expect(200);
  assert.equal(
    (await one(pool, 'SELECT amount_minor FROM payments WHERE ride_id=$1', [r.rideId]))
      .amount_minor,
    12000,
  );
  assert.equal(
    (await one(pool, "SELECT count(*)::int AS n FROM audit_logs WHERE action='RIDE_INTERRUPTED'"))
      .n,
    1,
  );
});
test('no-show grace timer is enforced and driver cancellation restarts dispatch', async () => {
  const s = await login(),
    d = await driver(),
    r = await assigned(s, d);
  assert.equal((await act(d, r, 'ARRIVE')).status, 200);
  assert.equal((await act(d, r, 'NO_SHOW')).status, 409);
  await pool.query("UPDATE rides SET search_deadline=now()-interval '5 minutes' WHERE id=$1", [
    r.rideId,
  ]);
  await request('post', `/v1/rides/${r.rideId}/cancel`, d, {
    reason: 'Vehicle unavailable',
  }).expect(200);
  const row = await one(pool, 'SELECT state,driver_id,search_deadline FROM rides WHERE id=$1', [
    r.rideId,
  ]);
  assert.equal(row.state, 'SEARCHING');
  assert.equal(row.driver_id, null);
  assert(row.search_deadline > Date.now());
});

test('offer acceptance rechecks vehicle category after staff configuration changes', async () => {
  const rider = await login(),
    d = await driver(),
    r = await booking(rider);
  await dispatchTick(pool, maps);
  const offer = await one(
    pool,
    "SELECT * FROM dispatch_offers WHERE ride_id=$1 AND status='PENDING'",
    [r.rideId],
  );
  await pool.query("UPDATE vehicles SET category='CAR' WHERE driver_id=$1", [d.user.id]);
  await request('post', `/v1/driver-offers/${offer.id}/respond`, d, { accept: true }).expect(409);
  assert.equal(
    (await one(pool, 'SELECT state FROM rides WHERE id=$1', [r.rideId])).state,
    'SEARCHING',
  );
});
test('drivers cannot match or accept their own rider booking', async () => {
  const d = await driver(),
    r = await booking(d);
  await dispatchTick(pool, maps);
  assert.equal(
    (await one(pool, 'SELECT count(*)::int AS n FROM dispatch_offers WHERE ride_id=$1', [r.rideId]))
      .n,
    0,
  );
  const offer = await one(
    pool,
    "INSERT INTO dispatch_offers(ride_id,driver_id,expires_at) VALUES($1,$2,now()+interval '15 seconds') RETURNING id",
    [r.rideId, d.user.id],
  );
  const result = await request('post', `/v1/driver-offers/${offer.id}/respond`, d, {
    accept: true,
  }).expect(409);
  assert.equal(result.body.code, 'CANNOT_DRIVE_OWN_RIDE');
});

test('WhatsApp credentials import is encrypted, retains Signal keys and never replaces an existing session', async () => {
  const { initAuthCreds, BufferJSON } = await import('@whiskeysockets/baileys');
  const { createWhatsAppAuth } = await import('../src/adapters/whatsapp-auth.js');
  const seed = { ...initAuthCreds(), registered: true, me: { id: '94770000001@s.whatsapp.net' } };
  const state = await createWhatsAppAuth(pool, {
    ...config,
    WA_AUTH_CREDS: JSON.stringify(seed, BufferJSON.replacer),
  });
  assert(state.state.creds.noiseKey.private.equals(seed.noiseKey.private));
  await state.state.keys.set({ session: { 'synthetic-contact': Buffer.from('ratchet-state') } });
  await state.saveCreds();
  const persisted = await one(pool, "SELECT value FROM gateway_credentials WHERE id='creds'");
  assert(!persisted.value.includes(seed.me.id));
  const restored = await createWhatsAppAuth(pool, {
    ...config,
    WA_AUTH_CREDS: 'stale-or-invalid-export',
  });
  assert.equal(restored.state.creds.me.id, seed.me.id);
  assert(
    (await restored.state.keys.get('session', ['synthetic-contact']))['synthetic-contact'].equals(
      Buffer.from('ratchet-state'),
    ),
  );
});

test('concurrent scan jobs publish one immutable document reference and discard the unused copy', async () => {
  const d = await driver();
  const doc = await one(
    pool,
    "INSERT INTO documents(driver_id,kind,object_key,mime,bytes,expires_at) VALUES($1,'IDENTITY',$2,'application/pdf',100,now()+interval '1 year') RETURNING *",
    [d.user.id, 'staging/' + randomUUID()],
  );
  await pool.query("INSERT INTO jobs(kind,payload) VALUES('scan',$1),('scan',$1)", [
    { documentId: doc.id },
  ]);
  let release,
    calls = 0;
  const ready = new Promise((resolve) => {
    release = resolve;
  });
  const generated = [],
    discarded = [];
  const storage = {
    async scan() {
      const key = `verified/${doc.id}/${'a'.repeat(64)}-${randomUUID()}`;
      generated.push(key);
      if (++calls === 2) release();
      await ready;
      return { status: 'CLEAN', version: null, verifiedKey: key };
    },
    async discardVerified(key) {
      discarded.push(key);
    },
  };
  await Promise.all([
    processJobs({ pool, storage, logger }),
    processJobs({ pool, storage, logger }),
  ]);
  const published = await one(pool, 'SELECT * FROM documents WHERE id=$1', [doc.id]);
  assert.equal(published.scan_status, 'CLEAN');
  assert(generated.includes(published.object_key));
  assert.equal(discarded.length, 1);
  assert.notEqual(discarded[0], published.object_key);
});

test('private schema migrations are repeatable and backend role is constrained by RLS and grants', async () => {
  const { installPrivateSchema } = await import('../src/database/private-schema.js');
  const admin = await pool.connect();
  try {
    await installPrivateSchema(admin);
    await installPrivateSchema(admin);
  } finally {
    admin.release();
  }
  const appPool = database(dbUrl, { schema: 'ridenow', role: 'ridenow_backend', max: 1 });
  try {
    const conn = await appPool.connect();
    try {
      assert.equal((await one(conn, 'SELECT current_schema() AS name')).name, 'ridenow');
      await conn.query('BEGIN');
      const u = await one(conn, "INSERT INTO users(phone) VALUES('+94770000009') RETURNING id");
      assert(u.id);
      await conn.query('ROLLBACK');
      assert.equal(
        (await one(conn, "SELECT has_schema_privilege(current_user,'ridenow','CREATE') AS allowed"))
          .allowed,
        false,
      );
      await assert.rejects(conn.query('UPDATE schema_migrations SET name=name'), { code: '42501' });
      await assert.rejects(conn.query('DELETE FROM audit_logs'), { code: '42501' });
      const rls = await one(
        conn,
        "SELECT count(*)::int AS total,count(*) FILTER(WHERE rowsecurity)::int AS secured FROM pg_tables WHERE schemaname='ridenow'",
      );
      assert.equal(rls.total, rls.secured);
    } finally {
      conn.release();
    }
  } finally {
    await appPool.end();
  }
});

async function webAdmin({ totp = false } = {}) {
  const bcrypt = await import('bcryptjs'), OTPAuth = await import('otpauth');
  const password = 'admin-browser-password-1234', phone = '+94770000222';
  const recovery = 'test-recovery-password-'.repeat(3);
  const secret = totp ? new OTPAuth.Secret({size:20}) : null;
  const user = await one(pool, "INSERT INTO users(phone,name,roles,password_hash,totp_secret,recovery_password_hash) VALUES($1,'Alex Administrator',ARRAY['ADMIN'],$2,$3,$4) RETURNING id",
    [phone,await bcrypt.hash(password,4),secret?encrypt(secret.base32,config.ENCRYPTION_KEY):null,await bcrypt.hash(recovery,4)]);
  return { user, phone, password, recovery, secret, client: supertest.agent(app) };
}
const web = (client, method, path, body) => client[method]('/v1/admin-web/'+path).set('X-Admin-Request','1')
  .send(path==='begin'?{...body,captchaToken:body?.captchaToken??'captcha-test-token-value'}:body);
async function webOtp() {
  const job = await one(pool,"SELECT j.payload FROM jobs j JOIN otp_challenges c ON c.id::text=j.payload->>'challengeId' WHERE j.kind='otp' ORDER BY c.created_at DESC LIMIT 1");
  return decrypt(job.payload.encryptedCode,config.ENCRYPTION_KEY);
}
async function fillWebCode(page, value) {
  await page.locator('[data-code-digit="0"]').evaluate((field, code) => {
    const data = new DataTransfer();
    data.setData('text/plain', code);
    field.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
  }, value);
  assert.equal(await page.locator('[data-code-digit]').evaluateAll(fields => fields.map(field => field.value).join('')), value);
}

test('admin web flow protects pages and never exposes credentials in browser responses', async () => {
  const a=await webAdmin();
  await api.get('/admin/').expect(303).expect('Location','/admin/login/');
  await api.get('/admin-access/').expect(303).expect('Location','/admin/login/');
  await api.get('/admin/login/').expect(200).expect('Cache-Control','no-store');
  await a.client.post('/v1/admin-web/begin').send({phone:a.phone,password:a.password}).expect(403);
  await web(a.client,'post','begin',{phone:a.phone,password:'incorrect-password'}).expect(401);
  const begin=await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  assert(begin.headers['set-cookie'].some(c=>c.includes('HttpOnly')&&c.includes('SameSite=Strict')));
  assert.deepEqual(begin.body,{next:'/admin/verify/'});
  const state=(await web(a.client,'get','flow').expect(200)).body;
  assert.equal(state.method,'whatsapp');
  assert(!JSON.stringify(state).includes(a.phone));
  await web(a.client,'post','send-otp',{}).expect(200);
  const verified=await web(a.client,'post','verify',{code:await webOtp()}).expect(200);
  assert.deepEqual(verified.body,{next:'/admin/'});
  assert(!JSON.stringify(verified.body).includes('Token'));
  await a.client.get('/admin/').expect(200);
  await a.client.get('/v1/admin/dashboard').expect(403);
  await a.client.get('/v1/admin/dashboard').set('X-Admin-Request','1').expect(200);
  await a.client.post('/v1/admin-web/logout').set('X-Admin-Request','1').set('Origin','https://untrusted.invalid').send({}).expect(403);
  await web(a.client,'get','session').expect(200);
  await web(a.client,'post','logout',{}).expect(200);
  await web(a.client,'get','session').expect(401);
});

test('admin login requires a verified captcha and exposes only the public site key', async () => {
  const a = await webAdmin();
  const captcha = (await web(a.client, 'get', 'captcha-config').expect(200)).body;
  assert.deepEqual(captcha, { enabled: true, siteKey: 'test-site-key', testMode: true });
  await web(a.client, 'post', 'begin', {
    phone: a.phone, password: a.password, captchaToken: '',
  }).expect(400);
  await web(a.client, 'post', 'begin', {
    phone: a.phone, password: a.password, captchaToken: 'x'.repeat(30),
  }).expect(403);
  await web(a.client, 'post', 'begin', { phone: a.phone, password: a.password }).expect(200);
});

test('admin login rate limits password attempts by phone', async () => {
  const a = await webAdmin();
  for (let i = 0; i < 5; i++)
    await web(a.client, 'post', 'begin', { phone: a.phone, password: 'incorrect-password' }).expect(401);
  await web(a.client, 'post', 'begin', { phone: a.phone, password: a.password })
    .expect(429).expect('Retry-After', '900');
});

test('admin web password proof is invalid after password changes and cannot authenticate riders', async () => {
  const a=await webAdmin();
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  await pool.query("UPDATE users SET password_hash='changed-password-hash' WHERE id=$1",[a.user.id]);
  await web(a.client,'post','verify',{recoveryPassword:a.recovery}).expect(401);
  await pool.query("UPDATE users SET roles=ARRAY['RIDER'] WHERE id=$1",[a.user.id]);
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(401);
});

test('admin web enforces enrolled TOTP and recovery replacement is shown only once', async () => {
  const a=await webAdmin({totp:true}), OTPAuth=await import('otpauth');
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  await web(a.client,'post','send-otp',{}).expect(409);
  const code=new OTPAuth.TOTP({secret:a.secret}).generate();
  await web(a.client,'post','verify',{code}).expect(200);
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  const recovered=await web(a.client,'post','verify',{recoveryPassword:a.recovery}).expect(200);
  assert.equal(recovered.body.next,'/admin/recovery-saved/');
  const replacement=(await web(a.client,'get','recovery-password').expect(200)).body.recoveryPassword;
  assert(replacement&&replacement!==a.recovery);
  await web(a.client,'get','recovery-password').expect(404);
  await web(a.client,'post','verify',{code}).expect(401);
});

test('admin cookie session refresh is serialized across concurrent requests', async () => {
  const a=await webAdmin();
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  await web(a.client,'post','verify',{recoveryPassword:a.recovery}).expect(200);
  const keys=await redis.keys('admin-web:session:*');assert.equal(keys.length,1);
  const state=JSON.parse(decrypt(await redis.get(keys[0]),config.ENCRYPTION_KEY));
  state.accessExpiresAt=Date.now()-1000;
  await redis.set(keys[0],encrypt(JSON.stringify(state),config.ENCRYPTION_KEY),{EX:43200});
  const results=await Promise.all(Array.from({length:4},()=>web(a.client,'get','session')));
  assert(results.every(r=>r.status===200));
  assert.equal((await one(pool,'SELECT count(*)::int AS n FROM sessions WHERE revoked_at IS NULL')).n,1);
  await web(a.client,'post','logout',{}).expect(200);
  assert.equal((await one(pool,'SELECT count(*)::int AS n FROM sessions WHERE revoked_at IS NULL')).n,0);
});

test('staff web sign-in uses granted permissions and cannot use administrator recovery', async () => {
  const a=await webAdmin({totp:true}), OTPAuth=await import('otpauth');
  await pool.query("UPDATE users SET roles=ARRAY['STAFF'],permissions=ARRAY['finance.manage'] WHERE id=$1",[a.user.id]);
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  assert.equal((await web(a.client,'get','flow').expect(200)).body.recoveryAllowed,false);
  const code=new OTPAuth.TOTP({secret:a.secret}).generate();
  const result=await web(a.client,'post','verify',{code}).expect(200);
  assert.equal(result.body.next,'/admin/finance/');
  await a.client.get('/v1/admin/finance/payouts').set('X-Admin-Request','1').expect(200);
  await a.client.get('/v1/admin/finance/drivers').set('X-Admin-Request','1').expect(200);
  await a.client.get('/v1/admin/users').set('X-Admin-Request','1').expect(403);
  await web(a.client,'post','begin',{phone:a.phone,password:a.password}).expect(200);
  await web(a.client,'post','verify',{recoveryPassword:a.recovery}).expect(401);
});

test('admin dashboard read APIs report actual records and respect permissions', async () => {
  const a=await login(),d=await driver();
  await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1",[a.user.id]);
  const dashboard=(await request('get','/v1/admin/dashboard',a).expect(200)).body;
  assert.equal(dashboard.counts.users,2);
  const detail=(await request('get',`/v1/admin/drivers/${d.user.id}`,a).expect(200)).body;
  assert.equal(detail.documents.length,4);
  assert.equal(detail.vehicles.length,1);
  assert(detail.documents.every(doc=>doc.kind&&!doc.object_key));
  const fares=(await request('get',`/v1/admin/service-areas/${area.id}/fares`,a).expect(200)).body;
  assert.equal(fares.items.length,3);
  await request('get','/v1/admin/dashboard',d).expect(403);
});

test('admin browser staged sign-in, management pages, forms and logout', {skip:process.env.ADMIN_BROWSER_TEST!=='1'}, async () => {
  const {chromium}=await import('@playwright/test');
  const a=await webAdmin();
  await driver();
  const chromiumPath=process.env.CHROMIUM_PATH??(existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined);
  const browser=await chromium.launch({...(chromiumPath?{executablePath:chromiumPath}:{}),args:['--no-sandbox']});
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const page=await context.newPage(),base=`http://localhost:${server.address().port}`;
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  try{
    await page.goto(base+'/admin/login/');
    assert.equal(await page.locator('link[rel=icon]').getAttribute('href'),'/admin/assets/brand/ridenow-admin-icon.webp');
    assert.match(await page.locator('.auth-aside').evaluate(el=>getComputedStyle(el,'::before').backgroundImage),/admin-route-artwork\.webp/);
    await page.getByLabel('Phone number').fill(a.phone);
    await page.getByLabel('Password',{exact:true}).fill(a.password);
    await page.getByRole('button',{name:/^Continue/}).click();
    await page.waitForURL('**/admin/verify/');
    assert.equal(await page.locator('[data-code-digit]').count(),6);
    await page.setViewportSize({width:360,height:800});
    const digitRects=await page.locator('[data-code-digit]').evaluateAll(fields=>fields.map(field=>field.getBoundingClientRect().toJSON()));
    assert(digitRects.every(rect=>rect.width>=36&&rect.left>=0&&rect.right<=360));
    assert(digitRects.every(rect=>Math.abs(rect.top-digitRects[0].top)<1));
    await page.getByRole('button',{name:'Send WhatsApp code',exact:true}).click();
    await page.getByText('Code requested.',{exact:false}).waitFor();
    const otp=await webOtp();
    await fillWebCode(page,otp);
    await page.getByRole('button',{name:'Verify & sign in',exact:false}).click();
    await page.waitForURL(base+'/admin/');
    await page.getByText('Registered accounts',{exact:true}).waitFor();
    assert(await page.locator('.brand-mark-image').evaluate(img=>img.complete&&img.naturalWidth>0));
    await page.emulateMedia({colorScheme:'dark'});
    assert.equal(await page.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(17, 18, 20)');
    assert.equal(await page.locator('h1').evaluate(el=>getComputedStyle(el).color),'rgb(240, 241, 243)');
    await page.waitForFunction(()=>getComputedStyle(document.querySelector('.nav-item.active')).color==='rgb(255, 189, 0)');
    assert.equal(await page.locator('.nav-item.active').evaluate(el=>getComputedStyle(el).color),'rgb(255, 189, 0)');
    assert(await page.locator('.brand-mark-image').evaluate(img=>img.currentSrc.endsWith('ridenow-mark-white.webp')));
    await page.emulateMedia({colorScheme:'light'});
    assert.equal(await page.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(245, 246, 248)');
    assert.equal(await page.locator('h1').evaluate(el=>getComputedStyle(el).color),'rgb(23, 25, 28)');
    await page.waitForFunction(()=>getComputedStyle(document.querySelector('.nav-item.active')).color==='rgb(147, 107, 0)');
    assert(await page.locator('.brand-mark-image').evaluate(img=>img.currentSrc.endsWith('ridenow-mark-ink.webp')));
    const otpJobsBeforeResume=(await one(pool,"SELECT count(*)::int AS n FROM jobs WHERE kind='otp'")).n;
    await page.goto(base+'/admin/login/');
    await page.waitForURL(base+'/admin/');
    await page.goto(base+'/admin/login/?reauth=1');
    await page.getByLabel('Password',{exact:true}).waitFor();
    assert(page.url().endsWith('/admin/login/?reauth=1'));
    await page.goto(base+'/admin/');
    const savedBrowser=await context.storageState();
    const reopened=await browser.newContext({storageState:savedBrowser});
    try{
      const reopenedPage=await reopened.newPage();
      await reopenedPage.goto(base+'/admin/login/');
      await reopenedPage.waitForURL(base+'/admin/');
    }finally{await reopened.close();}
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.evaluate(url=>{location.href=url;},base+'/admin/');
    await page.waitForURL(base+'/admin/');
    await page.getByText('Registered accounts',{exact:true}).waitFor();
    assert.equal((await one(pool,"SELECT count(*)::int AS n FROM jobs WHERE kind='otp'")).n,otpJobsBeforeResume);
    await page.screenshot({path:'/tmp/ridenow-admin-overview.png',fullPage:true});
    for(const [path,title] of [['users','Users'],['drivers','Drivers'],['rides','Rides'],['areas','Service areas'],['pricing','Pricing'],['finance','Finance'],['staff','Team & access'],['audit','Audit log'],['security','Account & security']]){
      await page.goto(base+`/admin/${path}/`);
      await page.getByRole('heading',{name:title,exact:true}).waitFor();
      await page.waitForTimeout(150);
      assert.equal(await page.locator('#notice.error:visible').count(),0,path);
      assert(!/[\u0B80-\u0BFF]/.test(await page.locator('body').innerText()),path+' contains non-English UI');
    }
    await page.goto(base+'/admin/areas/');
    await page.getByLabel('Area name',{exact:true}).fill('Browser test area');
    await page.getByLabel('Boundary coordinates',{exact:false}).fill(JSON.stringify({type:'Polygon',coordinates:[[[81.69,7.70],[81.71,7.70],[81.71,7.72],[81.69,7.72],[81.69,7.70]]]}));
    await page.getByRole('button',{name:'Create inactive area',exact:true}).click();
    await page.getByText('Inactive service area created.',{exact:false}).waitFor();
    assert(await one(pool,"SELECT 1 FROM service_areas WHERE name='Browser test area' AND NOT active"));
    const browserArea=await one(pool,"SELECT id FROM service_areas WHERE name='Browser test area'");
    await page.goto(base+'/admin/pricing/');
    await page.getByRole('button',{name:'Service area'}).click();
    await page.getByRole('option',{name:'Browser test area · inactive'}).click();
    assert.equal(await page.locator('#area-select').inputValue(),browserArea.id);
    const fareForm=page.locator('#fare-form');
    for(const [name,value] of Object.entries({base:'100.50',perKm:'80',perMinute:'10',waiting:'20',minimum:'100.50',cancellation:'0',commission:'20'}))
      await fareForm.locator(`[name=${name}]`).fill(value);
    await fareForm.getByRole('button',{name:'Publish new version',exact:true}).click();
    await page.getByRole('dialog').getByRole('button',{name:'Cancel',exact:true}).click();
    assert.equal(await one(pool,'SELECT count(*)::int AS n FROM fare_plans WHERE area_id=$1',[browserArea.id]).then(r=>r.n),0);
    await fareForm.getByRole('button',{name:'Publish new version',exact:true}).click();
    await page.getByRole('dialog').getByRole('button',{name:'Publish fare plan',exact:true}).click();
    await page.getByText('New fare version published.',{exact:false}).waitFor();
    assert.equal((await one(pool,'SELECT base_minor FROM fare_plans WHERE area_id=$1',[browserArea.id])).base_minor,10050);
    await page.goto(base+'/admin/drivers/');
    await page.getByRole('button',{name:'Review',exact:true}).first().click();
    await page.getByRole('dialog').getByRole('heading',{name:'Driver review',exact:true}).waitFor();
    await page.getByRole('button',{name:'Close dialog'}).click();
    await page.goto(base+'/admin/finance/');
    await page.getByLabel('Name or phone',{exact:true}).fill('Test driver');
    await page.getByRole('button',{name:'Search drivers',exact:true}).click();
    await page.getByText('Driver choices updated.',{exact:true}).waitFor();
    assert.equal(await page.locator('[data-driver-select]').first().locator('option').count(),2);
    await page.setViewportSize({width:390,height:844});
    await page.goto(base+'/admin/');
    await page.getByRole('button',{name:'Open navigation',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('#sidebar').getBoundingClientRect().x>=-1);
    await page.screenshot({path:'/tmp/ridenow-admin-mobile.png',fullPage:true});
    await page.getByRole('button',{name:'Sign out',exact:false}).click();
    await page.waitForURL('**/admin/login/');
    await page.setViewportSize({width:1440,height:1000});
    await page.screenshot({path:'/tmp/ridenow-admin-login.png',fullPage:true});
    await page.getByLabel('Phone number').fill(a.phone);
    await page.getByLabel('Password',{exact:true}).fill(a.password);
    await page.getByRole('button',{name:/^Continue/}).click();
    await page.waitForURL('**/admin/verify/');
    await page.getByRole('link',{name:'Can’t access your code? Use recovery password'}).click();
    await page.waitForURL('**/admin/recovery/');
    await page.getByLabel('Recovery password',{exact:true}).fill(a.recovery);
    await page.getByRole('button',{name:/^Recover & sign in/}).click();
    await page.waitForURL('**/admin/recovery-saved/');
    await page.waitForFunction(()=>document.querySelector('#new-recovery-password').value.length>=32);
    assert(await page.getByRole('button',{name:/^Continue to workspace/}).isDisabled());
    await page.getByLabel('I have saved my new recovery password securely.').check();
    await page.getByRole('button',{name:/^Continue to workspace/}).click();
    await page.waitForURL(base+'/admin/');
    await page.getByRole('button',{name:'Sign out',exact:false}).click();
    await page.waitForURL('**/admin/login/');
    const OTPAuth=await import('otpauth');
    const secret=new OTPAuth.Secret({size:20});
    await pool.query('UPDATE users SET totp_secret=$2,totp_counter=-1 WHERE id=$1',[a.user.id,encrypt(secret.base32,config.ENCRYPTION_KEY)]);
    const jobsBeforeTotp=(await one(pool,"SELECT count(*)::int AS n FROM jobs WHERE kind='otp'")).n;
    await page.getByLabel('Phone number').fill(a.phone);
    await page.getByLabel('Password',{exact:true}).fill(a.password);
    await page.getByRole('button',{name:/^Continue/}).click();
    await page.waitForURL('**/admin/verify/');
    await page.getByLabel('Authenticator app code').waitFor();
    assert(await page.getByRole('button',{name:/Send WhatsApp code/}).isHidden());
    await fillWebCode(page,new OTPAuth.TOTP({secret}).generate());
    await page.getByRole('button',{name:'Verify & sign in',exact:false}).click();
    await page.waitForURL(base+'/admin/');
    assert.equal((await one(pool,"SELECT count(*)::int AS n FROM jobs WHERE kind='otp'")).n,jobsBeforeTotp);
    await page.getByRole('button',{name:'Sign out',exact:false}).click();
    await page.waitForURL('**/admin/login/');
    assert.deepEqual(errors,[]);
    assert.equal((await context.cookies()).some(c=>c.name==='ridenow-admin'),false);
  }catch(error){await page.screenshot({path:'/tmp/ridenow-admin-browser-failure.png',fullPage:true});throw error;}
  finally{await context.close();await browser.close();}
});
