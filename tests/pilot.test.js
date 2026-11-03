import test from 'node:test';
import assert from 'node:assert/strict';
import { supervise, prepareEnvironment } from '../src/processes/pilot.js';
import { rootCertificates } from 'node:tls';
import { readFile, stat } from 'node:fs/promises';

test('pilot makes the supplied CA available before child startup and cleans it up', async () => {
  const { env, cleanup } = await prepareEnvironment({ SUPABASE_CA_CERT: rootCertificates[0] });
  const path = env.NODE_EXTRA_CA_CERTS;
  try {
    assert((await readFile(path, 'utf8')).includes(rootCertificates[0]));
    assert.equal((await stat(path)).mode & 0o777, 0o600);
    assert.equal(env.NODE_TLS_REJECT_UNAUTHORIZED, undefined);
  } finally { await cleanup(); }
  await assert.rejects(stat(path), { code: 'ENOENT' });
  await assert.rejects(prepareEnvironment({ SUPABASE_CA_CERT: 'invalid-private-value' }),
    { message: 'SUPABASE_CA_CERT must contain a public PEM certificate' });
});

test('pilot stops siblings and fails when a service exits unexpectedly', async () => {
  const group = supervise([
    { name: 'failure', args: ['-e', 'process.exit(2)'] },
    { name: 'sibling', args: ['-e', 'setInterval(() => {}, 1000)'] },
  ], { shutdownMs: 500 });
  assert.equal(await group.done, 1);
});

test('pilot forwards shutdown and exits without a failure', async () => {
  const group = supervise([
    { name: 'service', args: ['-e', 'setInterval(() => {}, 1000)'] },
  ], { shutdownMs: 500 });
  group.stop();
  assert.equal(await group.done, 0);
});
