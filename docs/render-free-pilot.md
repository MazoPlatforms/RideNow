# Render Free + UptimeRobot preview

This setup is intended for a controlled demo or internal testing. It does not guarantee availability for continuous ride operations. Create only one Free Web Service on Render; no new database, Redis, paid worker, or disk is needed. Use the existing Supabase **ridenow-demo** (`bhjuxggttifldkrcrshj`), Upstash, and R2. Do not use another Supabase project named `ridenow`. Only a database schema named `ridenow` exists within ridenow-demo.

## Service configuration

Create the service only after the owner has confirmed the Render workspace.

| Setting | Value |
| --- | --- |
| Type | Web Service |
| Name | ridenow-demo-api (confirm availability) |
| Repository | https://github.com/MazoPlatforms/RideNow |
| Branch | main |
| Runtime | Node |
| Compute plan | Free |
| Build command | `npm ci --omit=dev` |
| Start command | `npm run start:pilot` |
| Health check path | `/health/ready` |
| Auto-deploy | Disable initially; deploy a verified commit |

`start:pilot` runs the API, worker, and WhatsApp gateway as three child processes in the same instance. If one exits, the others are stopped and the service exits with a failure code so the hosting platform can restart it. SIGTERM/SIGINT is forwarded to all child processes; any process still running after 20 seconds is forcibly stopped. Each process defaults to a PostgreSQL pool of four connections, at most 12 total. Measure instance memory/CPU using real deployment metrics; capacity has not yet been proven.

During overlapping deployments, the new WhatsApp gateway waits for the old gateway to release its database lock. Once the old instance stops, the new one resumes the same encrypted database session. Builds/startup do not automatically run migrations, seed data, or create admins. Run the private schema installer workflow separately; it has previously succeeded.

## Render environment

GitHub production environment secrets do not transfer automatically to Render. Add the existing values below separately to the Render environment; do not expose them in chats, commits, or build logs.

| Secret | Purpose |
| --- | --- |
| `DATABASE_URL` | ridenow-demo session pooler, port 5432; URL-encoded password |
| `REDIS_URL` | Upstash TLS connection |
| `JWT_SECRET` | Existing signing secret |
| `OTP_PEPPER` | Existing separate OTP secret |
| `ENCRYPTION_KEY` | Same 64-character hex key; changing it prevents reading old credentials |
| `WA_AUTH_CREDS` | Previously paired Baileys JSON; imported only into an empty auth database |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | R2 credentials |

Common configuration:

```dotenv
NODE_VERSION=24
NODE_ENV=production
OTP_PROVIDER=baileys
DB_SCHEMA=ridenow
DB_ROLE=ridenow_backend
DB_POOL_MAX=4
S3_REGION=auto
FCM_ENABLED=false
NODE_EXTRA_CA_CERTS=/etc/secrets/supabase-ca.crt
```

In production, the code forces `OTP_PROVIDER` to `baileys`. Even if the Render variable is missing or has a different value, only Baileys is used. Development/test adapter selection remains available.

Alternative CA setup: If `SUPABASE_CA_CERT` contains public PEM in an environment variable, `start:pilot` writes a temporary CA file and configures `NODE_EXTRA_CA_CERTS` before starting child processes. In this mode, the `/etc/secrets/supabase-ca.crt` setting above is unnecessary. If both are set, the new CA is added to the existing CA file; the referenced file must exist. TLS certificate verification remains enabled.

Also add `S3_ENDPOINT` and `S3_BUCKET`. Include actual HTTPS frontend origins as comma-separated values in `CORS_ORIGINS`. Specify only approved vehicle categories in `MAP_CATEGORY_APPROVALS`; do not enable `TUK,CAR,VAN` before roads are verified. Keep `PORT` at the value provided by Render.

Configure `TRUST_PROXY_HOPS` only after verifying the actual proxy chain. Incorrect settings affect rate limiting. Use `1` only after confirming one proxy hop for direct onrender.com ingress; recheck if a Cloudflare proxy is added later.

Render secret files:

- `supabase-ca.crt`: public CA PEM from GitHub variable `SUPABASE_CA_CERT`. Do not disable TLS verification.
- Firebase credentials are not required for this self-hosted notification setup. If FCM is added later, set `FCM_ENABLED=true` and `GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-service-account.json`, then test real push delivery.

Document malware scanning requires a real ClamAV endpoint reachable from Render: `CLAMAV_HOST`, `CLAMAV_PORT`. ClamAV's TCP protocol has no authentication/TLS; do not expose the scanner port directly to the public internet. Configure a protected network/tunnel. Without a scanner, driver documents do not become CLEAN. Running ClamAV within the Free instance is not part of this deployment configuration. Rider profile avatars are instead decoded/re-encoded as JPEG with metadata removed; they do not require ClamAV.

## UptimeRobot

Only after the service is live, use its actual URL shown in the Render dashboard. Do not guess the URL from the service name.

1. Add New Monitor → HTTP(s).
2. Name: `RideNow demo readiness`.
3. URL: `https://<actual-render-host>/health/ready`.
4. Interval: 5 minutes on the Free plan.
5. Select an alert contact and test down/recovery alerts.

The endpoint checks PostgreSQL, Redis, and the production worker heartbeat. It does not demonstrate full WhatsApp delivery, FCM delivery, R2 permissions, or ClamAV functionality. Test each separately end to end. Do not include JWT, OTP, or API credentials in the monitor. This repository has no UptimeRobot account access/API credential; the monitor has not yet been created.

## Deployment verification

1. The Render deployment must reach `live`; a successful build alone is insufficient.
2. `/health/live` → 200; `/health/ready` → 200 with `status=ready`.
3. Check the worker heartbeat and WhatsApp connection. Send one OTP to an owner-approved number and test verification/replay rejection; never send OTPs automatically on every deployment.
4. Confirm the WhatsApp session resumes and pending jobs continue after restart/redeployment.
5. Test WebSocket notifications, the notification inbox after reconnect, R2 upload/download through the scanner, driver approval, and booking → dispatch → accept → start → complete → cash ledger.
6. Measure Free-tier memory/CPU, connection counts, job backlog, and latency before admitting a small number of pilot users.

## Free-tier limits

Official documentation was checked on 2026-11-03:

- [Render Free](https://render.com/docs/free): sleeps after 15 minutes without inbound HTTP/WebSocket traffic; restarting may take about one minute. Restarts can occur at any time.
- Each workspace shares 750 free instance hours per month. Other Free services in the same workspace also consume these hours.
- Free web instance: 0.1 CPU / 512 MB. Render may suspend services for excessive external database/API/storage traffic.
- Bandwidth/build usage has separate limits; if a payment method exists, additional usage may be billed. The owner must review budget/spend settings.
- [UptimeRobot Free](https://uptimerobot.com/pricing/): five-minute monitoring. HTTP checks may reduce idle periods but do not guarantee prevention of hosting suspensions, restarts, quota limits, or downtime.

Moving to continuously available paid infrastructure for real-trip dispatch, scheduled rides, live tracking, and WhatsApp OTP must be part of the pilot readiness assessment.

## Self-hosted notifications in the same service

The notification worker already runs within `start:pilot`; no separate Render service is required. Notifications flow through PostgreSQL outbox → notification inbox → Redis → authenticated Socket.IO connection. The inbox and WebSocket continue to work without FCM. High-frequency tracking events such as `driver.location` are not stored in the inbox.

Android/iOS clients must use the Socket.IO protocol; a raw WebSocket client alone is insufficient. Send `auth: { token: accessToken }` during the handshake. Listen for relevant events such as `ride.updated` and avoid duplicates using `eventId`. On each reconnect, call `GET /v1/me/notifications` with the JWT to retrieve missed notifications; after reading, call `POST /v1/me/notifications/:id/read`. The PostgreSQL inbox remains available even when socket events are missed; current worker policy retains notifications for 90 days.

With `FCM_ENABLED=false`, no new external push jobs are created. Existing push jobs, if any, remain pending without increasing attempts; they may continue when FCM is later enabled. Review the relevance of old notifications before enabling a push provider. An inbox read status is not a device OS push-delivery receipt.

This method provides immediate notifications while the app is connected and an inbox when it is reopened. Immediate OS notifications are not guaranteed while the app is backgrounded, suspended, or closed; separate push transport such as APNs for iOS or FCM for common Android devices is required. A self-hosted worker alone cannot replace them. Sending also pauses during Render Free sleep/restart, and pending outbox work resumes when the service restarts.
