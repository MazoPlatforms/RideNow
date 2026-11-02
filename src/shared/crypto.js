import {
  randomBytes,
  createHmac,
  createCipheriv,
  createDecipheriv,
  timingSafeEqual,
} from 'node:crypto';
export const token = () => randomBytes(32).toString('base64url');
export const hash = (value, secret) => createHmac('sha256', secret).update(value).digest('hex');
export function equal(a, b) {
  const x = Buffer.from(a),
    y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}
export function encrypt(value, key) {
  const iv = randomBytes(12),
    c = createCipheriv('aes-256-gcm', Buffer.from(key, 'hex'), iv);
  return (() => {
    const body = Buffer.concat([c.update(value), c.final()]);
    return Buffer.concat([iv, c.getAuthTag(), body]).toString('base64');
  })();
}
export function decrypt(value, key) {
  const b = Buffer.from(value, 'base64'),
    c = createDecipheriv('aes-256-gcm', Buffer.from(key, 'hex'), b.subarray(0, 12));
  c.setAuthTag(b.subarray(12, 28));
  return Buffer.concat([c.update(b.subarray(28)), c.final()]).toString();
}
