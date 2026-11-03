import { randomUUID } from 'node:crypto';
import { database } from '../src/shared/db.js';
import { postgresConnectionString } from '../src/shared/connections.js';
import { installPrivateSchema } from '../src/database/private-schema.js';
const expected = 'bhjuxggttifldkrcrshj';
let pool, client;
try {
  const url = new URL(postgresConnectionString(process.env.DATABASE_URL));
  const matched =
    url.hostname === `db.${expected}.supabase.co` ||
    (url.hostname.endsWith('.pooler.supabase.com') &&
      decodeURIComponent(url.username).endsWith('.' + expected));
  if (!matched)
    throw Object.assign(new Error('Only ridenow-demo is authorized'), {
      code: 'WRONG_SUPABASE_PROJECT',
    });
  pool = database(url.toString(), { schema: 'public', role: null, max: 1 });
  client = await pool.connect();
  const installed = await installPrivateSchema(client);
  await client.query('SET ROLE ridenow_backend');
  await client.query('SET search_path TO ridenow,extensions,public,pg_temp');
  await client.query('BEGIN');
  const user = (
    await client.query(
      "INSERT INTO users(phone,name) VALUES($1,'Rollback-only verification') RETURNING id",
      ['schema-verification:' + randomUUID()],
    )
  ).rows[0];
  await client.query('INSERT INTO audit_logs(actor_id,action,target_id) VALUES($1,$2,$1)', [
    user.id,
    'SCHEMA_VERIFICATION',
  ]);
  await client.query('ROLLBACK');
  const counts = (
    await client.query(
      "SELECT count(*)::int AS tables,count(*) FILTER(WHERE rowsecurity)::int AS protected FROM pg_tables WHERE schemaname='ridenow'",
    )
  ).rows[0];
  const publicRoles = (
    await client.query(
      "SELECT rolname FROM pg_roles WHERE rolname IN ('anon','authenticated','service_role') AND has_schema_privilege(rolname,'ridenow','USAGE')",
    )
  ).rows;
  if (publicRoles.length || counts.tables !== counts.protected)
    throw Object.assign(new Error('Access check failed'), {
      code: 'PRIVATE_SCHEMA_ACCESS_CHECK_FAILED',
    });
  console.log(
    JSON.stringify({
      project: 'ridenow-demo',
      schema: 'ridenow',
      ...installed,
      rlsTables: counts.protected,
      apiRolesDenied: true,
      applicationWriteVerified: true,
      verificationRowsRolledBack: true,
      result: 'passed',
    }),
  );
} catch (e) {
  console.error(
    JSON.stringify({
      result: 'failed',
      code: /^[A-Z0-9_]{3,80}$/.test(e.code ?? '') ? e.code : 'PRIVATE_SCHEMA_INSTALL_FAILED',
    }),
  );
  process.exitCode = 1;
} finally {
  if (client) client.release();
  if (pool) await pool.end();
}
