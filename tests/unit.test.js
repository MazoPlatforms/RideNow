import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateFare } from '../src/modules/pricing.js';
import { normalizePhone } from '../src/modules/auth.js';
import { encrypt, decrypt, hash, equal } from '../src/shared/crypto.js';
import { loadConfig } from '../src/shared/config.js';
import { createMaps } from '../src/adapters/maps.js';
const plan = {
  base_minor: 10000,
  per_km_minor: 8000,
  per_minute_minor: 1000,
  waiting_minor: 2000,
  minimum_minor: 10000,
  surge: 1,
};
test('fare separates driving time and stop waiting with five free minutes', () => {
  assert.equal(calculateFare(plan, 2000, 600, 420), 40000);
  assert.equal(calculateFare({ ...plan, surge: 2 }, 2000, 600, 420), 66000);
  assert.equal(calculateFare(plan, 0, 0, 299), 10000);
});
test('money rounds once to minor unit and enforces minimum', () => {
  assert.equal(calculateFare({ ...plan, minimum_minor: 50000 }, 1, 1, 0), 50000);
  assert.equal(
    calculateFare(
      { ...plan, base_minor: 0, minimum_minor: 0, per_km_minor: 1, per_minute_minor: 0 },
      1500,
      0,
    ),
    2,
  );
});
test('Sri Lankan mobile normalization rejects other countries', () => {
  assert.equal(normalizePhone('077 123 4567'), '+94771234567');
  assert.equal(normalizePhone('+94 (77) 123-4567'), '+94771234567');
  assert.throws(() => normalizePhone('+917712345678'));
});
test('credential encryption authenticates ciphertext', () => {
  const key = 'ab'.repeat(32),
    encrypted = encrypt('private-credential', key);
  assert.equal(decrypt(encrypted, key), 'private-credential');
  const corrupted = Buffer.from(encrypted, 'base64');
  corrupted[20] ^= 1;
  assert.throws(() => decrypt(corrupted.toString('base64'), key));
  assert(equal(hash('secret', 'pepper'), hash('secret', 'pepper')));
  assert(!equal('abc', 'abcd'));
});
test('production fixes Baileys regardless of environment provider and enforces HTTPS', () => {
  const env = {
    NODE_ENV: 'production',
    DATABASE_URL: 'postgres://localhost/test',
    JWT_SECRET: 'x'.repeat(32),
    OTP_PEPPER: 'y'.repeat(32),
    ENCRYPTION_KEY: 'ab'.repeat(32),
    CORS_ORIGINS: 'https://example.com',
    FCM_ENABLED: 'true',
    S3_BUCKET: 'test-private-documents',
  };
  for (const OTP_PROVIDER of [undefined, 'database', 'baileys', 'invalid'])
    assert.equal(loadConfig({ ...env, OTP_PROVIDER }).OTP_PROVIDER, 'baileys');
  assert.throws(
    () => loadConfig({ ...env, CORS_ORIGINS: 'http://localhost:5173' }),
    /Production requires HTTPS origins/,
  );
  assert.equal(loadConfig({ ...env, NODE_ENV: 'test', OTP_PROVIDER: undefined }).OTP_PROVIDER, 'database');
  assert.equal(loadConfig({ ...env, FCM_ENABLED: undefined }).FCM_ENABLED, 'false');
  assert.throws(() => loadConfig({ ...env, FCM_ENABLED: 'false', S3_BUCKET: undefined }),
    /Production requires private S3 storage/);
});
test('real Mazo graph verifies checksum, routes, matrix, search and coverage', async () => {
  const maps = await createMaps({
    MAP_GRAPH_PATH: 'data/kattankudy-routing.json',
    MAP_MANIFEST_PATH: 'data/kattankudy-routing-manifest.json',
    MAP_CATEGORY_APPROVALS: '',
    NODE_ENV: 'test',
  });
  const points = [
    [81.6973617, 7.7060005],
    [81.7005996, 7.7099703],
  ];
  const route = await maps.route({ waypoints: points, vehicleCategory: 'TUK' });
  assert(route.distanceMeters > 1000);
  assert(route.legs[0].geometry.coordinates.length > 2);
  assert((await maps.searchPlaces({ query: 'mosque' })).items.length > 0);
  assert.equal(
    (await maps.matrix({ origins: [[0, 0]], destination: points[0], vehicleCategory: 'TUK' }))[0],
    null,
  );
  await assert.rejects(
    maps.route({
      waypoints: [
        [0, 0],
        [1, 1],
      ],
      vehicleCategory: 'TUK',
    }),
    { code: 'ROUTE_UNAVAILABLE' },
  );
});

test('managed connection URLs enforce TLS and preserve local test connections', async () => {
  const { postgresConnectionString, redisConnectionString } = await import(
    '../src/shared/connections.js'
  );
  assert.equal(
    new URL(redisConnectionString('redis://default:synthetic@sample.upstash.io:6379')).protocol,
    'rediss:',
  );
  assert.equal(new URL(redisConnectionString('redis://localhost:6379/15')).protocol, 'redis:');
  assert.throws(() => redisConnectionString('https://sample.upstash.io'), {
    code: 'REDIS_TCP_URL_REQUIRED',
  });
  assert.equal(
    new URL(
      postgresConnectionString(
        'postgresql://postgres.project:synthetic@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres',
      ),
    ).searchParams.get('sslmode'),
    'verify-full',
  );
  assert.throws(
    () =>
      postgresConnectionString(
        'postgresql://postgres.project:synthetic@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres',
      ),
    { code: 'SUPABASE_SESSION_POOLER_REQUIRED' },
  );
  assert.throws(
    () => postgresConnectionString('private-invalid-string'),
    (e) => e.code === 'DATABASE_TCP_URL_REQUIRED' && !e.message.includes('private-invalid-string'),
  );
  assert.equal(
    new URL(
      postgresConnectionString('postgres://ride:ride@localhost:5432/ride_test'),
    ).searchParams.get('sslmode'),
    null,
  );
});

test('R2 selects immutable verified copies without changing versioned S3 defaults', async () => {
  const { storageSettings } = await import('../src/adapters/storage-settings.js');
  const r2 = storageSettings({ S3_ENDPOINT: 'https://example.r2.cloudflarestorage.com' });
  assert.equal(r2.r2, true);
  assert.equal(r2.mode, 'immutable');
  assert.equal(r2.options.region, 'auto');
  assert.equal(r2.options.requestChecksumCalculation, 'WHEN_REQUIRED');
  const s3 = storageSettings({ S3_ENDPOINT: 'http://localhost:9000' });
  assert.equal(s3.mode, 'versioned');
  assert.throws(() => storageSettings({ S3_ENDPOINT: 'http://example.r2.cloudflarestorage.com' }), {
    code: 'R2_HTTPS_REQUIRED',
  });
});
