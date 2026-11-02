import { one } from '../shared/db.js';
import { decrypt } from '../shared/crypto.js';

export async function deliverWhatsAppOtp({ pool, socket, config, job, demo = false }) {
  const challenge = await one(pool, 'SELECT * FROM otp_challenges WHERE id=$1', [
    job.payload.challengeId,
  ]);
  if (!challenge || challenge.used_at || challenge.expires_at <= Date.now())
    return { skipped: true };
  const code = decrypt(job.payload.encryptedCode, config.ENCRYPTION_KEY);
  let timer;
  try {
    const sent = await Promise.race([
      socket.sendMessage(`${challenge.phone.slice(1)}@s.whatsapp.net`, {
        text: `${demo ? 'RideNow சோதனை' : 'Mazo பயணச் சேவை'} OTP: ${code}. 5 நிமிடங்களில் காலாவதியாகும். இதைப் பகிர வேண்டாம்.`,
      }),
      new Promise((_, reject) => {
        timer = setTimeout(
          () =>
            reject(
              Object.assign(new Error('WhatsApp send timed out'), { code: 'WA_SEND_TIMEOUT' }),
            ),
          20000,
        );
        timer.unref();
      }),
    ]);
    return { skipped: false, messageId: sent?.key?.id };
  } finally {
    clearTimeout(timer);
  }
}

export function trackWhatsAppReceipts(socket) {
  const states = new Map(),
    waits = new Map();
  const record = (id, level) => {
    if (!id) return;
    states.set(id, Math.max(states.get(id) ?? 0, level));
    if (states.size > 100) states.delete(states.keys().next().value);
    if (level >= 3 && waits.has(id)) waits.get(id)();
  };
  const messages = (updates) => {
    for (const { key, update } of updates)
      if (Number(update?.status) >= 2) record(key?.id, Number(update.status));
  };
  const receipts = (updates) => {
    for (const { key, receipt } of updates)
      if (receipt?.receiptTimestamp || receipt?.readTimestamp) record(key?.id, 3);
  };
  socket.ev.on('messages.update', messages);
  socket.ev.on('message-receipt.update', receipts);
  return {
    async wait(id, timeoutMs = 60000) {
      if ((states.get(id) ?? 0) < 3)
        await new Promise((resolve) => {
          const done = () => {
            clearTimeout(timer);
            waits.delete(id);
            resolve();
          };
          const timer = setTimeout(done, timeoutMs);
          waits.set(id, done);
        });
      const status = states.get(id) ?? 0;
      return status >= 3 ? 'delivered' : status >= 2 ? 'server_acknowledged' : 'unconfirmed';
    },
    close() {
      socket.ev.off('messages.update', messages);
      socket.ev.off('message-receipt.update', receipts);
      for (const done of waits.values()) done();
    },
  };
}
