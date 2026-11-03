import pg from 'pg';
import { postgresConnectionString } from './connections.js';
export function database(url, options = {}) {
  return new pg.Pool({
    connectionString: postgresConnectionString(url),
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000,
    ...options,
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
