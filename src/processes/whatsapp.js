import makeWASocketExport, {
  initAuthCreds,
  BufferJSON,
  proto,
  DisconnectReason,
} from '@whiskeysockets/baileys';
import pino from 'pino';
import { loadConfig } from '../shared/config.js';
import { database, one } from '../shared/db.js';
import { encrypt, decrypt } from '../shared/crypto.js';
import { claimJob, finishJob } from '../worker.js';
const config = loadConfig();
if (config.OTP_PROVIDER !== 'baileys') throw new Error('Set OTP_PROVIDER=baileys');
const pool = database(config.DATABASE_URL),
  lock = await pool.connect();
if (!(await one(lock, 'SELECT pg_try_advisory_lock(927561) AS locked')).locked)
  throw new Error('WhatsApp gateway already running');
const makeWASocket =
  typeof makeWASocketExport === 'function' ? makeWASocketExport : makeWASocketExport.default;
const logger = pino({ level: 'silent' });
const read = async (id) => {
  const row = await one(pool, 'SELECT value FROM gateway_credentials WHERE id=$1', [id]);
  return row ? JSON.parse(decrypt(row.value, config.ENCRYPTION_KEY), BufferJSON.reviver) : null;
};
const save = async (id, value) => {
  await pool.query(
    'INSERT INTO gateway_credentials(id,value) VALUES($1,$2) ON CONFLICT(id) DO UPDATE SET value=$2,updated_at=now()',
    [id, encrypt(JSON.stringify(value, BufferJSON.replacer), config.ENCRYPTION_KEY)],
  );
};
const creds = (await read('creds')) ?? initAuthCreds();
const keys = {
  async get(type, ids) {
    const values = {};
    for (const id of ids) {
      let value = await read(`${type}:${id}`);
      if (type === 'app-state-sync-key' && value)
        value = proto.Message.AppStateSyncKeyData.fromObject(value);
      values[id] = value;
    }
    return values;
  },
  async set(data) {
    for (const [type, values] of Object.entries(data))
      for (const [id, value] of Object.entries(values)) {
        if (value) await save(`${type}:${id}`, value);
        else await pool.query('DELETE FROM gateway_credentials WHERE id=$1', [`${type}:${id}`]);
      }
  },
};
let running = true,
  connected = false,
  socket,
  reconnectAt = 0;
const ready = async (value) =>
  pool.query(
    "INSERT INTO system_state(key,value) VALUES('whatsapp.ready',$1) ON CONFLICT(key) DO UPDATE SET value=$1,updated_at=now()",
    [JSON.stringify(value)],
  );
function connect() {
  socket = makeWASocket({
    auth: { creds, keys },
    logger,
    markOnlineOnConnect: false,
    syncFullHistory: false,
  });
  socket.ev.on('creds.update', () =>
    save('creds', creds).catch(() => {
      running = false;
    }),
  );
  socket.ev.on('connection.update', async (update) => {
    if (update.qr)
      process.stdout.write(`WhatsApp pairing QR payload (render locally): ${update.qr}\n`);
    if (update.connection === 'open') {
      connected = true;
      await ready(true);
    }
    if (update.connection === 'close') {
      connected = false;
      await ready(false);
      if (update.lastDisconnect?.error?.output?.statusCode === DisconnectReason.loggedOut) {
        process.stderr.write('WhatsApp logged out; operator re-pairing required.\n');
        running = false;
      } else reconnectAt = Date.now() + 5000;
    }
  });
}
process.on('SIGTERM', () => {
  running = false;
});
process.on('SIGINT', () => {
  running = false;
});
await ready(false);
connect();
try {
  while (running) {
    if (!connected && reconnectAt && Date.now() >= reconnectAt) {
      reconnectAt = 0;
      connect();
    }
    if (connected) {
      await ready(true);
      const job = await claimJob(pool, ['otp']);
      if (job) {
        try {
          const c = await one(pool, 'SELECT * FROM otp_challenges WHERE id=$1', [
            job.payload.challengeId,
          ]);
          if (c && !c.used_at && c.expires_at > Date.now()) {
            const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
            await Promise.race([
              socket.sendMessage(`${c.phone.slice(1)}@s.whatsapp.net`, {
                text: `Mazo பயணச் சேவை OTP: ${code}. 5 நிமிடங்களில் காலாவதியாகும். இதைப் பகிர வேண்டாம்.`,
              }),
              new Promise((_, reject) => {
                const t = setTimeout(() => reject(new Error('WhatsApp timeout')), 20000);
                t.unref();
              }),
            ]);
          }
          await finishJob(pool, job);
        } catch (e) {
          await finishJob(pool, job, e);
        }
      }
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
} finally {
  await ready(false);
  socket?.end();
  lock.release();
  await pool.end();
}
