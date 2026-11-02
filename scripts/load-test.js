import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { once } from 'node:events';
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { SignJWT } from 'jose';
import { createClient } from 'redis';
import { io as client } from 'socket.io-client';
import pino from 'pino';
import { runner } from 'node-pg-migrate';
import { database, one } from '../src/shared/db.js';
import { loadConfig } from '../src/shared/config.js';
import { createApp } from '../src/app.js';
import { createMaps } from '../src/adapters/maps.js';
import { attachRealtime } from '../src/realtime.js';
import { publishOutbox } from '../src/worker.js';
const url = process.env.LOAD_DATABASE_URL ?? 'postgres://ride:ride@localhost:5432/ride_load_test';
assert(new URL(url).pathname.endsWith('_load_test'), 'Dedicated _load_test database required');
const count = Number(process.env.LOAD_DRIVERS ?? 1000),
  active = Math.min(100, count),
  rounds = Number(process.env.LOAD_ROUNDS ?? 2);
const config = loadConfig({
  NODE_ENV: 'test',
  DATABASE_URL: url,
  REDIS_URL: process.env.LOAD_REDIS_URL ?? 'redis://localhost:6379/14',
  JWT_SECRET: 'l'.repeat(64),
  OTP_PEPPER: 'p'.repeat(64),
  ENCRYPTION_KEY: 'ab'.repeat(32),
});
const admin = database(new URL('/postgres', url).href),
  name = new URL(url).pathname.slice(1);
try {
  if (!(await one(admin, 'SELECT 1 FROM pg_database WHERE datname=$1', [name])))
    await admin.query(`CREATE DATABASE "${name.replaceAll('"', '""')}"`);
} finally {
  await admin.end();
}
await runner({
  databaseUrl: url,
  dir: 'migrations',
  direction: 'up',
  migrationsTable: 'schema_migrations',
  count: Infinity,
  log: () => {},
});
const pool = database(url),
  redis = createClient({ url: config.REDIS_URL });
await redis.connect();
await redis.flushDb();
const maps = await createMaps(config),
  logger = pino({ level: 'silent' }),
  { app, auth } = createApp({ pool, redis, config, maps, logger, storage: {} }),
  server = createServer(app);
const realtime = await attachRealtime(server, { pool, redis, config, logger, auth });
server.listen(0);
await once(server, 'listening');
const base = `http://localhost:${server.address().port}`,
  sockets = [],
  tokens = [],
  restTimes = [],
  locationTimes = [],
  realtimeTimes = [];
let errors = 0,
  publishing = false;
const sample = [81.6973617, 7.7060005],
  destination = [81.7005996, 7.7099703];
async function credentials(user) {
  const refresh = randomUUID();
  const session = await one(
    pool,
    "INSERT INTO sessions(user_id,token_hash,expires_at) VALUES($1,$2,now()+interval '1 day') RETURNING id",
    [user.id, refresh],
  );
  return new SignJWT({ sid: session.id })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuer('mazo-rides')
    .setAudience('mazo-clients')
    .setSubject(user.id)
    .setIssuedAt()
    .setExpirationTime('15m')
    .sign(new TextEncoder().encode(config.JWT_SECRET));
}
async function connect(token) {
  const socket = client(base, {
    auth: { token },
    transports: ['websocket'],
    reconnection: false,
    timeout: 10000,
  });
  await once(socket, 'connect');
  sockets.push(socket);
  return socket;
}
const timer = setInterval(async () => {
  if (publishing) return;
  publishing = true;
  try {
    await publishOutbox({ pool, redis });
  } catch {
    errors++;
  } finally {
    publishing = false;
  }
}, 100);
try {
  await pool.query(
    'TRUNCATE users,otp_challenges,service_areas,jobs,outbox_events,system_state,gateway_credentials RESTART IDENTITY CASCADE',
  );
  const area = await one(
    pool,
    "INSERT INTO service_areas(name,polygon,active) VALUES('Load area',ST_MakeEnvelope(81.69,7.50,81.80,7.73,4326),true) RETURNING id",
  );
  const users = (
    await pool.query(
      "INSERT INTO users(phone,roles) SELECT '+9476'||lpad(i::text,7,'0'),'{DRIVER}' FROM generate_series(1,$1) i RETURNING id",
      [count],
    )
  ).rows;
  await pool.query(
    "INSERT INTO driver_profiles(user_id,status,availability) SELECT id,'APPROVED','AVAILABLE' FROM users",
  );
  await pool.query(
    "INSERT INTO vehicles(driver_id,registration,category,capacity,status) SELECT id,id::text,'TUK',3,'APPROVED' FROM users",
  );
  await pool.query(
    "INSERT INTO driver_locations(driver_id,point,accuracy,sequence,recorded_at) SELECT id,ST_SetSRID(ST_MakePoint($1,$2),4326)::geography,10,0,now()-interval '1 second' FROM users",
    sample,
  );
  const riders = (
    await pool.query(
      "INSERT INTO users(phone) SELECT '+9475'||lpad(i::text,7,'0') FROM generate_series(1,$1) i RETURNING id",
      [active],
    )
  ).rows;
  const route = await maps.route({ waypoints: [sample, destination], vehicleCategory: 'TUK' });
  const plan = {
    base_minor: 10000,
    per_km_minor: 8000,
    per_minute_minor: 1000,
    waiting_minor: 1000,
    minimum_minor: 10000,
    commission_bps: 2000,
    surge: 1,
  };
  for (let i = 0; i < active; i++) {
    const q = await one(
      pool,
      "INSERT INTO fare_quotes(rider_id,area_id,category,trip_type,waypoints,route,fare_snapshot,estimated_minor,expires_at,consumed_at) VALUES($1,$2,'TUK','ONE_WAY',$3,$4,$5,20000,now()+interval '2 minutes',now()) RETURNING id",
      [riders[i].id, area.id, JSON.stringify([sample, destination]), route, plan],
    );
    const vehicle = await one(pool, 'SELECT id FROM vehicles WHERE driver_id=$1', [users[i].id]);
    await pool.query(
      "INSERT INTO rides(rider_id,quote_id,driver_id,vehicle_id,state,started_at,pin_hash,pin_encrypted) VALUES($1,$2,$3,$4,'IN_PROGRESS',now(),'load-fixture','load-fixture')",
      [riders[i].id, q.id, users[i].id, vehicle.id],
    );
    await pool.query("UPDATE driver_profiles SET availability='ON_TRIP' WHERE user_id=$1", [
      users[i].id,
    ]);
    const s = await connect(await credentials(riders[i]));
    s.on('driver.location', (e) => realtimeTimes.push(Date.now() - new Date(e.payload.recordedAt)));
  }
  const driverSockets = [];
  for (let i = 0; i < users.length; i += 50)
    await Promise.all(
      users.slice(i, i + 50).map(async (u) => {
        const token = await credentials(u);
        tokens.push(token);
        driverSockets.push(await connect(token));
      }),
    );
  for (let i = 0; i < tokens.length; i += 20)
    await Promise.all(
      tokens.slice(i, i + 20).map(async (token) => {
        const begin = performance.now();
        const r = await fetch(base + '/v1/me', { headers: { Authorization: `Bearer ${token}` } });
        await r.json();
        restTimes.push(performance.now() - begin);
        if (!r.ok) errors++;
      }),
    );
  const begin = performance.now();
  for (let r = 0; r < rounds; r++)
    await Promise.all(
      driverSockets.map(
        (socket, i) =>
          new Promise((resolve) =>
            setTimeout(
              async () => {
                const start = performance.now();
                try {
                  const result = await socket
                    .timeout(5000)
                    .emitWithAck('driver.location', {
                      coordinates: sample,
                      accuracy: 10,
                      sequence: r + 1,
                      recordedAt: new Date().toISOString(),
                    });
                  if (!result.ok) errors++;
                  locationTimes.push(performance.now() - start);
                } catch {
                  errors++;
                }
                resolve();
              },
              (i * 5000) / count,
            ),
          ),
      ),
    );
  while (publishing) await new Promise((r) => setTimeout(r, 10));
  await publishOutbox({ pool, redis });
  await new Promise((r) => setTimeout(r, 100));
  const percentile = (values, p) =>
    Math.round(
      [...values].sort((a, b) => a - b)[Math.max(0, Math.ceil(values.length * p) - 1)] ?? 0,
    );
  const report = {
    generatedAt: new Date().toISOString(),
    drivers: count,
    activeRides: active,
    connections: sockets.length,
    locationUpdates: locationTimes.length,
    elapsedSeconds: Math.round((performance.now() - begin) / 100) / 10,
    errors,
    restP95Ms: percentile(restTimes, 0.95),
    locationAckP95Ms: percentile(locationTimes, 0.95),
    realtimeP95Ms: percentile(realtimeTimes, 0.95),
    realtimeSamples: realtimeTimes.length,
    notes:
      'Local single API, real PostgreSQL/PostGIS and Redis; Mazo routes. Does not benchmark WhatsApp, FCM, storage or dispatch throughput.',
  };
  await writeFile('docs/load-report.json', JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
  assert.equal(errors, 0);
  assert(report.restP95Ms < 500);
  assert(report.realtimeSamples > 0 && report.realtimeP95Ms < 2000);
} finally {
  clearInterval(timer);
  while (publishing) await new Promise((r) => setTimeout(r, 10));
  for (const s of sockets) s.disconnect();
  await realtime.close();
  if (server.listening) await new Promise((r) => server.close(r));
  await pool.end();
  await redis.quit();
}
