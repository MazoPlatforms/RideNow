import { spawn } from 'node:child_process';

// One free web instance owns all three processes. Persistent state stays in PostgreSQL/R2.
export function supervise(services, { shutdownMs = 20000, env = process.env } = {}) {
  const children = new Set();
  let stopping = false, result = 0, timer;
  let resolve;
  const done = new Promise((r) => { resolve = r; });
  const finish = () => {
    if (!stopping || children.size) return;
    clearTimeout(timer);
    process.off('SIGTERM', stop);
    process.off('SIGINT', stop);
    resolve(result);
  };
  function stop(failed = false) {
    if (stopping) return;
    stopping = true;
    result = failed === true ? 1 : 0;
    timer = setTimeout(() => {
      result = 1;
      for (const child of children) child.kill('SIGKILL');
    }, shutdownMs);
    for (const child of children) child.kill('SIGTERM');
    finish();
  }
  process.on('SIGTERM', stop);
  process.on('SIGINT', stop);
  for (const service of services) {
    if (stopping) break;
    const child = spawn(process.execPath, service.args, {
      env, stdio: 'inherit',
    });
    children.add(child);
    // Do not print exception objects: configuration failures may contain credentials.
    child.on('error', () => {
      process.stderr.write(`Pilot process could not start: ${service.name}\n`);
      stop(true);
    });
    child.on('close', () => {
      children.delete(child);
      if (!stopping) {
        process.stderr.write(`Pilot process stopped: ${service.name}\n`);
        stop(true);
      }
      finish();
    });
  }
  return { done, stop };
}

if (import.meta.main) {
  const env = { ...process.env, DB_POOL_MAX: process.env.DB_POOL_MAX ?? '4' };
  const services = ['worker', 'whatsapp', 'api'].map((name) => ({
    name, args: [`src/processes/${name}.js`],
  }));
  process.exitCode = await supervise(services, { env }).done;
}
