import { AppError } from './errors.js';
import { hash } from './crypto.js';
const lua = `local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n`;
export function rateLimiter(redis, config, { limit = 120, seconds = 60, prefix = 'api', identity } = {}) {
  return async (req, res, next) => {
    try {
      const value = identity ? identity(req) : req.user?.id ?? req.ip;
      const hashedIdentity = hash(`${value}:${prefix}`, config.OTP_PEPPER);
      const n = await redis.eval(lua, {
        keys: [`limit:${hashedIdentity}`],
        arguments: [String(seconds)],
      });
      if (n > limit) {
        res.set('Retry-After', String(seconds));
        throw new AppError(429, 'RATE_LIMITED');
      }
      next();
    } catch (e) {
      next(e instanceof AppError ? e : new AppError(503, 'RATE_LIMITER_UNAVAILABLE'));
    }
  };
}
export async function socketRate(redis, user) {
  const n = await redis.eval(lua, { keys: [`socket-limit:${user}`], arguments: ['60'] });
  if (n > 120) throw new AppError(429, 'RATE_LIMITED');
}
