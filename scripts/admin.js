import { createInterface } from 'node:readline/promises';
import { randomBytes } from 'node:crypto';
import bcrypt from 'bcryptjs';
import * as OTPAuth from 'otpauth';
import { loadConfig } from '../src/shared/config.js';
import { database, transaction, one, audit } from '../src/shared/db.js';
import { encrypt } from '../src/shared/crypto.js';
import { normalizePhone } from '../src/modules/auth.js';
const config = loadConfig(),
  pool = database(config.DATABASE_URL);
const phone = normalizePhone(process.argv[2] ?? '');
// Supply the password using stdin or ADMIN_PASSWORD, never a command-line argument.
let password = process.env.ADMIN_PASSWORD;
if (!password) {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  password = await rl.question('Admin password (12+ characters; input is visible): ');
  rl.close();
}
if (password.length < 12 || password.length > 128)
  throw new Error('Password length must be 12–128');
const secret = new OTPAuth.Secret({ size: 20 }),
  hashed = await bcrypt.hash(password, 12);
try {
  const user = await transaction(pool, async (db) => {
    const u = await one(
      db,
      'INSERT INTO users(phone,name,roles,password_hash,totp_secret) VALUES($1,$2,$3,$4,$5) RETURNING id',
      [phone, 'Administrator', ['ADMIN'], hashed, encrypt(secret.base32, config.ENCRYPTION_KEY)],
    );
    await audit(db, u.id, 'ADMIN_BOOTSTRAP', u.id);
    return u;
  });
  process.stdout.write(
    `Admin ID: ${user.id}\nTOTP enrollment secret (save securely): ${secret.base32}\n`,
  );
} finally {
  await pool.end();
}
