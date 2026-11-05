import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
import {
  S3Client,
  CreateBucketCommand,
  PutBucketVersioningCommand,
  ListObjectVersionsCommand,
  DeleteObjectsCommand,
  DeleteBucketCommand,
  ListObjectsV2Command,
} from '@aws-sdk/client-s3';
import sharp from 'sharp';
import { createStorage } from '../src/adapters/storage.js';

function scannerFixture() {
  return net.createServer((socket) => {
    let buffer = Buffer.alloc(0),
      done = false;
    socket.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (done || buffer.length < 14) return;
      let offset = 10,
        content = Buffer.alloc(0);
      while (offset + 4 <= buffer.length) {
        const size = buffer.readUInt32BE(offset);
        offset += 4;
        if (!size) {
          done = true;
          socket.end(
            content.toString().includes('EICAR') ? 'stream: fixture FOUND\0' : 'stream: OK\0',
          );
          return;
        }
        if (offset + size > buffer.length) return;
        content = Buffer.concat([content, buffer.subarray(offset, offset + size)]);
        offset += size;
      }
    });
  });
}

test('real versioned S3 upload, immutable review download and scanner protocol fail closed', async () => {
  // Only this randomly named bucket is touched. The scanner is a deterministic TCP protocol fixture.
  const config = {
    S3_ENDPOINT: process.env.TEST_S3_ENDPOINT ?? 'http://localhost:9000',
    S3_REGION: 'us-east-1',
    S3_BUCKET: `ride-test-${randomUUID()}`,
    S3_ACCESS_KEY: process.env.TEST_S3_ACCESS_KEY ?? 'ride-local',
    S3_SECRET_KEY: process.env.TEST_S3_SECRET_KEY ?? 'ride-local-password',
    CLAMAV_HOST: '127.0.0.1',
  };
  const s3 = new S3Client({
    region: config.S3_REGION,
    endpoint: config.S3_ENDPOINT,
    forcePathStyle: true,
    credentials: { accessKeyId: config.S3_ACCESS_KEY, secretAccessKey: config.S3_SECRET_KEY },
  });
  const scanner = scannerFixture();
  scanner.listen(0, '127.0.0.1');
  await once(scanner, 'listening');
  config.CLAMAV_PORT = scanner.address().port;
  const storage = createStorage(config),
    Bucket = config.S3_BUCKET,
    key = 'driver/document.pdf';
  try {
    await s3.send(new CreateBucketCommand({ Bucket }));
    await s3.send(
      new PutBucketVersioningCommand({ Bucket, VersioningConfiguration: { Status: 'Enabled' } }),
    );
    const first = Buffer.from('%PDF-1.7 clean document\n');
    let url = await storage.upload(key, 'application/pdf', first.length);
    assert(
      (
        await fetch(url, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/pdf' },
          body: first,
        })
      ).ok,
    );
    const doc = { object_key: key, mime: 'application/pdf', bytes: first.length },
      clean = await storage.scan(doc);
    assert.equal(clean.status, 'CLEAN');
    assert(clean.version);
    const second = Buffer.from('%PDF-1.7 EICAR fixture\n');
    url = await storage.upload(key, 'application/pdf', second.length);
    assert(
      (
        await fetch(url, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/pdf' },
          body: second,
        })
      ).ok,
    );
    const infected = await storage.scan({ ...doc, bytes: second.length });
    assert.equal(infected.status, 'INFECTED');
    assert.notEqual(infected.version, clean.version);
    const saved = await fetch(await storage.download(key, clean.version));
    assert.equal(await saved.text(), first.toString());
    await assert.rejects(storage.scan({ ...doc, bytes: 1 }), {
      code: 'DOCUMENT_METADATA_MISMATCH',
    });
    await new Promise((resolve) => scanner.close(resolve));
    await assert.rejects(storage.scan({ ...doc, bytes: second.length }), { code: 'ECONNREFUSED' });
  } finally {
    if (scanner.listening) await new Promise((resolve) => scanner.close(resolve));
    const versions = await s3.send(new ListObjectVersionsCommand({ Bucket }));
    const objects = [...(versions.Versions ?? []), ...(versions.DeleteMarkers ?? [])].map((v) => ({
      Key: v.Key,
      VersionId: v.VersionId,
    }));
    if (objects.length)
      await s3.send(new DeleteObjectsCommand({ Bucket, Delete: { Objects: objects } }));
    await s3.send(new DeleteBucketCommand({ Bucket }));
    s3.destroy();
    storage.close();
  }
});

test('non-versioned storage publishes only scanned bytes under a server-only verified key', async () => {
  const config = {
    S3_ENDPOINT: process.env.TEST_S3_ENDPOINT ?? 'http://localhost:9000',
    S3_REGION: 'us-east-1',
    S3_BUCKET: `ride-immutable-test-${randomUUID()}`,
    S3_ACCESS_KEY: process.env.TEST_S3_ACCESS_KEY ?? 'ride-local',
    S3_SECRET_KEY: process.env.TEST_S3_SECRET_KEY ?? 'ride-local-password',
    S3_OBJECT_MODE: 'immutable',
    CLAMAV_HOST: '127.0.0.1',
  };
  const { storageSettings } = await import('../src/adapters/storage-settings.js');
  const s3 = new S3Client(storageSettings(config).options),
    storage = createStorage(config),
    Bucket = config.S3_BUCKET;
  const scanner = scannerFixture();
  scanner.listen(0, '127.0.0.1');
  await once(scanner, 'listening');
  config.CLAMAV_PORT = scanner.address().port;
  const key = `staging/${randomUUID()}.pdf`,
    id = randomUUID();
  try {
    await s3.send(new CreateBucketCommand({ Bucket }));
    const first = Buffer.from('%PDF-1.7 ' + 'A'.repeat(40)),
      second = Buffer.from('%PDF-1.7 ' + 'B'.repeat(40));
    const upload = await storage.upload(key, 'application/pdf', first.length);
    assert(
      (
        await fetch(upload, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/pdf' },
          body: first,
        })
      ).ok,
    );
    const clean = await storage.scan({
      id,
      object_key: key,
      mime: 'application/pdf',
      bytes: first.length,
    });
    assert.equal(clean.status, 'CLEAN');
    assert.equal(clean.version, null);
    assert(clean.verifiedKey.includes(clean.sha256));
    assert.throws(() => storage.upload(clean.verifiedKey, 'application/pdf', first.length), {
      code: 'VERIFIED_OBJECT_UPLOAD_FORBIDDEN',
    });
    assert.throws(() => storage.download(key), { code: 'DOCUMENT_RESCAN_REQUIRED' });
    assert(
      (
        await fetch(upload, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/pdf' },
          body: second,
        })
      ).ok,
    );
    const published = await fetch(await storage.download(clean.verifiedKey));
    assert.equal(await published.text(), first.toString());
    const infected = Buffer.from('%PDF-1.7 EICAR fixture');
    const infectedUpload = await storage.upload(key, 'application/pdf', infected.length);
    assert(
      (
        await fetch(infectedUpload, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/pdf' },
          body: infected,
        })
      ).ok,
    );
    const rejected = await storage.scan({
      id,
      object_key: key,
      mime: 'application/pdf',
      bytes: infected.length,
    });
    assert.equal(rejected.status, 'INFECTED');
    assert.equal(rejected.verifiedKey, undefined);
    assert.equal(
      (await s3.send(new ListObjectsV2Command({ Bucket, Prefix: `verified/${id}/` }))).Contents
        .length,
      1,
    );

    const photoKey = `staging/${randomUUID()}.png`,
      photoId = randomUUID(),
      photoBytes = await sharp({
        create: { width: 32, height: 24, channels: 3, background: '#ffcc00' },
      }).png().toBuffer(),
      photoUpload = await storage.upload(photoKey, 'image/png', photoBytes.length);
    assert((await fetch(photoUpload, {
      method: 'PUT',
      headers: { 'Content-Type': 'image/png' },
      body: photoBytes,
    })).ok);
    await new Promise((resolve) => scanner.close(resolve));
    const photo = await storage.sanitizeProfilePhoto({
      id: photoId,
      object_key: photoKey,
      mime: 'image/png',
      bytes: photoBytes.length,
    });
    assert.equal(photo.status, 'CLEAN');
    assert.match(photo.verifiedKey, new RegExp(`^verified/${photoId}/`));
    assert.equal(photo.version, null);
    const profileImage = await fetch(await storage.download(photo.verifiedKey));
    assert.equal(profileImage.headers.get('content-type'), 'image/jpeg');
    assert.equal((await sharp(Buffer.from(await profileImage.arrayBuffer())).metadata()).format, 'jpeg');
  } finally {
    await new Promise((resolve) => scanner.close(resolve));
    const objects = (await s3.send(new ListObjectsV2Command({ Bucket }))).Contents ?? [];
    if (objects.length)
      await s3.send(
        new DeleteObjectsCommand({
          Bucket,
          Delete: { Objects: objects.map(({ Key }) => ({ Key })) },
        }),
      );
    await s3.send(new DeleteBucketCommand({ Bucket }));
    s3.destroy();
    storage.close();
  }
});
