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
} from '@aws-sdk/client-s3';
import { createStorage } from '../src/adapters/storage.js';

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
  const scanner = net.createServer((socket) => {
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
