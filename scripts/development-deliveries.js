import { loadConfig } from '../src/shared/config.js';
import { database } from '../src/shared/db.js';
import { decrypt } from '../src/shared/crypto.js';
const config = loadConfig();
if (config.NODE_ENV === 'production' || config.OTP_PROVIDER !== 'database')
  throw new Error('Development delivery reader disabled');
const pool = database(config.DATABASE_URL);
try {
  const rows = (
    await pool.query(
      "SELECT j.payload,c.expires_at FROM jobs j JOIN otp_challenges c ON c.id=(j.payload->>'challengeId')::uuid WHERE j.kind='otp' AND c.used_at IS NULL AND c.expires_at>now() ORDER BY c.created_at DESC LIMIT 10",
    )
  ).rows;
  for (const r of rows)
    console.log({
      challengeId: r.payload.challengeId,
      code: decrypt(r.payload.encryptedCode, config.ENCRYPTION_KEY),
      expiresAt: r.expires_at,
    });
} finally {
  await pool.end();
}
