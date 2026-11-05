import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateFare } from '../src/modules/pricing.js';
import { normalizePhone } from '../src/modules/auth.js';
import { encrypt, decrypt, hash, equal } from '../src/shared/crypto.js';
import { loadConfig } from '../src/shared/config.js';
import { createRecaptchaVerifier } from '../src/shared/recaptcha.js';
import { createMaps } from '../src/adapters/maps.js';
import { decimalMinor } from '../admin/assets/api.js';
import sharp from 'sharp';
import { sanitizeProfilePhotoBytes } from '../src/adapters/storage.js';
test('admin money inputs preserve exact LKR cents and reject ambiguous amounts', () => {
  assert.equal(decimalMinor('125.50'), 12550);
  assert.equal(decimalMinor('0.01'), 1);
  assert.equal(decimalMinor('20'), 2000);
  for (const value of ['-1', '1.001', '1e3', 'NaN', '100,00']) assert.throws(() => decimalMinor(value));
});
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
test('profile photos are decoded, bounded, and re-encoded as metadata-free JPEGs', async () => {
  const source = await sharp({
    create: { width: 24, height: 16, channels: 3, background: '#f0b000' },
  }).png().toBuffer();
  const sanitized = await sanitizeProfilePhotoBytes(Buffer.concat([source, Buffer.from('<script>ignored</script>')]), 'image/png');
  const metadata = await sharp(sanitized).metadata();
  assert.equal(metadata.format, 'jpeg');
  assert.equal(metadata.width, 24);
  assert.equal(metadata.height, 16);
  assert.equal(metadata.exif, undefined);
  await assert.rejects(sanitizeProfilePhotoBytes(Buffer.from('not an image'), 'image/jpeg'), { code: 'PROFILE_PHOTO_INVALID' });

  const pixels = Buffer.alloc(1024 * 1024 * 3);
  let seed = 0x12345678;
  for (let i = 0; i < pixels.length; i++) {
    seed ^= seed << 13;
    seed ^= seed >>> 17;
    seed ^= seed << 5;
    pixels[i] = seed & 255;
  }
  const largePng = await sharp(pixels, { raw: { width: 1024, height: 1024, channels: 3 } }).png().toBuffer();
  const compactJpeg = await sanitizeProfilePhotoBytes(largePng, 'image/png');
  const compactMetadata = await sharp(compactJpeg).metadata();
  assert.equal(compactMetadata.format, 'jpeg');
  assert(Math.max(compactMetadata.width, compactMetadata.height) <= 320);
  assert(compactJpeg.length < largePng.length / 4);
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
    RECAPTCHA_V2_SITE_KEY: 'site-key',
    RECAPTCHA_V2_SECRET_KEY: 'secret-key',
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
test('reCAPTCHA verifier checks Google result and exact hostname, and fails closed', async () => {
  let submitted;
  const verifier = createRecaptchaVerifier({ NODE_ENV: 'production', RECAPTCHA_V2_SECRET_KEY: 'secret-value' },
    async (url, options) => {
      submitted = { url, body: options.body };
      return { ok: true, json: async () => ({ success: true, hostname: 'admin.example.com' }) };
    });
  await verifier({ token: 'captcha-response-token-value', hostname: 'admin.example.com' });
  assert.equal(submitted.url, 'https://www.google.com/recaptcha/api/siteverify');
  assert.equal(submitted.body.get('secret'), 'secret-value');
  await assert.rejects(verifier({ token: 'captcha-response-token-value', hostname: 'untrusted.example.com' }),
    { code: 'CAPTCHA_INVALID' });
  const failed = createRecaptchaVerifier({ NODE_ENV: 'production', RECAPTCHA_V2_SECRET_KEY: 'secret-value' },
    async () => ({ ok: true, json: async () => ({ success: false, hostname: 'admin.example.com' }) }));
  await assert.rejects(failed({ token: 'captcha-response-token-value', hostname: 'admin.example.com' }),
    { code: 'CAPTCHA_INVALID' });
  const unavailable = createRecaptchaVerifier({ NODE_ENV: 'production', RECAPTCHA_V2_SECRET_KEY: '' });
  await assert.rejects(unavailable({ token: 'captcha-response-token-value', hostname: 'admin.example.com' }),
    { code: 'CAPTCHA_UNAVAILABLE' });
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
