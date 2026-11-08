# Cloudflare R2 document storage

GitHub `production` environment secrets:

- `S3_ACCESS_KEY`: R2 S3 Access Key ID.
- `S3_SECRET_KEY`: the corresponding Secret Access Key; Object Read & Write permissions are required for the selected bucket.

Environment variables:

- `S3_ENDPOINT=https://ACCOUNT_ID.r2.cloudflarestorage.com` — S3 API endpoint, not the public `r2.dev` address.
- `S3_BUCKET` — name of the existing bucket.
- `S3_REGION=auto`.

The live workflow also accepts these three settings as secrets. Keep public bucket access disabled. Browser uploads require bucket CORS settings allowing the application's origin. A direct Node.js test does not verify browser CORS or bucket public-access status.

## Protection without versioning

The [R2 S3 API](https://developers.cloudflare.com/r2/api/s3/api/) does not support bucket versioning. The backend recognizes R2 hosts and selects the verified-copy method:

1. The user's presigned URL uploads only to a staging key.
2. The worker verifies metadata, ETag, file signature, and ClamAV scan results.
3. When the result is CLEAN, exactly the scanned bytes are written to a new `verified/` key with a SHA-256 identity.
4. The database document reference is atomically switched to that copy. In concurrent scans, only the first completed reference is published and unused verified copies are deleted.
5. Users never receive a presigned URL that can write to verified keys. Review downloads serve only that copy.

This is an application-level immutable-key method, not R2 object locking or versioning. Never provide privileged storage credentials to the frontend. Existing versioned S3 documents continue to use object-version pinning. No database migration is required for this change.

## Live testing

GitHub Actions → **Cloudflare R2 storage test** → Run workflow.

The workflow uploads a small synthetic PDF and creates a verified copy through a deterministic scanner protocol fixture. It confirms that changing the staging file does not change the verified download. Only files created by the test are deleted; user documents are not read. Existing bucket settings/CORS are not modified.

The report has `malwareEngineVerified=false`, `browserCorsVerified=false`, and `bucketVisibilityVerified=false`: each needs separate real-world testing during deployment. This workflow only demonstrates storage integration.
