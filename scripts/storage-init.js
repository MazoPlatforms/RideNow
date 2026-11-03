import {
  S3Client,
  CreateBucketCommand,
  PutBucketVersioningCommand,
  PutBucketCorsCommand,
} from '@aws-sdk/client-s3';
import { storageSettings } from '../src/adapters/storage-settings.js';
const settings = storageSettings(process.env);
const region = settings.options.region;
const s3 = new S3Client(settings.options);
const Bucket = process.env.S3_BUCKET;
try {
  try {
    await s3.send(
      new CreateBucketCommand({
        Bucket,
        ...(settings.r2 || region === 'us-east-1'
          ? {}
          : { CreateBucketConfiguration: { LocationConstraint: region } }),
      }),
    );
  } catch (e) {
    if (!['BucketAlreadyOwnedByYou', 'BucketAlreadyExists'].includes(e.name)) throw e;
  }
  if (settings.mode === 'versioned')
    await s3.send(
      new PutBucketVersioningCommand({ Bucket, VersioningConfiguration: { Status: 'Enabled' } }),
    );
  await s3.send(
    new PutBucketCorsCommand({
      Bucket,
      CORSConfiguration: {
        CORSRules: [
          {
            AllowedOrigins: (process.env.CORS_ORIGINS ?? 'http://localhost:5173').split(','),
            AllowedMethods: ['PUT', 'GET', 'HEAD'],
            AllowedHeaders: ['*'],
            ExposeHeaders: ['ETag', 'x-amz-version-id'],
            MaxAgeSeconds: 300,
          },
        ],
      },
    }),
  );
  console.log(
    `Private bucket and browser CORS configured. Document protection mode: ${settings.mode}`,
  );
} finally {
  s3.destroy();
}
