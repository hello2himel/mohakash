<picture>
  <source media="(prefers-color-scheme: dark)" srcset="web/public/brand/mohakash-horizontal-reversed-480.png">
  <img src="web/public/brand/mohakash-horizontal-color-480.png" alt="mohakash" width="260">
</picture>


> Daily astronomy picture. Browse the NASA Astronomy Picture of the Day
> archive, read the explanations, save what you like, and put the newest
> picture on your Android home screen as a live wallpaper.

Mohakash is one repository with three connected products:

| Product | What it is | Where it runs |
|---|---|---|
| **Website** | Astro + TypeScript, responsive, accessible | Netlify |
| **Public API** | Versioned static JSON + an optional RSS feed | GitHub Pages |
| **Android app** | Kotlin, Jetpack Compose, Material 3, with a genuine `WallpaperService` | Your device |

Everything reads the same normalized data. There is no user account, no
database server, no paid hosting and no API key.

---

## 1. Architecture and data flow

```text
                     NASA APOD (science.nasa.gov)
                                |
                                |  scripts/sync_apod.py  (normalize + validate)
                                v
                    data/records/YYYY.json          <- authoritative archive (in git)
                                |
                                |  scripts/build_public_api.py
                                v
             data/api/v1/{latest,archive-index,status}.json
             data/api/v1/archive/YYYY.json
             data/feed.xml
                                |
                GitHub Actions (commit + upload-pages-artifact)
                                v
                    GitHub Pages  https://<owner>.github.io/<repo>/api/v1/...
                        ^                        ^
                        |                        |
       Netlify rewrite   |                        |  direct fetch
        (same origin)    |                        |
                +-------+--------+               |
                |                |               |
         Astro website      Android app  --->   +------+
         (netlify.app)      (GitHub Pages)   WallpaperService
```

* The **archive** in `data/records/` is the source of truth and lives in git.
* The **public API** is generated from the archive and is the only thing the
  website and the app read.
* The **website** fetches from its own origin (`/api/apod/v1/...`); Netlify
  proxies that path to GitHub Pages. The website build therefore never has to
  be re-run when the data changes.
* The **Android app** fetches straight from GitHub Pages.
* **Image binaries are never mirrored.** The API publishes links to NASA's own
  image files; the app downloads an image only when it needs it.

### Why a daily data change does not trigger a Netlify build

Scheduled data commits carry `[skip netlify]` in the commit message, which
Netlify honours. On top of that the authoritative data lives outside `web/`,
so a data refresh never touches the website source tree at all. A normal
source-code commit still triggers a standard Netlify build.

---

## 2. The NASA APOD endpoint (verified behaviour)

The legacy `https://api.nasa.gov/planetary/apod` service is scheduled to be
archived on **1 December 2026**. This project uses the current endpoint:

```text
GET https://science.nasa.gov/wp-json/wp/v2/apod-basic
```

What was checked against the live service (October 2026):

| Question | Answer |
|---|---|
| Is an API key needed? | **No.** No key is used and none is stored. |
| Does it answer without a `User-Agent`? | Yes, but a descriptive one is sent anyway. |
| What does the collection return? | A JSON **array** of records, newest first. |
| Which query parameters work? | `page` (default 1) and `per_page` (default 25, clamped to 25 by the service). |
| Does `date` / `start_date` / `end_date` work? | **No.** They are silently ignored and the newest page is returned. |
| What about `date_from` / `date_to`? | They are advertised in the endpoint's own `OPTIONS` response but currently answer `400 rest_invalid_param`. The adapter probes them once and transparently falls back to pagination. |
| How do I fetch one exact date? | `GET .../apod-basic/{YYMMDD}` — e.g. `.../apod-basic/261016` for 2026-10-16. A missing date answers `404 apod_basic_not_found`. |
| What is `url`? | The **APOD article page**, not an image. |
| What is `hdurl`? | The actual image asset (or the poster frame of a video). This is the field to download. |
| What is `basic_html`? | A complete HTML document. It is never stored. |

Response fields used: `date`, `post_id`, `title`, `permalink`, `media_type`,
`explanation`, `credit`, `copyright`, `alt`, `url`, `hdurl`.

Two traps that the adapter handles:

* **`url` is not an image.** Publishing it as an image URL would download an
  HTML page.
* **NASA's image CDN can resize on the fly.** URLs under
  `assets.science.nasa.gov/dynamicimage/` accept a `?w=` parameter, so the
  pipeline derives a 1600 px display URL, a 2400 px large URL and a 480 px
  thumbnail from one asset. Older `content/dam` assets are static files and
  are used unchanged.

Some entries from the 1990s point at NASA's generic placeholder thumbnail
rather than the original picture. Those are published with
`image_status: "placeholder"` and no image URLs, so no client ever shows the
placeholder as "today's picture".

All provider-specific parsing lives in `scripts/mohakash/nasa_adapter.py`.
The website and the Android app only ever see the project's own schema.

---

## 3. Repository structure

```text
mohakash/
├── android/                     # Kotlin app (Compose, Room, DataStore, WorkManager)
│   ├── app/
│   ├── gradle/libs.versions.toml
│   └── settings.gradle.kts
├── web/                         # Astro website
│   ├── src/{components,layouts,pages,lib,styles}/
│   ├── public/
│   ├── tests/
│   ├── astro.config.mjs
│   └── package.json
├── data/
│   ├── records/YYYY.json        # authoritative archive, one file per year
│   ├── api/v1/                  # generated public API
│   │   ├── latest.json
│   │   ├── archive-index.json
│   │   ├── status.json
│   │   └── archive/YYYY.json
│   ├── feed.xml                 # generated RSS 2.0 feed
│   └── sync-state.json          # last successful sync, source status
├── scripts/
│   ├── mohakash/                # the pipeline package
│   │   ├── config.py            # reads config/site.json
│   │   ├── schema.py            # the normalized, versioned schema
│   │   ├── nasa_adapter.py      # the only NASA-aware module
│   │   ├── normalize.py         # raw payload -> normalized record
│   │   ├── archive.py           # the on-disk archive store
│   │   └── publish.py           # public API + RSS generation
│   ├── tests/                   # pytest suite (no network needed)
│   ├── sync_apod.py             # CLI: sync / backfill / build / validate
│   ├── build_public_api.py      # CLI: rebuild the public API only
│   └── requirements.txt
├── config/site.json             # owner + repository URLs (single edit point)
├── .github/workflows/
│   ├── update-apod.yml          # scheduled sync, commit, GitHub Pages
│   └── ci.yml                   # tests for the pipeline, website and app
├── netlify.toml                 # build configuration + API proxy rules
├── CONTENT_RIGHTS.md
├── LICENSE
└── README.md
```

---

## 4. Local prerequisites

| Tool | Version used | Notes |
|---|---|---|
| Python | 3.11+ (3.12 in CI) | The pipeline only uses the standard library. `pip install -r scripts/requirements.txt` adds `pytest` and `PyYAML`. |
| Node.js | 20+ (22 in CI) | For the website. |
| JDK | 17 | For the Android app. |
| Android SDK | platform 36, build-tools 35+ | `ANDROID_HOME` or `android/local.properties`. |

---

## 5. Website

```bash
cd web
npm install
npm run dev        # http://localhost:4321
npm run build      # static output in web/dist
npm test           # unit tests
```

The site is fully static. It fetches its data in the browser from
`/api/apod/v1/...`; nothing is baked in at build time. During local
development the Astro dev server proxies `/api/apod/v1/*` to GitHub Pages (see
`web/astro.config.mjs`), so the site works locally without a Netlify account.

### Routes

| Route | Page |
|---|---|
| `/` | Today |
| `/archive` | Archive (search, year filter, jump to date, paged grid, hide videos) |
| `/archive/YYYY-MM-DD` | Entry detail |
| `/api` | API documentation |
| `/about` | About |
| `/rights` | Content rights |
| anything else | 404 |

The shell keeps the header/footer visible on desktop: the document itself does
not scroll, only the main area does. On phones and small tablets the page
scrolls normally with a **sticky header** and a **traditional footer at the end
of the document**; the footer is reached by scrolling to the bottom, so it never
overflows or eats into the reading area. Below 700px the main navigation moves
into the accessible full-screen menu.

**Share on Today.** The share button on the Today page copies the canonical
entry URL (`https://<website>/archive/YYYY-MM-DD`) for the picture currently on
screen, with a short "Copied" confirmation. It does not copy the bare `/`
address, so the recipient opens that day's picture with its explanation and
credit. The action row stays compact: icon + text for the primary *Save HD
image* and 44px icon-only buttons with tooltips for *Share*, *Original APOD
page* (using the info “i” icon) and *Browse archive*.

The Today description is a readable preview ending on a sentence (up to six
lines on desktop); **Read full explanation** opens the complete text in the
existing drawer.

**Today support card.** Under that preview sits a quiet dismissible card. Most
sessions show the Android invitation — a compact real-app screenshot, a short
“update your wallpaper with astronomy pictures daily” line and one **Download
for Android** button (direct APK). Occasionally it becomes a polite donation
note instead. It is chosen once per session, can be dismissed for 14 days, and
is never a footer ribbon or an auto-opening modal. The
[About page](web/src/pages/about.astro) explains the app in more detail.

---

## 6. Android app

```bash
cd android
./gradlew :app:assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit tests
```

> **JDK:** Gradle 8.14.3 supports JDK 17 and 21. If the system JDK is newer
> (this machine has JDK 27), point `JAVA_HOME` at a 17 or 21 installation:
> `export JAVA_HOME=/path/to/jdk-21`.

The app uses a compact floating **Launch Cradle** for navigation: **Feed · mohakash mark · Archive**. The Archive tab contains the archive and Saved as a segmented pair. The feed starts
on the latest picture; swiping up goes to older pictures and down to newer ones (the
list never wraps), and search sits in the top-left corner. Tapping a picture hides
every overlay and the bottom bar; tapping again brings them back. Archive keeps the
year/media filters and jump-to-date, and the uncropped entry view is still a tap away.

The API base URL is read from `config/site.json` at build time and turned into
a `BuildConfig` field, so there is no URL in the Kotlin sources. The app tries
the configured candidates in order (a GitHub **project** site first, then a
GitHub **user** site) and remembers the one that works.

**Settings → Advanced → API endpoint** lets you point the app at a self-hosted
server instead. The endpoint must use HTTPS and serve the same JSON files as the
public API (`latest.json`, `archive-index.json` and `archive/YYYY.json`). Changing
it clears the cached APOD metadata and the cached archive index and schedules a
refresh; saved pictures, the image cache and the current live wallpaper are kept.
The reset button returns to the built-in endpoint.

UI icons come from [Remix Icon](https://remixicon.com); the launcher icon and the
notification mark are original Mohakash artwork. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

### App Links and sharing

Public website URLs open inside the app when it is installed. The Android
manifest declares `https://mohakash.netlify.app/` and
`https://mohakash.netlify.app/archive...` as verified App Links, and
`MainActivity` maps them to the navigation graph:

| Website URL | App screen |
|---|---|
| `https://mohakash.netlify.app/` | Feed, opened on the latest picture |
| `https://mohakash.netlify.app/archive` | Archive |
| `https://mohakash.netlify.app/archive/YYYY-MM-DD` | Full entry |

The host is not hard-coded twice: Gradle turns `config/site.json`'s
`website.base_url` into the `websiteHost` manifest placeholder, and the parser
uses the same value through `BuildConfig.WEBSITE_BASE_URL`. Verification uses
[`web/public/.well-known/assetlinks.json`](web/public/.well-known/assetlinks.json),
which must contain the package name and the SHA-256 signing-certificate
fingerprint of every build users install. The GitHub release key is listed
there; update it with the Play App Signing certificate (if Play re-signs the
bundle) and the F-Droid certificate when those listings go live, then redeploy
the website. A stale fingerprint makes the links fall back to the browser.
Published builds older than the one that introduced the intent filters (v1.0.4
and earlier) cannot open the links; **1.0.5** and later signed with the same key
can.

The share button (in the feed overlay and the full entry view) shares the public
`/archive/YYYY-MM-DD` URL as plain text together with the title, date and credit. It
does not attach the picture, so the recipient always gets a link to the entry page
instead of a bare image.

Test a link on a connected device:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d https://mohakash.netlify.app/archive/2026-10-25
```

Install and enable the wallpaper:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open the app and press **Set live wallpaper** during onboarding or
**Wallpaper** on a feed picture, or pick *Settings → Wallpaper → Mohakash* in
Android's own wallpaper picker. Android asks for confirmation only the first
time; afterwards the app applies a chosen picture or a new APOD directly to the
active live wallpaper without re-opening the system picker.

The wallpaper is a genuine `WallpaperService` (not a static bitmap set through
`WallpaperManager`). A `WorkManager` job checks for a new APOD every 6 hours,
downloads the picture only when the date or image URL changed and swaps it in
atomically; it works without opening or refreshing the app, subject to Android's
battery/Doze delays. Fill/Fit is honoured by the feed, the sheet preview and by
the engine, Wi-Fi only is off by default, and *Hide videos* / *Image scaling* /
*Prefer HD* are in Settings.

---

## 7. Data pipeline commands

All commands are run from the repository root.

```bash
# Fetch the newest entry and revalidate the previous seven days
python3 scripts/sync_apod.py sync

# Build the historical archive (resumable, safe to interrupt)
python3 scripts/sync_apod.py backfill --start-year 1995 --end-year 2026

# Re-fetch years that are already stored (for corrections)
python3 scripts/sync_apod.py backfill --start-year 2020 --end-year 2021 --refresh

# Re-derive the image URLs of the stored records (no network access)
python3 scripts/sync_apod.py renormalize

# Regenerate the public API and the RSS feed from the archive
python3 scripts/sync_apod.py build
python3 scripts/build_public_api.py        # same thing, separate entry point

# Validate the archive and the generated documents (also used in CI)
python3 scripts/sync_apod.py validate

# Build the GitHub Pages artifact locally
python3 scripts/sync_apod.py assemble-site --site-root api-site

# Render netlify.toml from config/site.json (and check that it is up to date)
python3 scripts/render_netlify_config.py
python3 scripts/render_netlify_config.py --check

# Serve the built website with the Netlify rewrites, for a local check
cd web && npm run build && cd ..
python3 scripts/serve_site.py --port 4321

# Verify a deployed API
python3 scripts/verify_api.py --base-url https://<owner>.github.io/<repo>/api/v1

# Show what is stored locally
python3 scripts/sync_apod.py status

# Run the tests
python3 -m pytest scripts/tests -q
```

### Behaviour that matters

* **Idempotent.** Re-running `sync` without new data changes no file, so the
  scheduled workflow creates no commit and no deployment. The published
  timestamps are derived from the archive (`max(updated_at)`), never from the
  clock, so an unchanged archive always produces byte-identical documents.
* **Non-destructive.** A failed request never removes a stored record. A
  refresh that temporarily loses its image keeps the previous, working image.
* **Corrections are picked up.** A record whose title or explanation changed
  upstream is updated, and only the affected year file is rewritten.
* **Deduplicated.** Records are keyed by publication date.
* **Never publishes an empty archive.** `build` refuses to run without
  records, and `validate` fails loudly.
* **Bounded retries.** Transient failures are retried with exponential
  backoff; client errors are not retried at all.
* **Resumable.** The backfill finds the page it has to start at with a binary
  search and continues below the oldest day it already has, so an interrupted
  run does not start over.

### The normalized schema (version 1.0.0)

```json
{
  "schema_version": "1.0.0",
  "date": "2026-10-22",
  "title": "The First Green Star, WD J1330+6435",
  "explanation": "Plain text. Paragraphs are separated by a blank line.",
  "media_type": "image",
  "image_url": "https://…?w=1600",
  "hd_image_url": "https://…?w=2400",
  "thumbnail_url": "https://…?w=480",
  "apod_url": "https://science.nasa.gov/image-article/…",
  "copyright": "Mark A. Garlick/University of Warwick",
  "credit": "Mark A. Garlick/University of Warwick",
  "alt_text": "A star shines with a greenish hue…",
  "source": "NASA APOD",
  "source_id": "1497603",
  "source_media_type": "image",
  "image_status": "ok",
  "updated_at": "2026-10-22T12:17:44Z"
}
```

Unavailable optional values are `null` — never invented. `media_type` is one
of `image`, `video`, `other`. `image_status` is `ok`, `placeholder` or
`missing`.

---

## 8. Public API

Base URL (GitHub Pages project site):

```text
https://<owner>.github.io/<repo>/api/v1/
```

| Endpoint | Returns |
|---|---|
| `GET latest.json` | The newest record, flattened, plus `last_successful_sync`, `latest_available_date`, `record_count` and `source_status`. |
| `GET archive-index.json` | A compact index of every record, newest first. No explanations. Each entry has `date`, `title`, `media_type`, `thumbnail_url`, `image_url`, `apod_url`, `copyright` and `detail_path`. |
| `GET archive/YYYY.json` | Every full record of one calendar year, with explanations and attribution. |
| `GET status.json` | `schema_version`, `last_successful_sync`, `latest_available_date`, `archive_start_date`, `record_count`, `source_status` and a few non-sensitive diagnostics. |
| `GET /feed.xml` | RSS 2.0 with the newest 30 entries. |

Examples:

```bash
curl https://<owner>.github.io/<repo>/api/v1/latest.json
curl https://<owner>.github.io/<repo>/api/v1/archive/2026.json
curl -s https://<owner>.github.io/<repo>/api/v1/latest.json | jq -r '.title'
```

To check a deployment end to end:

```bash
python3 scripts/verify_api.py --base-url https://<owner>.github.io/<repo>/api/v1
```

It fetches every endpoint, parses the JSON and the feed, and checks that the
counts and dates agree with each other.

```js
const res = await fetch('/api/apod/v1/latest.json');   // same origin on the website
const latest = await res.json();
console.log(latest.title, latest.date);
```

The responses contain **metadata and links to the original image files**. No
image binaries are hosted by this project.

---

## 9. Owner setup

The steps in this section are for the repository owner. Everything else in
this README was implemented and verified by the coding agent.

### 9.1 Point the project at your account

Edit **one file**: `config/site.json`.

```json
{
  "github": {
    "owner": "your-github-username",
    "repo": "mohakash",
    "default_branch": "main",
    "pages_type": "project",
    "custom_domain": null
  },
  "website": { "base_url": "https://your-site.netlify.app" },
  "android": {
    "package": "mohakash.stargzr.space",
    "namespace": "app.mohakash.apod",
    "api_base_url_fallbacks": [
      "https://your-github-username.github.io/mohakash/api/v1",
      "https://your-github-username.github.io/api/v1"
    ]
  }
}
```

* `pages_type: "project"` → the API is served from
  `https://<owner>.github.io/<repo>/`.
* `pages_type: "user"` → the API is served from
  `https://<owner>.github.io/` (a user-level Pages site). The path segment is
  dropped automatically.
* `custom_domain` → set your own domain; it wins over both.

Everything else follows from that file:

| Generated from `config/site.json` | How |
|---|---|
| `netlify.toml` (the two rewrite targets) | `python3 scripts/render_netlify_config.py` |
| `web/src/lib/site.generated.ts` (the URLs shown on the site) | `npm run generate:site` in `web/` |
| The Android `BuildConfig` base URLs | read at Gradle configuration time |

Both generators also run automatically before `dev`, `build` and `test`, and CI
fails when the committed output is out of date. The only other place that may
need your attention is the `site` value in `web/astro.config.mjs` (the
canonical URL of the website itself).

### 9.2 GitHub Pages

1. Repository → **Settings → Pages → Build and deployment → Source**:
   choose **GitHub Actions**.
2. That is all. The `update-apod.yml` workflow publishes the artifact itself.
   The first run creates it.

The published artifact contains **only** the public data:

```text
api-site/
├── .nojekyll
├── api/v1/{latest,archive-index,status}.json
├── api/v1/archive/YYYY.json
└── feed.xml
```

No source code, no tests, no workflow logs.

### 9.3 Netlify

1. Add the repository to Netlify (Import an existing project).
2. Netlify reads `netlify.toml` from the repository root. It should show
   *Base directory* `web`, *Build command* `npm run build` and *Publish
   directory* `dist`. If Netlify's UI shows different values, set them to
   match.
3. Deploy. The redirect rules are part of the same file, so no extra
   configuration is needed.

### 9.4 Permissions and branch protection

The workflow needs:

| Permission | Why |
|---|---|
| `contents: write` | to commit the refreshed data |
| `pages: write` | to publish the artifact |
| `id-token: write` | required by `actions/deploy-pages` |

If the default branch is protected and blocks the bot commit, the workflow
will fail at the push step. The smallest possible fixes, in order of
preference:

1. Allow GitHub Actions to create "bot" commits on the default branch
   (branch protection → *Rules → Bypass list* → add the `github-actions`
   app), **or**
2. Exempt the `chore(apod): refresh APOD data [skip netlify]` commit from the
   required review, **or**
3. Use a GitHub App token with write access to the repository if your
   organisation forbids bot commits.

A personal access token is **not** required and should not be used.

### 9.5 Android App Links

`.well-known/assetlinks.json` is served by the website
(`web/public/.well-known/assetlinks.json`) and tells Android which signing
certificates may open `https://mohakash.netlify.app/...` links in the app. It
must list the SHA-256 certificate fingerprint of **every** build users install:

| Build source | Certificate |
|---|---|
| GitHub releases | The release key in `android/keystore/` — already listed. |
| Google Play | If Play App Signing is enabled, Google re-signs the bundle; copy the *app signing certificate* SHA-256 from Play Console → Setup → App signing and add it. |
| F-Droid | F-Droid signs with its own key; add its certificate fingerprint once the listing is live. |

After changing the file, deploy the website and confirm the links verify:

```bash
adb shell pm verify-app-links --re-verify mohakash.stargzr.space
adb shell pm get-app-links mohakash.stargzr.space
```

The host comes from `config/site.json` and the paths come from the Android
manifest; a fingerprint mismatch only makes the links open in the browser, it
does not break the website.

---

## 10. The scheduled workflow

`.github/workflows/update-apod.yml`

* **Schedule:** `17 7,13 * * *` (UTC) — twice a day. GitHub delays scheduled
  workflows, so the exact execution time is never guaranteed.
* **Manual runs:** *Actions → Update APOD data → Run workflow* with the mode
  `sync`, `backfill`, `build` or `validate`, optional `start_year` /
  `end_year` for a backfill, `refresh` to re-fetch stored records and
  `force_deploy` to publish to Pages even when nothing changed.
* **Concurrency:** one run at a time (`group: apod-data-<ref>`,
  `cancel-in-progress: false`), so an older run can never overwrite a newer
  archive.
* **Order of work:** fetch → validate → assemble the artifact → commit the
  data → publish to Pages, all inside the same workflow. The publication does
  not depend on a second workflow being triggered by the bot's push.
* **No empty commits.** If the data did not change, nothing is committed and
  Pages is not redeployed.

---

## 11. Cache behaviour and known limitations

| Resource | Cache policy |
|---|---|
| `latest.json`, `status.json` | `max-age=300, must-revalidate` at the Netlify proxy; the browser revalidates on every load. |
| `archive-index.json` | `max-age=300, must-revalidate`. |
| `archive/YYYY.json` | `max-age=86400` — historical records change rarely. |
| `feed.xml` | `max-age=3600`. |

* The website caches the last successful `latest.json` and
  `archive-index.json` in the browser so a brief API outage still shows
  something useful, with a status banner and a retry button.
* The Android app keeps the last successful metadata, the archive index and image
  in app-private storage and shows them when the network is unavailable.
* **A short mismatch is normal.** The data commit and the Pages publication
  happen in the same run, but GitHub Pages can take a minute to serve the new
  files. The website treats this as a stale-data condition, not an error.
* **Background updates are not exact.** The Android app schedules a
  6-hour `PeriodicWorkRequest` (four checks a day, so a delayed run does not skip
  an APOD). Android decides when each run actually happens: it may be delayed by
  battery optimisation, Doze, app-standby buckets or the device being in use. The
  app never promises a specific update time, and a foreground refresh happens
  whenever the app is opened and its metadata is stale.

---

## 12. Copyright and attribution

The pictures are not public domain and this project does not claim that they
are. Credits are shown exactly as supplied, are never removed, and travel with
the entry when it is saved or shared. Every entry links to its original APOD
page and image.

See [`CONTENT_RIGHTS.md`](CONTENT_RIGHTS.md) for the full text.

Mohakash is an independent project. It is **not affiliated with, sponsored
by, reviewed by or endorsed by NASA**.

Interface icons use [Remix Icon](https://remixicon.com) under the Remix Icon
License v1.0; the font files are bundled with the website. The mohakash brand
mark, launcher icon, favicon and social cards come from the project's brand
kit, and the brand wordmark is always lowercase.

---

## 13. Tests

```bash
python3 -m pytest scripts/tests -q     # data pipeline (no network required)
cd web && npm test                      # website unit tests
cd android && ./gradlew :app:testDebugUnitTest
```

Current result:

| Suite | Tests | Command |
|---|---|---|
| Data pipeline | **102 passed** | `python3 -m pytest scripts/tests -q` |
| Website | **99 passed** | `cd web && npm test` |
| Android | **202 passed** | `cd android && ./gradlew :app:testDebugUnitTest` |
| Website type check | 0 errors, 0 warnings | `cd web && npx astro check` |
| Archive validation | ok | `python3 scripts/sync_apod.py validate` |

The data-pipeline tests cover normalization against saved NASA payloads,
image-URL validation, video and placeholder records, missing credits,
upsert and deduplication, historical corrections, index and feed generation,
retry behaviour, temporary source failures, invalid JSON, idempotent runs,
interrupted backfills and the refusal to publish an empty archive. They use
saved fixtures and a fake HTTP layer, so **they never call NASA**.

The workflow tests assert the schedule, the minimal permissions, the
`[skip netlify]` marker, the absence of empty commits and the fact that the
Pages publication happens in the same workflow.

The Netlify configuration is covered by a test as well: it parses
`netlify.toml` and asserts the build base, the publish directory, both
rewrite targets, the `force` flags and the cache headers, and that the file
still matches `config/site.json`.

The website tests also validate `web/public/.well-known/assetlinks.json`: the
package name must match `config/site.json` and every listed fingerprint must be
a well-formed SHA-256 certificate fingerprint.

---

## 14. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| The website shows "Try again" and no data | The API is not reachable from the browser | Open `https://<owner>.github.io/<repo>/api/v1/latest.json` directly. If that fails, the Pages site is not published yet — run the workflow with `mode: build` and `force_deploy: true`. |
| The website 404s on `/api/apod/v1/...` | The Netlify rewrite target is wrong for your account | Check the two `to =` values in `netlify.toml` against `config/site.json`. |
| The website shows yesterday's picture | GitHub Pages has not served the new file yet | Wait a minute and reload. The site revalidates `latest.json` on every load. |
| The workflow fails at the push step | Branch protection blocks the bot commit | See §9.4. |
| The workflow fails with `400` on the date-range request | NASA's `date_from`/`date_to` are broken upstream | Nothing to do — the adapter falls back to pagination automatically. |
| The Android app shows "You're offline" | The API base URL does not match your Pages site | Fix `android.api_base_url_fallbacks` in `config/site.json` and rebuild the app. |
| The wallpaper does not change | Android deferred the background work, or the wallpaper is not active | The worker checks every 6 hours; open the app to force a foreground refresh, check *Update automatically* in Settings and make sure Mohakash is the selected live wallpaper, then check *Settings → Apps → Mohakash → Battery* is not *Restricted*. |
| The live wallpaper is not in the picker | The service is not registered | Rebuild and reinstall the APK; check `AndroidManifest.xml` for the `WallpaperService` and `res/xml/wallpaper.xml`. |
| `python3 scripts/sync_apod.py validate` fails | The archive or the generated files are inconsistent | Re-run `python3 scripts/sync_apod.py build` and commit the result. |

---

## 15. Owner checklist

**Done and verified by the coding agent**

- [x] Verified the current NASA endpoint, its parameters and its response fields against the live service.
- [x] Data pipeline with a dedicated NASA adapter, a normalized versioned schema, upsert/dedupe, resumable backfill, bounded retries and validation.
- [x] The complete archive: **11 443 records** from 1995-06-16 to 2026-10-22, backfilled from the live endpoint. NASA publishes 11 444 posts; one date (2007-07-16) has two posts and the newer one is kept.
- [x] Public API generator for `latest.json`, `archive-index.json`, `archive/YYYY.json`, `status.json` and `feed.xml`.
- [x] GitHub Actions workflow: schedule, manual runs, concurrency, minimal permissions, `[skip netlify]` commit, Pages deployment in the same workflow.
- [x] Astro website with all seven routes, all data states and a runtime-only data fetch.
- [x] Netlify configuration with the same-origin API proxy and freshness headers.
- [x] Kotlin/Compose Android app with Room, DataStore, WorkManager, MediaStore save, Sharesheet sharing and a real `WallpaperService`.
- [x] Automated tests: 102 pipeline, 99 website, 202 Android — all green. The debug APK builds.
- [x] Signed Android releases published on GitHub with APK, AAB and `SHA256SUMS.txt`; latest is **v1.0.6**, package `mohakash.stargzr.space`, certificate pinned in the release workflow.
- [x] Live wallpaper verified on an Android 15 emulator: the picker opens, the wallpaper applies and `dumpsys wallpaper` reports the Mohakash service.
- [x] Wallpaper audit on the Android 15 emulator: the system picker is shown only on first activation; choosing an archive picture updates the active wallpaper in place without a dialog; the background worker replaced that archive picture with the latest APOD without opening the app; a 6-hour periodic job is scheduled; Fill/Fit matches in the sheet preview and on the live wallpaper and changes while active; Wi-Fi only is off by default and Hide videos is in Settings.
- [x] Play Store phone screenshots added under `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- [x] App Links and archive-URL sharing verified on an Android 15 emulator: root, archive and archive-entry links open the right screen, query strings/fragments are ignored, a link that arrives during onboarding opens after it finishes, and the share button opens a text share sheet with the archive URL. The signed release APK was verified against the deployed `assetlinks.json` (`pm get-app-links` reports `verified`).

**Needs the repository owner**

- [ ] Push the repository to your own GitHub account.
- [ ] Update `config/site.json` (owner, repo, website URL, Android fallbacks) and re-run `python3 scripts/render_netlify_config.py` and `npm run generate:site` in `web/`.
- [ ] Update the `site` value in `web/astro.config.mjs`.
- [ ] Enable GitHub Pages with **GitHub Actions** as the source.
- [ ] Connect the repository to Netlify and deploy.
- [ ] Decide how the bot commit may pass branch protection (§9.4).
- [ ] Run the first backfill from the Actions tab (mode `backfill`, `start_year` 1995) if you did not keep the committed archive.
- [ ] Publish the app on Google Play (needs the owner's Play Console account and `PLAY_SERVICE_ACCOUNT_JSON`).
- [ ] Add the Play App Signing (and, later, F-Droid) certificate fingerprints to `web/public/.well-known/assetlinks.json` and redeploy the website so App Links verify for those builds.
- [ ] Submit the F-Droid metadata in `fdroid/metadata/` as a merge request to `fdroiddata`.

### Not verified here

The following could not be checked in this environment and must be confirmed
after deployment:

* The **real Netlify proxy** and the **real GitHub Pages publication** — the
  rewrite rules were exercised against a local server that mimics them
  (`scripts/serve_site.py`), not against Netlify.
* **Store signing certificates**: only the GitHub release key fingerprint is in
  `assetlinks.json`; the Play App Signing and F-Droid fingerprints must be added
  when those builds exist.
* The **WorkManager periodic delivery** has not been observed over a full 24-hour cycle;
  the worker itself was exercised on an emulator (it replaced an archive wallpaper with the
  latest APOD in the background) and the 6-hour job is visible in `dumpsys jobscheduler`, but
  OEM battery optimisation can still delay it.
* **The scheduled workflow** never ran on GitHub, so the cron schedule, the
  Pages deployment and the bot commit were verified by inspecting the workflow
  file and by running the same commands locally.
