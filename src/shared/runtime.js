import pino from 'pino';
import { createClient } from 'redis';
import { loadConfig } from './config.js';
import { database } from './db.js';
import { createMaps } from '../adapters/maps.js';
import { createStorage } from '../adapters/storage.js';
export async function runtime() {
  const config = loadConfig(),
    logger = pino({
      level: config.LOG_LEVEL ?? 'info',
      redact: [
        'req.headers.authorization',
        'phone',
        'code',
        'token',
        'password',
        'totp',
        'encryptedCode',
      ],
    }),
    pool = database(config.DATABASE_URL);
  pool.on('error', (err) =>
    logger.error({ message: err.message }, 'Idle database connection failed'),
  );
  const redis = createClient({ url: config.REDIS_URL });
  redis.on('error', (err) => logger.error({ message: err.message }, 'Redis error'));
  await redis.connect();
  const maps = await createMaps(config),
    storage = createStorage(config);
  return { config, logger, pool, redis, maps, storage };
}
