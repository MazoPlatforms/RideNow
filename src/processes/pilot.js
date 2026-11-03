import { spawn } from 'node:child_process';
import { X509Certificate } from 'node:crypto';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export async function prepareEnvironment(source = process.env) {
  const env = { ...source, DB_POOL_MAX: source.DB_POOL_MAX ?? '4' };
  const ca = source.SUPABASE_CA_CERT?.trim();
  if (!ca) return { env, cleanup: async () => {} };
  try { new X509Certificate(ca); }
  catch { throw new Error('SUPABASE_CA_CERT must contain a public PEM certificate'); }
  const existing = source.NODE_EXTRA_CA_CERTS
    ? await readFile(source.NODE_EXTRA_CA_CERTS, 'utf8') : '';
  const directory = await mkdtemp(join(tmpdir(), 'ridenow-ca-'));
  const cleanup = () => rm(directory, { recursive: true, force: true });
  try {
    const file = join(directory, 'supabase-ca.crt');
    await writeFile(file, `${existing}\n${ca}\n`, { mode: 0o600 });
    env.NODE_EXTRA_CA_CERTS = file;
    return { env, cleanup };
  } catch (error) {
    await cleanup();
    throw error;
  }
}

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
  // Node reads extra CAs only at process startup; prepare trust before spawning.
  const { env, cleanup } = await prepareEnvironment();
  const services = ['worker', 'whatsapp', 'api'].map((name) => ({
    name, args: [`src/processes/${name}.js`],
  }));
  try {
    process.exitCode = await supervise(services, { env }).done;
  } finally {
    await cleanup();
  }
}
