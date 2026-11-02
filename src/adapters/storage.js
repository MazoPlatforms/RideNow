import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import net from 'node:net';
import { ensure } from '../shared/errors.js';
export function createStorage(config) {
  const client = new S3Client({
    region: config.S3_REGION ?? 'us-east-1',
    endpoint: config.S3_ENDPOINT || undefined,
    forcePathStyle: !!config.S3_ENDPOINT,
    ...(config.S3_ACCESS_KEY
      ? {
          credentials: { accessKeyId: config.S3_ACCESS_KEY, secretAccessKey: config.S3_SECRET_KEY },
        }
      : {}),
  });
  const Bucket = config.S3_BUCKET;
  const signer = config.S3_PUBLIC_ENDPOINT
    ? new S3Client({
        region: config.S3_REGION ?? 'us-east-1',
        endpoint: config.S3_PUBLIC_ENDPOINT,
        forcePathStyle: true,
        credentials: config.S3_ACCESS_KEY
          ? { accessKeyId: config.S3_ACCESS_KEY, secretAccessKey: config.S3_SECRET_KEY }
          : undefined,
      })
    : client;
  return {
    close() {
      client.destroy();
      if (signer !== client) signer.destroy();
    },
    upload: (Key, mime, bytes) =>
      getSignedUrl(
        signer,
        new PutObjectCommand({ Bucket, Key, ContentType: mime, ContentLength: bytes }),
        { expiresIn: 300 },
      ),
    download: (Key, VersionId) =>
      getSignedUrl(signer, new GetObjectCommand({ Bucket, Key, VersionId }), { expiresIn: 120 }),
    async scan(doc) {
      const head = await client.send(new HeadObjectCommand({ Bucket, Key: doc.object_key }));
      ensure(
        head.ContentLength === doc.bytes && head.ContentType === doc.mime,
        422,
        'DOCUMENT_METADATA_MISMATCH',
      );
      const response = await client.send(
        new GetObjectCommand({ Bucket, Key: doc.object_key, VersionId: head.VersionId }),
      );
      const data = Buffer.from(await response.Body.transformToByteArray());
      ensure(head.VersionId, 503, 'BUCKET_VERSIONING_REQUIRED');
      const valid =
        doc.mime === 'application/pdf'
          ? data.subarray(0, 5).toString() === '%PDF-'
          : doc.mime === 'image/png'
            ? data.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))
            : data[0] === 255 && data[1] === 216 && data[2] === 255;
      ensure(valid, 422, 'DOCUMENT_SIGNATURE_MISMATCH');
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
          if (reply.includes('FOUND')) resolve({ status: 'INFECTED', version: response.VersionId });
          else if (reply.includes('OK')) resolve({ status: 'CLEAN', version: response.VersionId });
          else reject(new Error('Scanner unavailable'));
        });
        socket.on('connect', () => {
          socket.write('zINSTREAM\0');
          for (let i = 0; i < data.length; i += 65536) {
            const chunk = data.subarray(i, i + 65536),
              length = Buffer.alloc(4);
            length.writeUInt32BE(chunk.length);
            socket.write(length);
            socket.write(chunk);
          }
          socket.write(Buffer.alloc(4));
        });
      });
    },
  };
}
