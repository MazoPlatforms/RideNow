import pg from 'pg';
import { postgresConnectionString, isSupabaseHost } from './connections.js';
export function database(url, options = {}) {
  const poolMax = Number(process.env.DB_POOL_MAX ?? 20);
  if (!Number.isInteger(poolMax) || poolMax < 2 || poolMax > 100)
    throw new Error('INVALID_DATABASE_POOL_MAX');
  const connectionString = postgresConnectionString(url);
  const cloud = isSupabaseHost(new URL(connectionString).hostname);
  const {
    schema = process.env.DB_SCHEMA ?? (cloud ? 'ridenow' : 'public'),
    role = Object.hasOwn(options, 'role')
      ? options.role
      : (process.env.DB_ROLE ?? (cloud ? 'ridenow_backend' : null)),
    onConnect,
    ...rest
  } = options;
  if (!/^[a-z_][a-z0-9_]*$/.test(schema) || (role && !/^[a-z_][a-z0-9_]*$/.test(role)))
    throw new Error('INVALID_DATABASE_NAMESPACE');
  return new pg.Pool({
    connectionString,
    max: poolMax,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000,
    ...rest,
    onConnect: async (client) => {
      if (role) await client.query(`SET ROLE "${role}"`);
      await client.query(`SET search_path TO "${schema}",extensions,public,pg_temp`);
      if (onConnect) await onConnect(client);
    },
  });
}
export async function transaction(pool, fn) {
  const db = await pool.connect();
  try {
    await db.query('BEGIN');
    const value = await fn(db);
    await db.query('COMMIT');
    return value;
  } catch (e) {
    await db.query('ROLLBACK');
    throw e;
  } finally {
    db.release();
  }
}
export const one = async (db, sql, args = []) => (await db.query(sql, args)).rows[0];
export async function event(db, type, aggregateId, payload, userIds = []) {
  await db.query(
    'INSERT INTO outbox_events(type,aggregate_id,payload,user_ids) VALUES($1,$2,$3,$4)',
    [type, aggregateId, payload, userIds],
  );
}
export async function audit(db, actor, action, target, details = {}) {
  await db.query('INSERT INTO audit_logs(actor_id,action,target_id,details) VALUES($1,$2,$3,$4)', [
    actor,
    action,
    target,
    details,
  ]);
}
