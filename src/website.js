import express from 'express';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../website/', import.meta.url));

// Serve the supplied website without rewriting or generating any frontend files.
export function mountWebsite(app) {
  const redirects = new Map(
    readFileSync(join(root, '_redirects'), 'utf8').trim().split(/\r?\n/)
      .filter((line) => line && !line.startsWith('#'))
      .map((line) => {
        const [from, to, status] = line.trim().split(/\s+/);
        return [from, { to, status: Number(status) }];
      }),
  );
  app.use((req, res, next) => {
    if (req.method !== 'GET' && req.method !== 'HEAD') return next();
    const redirect = redirects.get(req.path);
    if (!redirect) return next();
    const query = req.originalUrl.includes('?') ? req.originalUrl.slice(req.originalUrl.indexOf('?')) : '';
    res.redirect(redirect.status, redirect.to + query);
  });
  for (const directory of ['assets', 'styles', 'src', 'content'])
    app.use(`/${directory}`, express.static(join(root, directory), {
      dotfiles: 'deny', index: false, maxAge: '1h',
    }));
  app.get(['/robots.txt', '/sitemap.xml'], (req, res) =>
    res.sendFile(join(root, req.path.slice(1))),
  );
  for (const file of readdirSync(root, { recursive: true, withFileTypes: true })) {
    if (!file.isFile() || file.name !== 'index.html') continue;
    const path = join(file.parentPath, file.name);
    const directory = relative(root, dirname(path)).split(sep).join('/');
    if (!directory) {
      app.get(['/', '/index.html'], (req, res) => res.sendFile(path));
    } else {
      app.get(`/${directory}`, (req, res, next) => {
        if (req.path.endsWith('/')) return next();
        const query = req.originalUrl.includes('?') ? req.originalUrl.slice(req.originalUrl.indexOf('?')) : '';
        res.redirect(301, `/${directory}/${query}`);
      });
      app.get([`/${directory}/`, `/${directory}/index.html`], (req, res) => res.sendFile(path));
    }
  }
  app.use((req, res, next) => {
    if (!['GET', 'HEAD'].includes(req.method) ||
        /^\/(?:v1|health|socket\.io)(?:\/|$)/.test(req.path) || req.path === '/openapi.json')
      return next();
    res.status(404).sendFile(join(root, '404.html'));
  });
}
