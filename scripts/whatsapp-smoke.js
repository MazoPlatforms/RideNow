import { randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { writeFile } from 'node:fs/promises';
import pino from 'pino';
import { createClient } from 'redis';
import makeWASocketExport, {
  DisconnectReason,
  fetchLatestBaileysVersion,
} from '@whiskeysockets/baileys';
import { loadConfig } from '../src/shared/config.js';
import { database, one } from '../src/shared/db.js';
import { decrypt } from '../src/shared/crypto.js';
import { createMaps } from '../src/adapters/maps.js';
import { createWhatsAppAuth } from '../src/adapters/whatsapp-auth.js';
import { deliverWhatsAppOtp, trackWhatsAppReceipts } from '../src/adapters/whatsapp-otp.js';
import { createApp } from '../src/app.js';
import { claimJob, finishJob } from '../src/worker.js';

// libsignal can print session key objects through console even when Pino is silent.
// This isolated process emits only the explicit, sanitized progress/report below.
for (const method of ['log', 'info', 'warn', 'error', 'debug', 'trace', 'dir', 'table'])
  console[method] = () => {};
const report = {
  startedAt: new Date().toISOString(),
  credentialsImported: false,
  connected: false,
  sendAccepted: false,
  delivery: 'not_attempted',
  otpVerification: false,
  otpReplayRejected: false,
  sessionRevoked: false,
};
let pool, redis, server, socket, tracker, waAuth;
function check(condition, code) {
  if (!condition) throw Object.assign(new Error(code), { code });
}
function mask(value) {
  if (process.env.GITHUB_ACTIONS === 'true') process.stdout.write(`::add-mask::${value}\n`);
}
function progress(stage) {
  process.stdout.write(JSON.stringify({ stage }) + '\n');
}
const makeWASocket =
  typeof makeWASocketExport === 'function' ? makeWASocketExport : makeWASocketExport.default;
async function connect(auth) {
  const { version } = await fetchLatestBaileysVersion({ timeout: 10000 });
  for (let attempt = 0; attempt < 2; attempt++) {
    const client = makeWASocket({
      auth: auth.state,
      logger: pino({ level: 'silent' }),
      version,
      markOnlineOnConnect: false,
      syncFullHistory: false,
      connectTimeoutMs: 30000,
    });
    client.ev.on('creds.update', () =>
      auth.saveCreds().catch(() => client.end(new Error('WA_AUTH_SAVE_FAILED'))),
    );
    try {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(
          () =>
            finish(Object.assign(new Error('Connection timeout'), { code: 'WA_CONNECT_TIMEOUT' })),
          45000,
        );
        function finish(error) {
          clearTimeout(timer);
          client.ev.off('connection.update', update);
          error ? reject(error) : resolve();
        }
        function update(value) {
          if (value.qr)
            finish(
              Object.assign(new Error('Imported session requires pairing'), {
                code: 'WA_PAIRING_REQUIRED',
              }),
            );
          else if (value.connection === 'open') finish();
          else if (value.connection === 'close') {
            const status = value.lastDisconnect?.error?.output?.statusCode;
            finish(
              Object.assign(new Error('WhatsApp connection closed'), {
                code:
                  status === DisconnectReason.loggedOut
                    ? 'WA_SESSION_LOGGED_OUT'
                    : 'WA_CONNECT_CLOSED',
                status,
              }),
            );
          }
        }
        client.ev.on('connection.update', update);
      });
      return client;
    } catch (error) {
      client.end(undefined);
      await auth.flush();
      if (error.status !== DisconnectReason.restartRequired || attempt === 1) throw error;
    }
  }
}
try {
  check(process.env.WA_AUTH_CREDS?.trim(), 'WA_AUTH_CREDS_MISSING');
  const digits = (process.env.WA_TEST_RECIPIENT ?? '').replace(/^\+/, '');
  check(/^947\d{8}$/.test(digits), 'WA_TEST_RECIPIENT_INVALID');
  mask(digits);
  mask(`+${digits}`);
  const url = process.env.DATABASE_URL;
  check(url && new URL(url).pathname.endsWith('_test'), 'ISOLATED_TEST_DATABASE_REQUIRED');
  const config = loadConfig({
    ...process.env,
    NODE_ENV: 'test',
    OTP_PROVIDER: 'baileys',
    JWT_SECRET: randomBytes(32).toString('hex'),
    OTP_PEPPER: randomBytes(32).toString('hex'),
    ENCRYPTION_KEY: randomBytes(32).toString('hex'),
  });
  pool = database(url);
  check(
    !(await one(pool, 'SELECT 1 FROM gateway_credentials LIMIT 1')),
    'FRESH_TEST_DATABASE_REQUIRED',
  );
  waAuth = await createWhatsAppAuth(pool, config);
  delete process.env.WA_AUTH_CREDS;
  delete config.WA_AUTH_CREDS;
  report.credentialsImported = true;
  progress('credentials_imported');
  socket = await connect(waAuth);
  report.connected = true;
  tracker = trackWhatsAppReceipts(socket);
  progress('whatsapp_connected');
  await pool.query(
    "INSERT INTO system_state(key,value) VALUES('whatsapp.ready','true') ON CONFLICT(key) DO UPDATE SET value='true',updated_at=now()",
  );
  redis = createClient({ url: config.REDIS_URL });
  redis.on('error', () => {});
  await redis.connect();
  const maps = await createMaps(config),
    { app } = createApp({
      pool,
      redis,
      config,
      maps,
      storage: {},
      logger: pino({ level: 'silent' }),
    });
  server = createServer(app);
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}`;
  const requested = await fetch(base + '/v1/auth/otp/request', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone: `+${digits}` }),
  });
  check(requested.status === 202, 'OTP_REQUEST_FAILED');
  const challenge = await requested.json(),
    job = await claimJob(pool, ['otp']);
  check(job?.payload.challengeId === challenge.challengeId, 'OTP_QUEUE_FAILED');
  const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  mask(code);
  // Exactly one send attempt. Timeout/failure never automatically sends another OTP.
  const sent = await deliverWhatsAppOtp({ pool, socket, config, job, demo: true });
  check(!sent.skipped && sent.messageId, 'WA_SEND_NOT_ACCEPTED');
  await finishJob(pool, job);
  report.sendAccepted = true;
  progress('otp_send_accepted');
  const delivery = tracker.wait(sent.messageId, 60000);
  const verified = await fetch(base + '/v1/auth/otp/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ challengeId: challenge.challengeId, code }),
  });
  check(verified.status === 200, 'OTP_VERIFICATION_FAILED');
  const session = await verified.json();
  mask(session.accessToken);
  mask(session.refreshToken);
  report.otpVerification = true;
  const replay = await fetch(base + '/v1/auth/otp/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ challengeId: challenge.challengeId, code }),
  });
  check(replay.status === 401, 'OTP_REPLAY_NOT_REJECTED');
  report.otpReplayRejected = true;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  check((await fetch(base + '/v1/me', { headers })).status === 200, 'AUTHENTICATED_REQUEST_FAILED');
  check(
    (await fetch(base + '/v1/auth/logout', { method: 'POST', headers })).status === 200,
    'LOGOUT_FAILED',
  );
  check((await fetch(base + '/v1/me', { headers })).status === 401, 'SESSION_NOT_REVOKED');
  report.sessionRevoked = true;
  report.delivery = await delivery;
  progress(
    report.delivery === 'delivered'
      ? 'device_delivery_acknowledged'
      : 'device_delivery_not_confirmed',
  );
  check(report.delivery === 'delivered', 'WA_DEVICE_DELIVERY_UNCONFIRMED');
  report.result = 'passed';
} catch (error) {
  report.result = 'failed';
  report.error = /^[A-Z][A-Z0-9_]{2,80}$/.test(error.code ?? '') ? error.code : 'WA_SMOKE_FAILED';
  if (Number.isInteger(error.status)) report.connectionStatus = error.status;
  process.exitCode = 1;
} finally {
  tracker?.close();
  socket?.end(undefined);
  if (waAuth) await waAuth.flush();
  if (server?.listening) await new Promise((resolve) => server.close(resolve));
  if (redis?.isOpen) redis.destroy();
  if (pool) await pool.end();
  report.finishedAt = new Date().toISOString();
  await writeFile('whatsapp-smoke-report.json', JSON.stringify(report, null, 2) + '\n', {
    mode: 0o600,
  });
  process.stdout.write(JSON.stringify(report) + '\n');
}
