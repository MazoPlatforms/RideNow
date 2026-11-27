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
import * as OTPAuth from 'otpauth';
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
  scan: async () => ({ status: 'CLEAN', version: 'profile-photo-test-version' }),
  sanitizeProfilePhoto: async () => ({ status: 'CLEAN', version: 'profile-photo-test-version' }),
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
test('international phone OTP verification creates a Rider account and profile', async () => {
  const challenge = (
    await api.post('/v1/auth/otp/request').send({ phone: '+1 (415) 555-2671' }).expect(202)
  ).body;
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [
    challenge.challengeId,
  ]);
  const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  const session = (
    await api.post('/v1/auth/otp/verify').send({ challengeId: challenge.challengeId, code }).expect(200)
  ).body;
  assert.equal(session.user.phone, '+14155552671');
  assert.equal(session.user.email, '');
  assert.equal(session.user.profileComplete, false);
  const profile = (await request('get', '/v1/me', session).expect(200)).body;
  assert.equal(profile.phone, '+14155552671');
  assert.deepEqual(profile.roles, ['RIDER']);
  assert.equal(profile.name, '');
  assert.equal(profile.profileComplete, false);
  assert.ok(session.accessToken);
  assert.ok(session.refreshToken);

  const saved = await request('patch', '/v1/me', session, {
    name: 'International Rider',
    email: 'international@example.test',
  }).expect(200);
  assert.equal(saved.body.name, 'International Rider');
  assert.equal(saved.body.email, 'international@example.test');
  assert.equal(saved.body.profileComplete, true);
  assert.ok(saved.body.profileCompletedAt);
  const cleared = await request('patch', '/v1/me', session, {
    name: 'International Rider',
    email: '',
  }).expect(200);
  assert.equal(cleared.body.email, '');
  assert.equal(cleared.body.profileComplete, true);
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '2 minutes' WHERE phone=$1", ['+14155552671']);
  const loginChallenge = (await api.post('/v1/auth/otp/request').send({ phone: '+14155552671' }).expect(202)).body;
  const loginJob = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [loginChallenge.challengeId]);
  const loginCode = decrypt(loginJob.payload.encryptedCode, config.ENCRYPTION_KEY);
  const nextDeviceSession = (await api.post('/v1/auth/otp/verify').send({ challengeId: loginChallenge.challengeId, code: loginCode }).expect(200)).body;
  assert.equal(nextDeviceSession.user.profileComplete, true);
  assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM users WHERE phone=$1', ['+14155552671'])).n, 1);
});
test('only approved active drivers can skip unfinished rider onboarding after phone verification', async () => {
  const session = await login();
  const admin = await login();
  await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1", [admin.user.id]);
  await request('post', '/v1/drivers/onboarding', session, { name: 'Driver Applicant' }).expect(200);
  const checkProfile = async (expected) => {
    const response = await request('get', '/v1/me', session).expect(200);
    assert.equal(response.body.profileComplete, expected);
    assert.equal(response.body.profileCompletedAt, null);
    const adminView = await request('get', '/v1/admin/users/' + session.user.id, admin).expect(200);
    assert.equal(adminView.body.user.profile_complete, expected);
  };
  for (const status of ['DRAFT', 'PENDING', 'REJECTED', 'SUSPENDED', 'APPROVED']) {
    await pool.query('UPDATE driver_profiles SET status=$2 WHERE user_id=$1', [session.user.id, status]);
    await checkProfile(status === 'APPROVED');
  }
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '2 minutes' WHERE phone=$1", [session.user.phone]);
  const challenge = (await api.post('/v1/auth/otp/request').send({ phone: session.user.phone }).expect(202)).body;
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [challenge.challengeId]);
  const samePhone = (await api.post('/v1/auth/otp/verify').send({
    challengeId: challenge.challengeId,
    code: decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY),
  }).expect(200)).body;
  assert.equal(samePhone.user.id, session.user.id);
  assert.equal(samePhone.user.profileComplete, true);
  assert.equal((await login()).user.profileComplete, false);

  await pool.query("UPDATE driver_profiles SET status='SUSPENDED' WHERE user_id=$1", [session.user.id]);
  await checkProfile(false);
  await request('patch', '/v1/me', session, { name: 'Rider Profile' }).expect(200);
  assert.equal((await request('get', '/v1/me', session).expect(200)).body.profileComplete, true);
});

test('rider profile creation stores name, email and only a normalized private profile photo', async () => {
  const session = await login();
  const saved = await request('patch', '/v1/me', session, {
    name: 'RideNow Rider',
    email: 'rider@example.test',
  }).expect(200);
  assert.equal(saved.body.name, 'RideNow Rider');
  assert.equal(saved.body.email, 'rider@example.test');
  assert.equal(saved.body.profilePhotoUrl, null);

  const staged = await request('post', '/v1/me/profile-photo', session, {
    mime: 'image/jpeg',
    bytes: 1024,
  }).expect(200);
  assert.match(staged.body.uploadUrl, /^http:\/\/storage\.test\/upload$/);
  assert.ok(staged.body.photoId);

  const completed = await request(
    'post',
    '/v1/me/profile-photo/' + staged.body.photoId + '/complete',
    session,
  ).expect(200);
  assert.equal(completed.body.profilePhotoUrl, 'http://storage.test/download');
  const stored = await one(pool, 'SELECT email,profile_photo_key,profile_photo_version FROM users WHERE id=$1', [session.user.id]);
  assert.equal(stored.email, 'rider@example.test');
  assert.equal(stored.profile_photo_version, 'profile-photo-test-version');
  assert.ok(stored.profile_photo_key.startsWith('profiles/' + session.user.id + '/'));
  await request('get', '/v1/me', session).expect(200);

  await request('post', '/v1/me/profile-photo', session, {
    mime: 'image/jpeg',
    bytes: 5 * 1024 * 1024 + 1,
  }).expect(400);
});
test('concurrent rider photo completion publishes once and commits both references together', async () => {
  const rider = await login();
  const staged = (await request('post', '/v1/me/profile-photo', rider,
    { mime: 'image/jpeg', bytes: 100 }).expect(200)).body;
  const original = storage.sanitizeProfilePhoto;
  let calls = 0;
  storage.sanitizeProfilePhoto = async () => {
    const version = 'sanitized-' + (++calls);
    await new Promise(resolve => setTimeout(resolve, 100));
    return { status: 'CLEAN', version };
  };
  try {
    const path = '/v1/me/profile-photo/' + staged.photoId + '/complete';
    await Promise.all([request('post', path, rider).expect(200), request('post', path, rider).expect(200)]);
    await request('post', path, rider).expect(200);
    const result = await one(pool, `SELECT u.profile_photo_version AS profile_version, p.object_version AS upload_version
      FROM users u JOIN profile_photo_uploads p ON p.user_id=u.id WHERE p.id=$1`, [staged.photoId]);
    assert.equal(calls, 1);
    assert.equal(result.profile_version, 'sanitized-1');
    assert.equal(result.upload_version, result.profile_version);
  } finally {
    storage.sanitizeProfilePhoto = original;
  }
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
  const receiptPath = `/v1/rides/${r.rideId}/receipt`;
  await request('get', receiptPath, s).expect(409);
  await request('get', receiptPath, d).expect(404);
  await request('post', `/v1/rides/${r.rideId}/cash`, d).expect(200);
  await request('post', `/v1/rides/${r.rideId}/cash`, d).expect(200);
  const receipt = await request('get', receiptPath, s).expect(200).expect('Cache-Control', 'no-store');
  assert.equal(receipt.body.rideId, r.rideId);
  assert.equal(receipt.body.amountMinor, finished.body.finalMinor);
  assert.equal(receipt.body.currency, 'LKR');
  assert.equal(receipt.body.paymentMethod, 'CASH');
  assert.ok(Number.isFinite(Date.parse(receipt.body.paidAt)));
  await request('get', receiptPath, d).expect(404);
  const summaryPath = '/v1/earnings/summary';
  await request('get', summaryPath, s).expect(403);
  const summary = (await request('get', summaryPath, d).expect(200)).body;
  assert.equal(summary.currency, 'LKR');
  assert.equal(summary.timezone, 'Asia/Colombo');
  const earned = finished.body.finalMinor - Math.round(finished.body.finalMinor * 0.2);
  for (const [name, length] of Object.entries({ today: 24, week: 7, month: 30, all: 6 })) {
    const period = summary.periods[name];
    assert.equal(period.rides, 1);
    assert.equal(Number(period.totalMinor), earned);
    assert.equal(Number(period.previousMinor), 0);
    assert.equal(period.series.length, length);
    assert.equal(period.series.reduce((total, item) => total + Number(item.minor), 0), earned);
  }
  const b = (await request('get', '/v1/earnings/balance', d).expect(200)).body;
  assert(Number(b.net_minor) < 0);
  assert.equal(-Number(b.net_minor), Math.round(finished.body.finalMinor * 0.2));
  assert.equal((await one(pool, 'SELECT count(*)::int AS n FROM ledger_transactions')).n, 2);
  assert.equal(Number((await one(pool, 'SELECT sum(debit-credit) AS n FROM ledger_entries')).n), 0);
  const other = await driver();
  const empty = (await request('get', summaryPath, other).expect(200)).body.periods;
  assert.equal(empty.all.totalMinor, '0');
  assert.equal(empty.all.rides, 0);
});
test('mobile ride flow exposes a scoped offer, live GPS and rider PIN, then recovers unsettled cash', async () => {
  const rider = await login();
  const outsider = await login();
  const operator = await driver();
  const waypointLabels = ['Library entrance', 'Town bus station'];
  const booked = await booking(rider, { waypointLabels });
  const riderPath = '/v1/mobile/ride?app=RIDER';
  const driverPath = '/v1/mobile/ride?app=DRIVER';
  await request('get', driverPath, rider).expect(403);
  await request('get', riderPath + '&id=' + booked.rideId, outsider).expect(404);
  let state = (await request('get', riderPath, rider).expect(200)).body.ride;
  assert.equal(state.state, 'SEARCHING');
  assert.deepEqual(state.waypoint_labels, waypointLabels);
  assert.equal(Object.hasOwn(state, 'ridePin'), false);
  assert.equal(Object.hasOwn(state, 'pin_encrypted'), false);

  await dispatchTick(pool, maps);
  const offerPath = '/v1/mobile/ride-offers';
  const offer = (await request('get', offerPath, operator).expect(200)).body.items[0];
  assert.ok(offer && Date.parse(offer.expires_at) > Date.now());
  assert.deepEqual(offer.waypoint_labels, waypointLabels);
  assert.equal(Object.hasOwn(offer, 'driver_id'), false);
  await request('get', offerPath, outsider).expect(403);
  await request('post', `/v1/driver-offers/${offer.id}/respond`, operator, { accept: true }).expect(200);
  assert.deepEqual((await request('get', offerPath, operator).expect(200)).body.items, []);
  assert.equal((await request('get', driverPath, operator).expect(200)).body.availability, 'RESERVED');

  state = (await request('get', riderPath, rider).expect(200)).body.ride;
  assert.equal(state.state, 'DRIVER_ASSIGNED');
  assert.equal(state.ridePin, booked.ridePin);
  assert.deepEqual(state.driverLocation, pickup);
  const driverState = (await request('get', driverPath, operator).expect(200)).body.ride;
  assert.equal(driverState.id, booked.rideId);
  assert.equal(Object.hasOwn(driverState, 'ridePin'), false);
  assert.equal(Object.hasOwn(driverState, 'pin_encrypted'), false);
  await request('get', driverPath + '&id=' + booked.rideId, await driver()).expect(404);

  await act(operator, booked, 'EN_ROUTE').then(response => assert.equal(response.status, 200));
  await act(operator, booked, 'ARRIVE').then(response => assert.equal(response.status, 200));
  await act(operator, booked, 'START', { pin: booked.ridePin }).then(response => assert.equal(response.status, 200));
  state = (await request('get', riderPath, rider).expect(200)).body.ride;
  assert.equal(state.state, 'IN_PROGRESS');
  assert.equal(Object.hasOwn(state, 'ridePin'), false);
  await teleport(operator, dropoff, 2);
  await act(operator, booked, 'COMPLETE').then(response => assert.equal(response.status, 200));
  state = (await request('get', driverPath, operator).expect(200)).body.ride;
  assert.equal(state.state, 'COMPLETED');
  assert.equal(state.payment_status, 'DUE');
  await request('put', '/v1/drivers/availability', operator, { availability: 'AVAILABLE' }).expect(409);
  await request('post', `/v1/rides/${booked.rideId}/cash`, operator).expect(200);
  assert.equal((await request('get', driverPath, operator).expect(200)).body.ride, null);
  state = (await request('get', riderPath + '&id=' + booked.rideId, rider).expect(200)).body.ride;
  assert.equal(state.payment_status, 'COLLECTED');
  assert.equal(Object.hasOwn(state, 'driverLocation'), false);
});

test('ride share links are opaque, scoped to participants, revocable and removed at ride end', async () => {
  const rider = await login();
  const outsider = await login();
  const operator = await driver();
  const ride = await assigned(rider, operator, { waypointLabels: ['Pickup gate', 'Market entrance'] });
  const sharePath = `/v1/rides/${ride.rideId}/share-link`;
  const first = (await request('post', sharePath, rider).expect(200).expect('Cache-Control', 'no-store')).body;
  assert.match(first.url, /^https:\/\/mzn-api\.onrender\.com\/trip\/#[-A-Za-z0-9_]{43}$/);
  assert(Date.parse(first.expiresAt) > Date.now());
  const firstToken = first.url.split('#')[1];
  const row = await one(pool, 'SELECT token_hash FROM ride_share_links WHERE ride_id=$1', [ride.rideId]);
  assert.notEqual(row.token_hash, firstToken);
  assert.equal(JSON.stringify(row).includes(firstToken), false);

  await request('post', sharePath, outsider).expect(403);
  await api.post('/v1/public/trips').send({ token: 'short' }).expect(400).expect('Cache-Control', 'no-store');
  const second = (await request('post', sharePath, operator).expect(200)).body;
  const secondToken = second.url.split('#')[1];
  assert.equal((await api.post('/v1/public/trips').send({ token: firstToken }).expect(404)).body.error.code, 'SHARED_TRIP_NOT_FOUND');
  const publicRide = (await api.post('/v1/public/trips').send({ token: secondToken }).expect(200).expect('Cache-Control', 'no-store')).body;
  assert.equal(publicRide.state, 'DRIVER_ASSIGNED');
  assert.equal(publicRide.pickup, 'Pickup gate');
  assert.equal(publicRide.destination, 'Market entrance');
  assert.equal(Object.hasOwn(publicRide, 'id'), false);
  assert.equal(Object.hasOwn(publicRide, 'phone'), false);
  assert.equal(Object.hasOwn(publicRide, 'ridePin'), false);
  assert.equal(Object.hasOwn(publicRide, 'passengerPhone'), false);

  await request('delete', sharePath, outsider).expect(403);
  await request('delete', sharePath, rider).expect(200);
  await api.post('/v1/public/trips').send({ token: secondToken }).expect(404);

  const terminalLink = (await request('post', sharePath, rider).expect(200)).body;
  await act(operator, ride, 'ARRIVE').expect(200);
  await act(operator, ride, 'START', { pin: ride.ridePin }).expect(200);
  await teleport(operator, dropoff, 2);
  await act(operator, ride, 'COMPLETE').expect(200);
  await api.post('/v1/public/trips').send({ token: terminalLink.url.split('#')[1] }).expect(404);
});

test('mobile history can open a scheduled booking and reload its updated pickup time', async () => {
  const rider = await login();
  const firstTime = new Date(Date.now() + 3600000).toISOString();
  const secondTime = new Date(Date.now() + 7200000).toISOString();
  const ride = await booking(rider, { scheduledAt: firstTime });
  const path = '/v1/mobile/ride?app=RIDER';
  assert.equal((await request('get', path, rider).expect(200)).body.ride, null);
  const idPath = path + '&id=' + ride.rideId;
  assert.equal((await request('get', idPath, rider).expect(200)).body.ride.scheduled_at, firstTime);
  const next = await makeQuote(rider, { scheduledAt: secondTime });
  await request('post', `/v1/rides/${ride.rideId}/reschedule`, rider, { quoteId: next.id }).expect(200);
  assert.equal((await request('get', idPath, rider).expect(200)).body.ride.scheduled_at, secondTime);
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
test('mobile ride history separates rider and driver records and paginates by recency', async () => {
  const dualRole = await driver();
  const rider = await login();
  const outsider = await login();
  const driven = await assigned(rider, dualRole);
  const scheduledAt = new Date(Date.now() + 3600000).toISOString();
  const first = await booking(dualRole, { scheduledAt });
  const second = await booking(dualRole, { scheduledAt: new Date(Date.now() + 3 * 3600000).toISOString() });
  await pool.query("UPDATE rides SET created_at=now()-interval '2 minutes' WHERE id=$1", [first.rideId]);

  const riderPath = '/v1/rides?app=RIDER&limit=1';
  const recent = (await request('get', riderPath, dualRole).expect(200)).body;
  assert.deepEqual(recent.items.map((item) => item.id), [second.rideId]);
  assert.equal(recent.items[0].state, 'SCHEDULED');
  assert.deepEqual(recent.items[0].waypoints, [pickup, dropoff]);
  assert.equal(recent.items[0].category, 'TUK');
  assert.ok(recent.items[0].estimated_minor > 0);
  for (const key of ['pin_hash', 'pin_encrypted', 'ridePin', 'rider_id', 'driver_id'])
    assert.equal(Object.hasOwn(recent.items[0], key), false);
  assert.equal(recent.nextCursor, second.rideId);

  const older = (await request('get', `${riderPath}&cursor=${recent.nextCursor}`, dualRole).expect(200)).body;
  assert.deepEqual(older.items.map((item) => item.id), [first.rideId]);
  assert.equal(older.nextCursor, null);
  const driverList = (await request('get', '/v1/rides?app=DRIVER', dualRole).expect(200)).body;
  assert.deepEqual(driverList.items.map((item) => item.id), [driven.rideId]);
  const combined = (await request('get', '/v1/rides', dualRole).expect(200)).body;
  assert.equal(combined.items.length, 3);
  const wrongRoleCursor = (await request('get', `${riderPath}&cursor=${driven.rideId}`, dualRole).expect(200)).body;
  assert.deepEqual(wrongRoleCursor.items, []);
  assert.deepEqual((await request('get', riderPath, outsider).expect(200)).body.items, []);
  await request('get', '/v1/rides?app=ADMIN', dualRole).expect(400);
});

test('scheduled cancellation rejects stale state and protects ownership', async () => {
  const rider = await login();
  const other = await login();
  const scheduledAt = new Date(Date.now() + 3600000).toISOString();
  const scheduled = await booking(rider, { scheduledAt });
  const active = await booking(rider);
  const payload = { reason: 'Changed plans', expectedState: 'SCHEDULED' };
  await request('post', `/v1/rides/${scheduled.rideId}/cancel`, other, payload).expect(403);
  await request('post', `/v1/rides/${active.rideId}/cancel`, rider, payload).expect(409);
  const key = randomUUID();
  for (let i = 0; i < 2; i++) {
    const cancelled = (await request('post', `/v1/rides/${scheduled.rideId}/cancel`, rider, payload, key).expect(200)).body;
    assert.equal(cancelled.state, 'CANCELLED');
  }
  assert.equal((await one(pool, 'SELECT state FROM rides WHERE id=$1', [active.rideId])).state, 'SEARCHING');
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

test('WhatsApp credentials import is encrypted and an unchanged environment seed keeps persisted session keys', async () => {
  const { initAuthCreds, BufferJSON } = await import('@whiskeysockets/baileys');
  const { createWhatsAppAuth } = await import('../src/adapters/whatsapp-auth.js');
  const seed = { ...initAuthCreds(), registered: true, me: { id: '94770000001@s.whatsapp.net' } };
  const seedExport = JSON.stringify(seed, BufferJSON.replacer);
  const state = await createWhatsAppAuth(pool, {
    ...config,
    WA_AUTH_CREDS: seedExport,
  });
  assert(state.state.creds.noiseKey.private.equals(seed.noiseKey.private));
  await state.state.keys.set({ session: { 'synthetic-contact': Buffer.from('ratchet-state') } });
  await state.saveCreds();
  const persisted = await one(pool, "SELECT value FROM gateway_credentials WHERE id='creds'");
  assert(!persisted.value.includes(seed.me.id));
  const restored = await createWhatsAppAuth(pool, {
    ...config,
    WA_AUTH_CREDS: seedExport,
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
async function solveBrowserCaptcha(page, responseOnly = false) {
  await page.waitForFunction(() => Boolean(window.__captchaOptions));
  await page.evaluate((withoutCallback) => {
    window.__captchaResponse = 'captcha-test-token-value';
    if (!withoutCallback) window.__captchaOptions.callback(window.__captchaResponse);
  }, responseOnly);
  await page.waitForFunction(() => !document.querySelector('#login-form button[type=submit]').disabled);
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
  await request('patch','/v1/me',a,{name:'Admin Updated Rider',email:'updated-rider@example.test'}).expect(200);
  const usersResponse=await request('get','/v1/admin/users',a).expect(200);
  assert.match(usersResponse.headers['cache-control'],/no-store/);
  const listed=usersResponse.body.items.find(user=>user.id===a.user.id);
  assert.equal(listed.name,'Admin Updated Rider');
  assert.equal(listed.email,'updated-rider@example.test');
  const userDetail=(await request('get',`/v1/admin/users/${a.user.id}`,a).expect(200)).body.user;
  assert.equal(userDetail.phone,a.user.phone);
  assert.equal(userDetail.name,'Admin Updated Rider');
  assert.equal(userDetail.email,'updated-rider@example.test');
  assert.equal(userDetail.profile_complete,true);
  assert.equal(userDetail.has_profile_photo,false);
  assert.equal(Object.hasOwn(userDetail,'password_hash'),false);
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
  await pool.query("UPDATE users SET name='Admin browser user',email='browser-admin@example.test' WHERE id=$1",[a.user.id]);
  await driver();
  const chromiumPath=process.env.CHROMIUM_PATH??(existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined);
  const browser=await chromium.launch({...(chromiumPath?{executablePath:chromiumPath}:{}),args:['--no-sandbox']});
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const page=await context.newPage(),base=`http://localhost:${server.address().port}`;
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  try{
    await page.route('**/v1/admin-web/captcha-config',route=>route.fulfill({json:{enabled:true,siteKey:'browser-test-site-key',testMode:false}}));
    await page.route('https://www.google.com/recaptcha/api.js?*',route=>route.fulfill({contentType:'application/javascript',body:'window.__captchaResponse="";window.grecaptcha={render:(node,options)=>{window.__captchaOptions=options;return 1},getResponse:()=>window.__captchaResponse,reset:()=>{window.__captchaResponse=""}};window.rideNowRecaptchaLoaded();'}));
    await page.goto(base+'/admin/login/?reauth=1');
    await page.getByLabel('Phone number').fill(a.phone);
    await page.getByLabel('Password',{exact:true}).fill(a.password);
    const continueButton=page.getByRole('button',{name:/^Continue/});
    await page.waitForFunction(()=>Boolean(window.__captchaOptions));
    assert.equal(await continueButton.isDisabled(),true);
    await solveBrowserCaptcha(page,true);
    assert.equal(await continueButton.isDisabled(),false);
    assert.equal(await page.locator('link[rel=icon]').getAttribute('href'),'/admin/assets/brand/ridenow-admin-icon.webp');
    assert.match(await page.locator('.auth-aside').evaluate(el=>getComputedStyle(el,'::before').backgroundImage),/admin-route-artwork\.webp/);
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
    await page.goto(base+'/admin/drivers/');
    await page.getByRole('button',{name:'Review',exact:true}).waitFor();
    await page.getByLabel('Onboarding status').selectOption('APPROVED');
    await page.getByRole('button',{name:'Review',exact:true}).click();
    await page.getByRole('dialog').getByRole('button',{name:'Open verified document'}).first().waitFor();
    await page.getByRole('dialog').getByRole('button',{name:'Close dialog'}).click();
    await page.goto(base+'/admin/users/');
    await page.locator('#search').fill('browser-admin@example.test');
    await page.getByRole('button',{name:'Details',exact:true}).click();
    await page.getByRole('dialog').getByText('browser-admin@example.test',{exact:true}).waitFor();
    await page.getByRole('dialog').getByText('Admin browser user',{exact:true}).waitFor();
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
    await page.setViewportSize({width:360,height:800});
    const helpRect=await page.locator('main>div:first-child>span').evaluate(el=>({rect:el.getBoundingClientRect().toJSON(),lineHeight:Number.parseFloat(getComputedStyle(el).lineHeight)}));
    assert(helpRect.rect.height<=helpRect.lineHeight+1&&helpRect.rect.right<=360);
    await page.setViewportSize({width:1440,height:1000});
    await page.screenshot({path:'/tmp/ridenow-admin-login.png',fullPage:true});
    await page.getByLabel('Phone number').fill(a.phone);
    await page.getByLabel('Password',{exact:true}).fill(a.password);
    await solveBrowserCaptcha(page);
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
    await solveBrowserCaptcha(page);
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

test('driver application enforces ownership, scan completion and revision-bound admin decisions', async () => {
  const applicant = await login();
  const other = await login();
  const admin = await login();
  await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1", [admin.user.id]);
  for (const session of [applicant, other])
    await request('post', '/v1/drivers/onboarding', session, { name: 'Applicant' }).expect(200);
  const vehicle = {
    registration: 'TX 4321', category: 'CAR', capacity: 4, make: 'Toyota',
    model: 'Yaris', modelYear: 2024, color: 'Blue',
  };
  const { body: { id: vehicleId } } = await request('post', '/v1/drivers/vehicles', applicant, vehicle).expect(200);
  const duplicate = (await request('post', '/v1/drivers/vehicles', other, {
    ...vehicle, registration: 'tx-4321', capacity: undefined,
  }).expect(200)).body;
  assert.notEqual(duplicate.id, vehicleId);
  assert.equal(duplicate.capacity, 4);
  const future = new Date(Date.now() + 86400000 * 365).toISOString();
  const draft = {
    name: 'Driver Applicant', email: 'driver@example.test', dateOfBirth: '1992-05-14', gender: 'OTHER',
    consent: { termsVersion: '1', termsAccepted: true, locationAccepted: true },
    identity: { number: '123456789V' },
    license: { number: 'DL123456', class: 'B', expiresOn: future.slice(0, 10) },
    schedule: { workType: 'PART_TIME', days: [1, 5], start: '10:00', end: '14:00', remind: false },
    serviceArea: { city: 'Kattankudy', radiusKm: 15, zones: ['CENTRE'], acceptOutside: false },
    bank: { holder: 'Driver Applicant', bank: 'Test bank', branch: 'Branch', number: '12678901234' },
  };
  await request('put', '/v1/drivers/application', applicant, { ...draft, dateOfBirth: '2020-01-01' }).expect(400);
  await request('put', '/v1/drivers/application', applicant, draft).expect(200);
  const stored = await one(pool, 'SELECT application_encrypted FROM driver_profiles WHERE user_id=$1', [applicant.user.id]);
  assert(!stored.application_encrypted.includes(draft.bank.number));
  const own = (await request('get', '/v1/drivers/me', applicant).expect(200)).body;
  assert.equal(own.application.bank.lastFour, '1234');
  assert(!JSON.stringify(own).includes(draft.bank.number));
  assert(!Object.hasOwn(own.profile, 'application_encrypted'));

  const adminPath = `/v1/admin/drivers/${applicant.user.id}`;
  await request('get', adminPath, other).expect(403);
  assert.equal((await request('get', adminPath, admin).expect(200)).body.application, null);
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId }).expect(409);

  const files = [
    ['PROFILE_PHOTO'], ['IDENTITY_FRONT'], ['IDENTITY_BACK'],
    ['LICENSE_FRONT', null, future], ['LICENSE_BACK', null, future],
    ['REGISTRATION', vehicleId, future], ['REVENUE_LICENSE', vehicleId, future], ['INSURANCE', vehicleId, future],
    ...['VEHICLE_FRONT','VEHICLE_REAR','VEHICLE_LEFT','VEHICLE_RIGHT'].map(kind => [kind, vehicleId]),
  ];
  await request('post', '/v1/drivers/documents', other, {
    vehicleId, kind: 'VEHICLE_FRONT', mime: 'image/jpeg', bytes: 100,
  }).expect(403);
  const documentIds = [];
  for (const [kind, owner, expiresAt] of files) {
    const { body: { documentId } } = await request('post', '/v1/drivers/documents', applicant, {
      kind, vehicleId: owner ?? undefined, expiresAt, mime: 'image/jpeg', bytes: 100,
    }).expect(200);
    documentIds.push(documentId);
    await request('post', `/v1/drivers/documents/${documentId}/submit`, applicant).expect(200);
  }
  const photoSubmit = `/v1/drivers/documents/${documentIds[0]}/submit`;
  await request('post', photoSubmit, other).expect(404);
  await request('post', photoSubmit, applicant).expect(200);
  assert.equal((await one(pool, "SELECT count(*)::int AS total FROM jobs WHERE kind='scan'")).total, files.length);
  const submit = { vehicleId, documentIds, informationConfirmed: true };
  await request('post', '/v1/drivers/submit', applicant, submit).expect(409);
  await pool.query("UPDATE jobs SET run_at=now() WHERE kind='scan'");
  await processJobs({ pool, storage, logger });
  await request('get', `/v1/admin/documents/${documentIds[0]}`, admin).expect(404);
  const submitted = (await request('post', '/v1/drivers/submit', applicant, submit).expect(200)).body;
  assert.equal(submitted.status, 'PENDING');
  await request('put', '/v1/drivers/application', applicant, draft).expect(409);
  await request('post', '/v1/drivers/onboarding', applicant, { name: 'Attempt to change name' }).expect(200);
  const detail = (await request('get', adminPath, admin).expect(200)).body;
  assert.equal(detail.application.name, draft.name);
  assert.equal(detail.application.identity.number, '••••789V');
  assert.equal(detail.application.bankProvided, true);
  assert(!JSON.stringify(detail).includes(draft.bank.number));
  assert(!JSON.stringify(detail).includes(stored.application_encrypted));
  assert.equal((await request('get', '/v1/admin/drivers?status=PENDING', admin).expect(200)).body.items.length, 1);
  await request('get', `/v1/admin/documents/${documentIds[0]}`, other).expect(403);
  const download = (await request('get', `/v1/admin/documents/${documentIds[0]}`, admin).expect(200)).body;
  assert.deepEqual(Object.keys(download), ['downloadUrl']);
  const revision = submitted.application_revision;
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision: randomUUID(), expectedStatus: 'PENDING' }).expect(409);
  await request('post', adminPath + '/review', admin, { status: 'REJECTED', reason: 'Use a clearer photo', revision, expectedStatus: 'PENDING' }).expect(200);
  const resubmitted = (await request('post', '/v1/drivers/submit', applicant, submit).expect(200)).body;
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision, expectedStatus: 'PENDING' }).expect(409);
  const newRevision = resubmitted.application_revision;
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision: newRevision, expectedStatus: 'PENDING' }).expect(200);
  await assert.rejects(pool.query("UPDATE vehicles SET status='APPROVED' WHERE id=$1", [duplicate.id]),
    { code: '23505' });
  await putLocation(applicant, pickup, 1);
  await request('put', '/v1/drivers/availability', applicant, { availability: 'AVAILABLE' }).expect(200);
  await request('post', adminPath + '/review', admin, { status: 'SUSPENDED', revision: newRevision, reason: 'Safety review', expectedStatus: 'APPROVED' }).expect(200);
  await request('put', '/v1/drivers/availability', applicant, { availability: 'AVAILABLE' }).expect(409);
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision: newRevision, expectedStatus: 'PENDING' }).expect(409);
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision: newRevision, expectedStatus: 'SUSPENDED' }).expect(200);
  await pool.query("UPDATE documents SET expires_at=now()-interval '1 day' WHERE id=$1", [documentIds[6]]);
  await request('put', '/v1/drivers/availability', applicant, { availability: 'AVAILABLE' }).expect(409);
  await request('post', '/v1/drivers/reapplication', applicant, { confirmed: true }).expect(200);
  await request('put', '/v1/drivers/application', applicant, draft).expect(200);
  const editedVehicle = (await request('post', '/v1/drivers/vehicles', applicant, {
    ...vehicle, color: 'Silver',
  }).expect(200)).body;
  assert.equal(editedVehicle.id, vehicleId);
  const replacement = (await request('post', '/v1/drivers/documents', applicant, {
    vehicleId, kind: 'REVENUE_LICENSE', expiresAt: future, mime: 'image/jpeg', bytes: 100,
  }).expect(200)).body.documentId;
  await request('post', `/v1/drivers/documents/${replacement}/submit`, applicant).expect(200);
  const updatedSubmission = { ...submit, documentIds: documentIds.map(id => id === documentIds[6] ? replacement : id) };
  await request('post', '/v1/drivers/submit', applicant, updatedSubmission).expect(409);
  await pool.query("UPDATE jobs SET run_at=now() WHERE kind='scan'");
  await processJobs({ pool, storage, logger });
  const updatedRevision = (await request('post', '/v1/drivers/submit', applicant, updatedSubmission).expect(200)).body.application_revision;
  assert.notEqual(updatedRevision, newRevision);
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision: newRevision, expectedStatus: 'PENDING' }).expect(409);
  await request('post', adminPath + '/review', admin, { status: 'APPROVED', vehicleId, revision: updatedRevision, expectedStatus: 'PENDING' }).expect(200);
  assert(await one(pool, 'SELECT id FROM documents WHERE id=$1', [documentIds[6]]));
  assert.equal((await one(pool, 'SELECT color FROM vehicles WHERE id=$1', [vehicleId])).color, 'Silver');
});

test('phone changes require both scoped codes, preserve the user and revoke other sessions', async () => {
  const owner = await login();
  const stranger = await login();
  const target = '+94772229999';
  await api.post('/v1/auth/phone-change/request').send({ phone: target }).expect(401);
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '2 minutes' WHERE phone=$1", [owner.user.phone]);
  await request('post', '/v1/auth/phone-change/request', owner, { phone: stranger.user.phone }).expect(409);
  const change = (await request('post', '/v1/auth/phone-change/request', owner, { phone: target }).expect(202)).body;
  assert.equal(change.phone, target);
  const codes = {};
  const rows = (await pool.query(`SELECT c.id,c.purpose,j.payload FROM otp_challenges c JOIN jobs j
    ON j.payload->>'challengeId'=c.id::text WHERE c.change_id=$1`, [change.changeId])).rows;
  assert.equal(rows.length, 2);
  for (const row of rows) {
    const code = decrypt(row.payload.encryptedCode, config.ENCRYPTION_KEY);
    codes[row.purpose] = code;
    await api.post('/v1/auth/otp/verify').send({ challengeId: row.id, code }).expect(401);
  }
  const proof = { changeId: change.changeId, currentCode: codes.PHONE_CURRENT, newCode: codes.PHONE_NEW };
  await request('post', '/v1/auth/phone-change/verify', stranger, proof).expect(400);
  const wrong = proof.newCode === '111111' ? '222222' : '111111';
  await request('post', '/v1/auth/phone-change/verify', owner, { ...proof, newCode: wrong }).expect(400);
  assert.equal((await request('get', '/v1/me', owner).expect(200)).body.phone, owner.user.phone);
  const verified = (await request('post', '/v1/auth/phone-change/verify', owner, proof).expect(200)).body;
  assert.equal(verified.user.phone, target);
  assert.equal(verified.user.id, owner.user.id);
  await request('get', '/v1/me', owner).expect(401);
  await api.post('/v1/auth/refresh').send({ refreshToken: owner.refreshToken }).expect(401);
  await request('post', '/v1/auth/phone-change/verify', verified, proof).expect(400);
  assert.equal((await request('get', '/v1/me', verified).expect(200)).body.phone, target);
  assert.equal((await one(pool, "SELECT count(*)::int AS n FROM audit_logs WHERE action='ACCOUNT_PHONE_CHANGED'")).n, 1);
});

test('rider extras and driver contact edits persist with separate private avatar scopes', async () => {
  const owner = await driver();
  const other = await driver();
  const rider = { name: 'Test Rider', email: 'rider@example.test', gender: 'NON_BINARY', dateOfBirth: '1996-03-10', city: 'Kattankudy' };
  await request('patch', '/v1/me', owner, rider).expect(200);
  const saved = (await request('get', '/v1/me', owner).expect(200)).body;
  assert.equal(saved.gender, rider.gender);
  assert.equal(saved.dateOfBirth, rider.dateOfBirth);
  assert.equal(saved.city, rider.city);
  await request('patch', '/v1/me', owner, { name: 'Test Rider', dateOfBirth: '2099-01-01' }).expect(400);
  await request('patch', '/v1/me', owner, { name: 'Test Rider', city: null }).expect(200);
  assert.equal((await request('get', '/v1/me', owner).expect(200)).body.city, null);

  const driverContact = { name: 'Test Driver', email: 'driver@example.test', address: 'Driver address', preferredLanguage: 'Tamil' };
  await request('patch', '/v1/drivers/profile', owner, { ...driverContact, identityNumber: 'Different ID' }).expect(409);
  await request('patch', '/v1/drivers/profile', owner, driverContact).expect(200);
  const returned = (await request('get', '/v1/drivers/me', owner).expect(200)).body;
  for (const [key, value] of Object.entries(driverContact)) assert.equal(returned.contact[key], value);
  assert(!Object.hasOwn(returned.profile, 'contact_encrypted'));
  assert(!Object.hasOwn(returned.profile, 'profile_photo_key'));
  assert.equal((await request('get', '/v1/me', owner).expect(200)).body.name, rider.name);
  const photo = (await request('post', '/v1/drivers/profile-photo', owner, { mime: 'image/jpeg', bytes: 100 }).expect(200)).body;
  await request('post', `/v1/me/profile-photo/${photo.photoId}/complete`, owner).expect(404);
  await request('post', `/v1/drivers/profile-photo/${photo.photoId}/complete`, other).expect(404);
  await request('post', `/v1/drivers/profile-photo/${photo.photoId}/complete`, owner).expect(200);
  assert.equal((await request('get', '/v1/me', owner).expect(200)).body.profilePhotoUrl, null);
  assert.equal((await request('get', '/v1/drivers/me', owner).expect(200)).body.contact.photoUrl, 'http://storage.test/download');
  await request('patch', '/v1/drivers/profile', owner, { ...driverContact, photoVisible: false }).expect(200);
  assert.equal((await request('get', '/v1/drivers/me', owner).expect(200)).body.contact.photoUrl, null);
});

test('reviewing updated vehicle details requires a new application and blocks online mode', async () => {
  const d = await driver();
  await pool.query("UPDATE driver_profiles SET availability='ON_TRIP' WHERE user_id=$1", [d.user.id]);
  await request('post', '/v1/drivers/reapplication', d, { confirmed: true }).expect(409);
  await pool.query("UPDATE driver_profiles SET availability='OFFLINE' WHERE user_id=$1", [d.user.id]);
  await request('post', '/v1/drivers/reapplication', d, { confirmed: false }).expect(400);
  const started = await request('post', '/v1/drivers/reapplication', d, { confirmed: true }).expect(200);
  assert.equal(started.body.status, 'DRAFT');
  const view = (await request('get', '/v1/drivers/me', d).expect(200)).body;
  assert.equal(view.profile.availability, 'OFFLINE');
  assert.equal(view.vehicles.length, 1);
  await request('put', '/v1/drivers/availability', d, { availability: 'AVAILABLE' }).expect(409);
  await request('post', '/v1/drivers/vehicles', d, {
    registration: view.vehicles[0].registration, category: 'TUK', make: 'Bajaj',
    model: 'RE', modelYear: 2024, color: 'Blue',
  }).expect(200);
});

async function signInDevice(phone, device) {
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '61 seconds' WHERE phone=$1", [phone]);
  const { challengeId } = await auth.requestOtp(phone);
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [challengeId]);
  const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  return (await api.post('/v1/auth/otp/verify').send({ challengeId, code, device }).expect(200)).body;
}

test('device list keeps names through rotation, registers older sessions and protects account boundaries', async () => {
  const old = await login();
  const rider = { installationId: randomUUID(), name: 'Maazin’s Phone', app: 'RIDER' };
  const driver = { installationId: randomUUID(), name: rider.name, app: 'DRIVER' };
  const signed = await signInDevice(old.user.phone, driver);
  const stranger = await login();
  await api.get('/v1/auth/devices').expect(401);
  await request('put', '/v1/auth/devices/current', old, { ...rider, name: 'Phone\u202e' }).expect(400);
  const enrolled = (await request('put', '/v1/auth/devices/current', old, rider).expect(200)).body.items;
  assert.equal(enrolled.length, 2);
  const current = enrolled.find(item => item.current);
  const other = enrolled.find(item => !item.current);
  assert.equal(current.name, rider.name);
  assert.equal(current.app, 'RIDER');
  assert.equal(other.app, 'DRIVER');
  assert.notEqual(current.id, other.id);
  assert(!JSON.stringify(enrolled).includes(rider.installationId));
  assert.deepEqual(Object.keys(current).sort(), ['app','current','id','name','updatedAt']);
  const saved = await one(pool, "SELECT device_key FROM sessions WHERE user_id=$1 AND device_app='RIDER'", [old.user.id]);
  assert.notEqual(saved.device_key, rider.installationId);

  const rotated = await auth.refresh(signed.refreshToken);
  await request('put', '/v1/auth/devices/current', rotated, { ...driver, installationId: randomUUID() }).expect(409);
  const view = (await request('get', '/v1/auth/devices', rotated).expect(200)).body.items.find(item => item.current);
  assert.equal(view.name, driver.name);
  assert.equal(view.id, other.id);
  await request('delete', '/v1/auth/devices/' + other.id, stranger).expect(200);
  await request('get', '/v1/me', rotated).expect(200);
  await request('delete', '/v1/auth/devices/' + current.id.toUpperCase(), old).expect(409);
  await request('delete', '/v1/auth/devices/' + other.id, old).expect(200);
  await request('get', '/v1/me', rotated).expect(401);
  await assert.rejects(auth.refresh(rotated.refreshToken), { code: 'REFRESH_REUSE' });
  await request('get', '/v1/me', old).expect(200);
});

test('signing in again on one installation replaces its session and bulk revocation wins against an in-flight refresh', async () => {
  const owner = await login();
  const install = { installationId: randomUUID(), name: 'Pixel', app: 'RIDER' };
  const replaced = await signInDevice(owner.user.phone, install);
  const current = await signInDevice(owner.user.phone, { ...install, name: 'My Pixel' });
  await request('get', '/v1/me', replaced).expect(401);
  const other = await signInDevice(owner.user.phone, { ...install, app: 'DRIVER' });
  const outsider = await login();
  const recent = (await request('get', '/v1/auth/devices', current).expect(200)).body.items;
  assert.equal(recent.length, 3);
  assert.equal(recent.find(item => item.current).name, 'My Pixel');
  assert.equal(recent.find(item => item.app === null).name, null);
  const [rotation, revoke] = await Promise.allSettled([
    auth.refresh(other.refreshToken),
    request('post', '/v1/auth/devices/revoke-others', current, {}).expect(200),
  ]);
  assert.equal(revoke.status, 'fulfilled');
  if (rotation.status === 'fulfilled') {
    await request('get', '/v1/me', rotation.value).expect(401);
    await assert.rejects(auth.refresh(rotation.value.refreshToken), { code: 'REFRESH_REUSE' });
  } else {
    assert.equal(rotation.reason.code, 'REFRESH_REUSE');
  }
  await request('get', '/v1/me', owner).expect(401);
  assert.equal((await request('get', '/v1/auth/devices', current).expect(200)).body.items.length, 1);
  await request('get', '/v1/me', outsider).expect(200);
  await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1", [outsider.user.id]);
  await request('get', '/v1/auth/devices', outsider).expect(403);
});

async function twoStepOtp(phone) {
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '61 seconds' WHERE phone=$1", [phone]);
  const challenge = await auth.requestOtp(phone);
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [challenge.challengeId]);
  return { challengeId: challenge.challengeId, code: decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY) };
}

async function enrollMobileFactor(session) {
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '61 seconds' WHERE phone=$1", [session.user.phone]);
  const setup = (await request('post', '/v1/auth/two-step/setup', session).expect(200)).body;
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'purpose'='TWO_STEP_SETUP' AND payload->>'phone'=$1 ORDER BY run_at DESC LIMIT 1",
    [session.user.phone]);
  const whatsappCode = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  const totp = new OTPAuth.TOTP({ secret: OTPAuth.Secret.fromBase32(setup.secret) });
  return { setup, job, whatsappCode, totp };
}

test('mobile two-step is opt-in, binds enrollment to its session family and revokes pre-enrollment sessions', async () => {
  const session = await login();
  const otherProof = await twoStepOtp(session.user.phone);
  const other = await auth.verifyOtp(otherProof.challengeId, otherProof.code);
  assert(other.accessToken);
  assert.deepEqual((await request('get', '/v1/auth/two-step', session).expect(200)).body,
    { enabled: false, recoveryRemaining: 0 });
  await api.post('/v1/auth/two-step/setup').expect(401);
  const { setup, job, whatsappCode, totp } = await enrollMobileFactor(session);
  const pending = await one(pool, 'SELECT mobile_totp_pending FROM users WHERE id=$1', [session.user.id]);
  assert(!JSON.stringify(pending).includes(setup.secret));
  await api.post('/v1/auth/otp/verify').send({ challengeId: job.payload.challengeId, code: whatsappCode }).expect(401);

  const enroll = { whatsappCode, code: totp.generate() };
  await request('post', '/v1/auth/two-step/enable', other, enroll).expect(400);
  const wrong = whatsappCode === '111111' ? '222222' : '111111';
  await request('post', '/v1/auth/two-step/enable', session, { ...enroll, whatsappCode: wrong }).expect(400);
  const refreshed = await auth.refresh(session.refreshToken);
  const response = (await request('post', '/v1/auth/two-step/enable', refreshed, enroll).expect(200)).body;
  assert.equal(response.recoveryCodes.length, 8);
  assert.equal(new Set(response.recoveryCodes).size, 8);
  const user = await one(pool, 'SELECT mobile_totp_secret,mobile_recovery_hashes,mobile_totp_pending FROM users WHERE id=$1', [session.user.id]);
  assert.equal(user.mobile_totp_pending, null);
  assert.equal(decrypt(user.mobile_totp_secret, config.ENCRYPTION_KEY), setup.secret);
  assert(!JSON.stringify(user).includes(response.recoveryCodes[0]));
  await request('get', '/v1/me', other).expect(401);
  await request('get', '/v1/me', refreshed).expect(200);
  await request('post', '/v1/auth/two-step/disable', refreshed, { code: enroll.code }).expect(400);
});

test('mobile sign-in enforces a fresh TOTP or single-use recovery code, limits attempts and supports secure disable', async () => {
  const session = await login();
  const { whatsappCode, totp } = await enrollMobileFactor(session);
  const enrollmentTime = Date.now();
  const enrollmentToken = totp.generate({ timestamp: enrollmentTime });
  const enrolled = (await request('post', '/v1/auth/two-step/enable', session,
    { whatsappCode, code: enrollmentToken }).expect(200)).body;
  const firstProof = await twoStepOtp(session.user.phone);
  const device = { installationId: randomUUID(), name: 'Phone', app: 'DRIVER' };
  const pending = (await api.post('/v1/auth/otp/verify').send({ ...firstProof, device }).expect(200)).body;
  assert.deepEqual(Object.keys(pending), ['twoStepToken']);
  await api.get('/v1/me').set('Authorization', 'Bearer ' + pending.twoStepToken).expect(401);
  const verify = (token, code) => api.post('/v1/auth/two-step/verify').send({ token, code });
  await verify(pending.twoStepToken, enrollmentToken).expect(401);
  const futureCode = totp.generate({ timestamp: enrollmentTime + 30_000 });
  const signed = (await verify(pending.twoStepToken, futureCode).expect(200)).body;
  await verify(pending.twoStepToken, enrolled.recoveryCodes[0]).expect(401);
  const listed = (await request('get', '/v1/auth/devices', signed).expect(200)).body.items;
  assert.equal(listed.find(item => item.current).app, 'DRIVER');

  const another = async () => {
    const proof = await twoStepOtp(session.user.phone);
    return (await auth.verifyOtp(proof.challengeId, proof.code)).twoStepToken;
  };
  const recovery = await another();
  const competing = await another();
  const redemption = await Promise.allSettled([recovery, competing].map(ticket =>
    auth.twoStep.verifyLogin(ticket, enrolled.recoveryCodes[0])));
  assert.equal(redemption.filter(item => item.status === 'fulfilled' && item.value.accessToken).length, 1);
  assert.equal(redemption.find(item => item.status === 'rejected')?.reason.code, 'TWO_STEP_INVALID');
  const blocked = await another();
  for (let i = 0; i < 5; i++) {
    await assert.rejects(auth.twoStep.verifyLogin(blocked, enrolled.recoveryCodes[0]), { code: 'TWO_STEP_INVALID' });
  }
  await assert.rejects(auth.twoStep.verifyLogin(blocked, enrolled.recoveryCodes[1]), { code: 'TWO_STEP_INVALID' });
  const expired = await another();
  await pool.query('UPDATE mobile_login_steps SET expires_at=now()-interval \'1 second\'');
  await assert.rejects(auth.twoStep.verifyLogin(expired, enrolled.recoveryCodes[1]), { code: 'TWO_STEP_INVALID' });

  const replaced = (await request('post', '/v1/auth/two-step/recovery', session,
    { code: enrolled.recoveryCodes[1] }).expect(200)).body.recoveryCodes;
  const fresh = await another();
  await assert.rejects(auth.twoStep.verifyLogin(fresh, enrolled.recoveryCodes[2]), { code: 'TWO_STEP_INVALID' });
  await request('post', '/v1/auth/two-step/disable', session, { code: replaced[0] }).expect(200);
  await assert.rejects(auth.twoStep.verifyLogin(fresh, replaced[1]), { code: 'TWO_STEP_INVALID' });
  await request('get', '/v1/me', signed).expect(401);
  const proof = await twoStepOtp(session.user.phone);
  assert((await auth.verifyOtp(proof.challengeId, proof.code)).accessToken);
});

async function startAccountDeletion(session) {
  await pool.query("UPDATE otp_challenges SET created_at=now()-interval '61 seconds' WHERE phone=$1", [session.user.phone]);
  const challenge = (await request('post', '/v1/auth/account-deletion/request', session).expect(202)).body;
  const job = await one(pool, "SELECT payload FROM jobs WHERE payload->>'challengeId'=$1", [challenge.challengeId]);
  return { challenge, code: decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY) };
}

test('account deletion OTP is scoped to its owner and session family, then erases the account and frees the phone', async () => {
  const owner = await login();
  const unrelated = await login();
  const proof = await twoStepOtp(owner.user.phone);
  const anotherSession = await auth.verifyOtp(proof.challengeId, proof.code);
  const id = owner.user.id;
  await request('patch', '/v1/me', owner, { name: 'Private Rider', email: 'private@example.test', city: 'Private City' }).expect(200);
  const token = 'push-token-' + randomUUID();
  await request('put', '/v1/me/devices', owner, { token }).expect(200);
  const photo = (await request('post', '/v1/me/profile-photo', owner, { mime: 'image/jpeg', bytes: 91 }).expect(200)).body;
  const support = '/v1/me/support';
  const file = (await request('post', support + '/attachments', owner, {
    app: 'RIDER', name: 'private.pdf', mime: 'application/pdf', bytes: 64,
  }).expect(201)).body;
  await request('post', support + '/attachments/' + file.id + '/submit', owner, { app: 'RIDER' }).expect(200);
  const ticket = (await request('post', support + '/tickets', owner, {
    app: 'RIDER', kind: 'TICKET', subject: 'Private support ticket', body: 'Goodbye', attachments: [file.id],
  }).expect(201)).body;
  const tripWithAddress = await booking(owner, { waypointLabels: ['Private pickup label', 'Private drop-off label'] });
  await request('post', `/v1/rides/${tripWithAddress.rideId}/cancel`, owner, { reason: 'Changed plans' }).expect(200);
  const { challenge, code } = await startAccountDeletion(owner);
  const payload = { challengeId: challenge.challengeId, code };
  assert.equal(challenge.twoStepRequired, false);
  await api.post('/v1/auth/account-deletion/confirm').send(payload).expect(401);
  await api.post('/v1/auth/otp/verify').send(payload).expect(401);
  await request('post', '/v1/auth/account-deletion/confirm', unrelated, payload).expect(400);
  await request('post', '/v1/auth/account-deletion/confirm', anotherSession, payload).expect(400);
  await request('post', '/v1/auth/account-deletion/confirm', owner,
    { ...payload, code: code === '111111' ? '222222' : '111111' }).expect(400);
  assert.equal((await one(pool, 'SELECT attempts FROM otp_challenges WHERE id=$1', [challenge.challengeId])).attempts, 1);

  const refreshed = await auth.refresh(owner.refreshToken);
  await request('post', '/v1/auth/account-deletion/confirm', refreshed, payload).expect(200, { deleted: true });
  await request('get', '/v1/me', refreshed).expect(401);
  await request('get', '/v1/me', anotherSession).expect(401);
  await assert.rejects(auth.refresh(refreshed.refreshToken), { code: 'INVALID_REFRESH' });
  await request('get', '/v1/me', unrelated).expect(200);
  await pool.query("UPDATE users SET roles=ARRAY['STAFF'] WHERE id=$1", [unrelated.user.id]);
  await request('post', '/v1/auth/account-deletion/request', unrelated).expect(403);
  const erased = await one(pool, 'SELECT phone,name,email,deleted_at,rider_city FROM users WHERE id=$1', [id]);
  assert(erased.deleted_at);
  assert.equal(erased.phone, 'deleted:' + id);
  assert.equal(erased.name, '');
  assert.equal(erased.email, '');
  assert.equal(erased.rider_city, null);
  const formerQuote = await one(pool, 'SELECT q.waypoint_labels FROM fare_quotes q JOIN rides r ON r.quote_id=q.id WHERE r.id=$1', [tripWithAddress.rideId]);
  assert.equal(formerQuote.waypoint_labels, null);
  assert.equal(await one(pool, 'SELECT id FROM profile_photo_uploads WHERE user_id=$1', [id]), undefined);
  assert.equal(await one(pool, 'SELECT id FROM support_attachments WHERE id=$1', [file.id]), undefined);
  assert.equal(await one(pool, 'SELECT id FROM support_messages WHERE ticket_id=$1', [ticket.id]), undefined);
  assert.equal(await one(pool, 'SELECT id FROM support_tickets WHERE id=$1', [ticket.id]), undefined);
  assert.equal(await one(pool, 'SELECT token FROM device_tokens WHERE user_id=$1', [id]), undefined);
  assert.equal(await one(pool, 'SELECT id FROM otp_challenges WHERE phone=$1 OR owner_id=$2', [owner.user.phone, id]), undefined);
  const targets = (await pool.query("SELECT payload->>'prefix' AS prefix FROM jobs WHERE kind='purge_account_storage' AND run_at>now()")).rows;
  assert.deepEqual(new Set(targets.map(target => target.prefix)), new Set([
    `profiles/${id}/`, `drivers/${id}/`, `verified/${photo.photoId}/`, `support/${id}/`, `verified/${file.id}/`,
  ]));
  const newProof = await twoStepOtp(owner.user.phone);
  const newAccount = await auth.verifyOtp(newProof.challengeId, newProof.code);
  assert.notEqual(newAccount.user.id, id);
  assert.equal(newAccount.user.profileComplete, false);
});

test('driver account deletion blocks pending rides and financial obligations and requires enabled two-step verification', async () => {
  const owner = await driver();
  const { whatsappCode, totp } = await enrollMobileFactor(owner);
  const { recoveryCodes } = (await request('post', '/v1/auth/two-step/enable', owner,
    { whatsappCode, code: totp.generate() }).expect(200)).body;
  const id = owner.user.id;
  const ride = await booking(owner);
  const busy = (await request('post', '/v1/auth/account-deletion/request', owner).expect(409)).body;
  assert.equal(busy.code, 'ACCOUNT_DELETION_RIDES_PENDING');
  await request('post', `/v1/rides/${ride.rideId}/cancel`, owner, { reason: 'Changed plans' }).expect(200);
  const staff = await login();
  const payout = await one(pool, `INSERT INTO payouts(driver_id,amount_minor,maker_id,reason)
    VALUES($1,100,$2,'fixture') RETURNING id`, [id, staff.user.id]);
  const blocked = (await request('post', '/v1/auth/account-deletion/request', owner).expect(409)).body;
  assert.equal(blocked.code, 'ACCOUNT_DELETION_PAYMENTS_PENDING');
  await pool.query("UPDATE payouts SET status='REJECTED' WHERE id=$1", [payout.id]);
  await pool.query("UPDATE driver_profiles SET application_encrypted='private', contact_encrypted='private', availability='AVAILABLE' WHERE user_id=$1", [id]);
  const { challenge, code } = await startAccountDeletion(owner);
  assert.equal(challenge.twoStepRequired, true);
  const payload = { challengeId: challenge.challengeId, code };
  await pool.query("UPDATE otp_challenges SET expires_at=now()-interval '1 second' WHERE id=$1", [challenge.challengeId]);
  await request('post', '/v1/auth/account-deletion/confirm', owner, { ...payload, factor: recoveryCodes[0] }).expect(400);
  const current = await startAccountDeletion(owner);
  await request('post', '/v1/auth/account-deletion/confirm', owner, { challengeId: current.challenge.challengeId, code: current.code }).expect(400);
  await request('post', '/v1/auth/account-deletion/confirm', owner,
    { challengeId: current.challenge.challengeId, code: current.code, factor: recoveryCodes[0] }).expect(200);
  const retained = await one(pool, 'SELECT status,availability,application_encrypted,contact_encrypted FROM driver_profiles WHERE user_id=$1', [id]);
  assert.deepEqual(retained, { status: 'SUSPENDED', availability: 'OFFLINE', application_encrypted: null, contact_encrypted: null });
  assert.equal(await one(pool, 'SELECT id FROM documents WHERE driver_id=$1', [id]), undefined);
  assert(await one(pool, 'SELECT id FROM rides WHERE id=$1 AND rider_id=$2', [ride.rideId, id]));
  assert(await one(pool, 'SELECT id FROM payouts WHERE id=$1', [payout.id]));
});

test('account deletion storage jobs retry and resume paginated storage erasure', async () => {
  const prefix = `profiles/${randomUUID()}/`;
  const job = await one(pool, "INSERT INTO jobs(kind,payload) VALUES('purge_account_storage',$1) RETURNING id", [{ prefix }]);
  let calls = 0;
  const cleanup = { async purgeAccountPrefix(value) {
    assert.equal(value, prefix);
    ++calls;
    if (calls === 1) throw new AppError(503, 'BUCKET_TEMPORARY_FAILURE');
    return { more: calls === 2 };
  } };
  await processJobs({ pool, storage: cleanup, logger });
  assert.equal((await one(pool, 'SELECT done_at,last_error FROM jobs WHERE id=$1', [job.id])).last_error, 'BUCKET_TEMPORARY_FAILURE');
  await pool.query('UPDATE jobs SET run_at=now() WHERE id=$1', [job.id]);
  await processJobs({ pool, storage: cleanup, logger });
  assert.equal(calls, 3);
  const completed = await one(pool, 'SELECT done_at,payload FROM jobs WHERE id=$1', [job.id]);
  assert(completed.done_at);
  assert.deepEqual(completed.payload, {});
});

test('rider city, coordinates and trusted contacts persist privately with account-scoped edit and removal', async () => {
  const rider = await login();
  const stranger = await login();
  await api.get('/v1/me/rider-settings').expect(401);
  const empty = (await request('get', '/v1/me/rider-settings', rider).expect(200)).body;
  assert.deepEqual(empty, { city: null, addresses: [], contacts: [] });
  await request('put', '/v1/me/current-city', rider, { city: 'Kattankudy' }).expect(200);
  assert.equal((await request('get', '/v1/me', rider).expect(200)).body.city, 'Kattankudy');
  const home = { kind: 'HOME', address: 'Private home, Kattankudy', coordinates: [81.7, 7.68] };
  const first = (await request('post', '/v1/me/saved-addresses', rider, home).expect(200)).body;
  const saved = (await request('post', '/v1/me/saved-addresses', rider,
    { ...home, address: 'Updated private home' }).expect(200)).body;
  assert.equal(first.id, saved.id);
  assert.deepEqual(saved.coordinates, home.coordinates);
  const other = { kind: 'OTHER', label: 'Library', address: 'Library road', coordinates: null };
  const favorite = (await request('post', '/v1/me/saved-addresses', rider, other).expect(200)).body;
  assert.equal((await request('post', '/v1/me/saved-addresses', rider, other).expect(200)).body.id, favorite.id);
  await request('post', '/v1/me/saved-addresses', rider, { ...home, coordinates: [7, 100] }).expect(400);
  await request('patch', '/v1/me/saved-addresses/' + favorite.id, stranger, other).expect(404);
  await request('delete', '/v1/me/saved-addresses/' + favorite.id, stranger).expect(404);
  await request('patch', '/v1/me/saved-addresses/' + favorite.id, rider, { ...other, label: 'Public library' }).expect(200);
  const contact = (await request('post', '/v1/me/trusted-contacts', rider,
    { name: 'Private contact', phone: '0771234567' }).expect(200)).body;
  assert.equal(contact.phone, '+94771234567');
  const matched = (await request('post', '/v1/me/trusted-contacts', rider,
    { name: 'Updated', phone: '+94771234567' }).expect(200)).body;
  assert.equal(matched.id, contact.id);
  await request('delete', '/v1/me/trusted-contacts/' + contact.id, stranger).expect(404);
  const stored = await one(pool, 'SELECT * FROM trusted_contacts WHERE id=$1', [contact.id]);
  assert(!JSON.stringify(stored).includes(contact.phone));
  assert(!(await one(pool, 'SELECT details_encrypted FROM saved_addresses WHERE id=$1', [first.id])).details_encrypted.includes(saved.address));
  const reloaded = (await request('get', '/v1/me/rider-settings', rider).expect(200)).body;
  assert.equal(reloaded.addresses.length, 2);
  assert.equal(reloaded.addresses.find(item => item.id === favorite.id).label, 'Public library');
  assert.deepEqual(reloaded.contacts, [matched]);
  await request('delete', '/v1/me/saved-addresses/' + favorite.id, rider).expect(200);
  await request('delete', '/v1/me/trusted-contacts/' + contact.id, rider).expect(200);
  const deletion = await startAccountDeletion(rider);
  await request('post', '/v1/auth/account-deletion/confirm', rider,
    { challengeId: deletion.challenge.challengeId, code: deletion.code }).expect(200);
  assert.equal(await one(pool, 'SELECT id FROM saved_addresses WHERE user_id=$1', [rider.user.id]), undefined);
});

test('notification preferences are app-specific and enforce notifications and inbox controls', async () => {
  const account = await driver();
  const stranger = await login();
  await request('get', '/v1/me/notification-preferences?app=DRIVER', stranger).expect(403);
  await request('patch', '/v1/me/notification-preferences', stranger, { app: 'DRIVER', key: 'inApp', enabled: false }).expect(403);
  await request('patch', '/v1/me/notification-preferences', account, { app: 'RIDER', key: 'offers', enabled: true }).expect(400);
  await request('patch', '/v1/me/notification-preferences', account, { app: 'RIDER', key: 'push', enabled: false }).expect(200);
  await request('patch', '/v1/me/notification-preferences', account, { app: 'DRIVER', key: 'offers', enabled: false }).expect(200);
  const driverPrefs = (await request('get', '/v1/me/notification-preferences?app=DRIVER', account).expect(200)).body;
  const riderPrefs = (await request('get', '/v1/me/notification-preferences?app=RIDER', account).expect(200)).body;
  assert.equal(driverPrefs.push, true);
  assert.equal(driverPrefs.offers, false);
  assert.equal(riderPrefs.push, false);
  const { allowsNotification } = await import('../src/modules/mobile-preferences.js');
  assert.equal(allowsNotification(driverPrefs, 'DRIVER', 'driver.offer', {}, 'push'), false);
  assert.equal(allowsNotification(driverPrefs, 'DRIVER', 'driver.reviewed', {}, 'push'), true);
  assert.equal(allowsNotification(riderPrefs, 'RIDER', 'ride.updated', { state:'SCHEDULED' }, 'push'), false);
  await pool.query("INSERT INTO notifications(user_id,event_id,type,payload) VALUES($1,$2,'ride.updated','{}'),($1,$3,'driver.offer','{}')",
    [account.user.id, randomUUID(), randomUUID()]);
  assert.equal((await request('get', '/v1/me/notifications?app=RIDER', account).expect(200)).body.items.length, 1);
  assert.equal((await request('get', '/v1/me/notifications?app=DRIVER', account).expect(200)).body.items.length, 2);
  await request('post', '/v1/me/notifications/read-all', account, { app: 'RIDER' }).expect(200);
  assert((await one(pool, "SELECT read_at FROM notifications WHERE user_id=$1 AND type='ride.updated'", [account.user.id])).read_at);
  assert.equal((await one(pool, "SELECT read_at FROM notifications WHERE user_id=$1 AND type='driver.offer'", [account.user.id])).read_at, null);
  await request('patch', '/v1/me/notification-preferences', account, { app: 'DRIVER', key: 'inApp', enabled: false }).expect(200);
  assert.equal((await request('get', '/v1/me/notifications?app=DRIVER', account).expect(200)).body.items.length, 0);
  await request('patch', '/v1/me/notification-preferences', account, { app: 'DRIVER', key: 'inApp', enabled: true }).expect(200);
  await request('post', '/v1/me/notifications/clear', account, { app: 'DRIVER' }).expect(200);
  assert.equal((await request('get', '/v1/me/notifications?app=DRIVER', account).expect(200)).body.items.length, 0);
  assert.equal((await request('get', '/v1/me/notifications?app=RIDER', account).expect(200)).body.items.length, 1);
});

test('support queues separate rider and driver requests, persist messages and restrict attachment access', async () => {
  const owner = await login(), outsider = await login(), staff = await login(), admin = await login();
  const rider = await signInDevice(owner.user.phone, { installationId: randomUUID(), name: 'Rider phone', app: 'RIDER' });
  const driverApp = await signInDevice(owner.user.phone, { installationId: randomUUID(), name: 'Driver phone', app: 'DRIVER' });
  await pool.query("UPDATE users SET roles=ARRAY['STAFF'] WHERE id=$1", [staff.user.id]);
  await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1", [admin.user.id]);
  const mobile = '/v1/me/support', portal = '/v1/admin/support';
  const attachment = { app: 'RIDER', name: 'receipt.pdf', mime: 'application/pdf', bytes: 250 };
  await request('post', mobile + '/attachments', rider, { ...attachment, name: '../receipt.pdf' }).expect(400);
  await request('post', mobile + '/attachments', rider, { ...attachment, bytes: 8388609 }).expect(400);
  const staged = (await request('post', mobile + '/attachments', rider, attachment).expect(201)).body;
  const filePath = mobile + '/attachments/' + staged.id;
  const draft = { app: 'RIDER', kind: 'COMPLAINT', subject: 'Receipt request', body: 'Please check my receipt.', attachments: [staged.id] };
  await request('post', mobile + '/tickets', outsider, draft).expect(409);
  await request('post', mobile + '/tickets', rider, draft).expect(409);
  await request('post', filePath + '/submit', outsider, { app: 'RIDER' }).expect(404);
  await request('post', filePath + '/submit', rider, { app: 'RIDER' }).expect(200);
  const key = randomUUID();
  const ticket = (await request('post', mobile + '/tickets', rider, draft, key).expect(201)).body;
  assert.equal((await request('post', mobile + '/tickets', rider, draft, key).expect(201)).body.id, ticket.id);
  await request('post', mobile + '/tickets', rider, draft).expect(409);
  await request('post', mobile + '/tickets', driverApp, draft, key).expect(403);
  assert.notEqual((await one(pool,'SELECT subject_encrypted FROM support_tickets WHERE id=$1',[ticket.id])).subject_encrypted,draft.subject);
  assert.notEqual((await one(pool,'SELECT body_encrypted FROM support_messages WHERE ticket_id=$1',[ticket.id])).body_encrypted,draft.body);
  const chat = (await request('post', mobile + '/tickets', driverApp, {
    app: 'DRIVER', kind: 'CHAT', subject: 'Driver support', body: 'Need help with onboarding.',
  }).expect(201)).body;
  const sameChat = (await request('post', mobile + '/tickets', driverApp, {
    app: 'DRIVER', kind: 'CHAT', subject: 'New chat tap', body: 'This should continue the open conversation.',
  }).expect(201)).body;
  assert.equal(sameChat.id, chat.id);
  assert.equal((await request('get', mobile + '/tickets?app=DRIVER&kind=CHAT&active=true', driverApp).expect(200)).body.items.length, 1);
  await request('get', mobile + '/tickets?app=DRIVER', rider).expect(403);
  await request('get', mobile + '/tickets/' + ticket.id + '?app=RIDER', outsider).expect(404);
  const initial = (await request('get', mobile + '/tickets/' + ticket.id + '?app=RIDER', rider).expect(200)).body;
  assert.equal(initial.messages[0].body, draft.body);
  assert.equal(initial.messages[0].attachments[0].scan_status,'PENDING');
  await request('get', filePath + '?app=RIDER', rider).expect(409);
  await request('get', filePath + '?app=RIDER', outsider).expect(404);
  await request('get', portal + '/attachments/' + staged.id + '?app=RIDER', staff).expect(403);
  await request('get', portal + '/tickets?app=RIDER', staff).expect(403);
  assert.deepEqual((await request('get', portal + '/tickets?app=RIDER&kind=COMPLAINT', admin).expect(200)).body.items.map(t=>t.id), [ticket.id]);
  assert.deepEqual((await request('get', portal + '/tickets?app=DRIVER&kind=CHAT', admin).expect(200)).body.items.map(t=>t.id), [chat.id]);
  await request('get', portal + '/tickets/' + ticket.id + '?app=DRIVER', admin).expect(404);
  await processJobs({pool,storage,logger});
  assert.equal((await request('get', filePath + '?app=RIDER', rider).expect(200)).body.downloadUrl, 'http://storage.test/download');
  await request('get', portal + '/attachments/' + staged.id + '?app=RIDER', admin).expect(200);
  await request('get', portal + '/attachments/' + staged.id + '?app=DRIVER', admin).expect(404);

  const adminTicket = portal + '/tickets/' + ticket.id;
  const reply = { app: 'RIDER', body: 'We found your receipt.' }, replyKey = randomUUID();
  const first = (await request('post', adminTicket + '/messages', admin, reply, replyKey).expect(200)).body;
  assert.equal((await request('post', adminTicket + '/messages', admin, reply, replyKey).expect(200)).body.id, first.id);
  const answered = (await request('get', mobile + '/tickets/' + ticket.id + '?app=RIDER', rider).expect(200)).body;
  assert.equal(answered.ticket.status, 'IN_PROGRESS');
  assert.deepEqual(answered.messages.map(m => [m.sender,m.body]), [['CUSTOMER', draft.body], ['SUPPORT', reply.body]]);
  await request('patch', adminTicket, admin, { app: 'RIDER', status: 'RESOLVED' }).expect(200);
  const mobileReply = { app: 'RIDER', body: 'Thank you.' };
  await request('post', mobile + '/tickets/' + ticket.id + '/messages', rider, mobileReply).expect(409);
  await request('patch', adminTicket, admin, { app: 'RIDER', status: 'OPEN' }).expect(200);
  await request('post', mobile + '/tickets/' + ticket.id + '/messages', rider, mobileReply).expect(200);
  const payload = (await one(pool,"SELECT payload FROM outbox_events WHERE type='support.updated' AND aggregate_id=$1",[ticket.id])).payload;
  assert.deepEqual(payload, { ticketId: ticket.id, app: 'RIDER' });
});

test('support rejects access to quarantined attachments for both customer and admin', async () => {
  const rider = await login(), admin = await login();
  await pool.query("UPDATE users SET roles=ARRAY['ADMIN'] WHERE id=$1", [admin.user.id]);
  const base = '/v1/me/support';
  const staged = (await request('post', base + '/attachments', rider, { app: 'RIDER', name: 'unsafe.png', mime: 'image/png', bytes: 32 }).expect(201)).body;
  await request('post', base + '/attachments/' + staged.id + '/submit', rider, { app: 'RIDER' }).expect(200);
  await request('post', base + '/tickets', rider, { app: 'RIDER', kind: 'SAFETY', subject: 'Safety concern', body: '', attachments: [staged.id] }).expect(201);
  await processJobs({pool,storage:{...storage,scan:async()=>({status:'INFECTED'})},logger});
  await request('get', base + '/attachments/' + staged.id + '?app=RIDER', rider).expect(409);
  await request('get', '/v1/admin/support/attachments/' + staged.id + '?app=RIDER', admin).expect(409);
});


test('admin support browser manages independent rider and driver queues', { skip: process.env.ADMIN_BROWSER_TEST !== '1' }, async () => {
  const { chromium } = await import('@playwright/test');
  const a = await webAdmin();
  await web(a.client, 'post', 'begin', { phone: a.phone, password: a.password }).expect(200);
  await web(a.client, 'post', 'send-otp', {}).expect(200);
  const verified = await web(a.client, 'post', 'verify', { code: await webOtp() }).expect(200);
  const session = verified.headers['set-cookie'].find(value => value.startsWith('ridenow-admin='));
  const base = `http://localhost:${server.address().port}`;
  const d=await driver(), r=await login();
  const driverTicket=(await request('post','/v1/me/support/tickets',d,{app:'DRIVER',kind:'CHAT',subject:'Driver queue sample',body:'Driver chat message'}).expect(201)).body;
  const riderTicket=(await request('post','/v1/me/support/tickets',r,{app:'RIDER',kind:'COMPLAINT',subject:'Rider queue sample',body:'Rider complaint message'}).expect(201)).body;
  const executablePath = process.env.CHROMIUM_PATH ?? (existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined);
  const browser = await chromium.launch({ executablePath, args: ['--no-sandbox'] });
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 840 } });
    await context.addCookies([{ name: 'ridenow-admin', value: session.split(';')[0].slice('ridenow-admin='.length), url: base }]);
    const page = await context.newPage();
    await page.goto(base+'/admin/support-rider/');
    await page.getByText('Rider queue sample',{exact:true}).waitFor();
    assert.equal(await page.getByText('Driver queue sample',{exact:true}).count(),0);
    await page.getByRole('button',{name:'Open',exact:true}).click();
    const ticketDialog=page.getByRole('dialog');
    await ticketDialog.getByText('Rider complaint message',{exact:true}).waitFor();
    await ticketDialog.getByLabel('Reply to customer').fill('Reply from the rider queue');
    await ticketDialog.getByRole('button',{name:'Send reply'}).click();
    await ticketDialog.getByText('Reply from the rider queue',{exact:true}).waitFor();
    await ticketDialog.getByRole('button',{name:'Resolve',exact:true}).click();
    await ticketDialog.getByRole('button',{name:'Reopen',exact:true}).waitFor();
    assert.equal((await request('get','/v1/me/support/tickets/'+riderTicket.id+'?app=RIDER',r).expect(200)).body.ticket.status,'RESOLVED');
    await page.goto(base+'/admin/support-driver/');
    await page.getByText('Driver queue sample',{exact:true}).waitFor();
    assert.equal(await page.getByText('Rider queue sample',{exact:true}).count(),0);
    await page.getByRole('button',{name:'Open',exact:true}).click();
    await ticketDialog.getByText('Driver chat message',{exact:true}).waitFor();
    assert.equal((await request('get','/v1/me/support/tickets/'+driverTicket.id+'?app=DRIVER',d).expect(200)).body.ticket.status,'OPEN');
  } finally {
    await browser.close();
  }
});

test('account data export honors app and section scope and excludes private credentials', async () => {
  const owner = await login(), other = await login();
  const rider = await signInDevice(owner.user.phone, { installationId: randomUUID(), name: 'Rider phone', app: 'RIDER' });
  const driverSession = await signInDevice(owner.user.phone, { installationId: randomUUID(), name: 'Driver phone', app: 'DRIVER' });
  const exportPath = '/v1/me/data-export';
  const selections = { app: 'RIDER', sections: ['PROFILE','SAVED_PLACES','SUPPORT','RIDES','PAYMENTS'] };
  await api.post(exportPath).send(selections).expect(401);
  await request('post', exportPath, rider, { ...selections, userId: other.user.id }).expect(400);
  await request('post', exportPath, rider, { app: 'RIDER', sections: ['EARNINGS'] }).expect(400);
  await request('post', exportPath, driverSession, selections).expect(403);
  await request('patch', '/v1/me', rider, { name: 'Export owner', city: 'Kattankudy' }).expect(200);
  await pool.query("UPDATE users SET password_hash='private-hash', profile_photo_key='owned-avatar',profile_photo_version='profile-version' WHERE id=$1", [owner.user.id]);
  await request('post', '/v1/me/saved-addresses', rider, {
    kind: 'HOME', label: 'Home', address: 'Export owner address', coordinates: [81.7, 7.7],
  }).expect(200);
  await request('post', '/v1/me/saved-addresses', other, { kind: 'HOME', address: 'Someone else address' }).expect(200);
  const mine = await booking(rider, { waypointLabels: ['Exported pickup', 'Exported destination'] });
  const others = await booking(other);

  const support = '/v1/me/support';
  const file = (await request('post', support + '/attachments', rider, { app: 'RIDER', name: 'Proof.pdf', mime: 'application/pdf', bytes: 32 }).expect(201)).body;
  await request('post', support + '/attachments/' + file.id + '/submit', rider, { app: 'RIDER' }).expect(200);
  await request('post', support + '/tickets', rider, {
    app: 'RIDER', kind: 'TICKET', subject: 'Exported ticket', body: 'Exported message', attachments: [file.id],
  }).expect(201);
  await request('post', support + '/tickets', driverSession, { app: 'DRIVER', kind: 'CHAT', subject: 'Driver only chat', body: 'Driver only message' }).expect(201);
  const filePath = exportPath + '/files/' + file.id + '?app=RIDER&type=support';
  await request('get', filePath, rider).expect(404);
  await processJobs({ pool, storage, logger });

  const result = (await request('post', exportPath, rider, selections).expect(200).expect('Cache-Control', 'no-store')).body;
  assert.equal(result.schemaVersion, 1);
  assert.equal(result.data.profile.name, 'Export owner');
  assert.deepEqual(result.data.profile.devices.map(device => device.name), ['Rider phone']);
  assert.deepEqual(result.data.savedPlaces.addresses.map(address => address.address), ['Export owner address']);
  assert.deepEqual(result.data.rides.map(ride => ride.id), [mine.rideId]);
  assert.deepEqual(result.data.rides[0].waypoint_labels, ['Exported pickup', 'Exported destination']);
  assert.equal(result.data.support[0].messages[0].body, 'Exported message');
  assert.deepEqual(result.files.map(file => file.type), ['avatar','support']);
  const serialized = JSON.stringify(result);
  for (const value of ['private-hash', 'Someone else address', others.rideId, 'Driver only chat', 'pin_encrypted', 'pin_hash', 'token_hash', 'profile-version', 'object_key', 'downloadUrl', other.user.phone])
    assert(!serialized.includes(value), 'export exposed ' + value);
  await request('get', filePath, other).expect(404);
  await request('get', filePath, rider).expect(200).expect('Cache-Control', 'no-store');
  await request('get', filePath, driverSession).expect(403);
  await request('get', exportPath + '/files/' + owner.user.id + '?app=RIDER&type=avatar', other).expect(404);
  const driverExport = (await request('post', exportPath, driverSession, { app: 'DRIVER', sections: ['SUPPORT'] }).expect(200)).body;
  assert.deepEqual(Object.keys(driverExport.data), ['support']);
  assert.deepEqual(driverExport.data.support.map(ticket => ticket.subject), ['Driver only chat']);
  await pool.query('UPDATE users SET suspended=true WHERE id=$1', [owner.user.id]);
  await request('post', exportPath, rider, selections).expect(401);
});

test('driver export includes their own vehicle and verified documents and rate limits export generation', async () => {
  const d = await driver(), other = await driver();
  const exportPath = '/v1/me/data-export';
  const body = { app: 'DRIVER', sections: ['VEHICLES','PROFILE','EARNINGS'] };
  const result = (await request('post', exportPath, d, body).expect(200)).body;
  assert.equal(result.data.vehicleRecords.vehicles.length, 1);
  assert.equal(result.data.vehicleRecords.vehicles[0].status, 'APPROVED');
  assert.equal(result.data.earnings.currency, 'LKR');
  assert.equal(result.data.profile.driver.status, 'APPROVED');
  const file = result.files.find(file => file.type === 'document');
  assert(file);
  await request('get', exportPath + '/files/' + file.id + '?app=DRIVER&type=document', d).expect(200);
  await request('get', exportPath + '/files/' + file.id + '?app=DRIVER&type=document', other).expect(404);
  for (let i = 1; i < 6; i++) await request('post', exportPath, d, { app: 'DRIVER', sections: ['EARNINGS'] }).expect(200);
  await request('post', exportPath, d, body).expect(429);
});
