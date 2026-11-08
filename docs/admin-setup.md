# Temporary initial admin setup

This feature creates the first admin account. It does not modify previously published public website files. New account-access pages are served by the backend.

## Setup

1. Install the `003-admin-recovery` migration. For Supabase, use the existing `schema-install.yml` workflow; only the `ridenow-demo` project is allowed.
2. Set `ADMIN_SETUP_KEY` to a random 32+ character secret and `ADMIN_SETUP_EXPIRES_AT` to a UTC ISO expiration in the Render environment. The key should expire within 24 hours for this deployment. Setup cannot run without an expiration configured.
3. Open the private link `/admin-setup/#key=<private-key>`. The fragment is not sent to the server; JavaScript reads it and removes it from the browser URL. Do not share keys, passwords, or recovery passwords in chats, screenshots, or Git commits.

The setup page/API responds with 404 if the first admin already exists, the permanent setup-completion marker is present, or the key has expired. Changing the admin role later does not reopen the old key. After setup, the key and expiration can be removed from the Render environment.

## Create the account

- Enter a dedicated admin phone number, name, and password of at least 12 characters. A password manager is recommended.
- Request a WhatsApp OTP and enter the received code within five minutes. The OTP must match the account phone and can be used only once.
- If OTP cannot be sent or received, select the recovery checkbox. **During initial setup, the private setup key provides operator authorization**; phone ownership is not checked on this path. The option is not available to a public visitor without the key.
- TOTP is optional. If selected, add the secret to an authenticator and confirm its code. An unconfirmed secret is not stored on the account.
- If the number already belongs to a regular rider, that account becomes an admin and old sessions are revoked. This setup does not modify driver/staff, suspended, or deleted accounts.
- After account creation, **a separate recovery password is displayed once**. Store it securely and separately from the login password. Both passwords are stored in the database only as bcrypt hashes.

## Later login

On `/admin-access/`:

- Without TOTP: login password + WhatsApp OTP.
- With TOTP: login password + a new TOTP code. The code already confirmed at setup cannot be used again immediately; wait for the next code.
- If OTP/TOTP is unavailable: login password + separate recovery password. Recovery revokes old sessions, invalidates the old recovery password, and displays a new one once. Save the replacement.
- Five incorrect recovery-password attempts cause a 15-minute recovery lock. API rate limiting also applies.

`/admin-access/` now redirects to the new multi-page sign-in at `/admin/login/`. After login, the `/admin/` dashboard opens. The browser session is secured with an HttpOnly cookie, while API tokens are stored in encrypted server-side Redis state. Passwords/JWTs are not saved in browser storage or the URL. See [dashboard details](admin-dashboard.md).

## API

- `POST /v1/admin-setup/otp`: setup key header + phone.
- `POST /v1/admin-setup/totp`: setup key header + phone; temporary encrypted enrollment envelope.
- `POST /v1/admin-setup/complete`: setup key header + name/phone/password; challenge/code for `verification=whatsapp` or explicit operator fallback for `verification=setup-key`. Optional enrollment and TOTP confirmation.
- `POST /v1/auth/staff/login`: phone/password + TOTP or phone-bound challenge/code.
- `POST /v1/auth/admin/recover`: phone/password/recoveryPassword; new session and rotated recovery password.

Setup key header: `X-Admin-Setup-Key`. Account pages, setup responses, and authentication responses use `Cache-Control: no-store`. Request bodies and credentials are not logged. The recovery password is returned to its owner once over HTTPS; if the response is lost without being saved, use normal OTP/TOTP login.
