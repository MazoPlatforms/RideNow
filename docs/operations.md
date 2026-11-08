# Operations and recovery

## Deployment

Three process commands use the same code image:

- API: `node src/processes/api.js`; multiple instances are allowed.
- Worker: `node src/processes/worker.js`; multiple instances; jobs and dispatch are protected by database locks.
- WhatsApp: `node src/processes/whatsapp.js`; only one active gateway per account.

Run database migrations in a release job with `npm run migrate` before deployment. Use only additive migrations compatible with running older API instances. The down migrations here do not perform destructive rollbacks.

Docker build:

```sh
docker build -t mazo-ride-backend:local .
```

When a managed proxy CA is needed:

```sh
docker build --secret id=proxy_ca,src="$CODEX_PROXY_CERT" -t mazo-ride-backend:local .
```

Do not disable TLS verification. The production image runs as a non-root user. Do not embed runtime secrets in the image. Adjust Database/Redis/S3 hostnames to the container network. Configure TLS, WebSocket upgrades, request-size limits, and timeouts on the reverse proxy. Set `TRUST_PROXY_HOPS` only to the number of trusted proxy hops.

## Provider configuration

- PostgreSQL: PostGIS and pgcrypto extensions; a DDL role for migrations, minimal application permissions, TLS, and a connection pool budget.
- Redis: private network and authentication/TLS; rate limiting and socket adapter. Business jobs live in PostgreSQL and are not lost when Redis restarts.
- Storage: private bucket. Versioned S3 requires object-version read permission; R2 requires Object Read & Write and verified copies. Deny public reads and set a narrow CORS policy for the browser upload origin.
- `S3_ENDPOINT`: backend internal endpoint. Optional `S3_PUBLIC_ENDPOINT`: browser-accessible presigning endpoint. Both must refer to the same bucket and credentials.
- ClamAV: `CLAMAV_HOST`, `CLAMAV_PORT` (default 3310); private network and current signatures. Without the scanner, documents remain PENDING and cannot be approved.
- FCM: `FCM_ENABLED=true`; Application Default Credentials or mounted `GOOGLE_APPLICATION_CREDENTIALS`; dry-run smoke test with a valid app registration token.
- Baileys: pair the account and confirm the gateway heartbeat is fresh; before launch an operator must request and verify an OTP with a test number. Account logout requires manual pairing again.

## Monitoring

`/health/live` checks process liveness. `/health/ready` checks database, Redis, map startup, and the production worker heartbeat. A ready response does not guarantee OTP/FCM delivery.

`GET /v1/admin/operations` provides ride counts, outbox backlog, jobs/dead jobs, and provider timestamps. Structured request logs include requestId, status, and durationMs; worker logs include jobId and retry code. Configure these alerts in the monitoring platform:

- Worker heartbeat older than 30 seconds.
- WhatsApp ready=false or heartbeat older than 30 seconds.
- Stale pending outbox, attempts ≥10, and dead jobs.
- Dispatch/no-driver ratio and scheduled pickup delays.
- REST p95 >500 ms, realtime p95 >2 seconds, and excessive GPS rejection.
- Database connections, disk, backup failures, and scanner failures.

Ledger SQL check:

```sql
SELECT transaction_id, SUM(debit-credit)
FROM ledger_entries GROUP BY transaction_id HAVING SUM(debit-credit)<>0;
```

The result should be empty. A commit-time trigger prevents imbalance.

## Job recovery

Job leases last 60 seconds; scanner timeout is 30 seconds and WhatsApp send timeout is 20 seconds. After a crash, retries resume when the lease expires. Jobs are marked dead after eight failures. External-service timeouts can cause duplicate push/WhatsApp delivery; OTPs are tied to a single challenge.

After fixing the underlying cause, an operator may retry only the reviewed dead job:

```sql
UPDATE jobs SET dead=false,attempts=0,run_at=now(),lease_until=NULL,lease_token=NULL
WHERE id='<reviewed-job-uuid>' AND done_at IS NULL;
```

Do not replay a job for an expired OTP; request a new OTP. Outbox attempts ≥10 may be reset only for reviewed events. Notifications are deduplicated by event ID.

## Backups and rollback

- Enable managed PostgreSQL PITR, make daily encrypted backups, and perform monthly isolated restore drills.
- Apply a backup/lifecycle policy to S3 versions or R2 verified objects; do not purge approved documents before retention ends.
- Back up `ENCRYPTION_KEY` and JWT/OTP secrets in a secret manager. Losing the encryption key prevents decryption of Baileys, MFA, and PIN/idempotency records.
- For application rollback, restore the previous image but preserve additive database migrations. Data rollback requires PITR restore and reconciliation.
- On SIGTERM, API connections are drained and workers finish the current tick. Allow at least a 60-second termination grace period.

## Current test evidence

`docs/load-report.json` measures a local API instance with real PostGIS/Redis, 1,000 drivers, and 100 active rides. It does not test provider delivery, S3 throughput, dispatch capacity, or multi-region failover. A Dockerfile is provided, but the application image was not built in this environment because of disk space limitations.
