# இயக்கம் மற்றும் மீட்பு

## Deployment

ஒரே code image-ல் மூன்று process commands:

- API: `node src/processes/api.js`; பல instances அனுமதி.
- Worker: `node src/processes/worker.js`; பல instances; jobs/dispatch database locking மூலம் பாதுகாப்பு.
- WhatsApp: `node src/processes/whatsapp.js`; ஒரு account-க்கு ஒரே active gateway.

Database migrations deployment-க்கு முன் ஒரு release job-ல் `npm run migrate`. பழைய API instances இயங்கிக்கொண்டிருக்கும்போது பொருந்தும் additive migrations மட்டும் பயன்படுத்தவும். இங்குள்ள down migrations destructive rollback செய்யாது.

Docker build:

```sh
docker build -t mazo-ride-backend:local .
```

Managed proxy CA தேவைப்படும் சூழலில்:

```sh
docker build --secret id=proxy_ca,src="$CODEX_PROXY_CERT" -t mazo-ride-backend:local .
```

TLS verification அணைக்க வேண்டாம். Production image non-root user-ல் இயங்கும். Runtime secrets-ஐ image-ல் சேர்க்கக்கூடாது. Database/Redis/S3 hostnames container network-க்கு ஏற்ப மாற்றவும். Reverse proxy-ல் TLS, WebSocket upgrade, request-size limits, timeouts அமைக்கவும். `TRUST_PROXY_HOPS`-ஐ நம்பகமான proxy எண்ணிக்கைக்கு மட்டுமே அமைக்கவும்.

## Provider அமைப்பு

- PostgreSQL: PostGIS, pgcrypto extensions; migrations-க்கு DDL role; application-க்கு குறைந்த permissions; TLS மற்றும் connection pool budget.
- Redis: private network, authentication/TLS; rate limits மற்றும் socket adapter; business jobs PostgreSQL-ல் இருப்பதால் Redis restart-ல் அவை இழக்கப்படாது.
- Storage: private bucket. Versioned S3-க்கு object-version read permission; R2-க்கு Object Read & Write மற்றும் verified நகல்கள். Public read மறுப்பு; browser upload origin-க்கு குறுகிய CORS policy.
- `S3_ENDPOINT`: backend internal endpoint. விருப்பமான `S3_PUBLIC_ENDPOINT`: browser அணுகக்கூடிய presign endpoint. இரண்டும் அதே bucket/credentials-ஐ குறிக்க வேண்டும்.
- ClamAV: `CLAMAV_HOST`, `CLAMAV_PORT` (default 3310); private network; fresh signatures. Scanner இல்லாவிட்டால் documents PENDING-ல் இருக்கும்; approval முடியாது.
- FCM: `FCM_ENABLED=true`; Application Default Credentials அல்லது mounted `GOOGLE_APPLICATION_CREDENTIALS`; valid app registration token-உடன் dry-run smoke test.
- Baileys: pair செய்து gateway heartbeat புதிதாக இருப்பதைச் சரிபார்க்கவும்; சோதனை எண்ணிற்கு OTP request/verify-ஐ launch முன் operator இயக்க வேண்டும். Account logout-ல் manual re-pairing தேவை.

## கண்காணிப்பு

`/health/live` process liveness; `/health/ready` database, Redis, map startup மற்றும் production-ல் worker heartbeat. Ready response OTP/FCM delivery உத்தரவாதம் அல்ல.

`GET /v1/admin/operations`-ல் ride counts, outbox backlog, jobs/dead jobs, provider timestamps. Structured request logs-ல் requestId, status, durationMs; worker logs-ல் jobId, retry code. Monitoring platform-ல் கீழ்கண்ட alerts அமைக்கவும்:

- Worker heartbeat 30 விநாடிக்கு மேல் பழையது.
- WhatsApp ready=false / heartbeat 30 விநாடிக்கு மேல் பழையது.
- பழைய pending outbox, attempts ≥10, dead jobs.
- Dispatch/no-driver விகிதம் மற்றும் scheduled pickup தாமதம்.
- REST p95 >500 ms, realtime p95 >2 seconds, அதிக GPS rejection.
- Database connections, disk, backup failures, scanner failures.

Ledger SQL சோதனை:

```sql
SELECT transaction_id, SUM(debit-credit)
FROM ledger_entries GROUP BY transaction_id HAVING SUM(debit-credit)<>0;
```

பதில் காலியாக இருக்க வேண்டும். Commit-time trigger imbalance-ஐத் தடுக்கிறது.

## Job மீட்பு

Job leases 60 விநாடிகள்; scanner timeout 30 விநாடிகள்; WhatsApp send timeout 20 விநாடிகள். Crash-ல் lease முடிந்தபின் மீண்டும் முயலும். 8 தோல்விகளுக்குப் பிறகு dead. வெளிச்சேவை timeout-ல் push/WhatsApp duplicate delivery சாத்தியம்; OTP ஒரே challenge-க்கு மட்டும் செல்லும்.

காரணத்தைச் சரிசெய்த பிறகு குறிப்பிட்ட dead job-ஐ மட்டும் operator மீண்டும் இயக்கலாம்:

```sql
UPDATE jobs SET dead=false,attempts=0,run_at=now(),lease_until=NULL,lease_token=NULL
WHERE id='<reviewed-job-uuid>' AND done_at IS NULL;
```

OTP காலாவதியான job-ஐ replay செய்ய வேண்டாம்; புதிய OTP கேட்க வேண்டும். Outbox ≥10 attempts-க்கு reviewed event-ல் மட்டும் attempts reset செய்யலாம். Notifications event ID மூலம் deduplicate ஆகும்.

## Backups மற்றும் rollback

- Managed PostgreSQL PITR இயக்கவும்; தினசரி encrypted backup; மாதந்தோறும் isolated restore drill.
- S3 versions அல்லது R2 verified objects-க்கு backup/lifecycle policy; approved ஆவணங்களை retention முடியும் முன் purge செய்யக்கூடாது.
- `ENCRYPTION_KEY`, JWT/OTP secrets-ஐ secret manager-ல் backup செய்யவும். Encryption key இழந்தால் Baileys, MFA, PIN/idempotency பதிவுகளை decrypt செய்ய முடியாது.
- Application rollback-ல் பழைய image-க்கு திரும்பவும்; additive database migration-ஐ அப்படியே வைத்திருக்கவும். Data rollback-க்கு PITR restore மற்றும் reconciliation தேவை.
- SIGTERM API-ல் connections drain; worker தற்போதைய tick முடித்து நிற்கும். குறைந்தது 60 விநாடி termination grace கொடுக்கவும்.

## தற்போதைய சோதனை ஆதாரம்

`docs/load-report.json` ஒரு local API instance, உண்மையான PostGIS/Redis, 1,000 drivers, 100 active rides ஆகியவற்றின் அளவீடு. அது provider delivery, S3 throughput, dispatch capacity அல்லது multi-region failover சோதனை அல்ல. Dockerfile வழங்கப்பட்டுள்ளது; application image build இந்தப் பணிச்சூழலில் disk அளவு காரணமாக இயக்கப்படவில்லை.
