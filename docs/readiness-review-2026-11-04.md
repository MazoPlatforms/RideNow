# Pilot readiness review — 2026-11-04

**Result: Core backend checks passed, but the system is not yet ready to launch a pilot with real passengers.** This review verified commit `437bc3dee540decb13d024a786163fd62fddbc59`. Website files were not changed. No new OTPs, drivers, documents, bookings, or financial transactions were created in production. Only the cloud storage probe created and cleaned up isolated temporary objects.

## Successful checks

| Area | Result |
| --- | --- |
| JavaScript syntax | `npm run check` passed |
| Unit and website routes | 17/17 passed |
| PostgreSQL/PostGIS, Redis, API, WebSocket integration | 32/32 passed |
| Versioned S3 and immutable storage | 2/2 passed; scanner was a controlled protocol fixture |
| Dependency audit | `npm audit --omit=dev --audit-level=moderate`: 0 vulnerabilities |
| API documentation | 56 API paths; generated OpenAPI unchanged |
| Production HTTP | 13 checks passed: website, assets, health, OpenAPI, authentication blocking, invalid challenge, API JSON 404, and disallowed CORS origin |
| Production WebSocket | Invalid token rejected with `AUTH_REQUIRED` over real WebSocket transport |
| Supabase | Session pooler 5432, verified TLS, session lock, pgcrypto, PostGIS, and geospatial query passed |
| Upstash | TLS, PING, read/write, Lua, and Pub/Sub passed |
| R2 | Upload, verified copy through scan fixture, download, staging overwrite isolation, and temporary object cleanup passed |
| Supabase security advisor | No security lints; this is not a comprehensive penetration test |
| Production processes | Render deployment live; worker and WhatsApp readiness heartbeats fresh |

No new login tokens were created in the production HTTP review. Earlier on the same day, a live authentication test using an OTP received by the owner on WhatsApp passed 11 checks: login, OTP reuse rejection, profile access, rider/admin isolation, token rotation, logout, and refresh reuse rejection. The test session was logged out; tokens were not exposed.

Cloud test evidence:

- [Supabase and Upstash live run](https://github.com/MazoPlatforms/RideNow/actions/runs/41806521716)
- [Cloudflare R2 live run](https://github.com/MazoPlatforms/RideNow/actions/runs/41806526426)
- [Backend CI for the reviewed revision](https://github.com/MazoPlatforms/RideNow/actions/runs/41636507050)

## Local load test

A separate local test database and Redis were used; this load was not directed at the Render production instance.

| Metric | Value |
| --- | --- |
| Simulated drivers | 1,000 |
| Active rides | 100 |
| Socket connections | 1,100 |
| Location updates | 2,000 |
| Errors | 0 |
| REST p95 | 33 ms |
| Location acknowledgement p95 | 251 ms |
| Realtime p95 | 941 ms |

These are local measurements only. They do not establish Render Free capacity, mobile network latency, WhatsApp delivery, production dispatch throughput, storage capacity, or malware scanning capacity. See the [full load report](load-report.json).

## Production blockers identified

Only the `ridenow` schema in the `ridenow-demo` project was inspected using read-only queries. No other Supabase project was used.

| Configuration | Current count | Required action |
| --- | --- | --- |
| Active admin accounts | 0 | Set up a valid operator account, password, and TOTP enrollment |
| Active service areas | 0 | Configure the polygon for the approved operating area |
| Fare plans | 0 | Configure approved fares for vehicle categories |
| Approved drivers | 0 | Complete real onboarding, document review, and vehicle approval |
| Pending documents | 0 | Production scanner functionality is not yet proven |
| Dead jobs | 0 | No dead jobs in this snapshot |
| Pending non-OTP jobs | 0 | No backlog in this snapshot |

Additional requirements:

1. **Real malware scanner:** The R2 probe reports `malwareEngineVerified=false`. Verify a secured scanner connection, CLEAN/rejected/timeout paths, and fail-closed behavior. Do not treat the protocol fixture as a test of the real engine.
2. **R2 policy verification:** The probe reports `browserCorsVerified=false` and `bucketVisibilityVerified=false`. Confirm the bucket is private. Test browser CORS if the website uses browser uploads; browser CORS restrictions do not apply to the native Android client.
3. **Routing approval:** Verify vehicle-category approvals and local road routes. The current graph lacks turn restrictions and cannot be assumed correct for every vehicle type.
4. **Android end-to-end:** Test booking → offer → accept → GPS → complete → cash ledger on a physical device, including network interruption, reconnect, notification inbox, and session expiry.
5. **Background notifications:** The current self-hosted inbox/WebSocket setup supports foreground and reconnect use. Immediate OS push delivery while the app is suspended or closed has not been demonstrated; FCM/APNs transport is not enabled in this setup.
6. **Hosting and operations:** Render Free sleep/restart/quota limits remain. Independently test UptimeRobot down/recovery alert delivery, backup restoration, operator procedures, and actual pilot load.

No business configuration or admin account was automatically created during this review to address these blockers. Core authentication and backend functionality have been verified, but full production booking readiness cannot be confirmed without service-area, fare, driver, and scanner configurations.
