# Isolated backend schema for ridenow-demo

The Supabase target for this repository is **ridenow-demo**, project reference `bhjuxggttifldkrcrshj`. Another Supabase project named `ridenow` is not used for this backend. **Within the ridenow-demo project, `ridenow` is only the schema name.**

## Migrations and permissions

GitHub Actions → **Set up isolated ridenow-demo schema** uses `DATABASE_URL` and the public CA variable from the `production` environment. If the project identity in the URL does not match, it stops before opening a database connection.

Canonical migration history is managed in `ridenow.schema_migrations` using node-pg-migrate. `npm run migrate` uses this isolated installer for Supabase URLs; local PostgreSQL tests continue using the public schema. Existing unknown tables or migration history are never modified automatically.

- All tables, sequences, and functions reside in the isolated `ridenow` schema.
- Schema/table/function access is denied to `PUBLIC`, `anon`, `authenticated`, and `service_role`.
- RLS applies to all tables and partitions; only the trusted backend role has an access policy.
- `ridenow_backend`: NOLOGIN, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOBYPASSRLS. It has standard application DML permissions but no schema DDL permissions.
- UPDATE/DELETE permissions are denied for ledger/audit/fare-version records; write permission to migration history is denied.
- Functions use a fixed search_path; SECURITY DEFINER is not used.

This is a custom WhatsApp/JWT backend, so browser users do not obtain PostgreSQL roles directly. Rider/Driver/Staff authorization is enforced by the REST/WebSocket backend. Database policies apply only to trusted backend sessions. Supabase Auth `auth.uid()` is not substituted for these user IDs.

## Application connectivity

For Supabase URLs, the connection pool automatically runs `SET ROLE ridenow_backend` and sets the private search_path. These can also be configured explicitly in deployment with `DB_SCHEMA` and `DB_ROLE`. Only installer/diagnostic connections use an administrative context.

**A NOLOGIN role is not a separate database password.** The current `DATABASE_URL` login credential remains unchanged; application queries switch to a lower-privilege role. If a distinct least-privilege LOGIN credential is needed, provision it separately and grant role membership. Completing schema setup never makes it safe to expose owner credentials to the frontend.

## Verification and maintenance

The installer checks permissions by writing a synthetic user/audit row and rolling back the transaction. It does not seed sample fares, drivers, admin passwords, or WhatsApp sessions.

Because the application role lacks DDL permission, monthly partitions are created by releases/the installer. The installer prepares partitions for the next 12 months; run a migration release monthly. Runtime retention/surge tasks continue, but creating new partitions is an administrator responsibility.

Finishing schema setup does not mean application hosting or deployment is complete. The API, worker, WhatsApp gateway, ClamAV, and client applications must be run separately.
