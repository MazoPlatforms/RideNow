# Security and data policies

- JWT lifetime is 15 minutes; rotating refresh sessions last 30 days, and token reuse revokes the session family. Database session checks run for every HTTP request and socket command.
- Sessions are revalidated before socket events are sent; idle connections are checked every 15 seconds.
- OTP lifetime is 5 minutes, with 5 attempts per challenge, a 60-second resend interval, and 10 requests per phone per hour. Delivery payloads are encrypted; OTP values are not exposed through the API.
- Admin/Staff authentication uses a bcrypt password and single-use TOTP, or a password plus phone-bound single-use WhatsApp OTP for accounts without TOTP. Configured TOTP cannot be bypassed using ordinary OTP. Secrets use AES-256-GCM; only admins can elevate roles; resource ownership is enforced on the backend.
- Initial admin browser setup requires a private, expiring setup key and is permanently closed using a completion marker after the admin is created. If OTP is unavailable, the owner can explicitly select the setup-key path, which does not verify phone ownership. Later recovery login requires both the login password and a separate rotating recovery password, with a 15-minute lock after 5 failed recovery attempts.
- The rate limiter fails closed if Redis fails. Limits: edge IP 10,000/min, authenticated user 180/min, auth endpoint IP 10/min, and socket user 120 commands/min.
- SQL is parameterized. State transitions, assignment, idempotency, and ledger operations use locked database transactions.
- Ledger entries/transactions and fare versions are immutable; corrections require reversals or new versions.
- Review downloads and driver approval require a CLEAN document scan. S3 pins the exact scanned object version; R2 stores scanned bytes under a new server-only key identified by SHA-256. User upload URLs are never issued for verified keys.
- Rider history does not expose the driver's current location; real-time GPS is limited to the current assignment.
- Never log OTPs, passwords, JWTs, Baileys credentials, or entire documents. QR/TOTP enrollment secrets are provided to operators only once.
- Admin browser sessions use Secure/HttpOnly/SameSite=Strict cookies, encrypted server-side Redis state, and same-origin/custom-header CSRF checks. Passwords are not stored during the password step; only an encrypted password-hash fingerprint proof is retained for five minutes. JWT/refresh tokens are not exposed to browser storage.

## Retention

Raw ride GPS and location outbox: 30 days. Terminal ride events, route geometry, and notifications: 90 days. Canonical ride/fare snapshots, ledger, audit, and reconciliation records are preserved until financial/legal retention policies are finalized. Expired unused quotes are removed after 90 days.

Location history uses monthly partitions; the worker creates the upcoming partition. The default partition provides a safeguard for unexpected timestamps. Old records are cleaned up; a DBA may monitor and drop empty old partitions.

Before production, verify Sri Lankan privacy, driver licensing, retention, and financial requirements, and update configuration and procedures accordingly. User data-deletion requests must coordinate account disabling, session revocation, and mandatory financial-record retention. This release has no destructive account-deletion API.
