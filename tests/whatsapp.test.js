import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { BufferJSON, initAuthCreds } from '@whiskeysockets/baileys';
import { parseWhatsAppSeed } from '../src/adapters/whatsapp-auth.js';
import { deliverWhatsAppOtp, trackWhatsAppReceipts } from '../src/adapters/whatsapp-otp.js';
import { encrypt } from '../src/shared/crypto.js';

export function testCredentials() {
  return {
    ...initAuthCreds(),
    registered: true,
    me: { id: '94770000001@s.whatsapp.net', name: 'Synthetic test account' },
  };
}
test('paired Baileys JSON restores binary keys without exposing malformed secrets', () => {
  const creds = testCredentials(),
    seed = parseWhatsAppSeed(JSON.stringify(creds, BufferJSON.replacer));
  assert(seed.creds.noiseKey.private.equals(creds.noiseKey.private));
  // QR-paired Baileys sessions may retain registered=false while me.id is present.
  assert.equal(
    parseWhatsAppSeed(JSON.stringify({ ...creds, registered: false }, BufferJSON.replacer)).creds.me
      .id,
    creds.me.id,
  );
  const wrapped = parseWhatsAppSeed(
    JSON.stringify(
      { creds, keys: { session: { example: Buffer.from('session-key') } } },
      BufferJSON.replacer,
    ),
  );
  assert(Buffer.isBuffer(wrapped.keys.session.example));
  for (const input of [
    'private-invalid-content',
    '{}',
    JSON.stringify(initAuthCreds(), BufferJSON.replacer),
  ]) {
    assert.throws(
      () => parseWhatsAppSeed(input),
      (e) => e.code.startsWith('WA_AUTH_CREDS_') && !e.message.includes(input),
    );
  }
});
test('one OTP job produces exactly one provider send and skips expired challenges', async () => {
  const config = { ENCRYPTION_KEY: 'ab'.repeat(32) },
    job = {
      payload: { challengeId: 'test', encryptedCode: encrypt('123456', config.ENCRYPTION_KEY) },
    },
    messages = [];
  const socket = {
    sendMessage: async (jid, body) => {
      messages.push({ jid, body });
      return { key: { id: 'message-one' } };
    },
  };
  const pool = {
    query: async () => ({
      rows: [{ phone: '+94770000001', expires_at: new Date(Date.now() + 60000) }],
    }),
  };
  const result = await deliverWhatsAppOtp({ pool, socket, config, job, demo: true });
  assert.equal(result.messageId, 'message-one');
  assert.equal(messages.length, 1);
  assert.equal(messages[0].jid, '94770000001@s.whatsapp.net');
  assert(messages[0].body.text.includes('123456'));
  pool.query = async () => ({ rows: [{ expires_at: new Date(Date.now() - 1) }] });
  assert.deepEqual(await deliverWhatsAppOtp({ pool, socket, config, job }), { skipped: true });
  assert.equal(messages.length, 1);
});
test('server acknowledgement is distinguished from device delivery; early receipts are retained', async () => {
  const socket = { ev: new EventEmitter() },
    tracker = trackWhatsAppReceipts(socket);
  try {
    socket.ev.emit('messages.update', [{ key: { id: 'server' }, update: { status: 2 } }]);
    assert.equal(await tracker.wait('server', 5), 'server_acknowledged');
    socket.ev.emit('message-receipt.update', [
      { key: { id: 'delivered' }, receipt: { receiptTimestamp: 1 } },
    ]);
    assert.equal(await tracker.wait('delivered', 5), 'delivered');
    const waiting = tracker.wait('later', 100);
    socket.ev.emit('messages.update', [{ key: { id: 'later' }, update: { status: 3 } }]);
    assert.equal(await waiting, 'delivered');
    assert.equal(await tracker.wait('unknown', 5), 'unconfirmed');
  } finally {
    tracker.close();
  }
});

test('raw provider acknowledgements are retained when application events are buffered', async () => {
  const socket = { ev: new EventEmitter(), ws: new EventEmitter() },
    tracker = trackWhatsAppReceipts(socket);
  try {
    socket.ws.emit('CB:ack,class:message', { attrs: { id: 'raw-server' } });
    assert.equal(await tracker.wait('raw-server', 5), 'server_acknowledged');
    socket.ws.emit('CB:receipt', { attrs: { id: 'raw-device' } });
    assert.equal(await tracker.wait('raw-device', 5), 'delivered');
    socket.ws.emit('CB:ack,class:message', { attrs: { id: 'rejected', error: '479' } });
    assert.equal(await tracker.wait('rejected', 5), 'rejected');
    socket.ws.emit('CB:receipt', { attrs: { id: 'needs-retry', type: 'retry' } });
    assert.equal(await tracker.wait('needs-retry', 5), 'unconfirmed');
  } finally {
    tracker.close();
  }
});
