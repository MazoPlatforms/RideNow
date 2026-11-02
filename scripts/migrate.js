import { runner } from 'node-pg-migrate';
await runner({
  databaseUrl: process.env.DATABASE_URL,
  dir: 'migrations',
  direction: 'up',
  migrationsTable: 'schema_migrations',
  count: Infinity,
  checkOrder: true,
});
