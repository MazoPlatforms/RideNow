import test from 'node:test';
import assert from 'node:assert/strict';
import { supervise } from '../src/processes/pilot.js';

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
