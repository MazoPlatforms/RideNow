import {
  S3Client,
  CreateBucketCommand,
  PutBucketVersioningCommand,
  PutBucketCorsCommand,
} from '@aws-sdk/client-s3';
const region = process.env.S3_REGION ?? 'us-east-1';
const s3 = new S3Client({
  region,
  endpoint: process.env.S3_ENDPOINT || undefined,
  forcePathStyle: !!process.env.S3_ENDPOINT,
  ...(process.env.S3_ACCESS_KEY
    ? {credentials: {accessKeyId: process.env.S3_ACCESS_KEY,secretAccessKey: process.env.S3_SECRET_KEY}}
    : {}),
});
const Bucket = process.env.S3_BUCKET;
try {
  try {
    await s3.send(new CreateBucketCommand({Bucket,...(region==='us-east-1'?{}:{CreateBucketConfiguration:{LocationConstraint:region}})}));
  } catch (e) {
    if (!['BucketAlreadyOwnedByYou', 'BucketAlreadyExists'].includes(e.name)) throw e;
  }
  await s3.send(new PutBucketVersioningCommand({Bucket, VersioningConfiguration: {Status:'Enabled'}}));
  await s3.send(new PutBucketCorsCommand({Bucket,CORSConfiguration:{CORSRules:[{
    AllowedOrigins:(process.env.CORS_ORIGINS ?? 'http://localhost:5173').split(','),
    AllowedMethods:['PUT','GET','HEAD'],AllowedHeaders:['*'],ExposeHeaders:['ETag','x-amz-version-id'],MaxAgeSeconds:300,
  }]}}));
  console.log('Private versioned bucket and browser CORS ready. Configure deployment IAM and TLS separately.');
} finally {
  s3.destroy();
}
