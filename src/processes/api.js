import { createServer } from 'node:http';
import { runtime } from '../shared/runtime.js';
import { createApp } from '../app.js';
import { attachRealtime } from '../realtime.js';
const deps = await runtime(),
  { app, auth } = createApp(deps),
  server = createServer(app);
const realtime = await attachRealtime(server, { ...deps, auth });
server.requestTimeout = 30000;
server.headersTimeout = 35000;
server.listen(deps.config.PORT, () =>
  deps.logger.info({ port: deps.config.PORT }, 'API listening'),
);
let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  const timeout = setTimeout(() => process.exit(1), 15000);
  timeout.unref();
  await realtime.close();
  await new Promise((resolve) => server.close(resolve));
  deps.storage.close();
  await Promise.all([deps.pool.end(), deps.redis.quit()]);
  clearTimeout(timeout);
}
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
