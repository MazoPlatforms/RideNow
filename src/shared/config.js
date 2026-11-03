import { z } from 'zod';
import { postgresConnectionString, redisConnectionString } from './connections.js';
export function loadConfig(env = process.env) {
  const schema = z
    .object({
      NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
      PORT: z.coerce.number().int().positive().default(3000),
      DATABASE_URL: z.string().min(1).transform(postgresConnectionString),
      REDIS_URL: z.string().default('redis://localhost:6379').transform(redisConnectionString),
      JWT_SECRET: z.string().min(32),
      OTP_PEPPER: z.string().min(32),
      ENCRYPTION_KEY: z.string().regex(/^[a-f\d]{64}$/i),
      CORS_ORIGINS: z.string().default('http://localhost:5173'),
      OTP_PROVIDER: z.enum(['database', 'baileys']).default('database'),
      MAP_GRAPH_PATH: z.string().default('data/kattankudy-routing.json'),
      MAP_MANIFEST_PATH: z.string().default('data/kattankudy-routing-manifest.json'),
      MAP_CATEGORY_APPROVALS: z.string().default(''),
      FCM_ENABLED: z.enum(['true', 'false']).default('false'),
    })
    .passthrough();
  // Production always sends OTPs through WhatsApp; environment values cannot
  // accidentally select the development database delivery adapter.
  const c = schema.parse(
    env.NODE_ENV === 'production' ? { ...env, OTP_PROVIDER: 'baileys' } : env,
  );
  if (
    c.NODE_ENV === 'production' &&
    !c.CORS_ORIGINS.split(',').every((origin) => {
        try {
          return new URL(origin).protocol === 'https:';
        } catch {
          return false;
        }
      })
  )
    throw new Error('Production requires HTTPS origins');
  if (c.NODE_ENV === 'production' && !c.S3_BUCKET)
    throw new Error('Production requires private S3 storage');
  return c;
}
