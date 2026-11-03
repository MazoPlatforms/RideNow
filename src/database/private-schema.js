import { runner } from 'node-pg-migrate';
import { readdir } from 'node:fs/promises';
export const APP_SCHEMA = 'ridenow';
export const APP_ROLE = 'ridenow_backend';
export async function installPrivateSchema(client) {
  const before = (await client.query('SHOW search_path')).rows[0].search_path;
  const locked = (await client.query('SELECT pg_try_advisory_lock(927563) AS locked')).rows[0]
    .locked;
  if (!locked)
    throw Object.assign(new Error('Schema installation already running'), {
      code: 'SCHEMA_INSTALL_IN_PROGRESS',
    });
  try {
    return await install(client);
  } finally {
    try {
      await client.query("SELECT set_config('search_path',$1,false)", [before]);
    } finally {
      await client.query('SELECT pg_advisory_unlock(927563)');
    }
  }
}
async function install(client) {
  const existing = await client.query("SELECT tablename FROM pg_tables WHERE schemaname='ridenow'");
  if (
    existing.rows.length &&
    !(await client.query("SELECT to_regclass('ridenow.schema_migrations') AS name")).rows[0].name
  )
    throw Object.assign(new Error('Existing schema requires review'), {
      code: 'UNMANAGED_SCHEMA_EXISTS',
    });
  if (existing.rows.length) {
    const names = (await client.query('SELECT name FROM ridenow.schema_migrations')).rows.map(
      (r) => r.name,
    );
    const known = (await readdir('migrations'))
      .filter((n) => n.endsWith('.cjs'))
      .map((n) => n.slice(0, -4));
    if (names.some((n) => !known.includes(n)))
      throw Object.assign(new Error('Migration history mismatch'), {
        code: 'MIGRATION_HISTORY_MISMATCH',
      });
  }
  await client.query('CREATE SCHEMA IF NOT EXISTS ridenow');
  await client.query('CREATE SCHEMA IF NOT EXISTS extensions');
  const apiRoles = (
    await client.query(
      "SELECT rolname FROM pg_roles WHERE rolname IN ('anon','authenticated','service_role')",
    )
  ).rows.map((r) => r.rolname);
  const denied = ['PUBLIC', ...apiRoles.map((n) => '"' + n + '"')].join(',');
  await client.query(`REVOKE ALL ON SCHEMA ridenow FROM ${denied}`);
  await runner({
    dbClient: client,
    dir: 'migrations',
    direction: 'up',
    migrationsTable: 'schema_migrations',
    migrationsSchema: 'ridenow',
    schema: ['ridenow', 'extensions', 'public'],
    count: Infinity,
    checkOrder: true,
    logger: { info() {}, warn() {}, error() {} },
  });
  await client.query('BEGIN');
  try {
    await client.query(
      "DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='ridenow_backend') THEN CREATE ROLE ridenow_backend NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS; END IF; END $$",
    );
    const owner = (await client.query('SELECT current_user AS name')).rows[0].name;
    await client.query(`GRANT ridenow_backend TO "${owner.replaceAll('"', '""')}"`);
    await client.query('GRANT USAGE ON SCHEMA ridenow,extensions TO ridenow_backend');
    await client.query(`REVOKE ALL ON ALL TABLES IN SCHEMA ridenow FROM ${denied}`);
    await client.query(`REVOKE ALL ON ALL SEQUENCES IN SCHEMA ridenow FROM ${denied}`);
    await client.query(`REVOKE ALL ON ALL FUNCTIONS IN SCHEMA ridenow FROM ${denied}`);
    await client.query(
      'GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA ridenow TO ridenow_backend',
    );
    await client.query('GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA ridenow TO ridenow_backend');
    await client.query('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA ridenow TO ridenow_backend');
    for (const f of ['check_balanced_ledger', 'immutable_ledger', 'immutable_fare'])
      await client.query(
        `ALTER FUNCTION ridenow.${f}() SET search_path TO ridenow,extensions,pg_temp`,
      );
    await client.query(
      'REVOKE UPDATE,DELETE ON ridenow.ledger_entries,ridenow.ledger_transactions,ridenow.audit_logs,ridenow.fare_plans FROM ridenow_backend',
    );
    await client.query(
      'REVOKE INSERT,UPDATE,DELETE ON ridenow.schema_migrations FROM ridenow_backend',
    );
    await client.query(
      `ALTER DEFAULT PRIVILEGES IN SCHEMA ridenow REVOKE ALL ON TABLES FROM ${denied}`,
    );
    await client.query(
      `ALTER DEFAULT PRIVILEGES IN SCHEMA ridenow REVOKE ALL ON SEQUENCES FROM ${denied}`,
    );
    await client.query(
      `ALTER DEFAULT PRIVILEGES IN SCHEMA ridenow REVOKE EXECUTE ON FUNCTIONS FROM ${denied}`,
    );
    // Partition creation is a release/admin operation, not an application privilege.
    for (let i = 0; i <= 12; i++) {
      const start = new Date();
      start.setUTCDate(1);
      start.setUTCHours(0, 0, 0, 0);
      start.setUTCMonth(start.getUTCMonth() + i);
      const end = new Date(start);
      end.setUTCMonth(end.getUTCMonth() + 1);
      const name = `ride_location_points_${start.getUTCFullYear()}_${String(start.getUTCMonth() + 1).padStart(2, '0')}`;
      await client.query(
        `CREATE TABLE IF NOT EXISTS ridenow.${name} PARTITION OF ridenow.ride_location_points FOR VALUES FROM ('${start.toISOString()}') TO ('${end.toISOString()}')`,
      );
    }
    const tables = (
      await client.query("SELECT tablename FROM pg_tables WHERE schemaname='ridenow'")
    ).rows;
    for (const { tablename } of tables) {
      const name = '"' + tablename.replaceAll('"', '""') + '"';
      await client.query(`ALTER TABLE ridenow.${name} ENABLE ROW LEVEL SECURITY`);
      await client.query(`DROP POLICY IF EXISTS backend_access ON ridenow.${name}`);
      await client.query(
        `CREATE POLICY backend_access ON ridenow.${name} FOR ALL TO ridenow_backend USING(true) WITH CHECK(true)`,
      );
      await client.query(`REVOKE ALL ON ridenow.${name} FROM ${denied}`);
      if (tablename !== 'schema_migrations' && tablename.startsWith('ride_location_points_'))
        await client.query(
          `GRANT SELECT,INSERT,UPDATE,DELETE ON ridenow.${name} TO ridenow_backend`,
        );
    }
    await client.query('COMMIT');
    return {
      tables: tables.length,
      migrations: Number(
        (await client.query('SELECT count(*)::int AS count FROM ridenow.schema_migrations')).rows[0]
          .count,
      ),
    };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  }
}
