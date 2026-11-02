import { writeFile } from 'node:fs/promises';
import pino from 'pino';
import { loadConfig } from '../src/shared/config.js';
import { createApp } from '../src/app.js';
const config = loadConfig({
  DATABASE_URL: 'postgres://docs/unused',
  JWT_SECRET: 'documentation'.repeat(4),
  OTP_PEPPER: 'documentation'.repeat(4),
  ENCRYPTION_KEY: 'ab'.repeat(32),
});
const { paths } = createApp({
  config,
  pool: {},
  redis: {},
  maps: {},
  storage: {},
  logger: pino({ level: 'silent' }),
});
await writeFile(
  'docs/openapi.json',
  JSON.stringify(
    {
      openapi: '3.1.0',
      info: { title: 'Mazo Ride API', version: '1.0.0' },
      components: {
        securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } },
      },
      paths,
    },
    null,
    2,
  ) + '\n',
);
console.log(`Exported ${Object.keys(paths).length} API paths`);
