/**
 * Generates `src/lib/site.generated.ts` from `config/site.json` at the
 * repository root.
 *
 * The website is built from the `web/` folder alone (that is the Netlify build
 * base), so it cannot import the root config directly. Generating the file
 * keeps the owner, the repository and the custom domain in exactly one place:
 * `config/site.json`.
 *
 * Run automatically through the `predev`, `prebuild` and `pretest` npm
 * scripts.
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const configPath = resolve(repoRoot, 'config', 'site.json');
const outputPath = resolve(here, '..', 'src', 'lib', 'site.generated.ts');

const config = JSON.parse(readFileSync(configPath, 'utf8'));

const owner = config.github?.owner ?? 'owner';
const repo = config.github?.repo ?? 'repo';
const customDomain = config.github?.custom_domain ?? null;
const pagesType = config.github?.pages_type === 'user' ? 'user' : 'project';

const pagesBase = customDomain
  ? `https://${customDomain}`.replace(/\/+$/, '')
  : pagesType === 'user'
    ? `https://${owner}.github.io`
    : `https://${owner}.github.io/${repo}`;

const apiVersion = config.api?.version ?? 'v1';
const apiBase = `${pagesBase}/api/${apiVersion}`;
const websiteUrl = (config.website?.base_url ?? pagesBase).replace(/\/+$/, '');
const sameOrigin = config.api?.same_origin_prefix ?? `/api/${apiVersion}`;

const contents = `/**
 * GENERATED FILE — do not edit.
 *
 * Source: \`config/site.json\` at the repository root.
 * Regenerate with \`npm run generate:site\` (run automatically before
 * \`dev\`, \`build\` and \`test\`).
 */

/** Base URL of the published GitHub Pages site. */
export const PAGES_BASE_URL = ${JSON.stringify(pagesBase)};

/** Absolute base URL of the published JSON API. */
export const PUBLIC_API_BASE = ${JSON.stringify(`${apiBase}/`)};

/** Absolute URL of the published RSS feed. */
export const FEED_URL = ${JSON.stringify(`${pagesBase}/feed.xml`)};

/** Same-origin path every website request uses. */
export const SAME_ORIGIN_API_BASE = ${JSON.stringify(`${sameOrigin}/`)};

/** Public URL of the website itself. */
export const WEBSITE_URL = ${JSON.stringify(websiteUrl)};

/** Public URL of the repository. */
export const REPOSITORY_URL = ${JSON.stringify(`https://github.com/${owner}/${repo}`)};

/** Public URL of the issue tracker. */
export const ISSUES_URL = ${JSON.stringify(`https://github.com/${owner}/${repo}/issues`)};
`;

mkdirSync(dirname(outputPath), { recursive: true });
const previous = (() => {
  try {
    return readFileSync(outputPath, 'utf8');
  } catch {
    return '';
  }
})();

if (previous !== contents) {
  writeFileSync(outputPath, contents, 'utf8');
  console.log(`generated ${outputPath}`);
} else {
  console.log(`${outputPath} is up to date`);
}
