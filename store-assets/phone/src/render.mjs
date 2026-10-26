/**
 * Renders the six 1080×1920 plates at 2× and writes src/out/*@2x.png.
 *
 *   node src/render.mjs            # all plates
 *   node src/render.mjs 01 03      # selected plates
 *
 * Playwright is resolved from PLAYWRIGHT_DIR, the local project, the npx cache,
 * or a normal node_modules install, in that order. Chromium comes from the
 * Playwright browser cache (~/.cache/ms-playwright).
 */

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import os from 'node:os';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(ROOT, 'out');
const require = createRequire(import.meta.url);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ttf': 'font/ttf',
  '.woff2': 'font/woff2',
  '.json': 'application/json',
};

function findPlaywright() {
  const npxRoot = path.join(os.homedir(), '.npm', '_npx');
  const npx = fs.existsSync(npxRoot)
    ? fs.readdirSync(npxRoot).map((d) => path.join(npxRoot, d, 'node_modules'))
    : [];
  const candidates = [process.env.PLAYWRIGHT_DIR, ROOT, ...npx].filter(Boolean);
  for (const base of candidates) {
    try {
      return require.resolve('playwright', { paths: [base] });
    } catch {
      /* try the next candidate */
    }
  }
  try {
    return require.resolve('playwright');
  } catch {
    throw new Error('Playwright not found. Set PLAYWRIGHT_DIR to a node_modules containing playwright.');
  }
}

function serve(root, port) {
  const server = http.createServer((req, res) => {
    const url = decodeURIComponent((req.url || '/').split('?')[0]);
    const rel = url === '/' ? '/compose.html' : url;
    const file = path.normalize(path.join(root, rel));
    if (!file.startsWith(root) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      res.writeHead(404);
      res.end('not found');
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => resolve(server));
  });
}

const wanted = process.argv.slice(2);
const { chromium } = await import(pathToFileURL(findPlaywright()).href).then((m) => m.default ?? m);
const { PLATES } = await import(pathToFileURL(path.join(ROOT, 'plates.mjs')).href);

fs.mkdirSync(OUT, { recursive: true });
const PORT = Number(process.env.PORT || 4173);
const server = await serve(ROOT, PORT);
const browser = await chromium.launch({ args: ['--force-color-profile=srgb'] });
const metrics = {};

try {
  const page = await browser.newPage({
    viewport: { width: 1080, height: 1920 },
    deviceScaleFactor: 2,
  });

  for (const plate of PLATES) {
    if (wanted.length && !wanted.includes(plate.id)) continue;

    await page.goto(`http://127.0.0.1:${PORT}/compose.html?plate=${plate.id}`, { waitUntil: 'load' });
    await page.waitForFunction(() => window.__ready === true, null, { timeout: 20000 });
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(120);

    const m = await page.evaluate(() => window.__metrics);
    metrics[plate.id] = m;

    const problems = [];
    if (m.canvas.w !== 1080 || m.canvas.h !== 1920) problems.push('canvas size');
    if (m.supportOverflow > 0) problems.push(`support overflow ${m.supportOverflow}px`);
    if (m.headlineOverflow > 0) problems.push(`headline overflow ${m.headlineOverflow}px`);
    if (m.screenNatural.w !== null && m.screenNatural.w !== 1080) problems.push('screenshot natural width');
    if (m.device.x < 40 || m.device.x + m.device.w > 1040) problems.push('device outside 40px side margin');
    if (m.device.bottom > 1876) problems.push(`device bottom ${m.device.bottom}`);
    if (!m.fonts.newsreader || !m.fonts.inter) problems.push('fonts not loaded');

    const file = path.join(OUT, `${plate.id}@2x.png`);
    await page.screenshot({ path: file, animations: 'disabled' });

    console.log(
      `${plate.id}  frame ${m.frame.w.toFixed(0)}×${m.frame.h.toFixed(0)}  ` +
        `headline ${m.headline.h.toFixed(0)}px  device bottom ${m.device.bottom.toFixed(0)}  ` +
        (problems.length ? `⚠ ${problems.join(', ')}` : 'ok')
    );
  }
} finally {
  await browser.close();
  await new Promise((res) => server.close(res));
}

fs.writeFileSync(path.join(OUT, 'metrics.json'), JSON.stringify(metrics, null, 2));
console.log(`\nwrote ${Object.keys(metrics).length} plate(s) to ${path.relative(process.cwd(), OUT)}`);
