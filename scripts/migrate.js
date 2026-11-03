import { runner } from 'node-pg-migrate';
import { postgresConnectionString } from '../src/shared/connections.js';
await runner({
  databaseUrl: postgresConnectionString(process.env.DATABASE_URL),
  dir: 'migrations',
  direction: 'up',
  migrationsTable: 'schema_migrations',
  count: Infinity,
  checkOrder: true,
});
