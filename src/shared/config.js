import { z } from 'zod';
export function loadConfig(env = process.env) {
  const schema = z
    .object({
      NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
      PORT: z.coerce.number().int().positive().default(3000),
      DATABASE_URL: z.string().min(1),
      REDIS_URL: z.string().default('redis://localhost:6379'),
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
  const c = schema.parse(env);
  if (
    c.NODE_ENV === 'production' &&
    (c.OTP_PROVIDER !== 'baileys' ||
      !c.CORS_ORIGINS.split(',').every((origin) => {
        try {
          return new URL(origin).protocol === 'https:';
        } catch {
          return false;
        }
      }))
  )
    throw new Error('Production requires Baileys and HTTPS origins');
  if (c.NODE_ENV === 'production' && (c.FCM_ENABLED !== 'true' || !c.S3_BUCKET))
    throw new Error('Production requires FCM and private S3 storage');
  return c;
}
