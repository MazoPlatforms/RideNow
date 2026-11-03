import { runner } from 'node-pg-migrate';
import { postgresConnectionString, isSupabaseHost } from '../src/shared/connections.js';
const connectionString = postgresConnectionString(process.env.DATABASE_URL);
if (isSupabaseHost(new URL(connectionString).hostname)) {
  await import('./install-private-schema.js');
} else {
  await runner({
    databaseUrl: connectionString,
    dir: 'migrations',
    direction: 'up',
    migrationsTable: 'schema_migrations',
    count: Infinity,
    checkOrder: true,
  });
}
