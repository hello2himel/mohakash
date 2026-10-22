# Mohakash — website

The static website for **Mohakash**, an explorer for NASA's Astronomy Picture of
the Day (APOD). It is a plain [Astro](https://astro.build) site: every page is
pre-rendered, and **all data is fetched at runtime** from the public JSON API.

No APOD data is embedded at build time.

## Routes

| Route | Page |
|---|---|
| `/` | Today — the newest picture, its explanation and the credit |
| `/archive` | Search, filter by year, jump to a date, thumbnail grid |
| `/archive/YYYY-MM-DD` | One entry: picture, explanation, credit, previous/next day |
| `/api` | API documentation |
| `/about` | What the project is and how it works |
| `/rights` | Content rights: code licence vs. image rights |
| `/feed.xml` | RSS 2.0 (proxied, see `netlify.toml`) |
| everything else | 404 |

Entry pages are addressed as `/archive/YYYY-MM-DD`. The site is a static build,
so a single shell (`/archive/entry/index.html`) is rewritten for every date by
Netlify and the record itself is loaded from the API at runtime. A newly
published entry therefore works without rebuilding the site.

## Data

Every request goes to the **same origin**:

```
/api/apod/v1/latest.json
/api/apod/v1/archive-index.json
/api/apod/v1/archive/<year>.json
/api/apod/v1/status.json
```

Netlify proxies `/api/apod/v1/*` to the published API on GitHub Pages
(`https://hello2himel.github.io/mohakash/api/v1/…`) — see `../netlify.toml`.
The GitHub Pages URL is only *displayed* on the API page, it is never fetched
from by the site.

* `latest.json` and `status.json` are requested with `cache: 'no-cache'` so
  they are revalidated on every visit.
* Yearly files use the default HTTP cache.
* The last successful `latest.json` and `archive-index.json` payloads are kept
  in `localStorage`, so a briefly unavailable API still shows the saved copy
  behind a status banner instead of an empty page.

## Local development

```bash
npm install
npm run dev      # http://localhost:4321
```

`npm run dev` does **not** proxy `/api/apod/v1/*`. Use a local static server
that serves `dist/` and proxies the API folder, or point the site at a running
copy of the API.

## Build, preview and test

```bash
npm run build    # static build into dist/
npm run preview  # serve dist/ locally
npm test         # vitest (unit tests for the pure logic in src/lib/)
npm run check    # astro check (TypeScript + template diagnostics)
```

## Layout

```
src/
  components/   design-system components (buttons, chips, frames, rows, banner…)
  layouts/      the document shell
  lib/          pure logic: api client, cache, formatting, archive, media rules
  pages/        the routes
  scripts/      runtime scripts (one per data-driven page)
  styles/       tokens.css (the only place with colour values) + global.css
tests/          vitest unit tests
```

`src/lib/site.ts` re-exports `src/lib/site.generated.ts`, which is generated
from `config/site.json` at the repository root. The website is built from
`web/` alone, so the root config cannot be imported directly — the
`generate:site` script copies the values over and runs automatically before
`dev`, `build` and `test`. Change the owner, the repository or the domain in
`config/site.json` and re-run `npm run generate:site`.

## Deployment (Netlify)

The repository root contains `netlify.toml`:

* build base `web`, command `npm run build`, publish `dist`
* `/api/apod/v1/*` → the published API on GitHub Pages (200 rewrite)
* `/feed.xml` → the published feed (200 rewrite)
* `/archive/:date` → the static entry shell (200 rewrite)
* cache headers: `latest.json`, `status.json` and `archive-index.json` for
  five minutes (`must-revalidate`), yearly files for a day

The comment block at the top of `netlify.toml` lists the lines to change for a
different GitHub user/repository, for a GitHub **user** site (no repository
path segment) and for a custom domain.

## Licence and rights

The code is open source (see the repository licence). The pictures belong to
their creators and are **not** covered by it — the credit shown with every
entry is never removed. This is an independent project, not affiliated with or
endorsed by NASA.
