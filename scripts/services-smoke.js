import { randomUUID } from 'node:crypto';
import { lookup } from 'node:dns/promises';
import { writeFile } from 'node:fs/promises';
import { createClient } from 'redis';
import { database, one } from '../src/shared/db.js';
import {
  postgresConnectionString,
  redisConnectionString,
  isSupabaseHost,
} from '../src/shared/connections.js';
const report = {
  startedAt: new Date().toISOString(),
  postgres: { connected: false },
  redis: { connected: false },
  warnings: [],
};
function check(value, code) {
  if (!value) throw Object.assign(new Error(code), { code });
}
function code(error, fallback) {
  const name = error.code ?? '';
  if (
    [
      'SELF_SIGNED_CERT_IN_CHAIN',
      'DEPTH_ZERO_SELF_SIGNED_CERT',
      'UNABLE_TO_VERIFY_LEAF_SIGNATURE',
      'CERT_HAS_EXPIRED',
      'ERR_TLS_CERT_ALTNAME_INVALID',
    ].includes(name)
  )
    return 'TLS_CERTIFICATE_VERIFICATION_FAILED';
  if (name === '28P01') return 'DATABASE_AUTHENTICATION_FAILED';
  if (name === 'ENETUNREACH') return 'NETWORK_UNREACHABLE_USE_SESSION_POOLER';
  if (name === 'ENOTFOUND') return 'HOST_NOT_FOUND';
  if (name === 'ECONNREFUSED') return 'CONNECTION_REFUSED';
  return /^[A-Z][A-Z0-9_]{2,80}$/.test(name) ? name : fallback;
}
async function timeout(operation, ms, errorCode) {
  let timer;
  try {
    return await Promise.race([
      operation,
      new Promise((_, reject) => {
        timer = setTimeout(
          () => reject(Object.assign(new Error(errorCode), { code: errorCode })),
          ms,
        );
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}
async function postgres() {
  let pool, client;
  try {
    check(process.env.DATABASE_URL?.trim(), 'DATABASE_URL_MISSING');
    const url = postgresConnectionString(process.env.DATABASE_URL),
      parsed = new URL(url);
    check(isSupabaseHost(parsed.hostname), 'SUPABASE_DATABASE_URL_REQUIRED');
    report.postgres.connectionKind = parsed.hostname.endsWith('.pooler.supabase.com')
      ? 'session_pooler'
      : 'direct';
    report.postgres.port = Number(parsed.port || 5432);
    const addresses = await lookup(parsed.hostname, { all: true });
    report.postgres.dns = {
      ipv4: addresses.filter((a) => a.family === 4).length,
      ipv6: addresses.filter((a) => a.family === 6).length,
    };
    pool = database(url, { max: 1, connectionTimeoutMillis: 10000, query_timeout: 10000 });
    pool.on('error', () => {});
    client = await pool.connect();
    await client.query('SELECT 1');
    report.postgres.connected = true;
    report.postgres.tlsVerified = Boolean(
      client.connection.stream.encrypted && client.connection.stream.authorized,
    );
    check(report.postgres.tlsVerified, 'POSTGRES_VERIFIED_TLS_REQUIRED');
    report.postgres.sessionConnection = true;
    const key = (await one(client, 'SELECT hashtextextended($1,0) AS key', [randomUUID()])).key;
    const locked = await one(client, 'SELECT pg_try_advisory_lock($1::bigint) AS locked', [key]);
    try {
      check(locked.locked, 'SESSION_LOCK_FAILED');
      report.postgres.sessionLock = true;
    } finally {
      if (locked.locked) await client.query('SELECT pg_advisory_unlock($1::bigint)', [key]);
    }
    const extensions = (
      await client.query(
        "SELECT e.extname,n.nspname AS schema FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname IN ('postgis','pgcrypto')",
      )
    ).rows;
    report.postgres.pgcrypto = extensions.some((e) => e.extname === 'pgcrypto');
    const postgis = extensions.find((e) => e.extname === 'postgis');
    report.postgres.postgis = Boolean(postgis);
    check(postgis, 'POSTGIS_NOT_INSTALLED');
    const schema = '"' + postgis.schema.replaceAll('"', '""') + '"';
    const distance = await one(
      client,
      `SELECT ${schema}.ST_Distance(${schema}.ST_SetSRID(${schema}.ST_MakePoint(81.7,7.7),4326)::${schema}.geography,${schema}.ST_SetSRID(${schema}.ST_MakePoint(81.71,7.71),4326)::${schema}.geography) AS metres`,
    );
    check(distance.metres > 0, 'POSTGIS_QUERY_FAILED');
    report.postgres.geospatialQuery = true;
    const columns = (
      await client.query(
        "SELECT table_schema,column_name FROM information_schema.columns WHERE table_schema IN ('public','ridenow') AND table_name='users'",
      )
    ).rows;
    const schemas = [...new Set(columns.map((c) => c.table_schema))];
    report.postgres.existingApplicationSchemas = schemas;
    report.postgres.schemaNeedsReview = schemas.some(
      (s) =>
        !['roles', 'permissions', 'suspended'].every((name) =>
          columns.some((c) => c.table_schema === s && c.column_name === name),
        ),
    );
    report.postgres.applicationSchemaStatus =
      schemas.length === 0
        ? 'not_installed_or_not_visible'
        : report.postgres.schemaNeedsReview
          ? 'existing_schema_requires_review'
          : 'candidate_requires_migration_review';
    if (report.postgres.schemaNeedsReview)
      report.warnings.push('EXISTING_APPLICATION_SCHEMA_REQUIRES_REVIEW');
    const current = await one(client, 'SELECT current_schema() AS schema');
    report.postgres.defaultSchema = current.schema;
    report.postgres.result = 'passed';
  } catch (error) {
    report.postgres.result = 'failed';
    report.postgres.error = code(error, 'POSTGRES_PROBE_FAILED');
  } finally {
    if (client) client.release();
    if (pool) await pool.end();
  }
}
async function redis() {
  let client, subscriber, key;
  try {
    check(process.env.REDIS_URL?.trim(), 'REDIS_URL_MISSING');
    const url = redisConnectionString(process.env.REDIS_URL),
      parsed = new URL(url);
    check(parsed.protocol === 'rediss:', 'REDIS_TLS_REQUIRED');
    report.redis.tls = true;
    report.redis.tlsSchemeNormalized = new URL(process.env.REDIS_URL).protocol === 'redis:';
    const options = { url, socket: { connectTimeout: 8000, reconnectStrategy: false } };
    client = createClient(options);
    subscriber = createClient(options);
    client.on('error', () => {});
    subscriber.on('error', () => {});
    await timeout(client.connect(), 10000, 'REDIS_CONNECT_TIMEOUT');
    report.redis.connected = true;
    check(
      (await timeout(client.ping(), 8000, 'REDIS_PING_TIMEOUT')) === 'PONG',
      'REDIS_PING_FAILED',
    );
    report.redis.ping = true;
    key = `ridenow:smoke:${randomUUID()}`;
    const marker = randomUUID();
    await timeout(client.set(key, marker, { EX: 30, NX: true }), 8000, 'REDIS_WRITE_TIMEOUT');
    check(
      (await timeout(client.get(key), 8000, 'REDIS_READ_TIMEOUT')) === marker,
      'REDIS_READ_WRITE_FAILED',
    );
    report.redis.readWrite = true;
    const lua = await timeout(
      client.eval("local n=redis.call('INCR',KEYS[1]);redis.call('EXPIRE',KEYS[1],30);return n", {
        keys: [key + ':limit'],
        arguments: [],
      }),
      8000,
      'REDIS_LUA_TIMEOUT',
    );
    check(lua === 1, 'REDIS_LUA_FAILED');
    report.redis.lua = true;
    await timeout(subscriber.connect(), 10000, 'REDIS_SUBSCRIBER_TIMEOUT');
    let receive;
    const received = new Promise((resolve) => {
      receive = resolve;
    });
    const channel = key + ':channel';
    await timeout(
      subscriber.subscribe(channel, (message) => {
        if (message === marker) receive();
      }),
      8000,
      'REDIS_SUBSCRIBE_TIMEOUT',
    );
    await timeout(client.publish(channel, marker), 8000, 'REDIS_PUBLISH_TIMEOUT');
    await timeout(received, 8000, 'REDIS_PUBSUB_TIMEOUT');
    report.redis.pubsub = true;
    report.redis.result = 'passed';
  } catch (error) {
    report.redis.result = 'failed';
    report.redis.error = code(error, 'REDIS_PROBE_FAILED');
  } finally {
    if (client?.isReady && key)
      try {
        await timeout(client.del([key, key + ':limit']), 3000, 'REDIS_CLEANUP_TIMEOUT');
      } catch {
        report.warnings.push('TEMPORARY_REDIS_KEYS_EXPIRE_WITHIN_30_SECONDS');
      }
    if (subscriber?.isOpen) subscriber.destroy();
    if (client?.isOpen) client.destroy();
  }
}
await Promise.all([postgres(), redis()]);
report.result =
  report.postgres.result === 'passed' && report.redis.result === 'passed' ? 'passed' : 'failed';
report.finishedAt = new Date().toISOString();
report.schemaChangesApplied = false;
await writeFile('services-smoke-report.json', JSON.stringify(report, null, 2) + '\n', {
  mode: 0o600,
});
process.stdout.write(JSON.stringify(report) + '\n');
if (report.result === 'failed') process.exitCode = 1;
