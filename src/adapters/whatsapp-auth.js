import { BufferJSON, initAuthCreds, proto } from '@whiskeysockets/baileys';
import { one, transaction } from '../shared/db.js';
import { encrypt, decrypt } from '../shared/crypto.js';

export function parseWhatsAppSeed(value) {
  let bundle;
  try {
    bundle = JSON.parse(value, BufferJSON.reviver);
  } catch {
    throw Object.assign(new Error('WA_AUTH_CREDS must contain the JSON contents of creds.json'), {
      code: 'WA_AUTH_CREDS_INVALID_JSON',
    });
  }
  const creds = bundle?.creds ?? bundle;
  const buffers = [
    creds?.noiseKey?.private,
    creds?.noiseKey?.public,
    creds?.signedIdentityKey?.private,
    creds?.signedIdentityKey?.public,
    creds?.signedPreKey?.keyPair?.private,
    creds?.signedPreKey?.keyPair?.public,
  ];
  if (
    !creds?.registered ||
    typeof creds.me?.id !== 'string' ||
    !buffers.every((b) => Buffer.isBuffer(b) && b.length === 32) ||
    !Buffer.isBuffer(creds.signedPreKey?.signature)
  ) {
    throw Object.assign(new Error('A paired Baileys credential export is required'), {
      code: 'WA_AUTH_CREDS_NOT_PAIRED',
    });
  }
  return { creds, keys: bundle.creds ? (bundle.keys ?? {}) : {} };
}

export async function createWhatsAppAuth(pool, config) {
  const read = async (db, id) => {
    const row = await one(db, 'SELECT value FROM gateway_credentials WHERE id=$1', [id]);
    return row ? JSON.parse(decrypt(row.value, config.ENCRYPTION_KEY), BufferJSON.reviver) : null;
  };
  const write = (db, id, value) =>
    db.query(
      'INSERT INTO gateway_credentials(id,value) VALUES($1,$2) ON CONFLICT(id) DO UPDATE SET value=$2,updated_at=now()',
      [id, encrypt(JSON.stringify(value, BufferJSON.replacer), config.ENCRYPTION_KEY)],
    );
  const creds = await transaction(pool, async (db) => {
    await db.query('SELECT pg_advisory_xact_lock(927562)');
    const existing = await read(db, 'creds');
    if (existing) return existing; // A stale environment export must never overwrite a live session.
    if (!config.WA_AUTH_CREDS) return initAuthCreds();
    const seed = parseWhatsAppSeed(config.WA_AUTH_CREDS);
    await write(db, 'creds', seed.creds);
    for (const [type, entries] of Object.entries(seed.keys)) {
      if (!entries || typeof entries !== 'object')
        throw Object.assign(new Error('Invalid auth keys'), { code: 'WA_AUTH_KEYS_INVALID' });
      for (const [id, value] of Object.entries(entries))
        if (value) await write(db, `${type}:${id}`, value);
    }
    return seed.creds;
  });
  let pending = Promise.resolve();
  const enqueue = (fn) => {
    const operation = pending.then(fn);
    pending = operation.catch(() => {});
    return operation;
  };
  const keys = {
    async get(type, ids) {
      await pending;
      const values = {};
      for (const id of ids) {
        let value = await read(pool, `${type}:${id}`);
        if (type === 'app-state-sync-key' && value)
          value = proto.Message.AppStateSyncKeyData.fromObject(value);
        values[id] = value;
      }
      return values;
    },
    set(data) {
      return enqueue(() =>
        transaction(pool, async (db) => {
          for (const [type, values] of Object.entries(data))
            for (const [id, value] of Object.entries(values)) {
              if (value) await write(db, `${type}:${id}`, value);
              else await db.query('DELETE FROM gateway_credentials WHERE id=$1', [`${type}:${id}`]);
            }
        }),
      );
    },
  };
  return {
    state: { creds, keys },
    saveCreds: () => enqueue(() => write(pool, 'creds', creds)),
    flush: () => pending,
  };
}
