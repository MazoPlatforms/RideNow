# Admin dashboard

The admin workspace is served at `/admin/`. Sign-in starts at `/admin/login/`; the previous `/admin-access/` address redirects there. Existing administrator passwords, authenticator enrollment, and recovery passwords remain valid.

The public website under `website/` is unchanged. The dashboard lives in `admin/` and uses plain HTML, CSS, and ES-module JavaScript. Inter is self-hosted with its OFL license. Tailwind CSS is compiled and committed; production does not use a CDN or a browser-side CSS compiler.

## Pages

| Page | Capabilities |
| --- | --- |
| Overview | Actual booking/account counts, recent rides, queue/provider heartbeats, configuration checklist |
| Users | Paginated records, search within loaded records, suspend/restore with self-suspension protection |
| Drivers | Review profile, vehicles, document scan status, clean-document download, approve/reject/suspend |
| Rides | Paginated monitoring, details, audited interruption with explicit final fare |
| Service areas | Create an inactive GeoJSON polygon, inspect boundaries, activate/deactivate with backend guards |
| Pricing | Review latest fares, publish immutable fare versions in LKR, configure surge caps |
| Finance | Find drivers by name/phone, inspect balances, record ledger adjustments, create and process payouts |
| Team & access | Create staff with scoped permissions, display enrollment secret, update permissions |
| Audit log | Paginated audit history and event details |
| Account & security | Profile name, verification status, active sessions, session revocation |

Search in record tables filters only loaded records; Load more retrieves another page. Finance driver search queries the server and returns up to 100 matches. Monetary inputs use explicit decimal-to-minor-unit conversion. Forms preserve their idempotency key across a failed request with the same input. Backend validation, authorization, document scanning, routing approvals, and separate-person payout controls still apply.

## Multi-page verification

1. `/admin/login/`: phone number and password.
2. `/admin/verify/`: WhatsApp code, or the current authenticator code if TOTP is enrolled. Sending a WhatsApp message requires an explicit button click.
3. `/admin/`: the workspace, or the first page permitted for a staff member.

Recovery uses `/admin/recovery/` after the password step. Successful recovery rotates the separate recovery password and signs out old sessions. `/admin/recovery-saved/` displays the replacement once and asks the user to save it before continuing. Staff members use their authenticator and granted permissions; administrator recovery is not available to staff.

## Browser-session security

- Passwords and JWT/refresh tokens are not kept in browser storage or URLs.
- The password step stores a password-hash fingerprint and account identity in an encrypted Redis flow with a five-minute TTL. It does not store the password. A password change invalidates this proof.
- Production cookies use the `__Host-` prefix, Secure, HttpOnly, SameSite=Strict, and Path=/ with no Domain attribute.
- Authenticated browser tokens remain encrypted in Redis behind an opaque cookie. The browser session lasts at most 12 hours; refresh is serialized to prevent concurrent refresh-token reuse.
- Returning to sign-in checks for an existing valid cookie-backed session and resumes the permitted workspace without requesting another code. This also handles entry from an external link with SameSite=Strict cookies. Explicit `?reauth=1` links let the user start fresh verification for recovery or switching accounts. Expired/revoked sessions and cleared cookies still require sign-in.
- Same-origin checks and the `X-Admin-Request` header protect cookie-authenticated API calls. Existing bearer-token APIs remain available to the Android client.
- Permissions and account suspension are checked against the database, including after session refresh. Verification flows are single-use and limited to five attempts.
- The verification page labels authenticator and WhatsApp codes explicitly. TOTP-enrolled accounts use the authenticator; the normal sign-in flow does not request a WhatsApp code for them.
- Sign-in and protected pages use no-store and noindex headers. Public admin assets use revalidation; the Inter font is cached.

Redis loss requires browser users to sign in again. Provider heartbeat status is not proof of malware-engine scanning or Android background-notification delivery.

## Build and verification

```sh
npm ci
npm run build:admin
npm run check
npm run test:all
npm run test:admin:browser
```

The browser test uses a disposable local test database and synthetic admin/driver accounts. It verifies multi-page sign-in, all management pages, area creation, exact LKR fare publishing, driver review, finance search, mobile navigation, recovery-password replacement, and logout. It never uses the production admin account or sends a real WhatsApp message. Chromium can be selected with `CHROMIUM_PATH`; CI installs Playwright Chromium.

`scripts/build-admin-pages.js` generates the checked-in HTML. Tailwind sources are limited to dashboard HTML and JavaScript. CI rebuilds the dashboard and checks for differences, runs database/API tests, then performs the browser checks and captures screenshots. Production uses the checked-in assets, so `npm ci --omit=dev` remains sufficient on Render.

No new database migration or external credentials are required for this dashboard. The first-admin setup completion marker remains in place; this change does not reopen account registration.
