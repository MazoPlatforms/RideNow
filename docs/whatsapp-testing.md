# Live WhatsApp testing

You can manually run the **WhatsApp live test** workflow in GitHub Actions. The `production` GitHub environment requires the complete JSON content of a paired Baileys `creds.json` as the `WA_AUTH_CREDS` secret. Normal environment approval rules apply.

Enter the recipient number starting with `94`. In `connection-only` mode, only the connection and recipient's WhatsApp registration are tested; no message is sent. For a real OTP, select `send-otp`. Each run attempts to send only one OTP; normal push/PR checks do not send WhatsApp messages. The recipient number is supplied to GitHub as a workflow input, not stored in source code, and masked in runner logs.

This workflow uses the `production` environment to access secrets. The application runs with `NODE_ENV=test` and isolated temporary PostgreSQL/Redis services. It does not use the production application's database, users, or sessions. JWT/encryption/OTP keys are freshly generated in memory for every run; production keys are not required.

Test sequence:

1. Validate credential JSON and binary keys; import into an encrypted database.
2. Establish a real WhatsApp connection; stop without exposing QR if pairing is required.
3. Real OTP request API → PostgreSQL queue → one test message through Baileys.
4. Verify the same OTP from the queue, reject reuse, make an authenticated request, log out, and verify session revocation.
5. Wait up to 60 seconds for a device-delivery acknowledgement from WhatsApp.

`otpVerification=true` confirms the server flow; it does not imply that the recipient manually entered the OTP. `sendAccepted=true` means the provider accepted the send. `delivery=delivered` means WhatsApp provided device-delivery acknowledgement, not proof the user read the message. Without delivery confirmation, the workflow fails and does not automatically send another OTP.

The `whatsapp-smoke-report` artifact contains only results, not OTPs, recipients, credentials, JWTs, Signal keys, or database dumps. It is retained for only three days.

## Common results

- `WA_AUTH_CREDS_MISSING`: environment secret is missing or empty.
- `WA_AUTH_CREDS_INVALID_JSON`: provide the raw `creds.json` JSON; a filename alone is insufficient.
- `WA_AUTH_CREDS_NOT_PAIRED` / `WA_PAIRING_REQUIRED`: a valid paired session export is required.
- `WA_SESSION_LOGGED_OUT`: the session is no longer valid and must be paired again.
- `WA_CONNECT_CLOSED`: check numeric `connectionStatus` in the report for the cause; new pairing is not attempted automatically.
- `WA_DEVICE_DELIVERY_UNCONFIRMED`: the message may have been accepted, but device delivery was not confirmed. Check the recipient's phone; do not rerun immediately.

## Persistent gateway

When the database is initially empty, `npm run whatsapp` imports `WA_AUTH_CREDS`. Existing sessions are not replaced by an environment export. New credentials and Signal keys are encrypted and stored in the same database. A normal multi-file Baileys session may include key files in addition to `creds.json`; exporting only credentials is not a substitute for a full session backup. The importer also accepts an optional `{creds,keys}` bundle.

The CI session database is temporary and does not provide a continuously running gateway. A persistent gateway requires persistent PostgreSQL and the same `ENCRYPTION_KEY`.
