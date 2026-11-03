import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  DeleteObjectCommand,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { createHash, randomUUID } from 'node:crypto';
import net from 'node:net';
import { ensure } from '../shared/errors.js';
import { storageSettings } from './storage-settings.js';

function scanBytes(data, config) {
  return new Promise((resolve, reject) => {
    const socket = net.connect({
      host: config.CLAMAV_HOST ?? 'localhost',
      port: Number(config.CLAMAV_PORT ?? 3310),
    });
    let reply = '';
    socket.setTimeout(30000, () => socket.destroy(new Error('Scanner timeout')));
    socket.on('error', reject);
    socket.on('data', (b) => {
      reply += b.toString();
    });
    socket.on('end', () => {
      if (reply.includes('FOUND')) resolve('INFECTED');
      else if (reply.includes('OK')) resolve('CLEAN');
      else reject(new Error('Scanner unavailable'));
    });
    socket.on('connect', () => {
      socket.write('zINSTREAM\0');
      for (let i = 0; i < data.length; i += 65536) {
        const chunk = data.subarray(i, i + 65536),
          size = Buffer.alloc(4);
        size.writeUInt32BE(chunk.length);
        socket.write(size);
        socket.write(chunk);
      }
      socket.write(Buffer.alloc(4));
    });
  });
}
const VERIFIED = /^verified\/[a-f\d-]{36}\/[a-f\d]{64}-[a-f\d-]{36}$/;
export function createStorage(config) {
  const { mode, options } = storageSettings(config),
    client = new S3Client(options),
    Bucket = config.S3_BUCKET;
  const signer = config.S3_PUBLIC_ENDPOINT
    ? new S3Client({ ...options, endpoint: config.S3_PUBLIC_ENDPOINT })
    : client;
  const send = (command) => client.send(command, { abortSignal: AbortSignal.timeout(15000) });
  return {
    mode,
    close() {
      client.destroy();
      if (signer !== client) signer.destroy();
    },
    upload(Key, mime, bytes) {
      ensure(!Key.startsWith('verified/'), 403, 'VERIFIED_OBJECT_UPLOAD_FORBIDDEN');
      return getSignedUrl(
        signer,
        new PutObjectCommand({ Bucket, Key, ContentType: mime, ContentLength: bytes }),
        { expiresIn: 300 },
      );
    },
    download(Key, VersionId) {
      if (mode === 'immutable') ensure(VERIFIED.test(Key), 409, 'DOCUMENT_RESCAN_REQUIRED');
      return getSignedUrl(
        signer,
        new GetObjectCommand({ Bucket, Key, ...(mode === 'versioned' ? { VersionId } : {}) }),
        { expiresIn: 120 },
      );
    },
    async discardVerified(Key) {
      ensure(VERIFIED.test(Key), 400, 'INVALID_VERIFIED_OBJECT_KEY');
      await send(new DeleteObjectCommand({ Bucket, Key }));
    },
    async scan(doc) {
      const head = await send(new HeadObjectCommand({ Bucket, Key: doc.object_key }));
      ensure(
        head.ContentLength === doc.bytes && head.ContentType === doc.mime,
        422,
        'DOCUMENT_METADATA_MISMATCH',
      );
      if (mode === 'versioned')
        ensure(head.VersionId && head.VersionId !== 'null', 503, 'BUCKET_VERSIONING_REQUIRED');
      ensure(head.ETag, 503, 'OBJECT_ETAG_REQUIRED');
      const response = await send(
        new GetObjectCommand({
          Bucket,
          Key: doc.object_key,
          IfMatch: head.ETag,
          ...(mode === 'versioned' ? { VersionId: head.VersionId } : {}),
        }),
      );
      const data = Buffer.from(await response.Body.transformToByteArray());
      ensure(data.length === doc.bytes, 422, 'DOCUMENT_LENGTH_MISMATCH');
      const valid =
        doc.mime === 'application/pdf'
          ? data.subarray(0, 5).toString() === '%PDF-'
          : doc.mime === 'image/png'
            ? data.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))
            : data[0] === 255 && data[1] === 216 && data[2] === 255;
      ensure(valid, 422, 'DOCUMENT_SIGNATURE_MISMATCH');
      const status = await scanBytes(data, config),
        result = { status, version: response.VersionId ?? null };
      if (status === 'CLEAN' && mode === 'immutable') {
        ensure(
          typeof doc.id === 'string' && /^[a-f\d-]{36}$/.test(doc.id),
          500,
          'DOCUMENT_ID_REQUIRED',
        );
        const digest = createHash('sha256').update(data).digest('hex');
        const key = `verified/${doc.id}/${digest}-${randomUUID()}`;
        // Publish the exact scanned bytes under a fresh server-only key. A user's
        // old presigned upload URL can only alter the staging key, never this copy.
        await send(
          new PutObjectCommand({
            Bucket,
            Key: key,
            Body: data,
            ContentType: doc.mime,
            ContentMD5: createHash('md5').update(data).digest('base64'),
            IfNoneMatch: '*',
            Metadata: { sha256: digest },
          }),
        );
        result.verifiedKey = key;
        result.sha256 = digest;
        result.version = null;
      }
      return result;
    },
  };
}
