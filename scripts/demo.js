// Development-only, independently runnable end-to-end database-backed demonstration.
import { spawn } from 'node:child_process';
const child = spawn(
  process.execPath,
  [
    '--test',
    '--test-name-pattern=instant ride completes|return trip bills',
    'tests/integration.test.js',
  ],
  { stdio: 'inherit', env: { ...process.env, NODE_ENV: 'test' } },
);
child.on('exit', (code) => (process.exitCode = code ?? 1));
