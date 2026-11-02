import { runtime } from '../shared/runtime.js';
import { workerTick } from '../worker.js';
import { createPush } from '../adapters/push.js';
const deps = await runtime();
deps.push = createPush(deps.pool, deps.config);
let running = true;
process.on('SIGTERM', () => {
  running = false;
});
process.on('SIGINT', () => {
  running = false;
});
while (running) {
  try {
    await workerTick(deps);
  } catch (err) {
    deps.logger.error({ err }, 'Worker tick failed');
  }
  if (running) await new Promise((r) => setTimeout(r, 1000));
}
deps.storage.close();
await Promise.all([deps.pool.end(), deps.redis.quit()]);
