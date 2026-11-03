import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import express from 'express';
import helmet from 'helmet';
import supertest from 'supertest';
import { mountWebsite } from '../src/website.js';

const app = express();
app.use(helmet());
app.get('/health/ready', (req, res) => res.json({ status: 'ready' }));
app.get('/v1/test', (req, res) => res.json({ api: true }));
mountWebsite(app);
app.use((req, res) => res.status(404).json({ code: 'NOT_FOUND' }));
const api = supertest(app);

test('website serves supplied HTML and assets without changing their contents', async () => {
  for (const file of ['index.html', 'newsroom/the-preview/index.html', 'styles/home.css', 'src/home.mjs']) {
    const path = file === 'index.html' ? '/' : `/${file}`;
    const result = await api.get(path).expect(200);
    assert.equal(result.text, await readFile(new URL(`../website/${file}`, import.meta.url), 'utf8'));
  }
  const page = await api.get('/newsroom/the-preview/').expect(200);
  assert.equal(page.text, await readFile(new URL('../website/newsroom/the-preview/index.html', import.meta.url), 'utf8'));
  await api.get('/assets/images/ridenow-home-og.jpg').expect(200).expect('Content-Type', /image\/jpeg/);
});

test('website redirects and 404 pages preserve backend endpoints', async () => {
  await api.get('/drive?source=test').expect(301).expect('Location', '/driver/?source=test');
  await api.get('/faq/').expect(301).expect('Location', '/help/');
  await api.get('/about').expect(301).expect('Location', '/about/');
  await api.get('/about/').expect(200);
  await api.get('/health/ready').expect(200, { status: 'ready' });
  await api.get('/v1/test').expect(200, { api: true });
  await api.get('/v1/missing').expect(404, { code: 'NOT_FOUND' });
  await api.get('/health/missing').expect(404, { code: 'NOT_FOUND' });
  assert.equal((await api.get('/missing-page').expect(404)).text,
    await readFile(new URL('../website/404.html', import.meta.url), 'utf8'));
  await api.get('/scripts/build-pages.py').expect(404);
  await api.get('/README.md').expect(404);
});
