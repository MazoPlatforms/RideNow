import { BufferJSON, initAuthCreds, proto } from '@whiskeysockets/baileys';
import { createHash } from 'node:crypto';
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
    typeof creds.me?.id !== 'string' ||
    !buffers.every((b) => Buffer.isBuffer(b) && b.length === 32) ||
    !Buffer.isBuffer(creds.signedPreKey?.signature)
  ) {
    throw Object.assign(new Error('A paired Baileys credential export is required'), {
      code: 'WA_AUTH_CREDS_NOT_PAIRED',
    });
  }
  return {
    creds,
    keys: bundle.creds ? (bundle.keys ?? {}) : {},
    hasKeyBundle: Boolean(bundle.creds && Object.hasOwn(bundle, 'keys')),
  };
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
    if (!config.WA_AUTH_CREDS) return existing ?? initAuthCreds();

    const seedHash = createHash('sha256').update(config.WA_AUTH_CREDS).digest('hex');
    const imported = await one(
      db,
      "SELECT value FROM system_state WHERE key='whatsapp.auth_seed_sha256'",
    );
    // Render environment secrets are an explicit seed. Import only when that seed changes;
    // otherwise always restore the database session, which may contain newer Signal keys.
    if (existing && imported?.value === seedHash) return existing;

    const seed = parseWhatsAppSeed(config.WA_AUTH_CREDS);
    for (const [type, entries] of Object.entries(seed.keys)) {
      if (!entries || typeof entries !== 'object')
        throw Object.assign(new Error('Invalid auth keys'), { code: 'WA_AUTH_KEYS_INVALID' });
    }

    if (existing) {
      const previous = await db.query(
        "SELECT id,value FROM gateway_credentials WHERE id NOT LIKE 'backup:%'",
      );
      for (const row of previous.rows)
        await db.query(
          'INSERT INTO gateway_credentials(id,value) VALUES($1,$2) ON CONFLICT(id) DO NOTHING',
          [`backup:${seedHash.slice(0, 16)}:${row.id}`, row.value],
        );
    }
    if (seed.hasKeyBundle)
      await db.query("DELETE FROM gateway_credentials WHERE id <> 'creds' AND id NOT LIKE 'backup:%'");
    await write(db, 'creds', seed.creds);
    for (const [type, entries] of Object.entries(seed.keys))
      for (const [id, value] of Object.entries(entries))
        if (value) await write(db, `${type}:${id}`, value);
    await db.query(
      "INSERT INTO system_state(key,value) VALUES('whatsapp.auth_seed_sha256',$1::jsonb) ON CONFLICT(key) DO UPDATE SET value=$1::jsonb,updated_at=now()",
      [JSON.stringify(seedHash)],
    );
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
