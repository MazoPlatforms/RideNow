import { randomUUID } from 'node:crypto';
import net from 'node:net';
import { once } from 'node:events';
import { writeFile } from 'node:fs/promises';
import { S3Client, DeleteObjectCommand } from '@aws-sdk/client-s3';
import { storageSettings } from '../src/adapters/storage-settings.js';
import { createStorage } from '../src/adapters/storage.js';

const report = {
  startedAt: new Date().toISOString(),
  result: 'failed',
  provider: 'cloudflare-r2',
  upload: false,
  scanFixture: false,
  verifiedDownload: false,
  stagingOverwriteIsolated: false,
  malwareEngineVerified: false,
  browserCorsVerified: false,
  bucketVisibilityVerified: false,
};
let client, storage, scanner;
const owned = new Set();
function check(value, code) {
  if (!value) throw Object.assign(new Error(code), { code });
}
try {
  for (const name of ['S3_ENDPOINT', 'S3_BUCKET', 'S3_ACCESS_KEY', 'S3_SECRET_KEY'])
    check(process.env[name]?.trim(), `${name}_MISSING`);
  const config = {
    ...process.env,
    S3_REGION: process.env.S3_REGION || 'auto',
    CLAMAV_HOST: '127.0.0.1',
  };
  const settings = storageSettings(config);
  check(settings.r2, 'R2_ENDPOINT_REQUIRED');
  check(settings.mode === 'immutable', 'R2_IMMUTABLE_MODE_REQUIRED');
  client = new S3Client(settings.options);
  scanner = net.createServer((socket) => {
    let buffer = Buffer.alloc(0),
      done = false;
    socket.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (done || buffer.length < 14) return;
      let offset = 10;
      while (offset + 4 <= buffer.length) {
        const size = buffer.readUInt32BE(offset);
        offset += 4;
        if (!size) {
          done = true;
          socket.end('stream: OK\0');
          return;
        }
        if (offset + size > buffer.length) return;
        offset += size;
      }
    });
  });
  scanner.listen(0, '127.0.0.1');
  await once(scanner, 'listening');
  config.CLAMAV_PORT = scanner.address().port;
  storage = createStorage(config);
  const id = randomUUID(),
    key = `ridenow-smoke/${id}/upload.pdf`,
    bytes = Buffer.from('%PDF-1.7 ' + 'A'.repeat(40));
  owned.add(key);
  const upload = await storage.upload(key, 'application/pdf', bytes.length);
  const put = await fetch(upload, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/pdf' },
    body: bytes,
    signal: AbortSignal.timeout(15000),
  });
  check(put.ok, 'R2_PRESIGNED_UPLOAD_FAILED');
  report.upload = true;
  const scanned = await storage.scan({
    id,
    object_key: key,
    mime: 'application/pdf',
    bytes: bytes.length,
  });
  check(scanned.status === 'CLEAN' && scanned.verifiedKey, 'R2_VERIFIED_COPY_FAILED');
  owned.add(scanned.verifiedKey);
  report.scanFixture = true;
  const changed = Buffer.from('%PDF-1.7 ' + 'B'.repeat(40));
  check(
    (
      await fetch(upload, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/pdf' },
        body: changed,
        signal: AbortSignal.timeout(15000),
      })
    ).ok,
    'R2_STAGING_OVERWRITE_FAILED',
  );
  const download = await fetch(await storage.download(scanned.verifiedKey), {
    signal: AbortSignal.timeout(15000),
  });
  check(download.ok, 'R2_PRESIGNED_DOWNLOAD_FAILED');
  check(Buffer.from(await download.arrayBuffer()).equals(bytes), 'R2_VERIFIED_BYTES_CHANGED');
  report.verifiedDownload = true;
  report.stagingOverwriteIsolated = true;
  report.result = 'passed';
} catch (error) {
  const mapped = {
    AccessDenied: 'R2_ACCESS_DENIED',
    NoSuchBucket: 'R2_BUCKET_NOT_FOUND',
    InvalidAccessKeyId: 'R2_ACCESS_KEY_INVALID',
    SignatureDoesNotMatch: 'R2_SIGNATURE_INVALID',
  };
  const candidate = error.code ?? error.name;
  report.error =
    mapped[candidate] ??
    (/^[A-Z][A-Z0-9_]{2,80}$/.test(candidate ?? '') ? candidate : 'R2_PROBE_FAILED');
  if (Number.isInteger(error.$metadata?.httpStatusCode))
    report.httpStatus = error.$metadata.httpStatusCode;
  process.exitCode = 1;
} finally {
  if (scanner?.listening) await new Promise((resolve) => scanner.close(resolve));
  report.cleanup = true;
  for (const Key of owned)
    if (client)
      try {
        await client.send(new DeleteObjectCommand({ Bucket: process.env.S3_BUCKET, Key }), {
          abortSignal: AbortSignal.timeout(10000),
        });
      } catch {
        report.cleanup = false;
      }
  if (storage) storage.close();
  if (client) client.destroy();
  if (!report.cleanup) {
    report.result = 'failed';
    process.exitCode = 1;
    report.cleanupError = 'R2_TEMPORARY_OBJECT_CLEANUP_FAILED';
  }
  report.finishedAt = new Date().toISOString();
  await writeFile('storage-smoke-report.json', JSON.stringify(report, null, 2) + '\n', {
    mode: 0o600,
  });
  process.stdout.write(JSON.stringify(report) + '\n');
}
