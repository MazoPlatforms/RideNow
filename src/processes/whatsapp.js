import makeWASocketExport, { DisconnectReason } from '@whiskeysockets/baileys';
import pino from 'pino';
import { loadConfig } from '../shared/config.js';
import { database, one } from '../shared/db.js';
import { createWhatsAppAuth } from '../adapters/whatsapp-auth.js';
import { deliverWhatsAppOtp } from '../adapters/whatsapp-otp.js';
import { claimJob, finishJob } from '../worker.js';
// Signal's console diagnostics can include private session keys.
for (const method of ['log', 'info', 'warn', 'error', 'debug', 'trace', 'dir', 'table'])
  console[method] = () => {};
const config = loadConfig();
if (config.OTP_PROVIDER !== 'baileys') throw new Error('Set OTP_PROVIDER=baileys');
const pool = database(config.DATABASE_URL),
  lock = await pool.connect();
if (!(await one(lock, 'SELECT pg_try_advisory_lock(927561) AS locked')).locked)
  throw new Error('WhatsApp gateway already running');
const makeWASocket =
  typeof makeWASocketExport === 'function' ? makeWASocketExport : makeWASocketExport.default;
const logger = pino({ level: 'silent' });
const auth = await createWhatsAppAuth(pool, config);
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
    auth: auth.state,
    logger,
    markOnlineOnConnect: false,
    syncFullHistory: false,
  });
  socket.ev.on('creds.update', () =>
    auth.saveCreds().catch(() => {
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
          await deliverWhatsAppOtp({ pool, socket, config, job });
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
  await auth.flush();
  lock.release();
  await pool.end();
}
