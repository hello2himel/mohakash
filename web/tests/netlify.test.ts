import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { parse } from 'smol-toml';
import { describe, expect, it } from 'vitest';

/** The repository-root netlify.toml (this file lives in `web/tests/`). */
const NETLIFY_TOML = fileURLToPath(new URL('../../netlify.toml', import.meta.url));

const config = parse(readFileSync(NETLIFY_TOML, 'utf8')) as {
  build?: { base?: string; command?: string; publish?: string };
  redirects?: Array<{ from?: string; to?: string; status?: number; force?: boolean }>;
  headers?: Array<{ for?: string; values?: Record<string, string> }>;
};

const redirects = config.redirects ?? [];
const headers = config.headers ?? [];

describe('netlify.toml', () => {
  it('builds the website from the web folder', () => {
    expect(config.build?.base).toBe('web');
    expect(config.build?.command).toBe('npm run build');
    expect(config.build?.publish).toBe('dist');
  });

  it('proxies the API to the published GitHub Pages API', () => {
    const redirect = redirects.find((item) => item.from === '/api/apod/v1/*');
    expect(redirect).toBeDefined();
    expect(redirect?.to).toBe('https://hello2himel.github.io/mohakash/api/v1/:splat');
    expect(redirect?.status).toBe(200);
    expect(redirect?.force).toBe(true);
  });

  it('proxies the RSS feed', () => {
    const redirect = redirects.find((item) => item.from === '/feed.xml');
    expect(redirect).toBeDefined();
    expect(redirect?.to).toBe('https://hello2himel.github.io/mohakash/feed.xml');
    expect(redirect?.status).toBe(200);
    expect(redirect?.force).toBe(true);
  });

  it('forces the proxy rules', () => {
    const api = redirects.find((item) => item.from === '/api/apod/v1/*');
    const feed = redirects.find((item) => item.from === '/feed.xml');
    expect(api?.force).toBe(true);
    expect(feed?.force).toBe(true);
  });

  it('rewrites the entry route to the static shell', () => {
    const redirect = redirects.find((item) => item.from === '/archive/:date');
    expect(redirect).toBeDefined();
    expect(redirect?.to).toBe('/archive/entry/index.html');
    expect(redirect?.status).toBe(200);
  });

  it('caches the fresh documents briefly and the yearly files longer', () => {
    const latest = headers.find((item) => item.for === '/api/apod/v1/latest.json');
    const status = headers.find((item) => item.for === '/api/apod/v1/status.json');
    const archive = headers.find((item) => item.for === '/api/apod/v1/archive/*');

    expect(latest?.values?.['Cache-Control']).toContain('max-age=300');
    expect(latest?.values?.['Cache-Control']).toContain('must-revalidate');
    expect(status?.values?.['Cache-Control']).toContain('max-age=300');
    expect(archive?.values?.['Cache-Control']).toContain('max-age=86400');
  });

  it('documents how to change the owner, repository or domain', () => {
    const raw = readFileSync(NETLIFY_TOML, 'utf8').toLowerCase();
    expect(raw).toContain('<owner>.github.io/<repo>');
    expect(raw).toContain('user site');
    expect(raw).toContain('user-level pages');
    expect(raw).toContain('custom domain');
  });
});
