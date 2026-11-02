import { createHash } from 'node:crypto';
import { transaction, one } from './db.js';
import { encrypt, decrypt } from './crypto.js';
import { ensure } from './errors.js';
function canonical(v) {
  if (Array.isArray(v)) return v.map(canonical);
  if (v && typeof v === 'object')
    return Object.fromEntries(
      Object.keys(v)
        .sort()
        .map((k) => [k, canonical(v[k])]),
    );
  return v;
}
export async function idempotent(pool, actor, operation, key, body, fn, encryptionKey) {
  ensure(
    typeof key === 'string' && key.length >= 8 && key.length <= 128,
    400,
    'IDEMPOTENCY_KEY_REQUIRED',
  );
  const digest = createHash('sha256')
    .update(JSON.stringify(canonical(body)))
    .digest('hex');
  return transaction(pool, async (db) => {
    await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))', [
      `${actor}:${operation}:${key}`,
    ]);
    const previous = await one(
      db,
      'SELECT * FROM idempotency_records WHERE actor_id=$1 AND operation=$2 AND key=$3',
      [actor, operation, key],
    );
    if (previous) {
      ensure(previous.request_hash === digest, 409, 'IDEMPOTENCY_MISMATCH');
      return previous.response.encryptedResponse
        ? JSON.parse(decrypt(previous.response.encryptedResponse, encryptionKey))
        : previous.response;
    }
    const response = await fn(db);
    await db.query(
      'INSERT INTO idempotency_records(actor_id,operation,key,request_hash,response) VALUES($1,$2,$3,$4,$5)',
      [
        actor,
        operation,
        key,
        digest,
        encryptionKey
          ? { encryptedResponse: encrypt(JSON.stringify(response), encryptionKey) }
          : response,
      ],
    );
    return response;
  });
}
