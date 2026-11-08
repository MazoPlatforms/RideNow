# Mazo Ride Service Backend

A backend built with Node.js 24, vanilla JavaScript ES modules, PostgreSQL/PostGIS, Redis, REST, and Socket.IO. Designed for Sri Lanka, LKR, and `Asia/Colombo`; supports immediate rides, scheduled rides, and return trips with the same driver using Tuk/Car/Van vehicles.

## Run locally

Requirements: Node.js 24, npm, and Docker Compose. Running the application on the host is the simplest way to get started.

```sh
npm ci
cp .env.example .env
```

In `.env`, set different random values of at least 32 characters for `JWT_SECRET` and `OTP_PEPPER`, and a 32-byte hexadecimal value for `ENCRYPTION_KEY`. To generate an example random value:

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
docker compose up -d postgres redis storage
npm run migrate
npm run seed
npm run storage:init
npm start
```

Run `npm run worker` in another terminal. For document scanning, run `docker compose --profile scanner up -d clamav`. The ClamAV signature database may take time to become ready. `seed` creates only a development service area and **test rates**; it is not permitted in production.

API: `http://localhost:3000`; `GET /health/live`, `GET /health/ready`, `GET /openapi.json`. Do not commit `.env` or private keys.

### Development OTP

With `OTP_PROVIDER=database`, WhatsApp messages are not sent. The OTP is not exposed in the API response.

```sh
curl -X POST http://localhost:3000/v1/auth/otp/request \
  -H 'Content-Type: application/json' -d '{"phone":"0771234567"}'
npm run otp:dev
```

Use the returned `challengeId` and code with `POST /v1/auth/otp/verify`. For other APIs, send `Authorization: Bearer <accessToken>`. Important write operations, including bookings and cash confirmation, require an `Idempotency-Key` header. All booking quotes expire after two minutes.

### WhatsApp and administration

For a GitHub test that sends real messages, see the [WhatsApp testing instructions](docs/whatsapp-testing.md). A separate manual workflow uses the `WA_AUTH_CREDS` secret from the `production` environment.

Set `OTP_PROVIDER=baileys` and run `npm run whatsapp`. Display the pairing QR payload printed in the terminal using a trusted local QR renderer to link the account. Never share the QR payload with public QR websites. Gateway credentials are encrypted in the database using AES-256-GCM; only one gateway can hold the advisory lock.

Baileys is not the official WhatsApp Business API. If disconnected, OTP requests return `503 OTP_PROVIDER_UNAVAILABLE`. To connect another provider, replace the gateway adapter without changing authentication code. This release does not include an SMS adapter.

```sh
npm run admin:create -- 0771234567
```

For temporary browser setup, optional TOTP, and recovery login when OTP is unavailable, see the [admin setup guide](docs/admin-setup.md).

English admin dashboard: `/admin/`. For multi-page login, permissions, build commands, and browser checks, see the [admin dashboard guide](docs/admin-dashboard.md).

The password is read from stdin or can be supplied through `ADMIN_PASSWORD`. Register the generated TOTP secret in an authenticator. Use password + TOTP via `POST /v1/auth/staff/login`. Staff/Admin login through rider OTP is not allowed. After creating the first admin, manage staff provisioning and permissions through the API.

## Ride flow

1. The rider obtains a server-validated route quote using `POST /v1/fare-quotes`.
2. Book using `POST /v1/rides`. Only the rider receives the four-digit ride PIN.
3. Driver onboarding → vehicle/documents → scan → staff approval → fresh location → availability.
4. The worker selects nearby drivers and sends offers; retrieve them using `GET /v1/driver-offers` or WebSocket.
5. Accept offer → `ARRIVE` → `START` with rider PIN → `COMPLETE`.
6. On a return trip: `AT_STOP` → optionally `WAIT` → `RETURN` → `COMPLETE` at pickup.
7. Once the driver collects cash, call `POST /v1/rides/:id/cash`. Completion alone does not mean cash was collected.

Scheduled quotes require `scheduledAt` in UTC ISO format, at least 30 minutes and at most 30 days in advance. Dispatch starts 15 minutes before departure and searches until five minutes after pickup time. Assignment is not guaranteed until a driver is found. Rescheduling requires a new quote and is not allowed once dispatch begins.

Final fare: booked route distance + actual driving time + stop waiting time. The first five minutes at a stop are free; waiting time is not counted again as driving time. Surge applies only to base and distance components. The fare-plan version and surge are locked in at booking.

In cash-only mode, the platform commission is owed from the total amount collected by the driver. For example, on a LKR 1,000 fare with 20% commission, the driver owes the platform LKR 200; the remaining LKR 800 is not paid out again. Payouts are only created when the driver has credit, and the maker and checker must be different people.

## Testing

For instructions to run the API, worker, and WhatsApp gateway on a single Render Free Tier instance with UptimeRobot monitoring, see the [preview deployment guide](docs/render-free-pilot.md).

```sh
npm test
npm run test:storage
TEST_DATABASE_URL=postgres://ride:ride@localhost:5432/ride_test npm run test:integration
LOAD_DATABASE_URL=postgres://ride:ride@localhost:5432/ride_load_test npm run test:load
TEST_DATABASE_URL=postgres://ride:ride@localhost:5432/ride_test npm run demo
npm run check
npm run docs:api
npm audit --omit=dev --audit-level=moderate
```

Integration and load scripts create their own databases and reset their fixtures. **Never use a production database URL.** Scripts require database names ending in `_test` or `_load_test`. Create the databases in advance if `CREATE DATABASE` permission is unavailable.

The default integration port is `5432`, default Redis integration DB is `15`, and load DB is `14`. Override them with `TEST_REDIS_URL` and `LOAD_REDIS_URL`. These Redis databases are flushed during testing, so use a separate test Redis instance.

PostgreSQL/PostGIS, transactions, locks, constraints, migrations, Redis, and WebSocket are exercised against real services. Deterministic adapters are used for WhatsApp/FCM delivery. The separate `test:storage` suite uses actual versioned S3-compatible storage and a controlled ClamAV TCP fixture; real malware-engine signatures must be smoke-tested separately. The Mazo graph is real. The [benchmark report](docs/load-report.json) reflects a local 1,000-driver test, not a production SLA guarantee.

## Production readiness

For the `ridenow-demo` isolated schema, migrations, and role permissions, see [database setup](docs/private-database.md).

For Supabase/Upstash secrets and live connectivity testing, see the [managed services guide](docs/managed-services.md). For Cloudflare R2, see the [storage guide](docs/r2-storage.md).

Before production, follow the [operations handbook](docs/operations.md), [API contract](docs/api.md), and [security and data policies](docs/security.md).

- Confirm actual fare/commission rates, service-area polygons, required documents, and legal retention requirements.
- A private S3/R2 bucket, ClamAV, and a linked Baileys account are required. Firebase is not required for the self-hosted notification inbox/WebSocket; credentials are only needed if FCM push is enabled. R2 stores scanned bytes as a separate verified copy; versioned S3 pins the object version.
- Specify only verified categories in `MAP_CATEGORY_APPROVALS`. The current graph has no turn-restriction relations; do not assume a generic driving route is legal for every category.
- Production configuration requires HTTPS origins, Baileys, and S3; FCM is optional. Quotes outside routing coverage are rejected; there is no public demo OSRM fallback.
- A Dockerfile is provided; a full application image build and external-service delivery smoke test were not run in this environment.

The Mazo routing core and place decoder include upstream GPL-3.0-licensed source in `vendor/mazo/LICENSE`. Data sources and checksum details are in [NOTICE](NOTICE.md).
