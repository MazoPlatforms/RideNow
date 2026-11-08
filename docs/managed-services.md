# Supabase and Upstash connectivity

The `production` GitHub environment requires `DATABASE_URL` and `REDIS_URL` secrets. The GitHub Actions **Supabase and Upstash connectivity test** workflow can be triggered manually on `main`.

- Supabase: Use a direct PostgreSQL connection or Session pooler for long-running backend/gateway processes. Session pooler port 5432 is the usual choice for IPv4 runners. Transaction pooler port 6543 does not support session-level locks and is rejected by the backend.
- TLS certificate and hostname verification are mandatory for Supabase hosts. If `TLS_CERTIFICATE_VERIFICATION_FAILED` occurs, the public CA certificate from Dashboard → Database Settings → SSL Configuration can be placed as PEM in the `production` environment variable `SUPABASE_CA_CERT`. Certificate verification remains enabled.
- Upstash: A direct Redis/TCP URL is required. CLI examples may use `redis://` with `--tls`; the backend automatically converts Upstash host URLs to `rediss://` without exposing secret values. REST URLs/tokens are not supported by this client.

The test checks PostgreSQL/TLS, session advisory locks, PostGIS distance calculations, Redis read/write, Lua rate-limit commands, and Pub/Sub. It does not modify PostgreSQL schema, migrations, or user data. In Redis, it creates and deletes only test keys with UUID names, which also expire automatically within 30 seconds. It does not use `FLUSHDB`.

Logs and the `services-smoke-report` artifact do not contain connection URLs, passwords, tokens, or user data. A successful test does not mean the running application is connected or the pilot has been launched.

## Schema alignment

If Supabase already contains tables, first review whether their columns and migration version history match the repository migrations. This workflow does not run migrations automatically. Place application tables in a schema with permissions that are not exposed through the Data API, and verify the PostgreSQL role and RLS access model before running deployment migrations.

Cloudflare R2 supports a verified-copy method that does not require versioning; see the [R2 guide](r2-storage.md). Supabase Storage has not been connected and tested separately. A successful PostgreSQL connection does not establish that Storage works.
