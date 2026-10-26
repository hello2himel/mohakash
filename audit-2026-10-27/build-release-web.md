# Mohakash — build, release and web-delivery audit

**Date:** 2026-10-27 (Asia/Dhaka)
**Audited revision:** `00dc87d51524e9dbe1669b428f8ece72adb61f68` (`main`), tag under test `v1.0.6` (`20e4333`)
**Scope:** Android Gradle/manifest/resources/locales/packaging/R8/signing/versioning, F-Droid & Play metadata, dependency hygiene, reproducibility, CI/release workflows, tag/version consistency, APK/AAB contents, website build output, package scripts, generated config, promo/donation banner JS, external links, responsive/a11y evidence, and documentation claims that conflict with code.
**Mode:** read-only. No tracked source, config, or metadata file was edited. Build/test commands were run in `web/` (writes only git-ignored `dist/`, `.astro/`) and in a throw-away `git archive` copy at `/tmp/mohakash-head`; the live deployed site and the published GitHub release were also verified over the network.

> **Environment note.** While this audit ran, the working tree was being edited concurrently by another process (`FeedRandomPick.kt`, `NotificationHelper.kt`, `MohakashApp.kt`, `SettingsScreen.kt`, several `strings.xml`). Those edits do not compile at the moment (`FeedRandomPick.kt:155`, `Float * Dp`), but the committed revision `00dc87d` compiles and tests cleanly. All findings below are against `00dc87d`; the transient working-tree state is not counted as a defect.

---

## 0. Verdict at a glance

| Result | Status |
|---|---|
| Committed Android sources compile | ✅ `:app:compileDebugKotlin` (HEAD snapshot) |
| Android unit tests | ✅ 202 passed / 0 failed |
| Unsigned release build (F-Droid path) | ✅ `app-release-unsigned.apk`, `lintVitalRelease` passed |
| Signed release APK/AAB | ✅ versionCode 7 / 1.0.6, cert matches `assetlinks.json`, SHA-256 matches release `SHA256SUMS.txt` |
| Data pipeline tests / validation | ✅ 102 passed, `sync_apod.py validate` ok |
| Website tests / type check / build | ✅ 99 passed, `astro check` 0/0, build ok |
| Deployed website matches current `main` build | ✅ same hashed CSS (`about.DWUjS_V9.css`) |
| Dependency security | ❌ Astro 5.18.2 carries 1 critical + 3 high advisories |
| Cache/security headers in production | ❌ configured API cache headers not in effect; no CSP/`nosniff`; hashed assets uncached |
| Release version hygiene | ❌ 12 commits past `v1.0.6` with unchanged version 7 / 1.0.6 |

Counts: **2 high, 9 medium, 12 low** confirmed defects (D1–D23); 14 suggestions (S1–S14); 12 stale-doc items (T1–T12).

### Severity key

- **critical** — would ship a broken/unsafe build or expose users immediately.
- **high** — will break or mislabel the next release / a serious, confirmed vulnerability class.
- **medium** — confirmed defect with user-visible, security, privacy or performance impact, or a doc claim that is materially false.
- **low** — correctness/hygiene/SEO/a11y nit with a small blast radius.

---

## 1. Confirmed defects

### D1 — [HIGH] Astro 5.18.2 is a vulnerable, outdated build toolchain

**Where:** `web/package.json:21` (`"astro": "^5.18.2"`), `web/package-lock.json` (resolved 5.18.2).

**Evidence:**

```
$ cd web && npm audit --json
vulnerabilities: {'info': 0, 'low': 1, 'moderate': 0, 'high': 3, 'critical': 1, 'total': 5}
  astro     critical  <=7.3.1   fix: astro 7.4.2 (semver-major)
  esbuild   low       0.27.3–0.28.0
  sharp     high      <=0.35.5-rc.1
  zod       high      <4.6.6
$ POST https://api.osv.dev/v1/query  astro@5.18.2 → 11 advisories
  GHSA-26w7-cxv4-gfx2 (crit) Astro: RCE through AVIF image optimization (fixed 7.2.8)
  GHSA-2pvr-wf23-7pc7 (high) Host-header SSRF in prerendered error page (fixed 6.4.6)
  GHSA-j687-52p2-xcff (mod)  XSS in define:vars (fixed 6.1.6)
  … 8 more XSS/authorization bypass advisories
```

**Impact:** the site is static and renders only trusted repository content, so the RCE/XSS advisories are not demonstrated reachable here (no `astro:assets`, no user-supplied props). It is still a release-surface defect: a critical-severity framework advisory is pinned by the lockfile, `^5` cannot reach a fixed release, and CI has no audit gate.

**Minimal fix:** upgrade to Astro 7.4.2+ (major; re-run `npm run build`, `npm test`, `npm run check`), or record a written risk acceptance; add `npm audit --audit-level=high` (or `osv-scanner`) to CI.

---

### D2 — [HIGH] Release version is frozen at 1.0.6 (code 7) for 12 unreleased commits, and version numbers have two sources of truth

**Where:** `android/app/version.properties:10-11` (`VERSION_CODE=7`, `VERSION_NAME=1.0.6`); `android/app/build.gradle.kts:157-161` (literal `versionCode = 7` / `versionName = "1.0.6"` immediately overwritten by the properties values); `android/RELEASING.md:110-116` (manual “keep both in sync” rule); `.github/workflows/release.yml:71-87` (tag must equal `v$VERSION_NAME`).

**Evidence:**

```
$ git log --oneline v1.0.6..HEAD | wc -l
12
$ git log -1 --format='%h %ci %s' v1.0.6
20e4333 2026-10-26 23:13:58 +0600 feat(android): v1.0.6 UI polish, Archive tab and audit fixes
$ grep -n '^VERSION_' android/app/version.properties
10:VERSION_CODE=7
11:VERSION_NAME=1.0.6
$ grep -n 'versionCode = 7\|versionName = "1.0.6"' android/app/build.gradle.kts
157:        versionCode = 7
158:        versionName = "1.0.6"
```

`workflow_dispatch` of `release.yml` builds “the checked-in version” (`release.yml:3-4`), so a manual run today emits **1.0.6 / code 7 from different code** than the published `v1.0.6`. A later `v1.0.7` tag is rejected unless both the properties file *and* the Gradle literals are bumped first; nothing in CI checks that they agree (the tag check only compares the tag to `version.properties`).

**Impact:** duplicate-versionCode Play uploads are rejected; a manual artifact can be mistaken for the released 1.0.6; F-Droid's `checkupdates` parser reads the literal, so a half-bump makes F-Droid advertise the wrong version. It also means the only downloadable APK (`v1.0.6`) predates the localized interface and the `localeConfig` wiring added in `00dc87d`, while the README/`android/README.md` already describe them.

**Minimal fix:** add a CI/step check that `version.properties` equals the literals in `build.gradle.kts`, and fail `release.yml` on `workflow_dispatch` when `git describe --tags --exact-match` is absent unless `ALLOW_UNRELEASED=1`; or generate the literals from `version.properties` in a pre-build task and stop hand-syncing. Bump to `1.0.7` (code 8) as the next release step.

---

### D3 — [MEDIUM] The API cache policy configured in `netlify.toml` is not in effect in production

**Where:** `netlify.toml:76-99` (rules: latest / latest-image / status / archive-index `max-age=300, must-revalidate`, `archive/*` `max-age=86400`; there is **no** `feed.xml` rule); docs assert these values at `README.md:623-626` and `web/README.md:122-123`.

**Evidence (production, 2026-10-27):**

```
$ for p in /api/apod/v1/latest.json /api/apod/v1/status.json \
           /api/apod/v1/archive/2026.json /feed.xml; do
    curl -sSI "https://mohakash.netlify.app$p" | grep -i cache-control; done
cache-control: max-age=600        # latest.json
cache-control: max-age=600        # status.json
cache-control: max-age=600        # archive/2026.json
cache-control: max-age=600        # feed.xml
```

The values are GitHub Pages' origin headers (`cache-control: max-age=600`), not the `netlify.toml` values. Netlify's custom header rules are not overriding the proxied (200-rewrite) responses. The same test on a hashed asset shows Netlify's default: `/_astro/about.DWUjS_V9.css` → `cache-control: public,max-age=0,must-revalidate` (see D6).

**Impact:** documented freshness is wrong (10 min instead of 5), the yearly archive files are not cached for a day, and `feed.xml` is not cached for the documented hour. Functionally the site still works (browser `no-cache` revalidates and gets `304`, verified), but the published cache contract is false.

**Minimal fix:** verify the Netlify precedence with a one-line probe; if proxied responses cannot be overridden, stop documenting these rules as effective and either (a) serve the API from Netlify (copy `data/api` into `dist` at build) or (b) set the cache policy at the origin; keep the `[[headers]]` block only if a test proves it applies.

---

### D4 — [MEDIUM] Every `/archive/YYYY-MM-DD` page has the same title, canonical URL and Open Graph metadata

**Where:** `web/src/layouts/BaseLayout.astro:38-39` (canonical/og:url derived from `Astro.url.pathname`), `web/src/pages/archive/entry.astro:6-7` (generic title/description), served through the rewrite `netlify.toml:66-70`.

**Evidence:**

```
$ curl -s https://mohakash.netlify.app/archive/2026-10-26 | grep -E 'title>|canonical|og:(title|url)'
<title>Entry · Mohakash</title>
<link rel="canonical" href="https://mohakash.netlify.app/archive/entry/">
<meta property="og:title" content="Entry · Mohakash">
<meta property="og:url" content="https://mohakash.netlify.app/archive/entry/">
$ curl -s https://mohakash.netlify.app/archive/2026-10-26 | grep -c "2026-10-26"
0
```

All dates share the pre-rendered shell at `/archive/entry/`. The app's share button (`ShareHelper.kt:39-41`) and the website's share action both distribute `/archive/YYYY-MM-DD`, so chat/social previews show “Entry · Mohakash” and Google is told every entry canonicalises to `/archive/entry/`.

**Impact:** severe loss of per-entry SEO and share-card quality for the site's primary content; the canonical tag actively de-indexes real entries.

**Minimal fix:** inject per-date meta at the edge (Netlify Edge Function rewriting the shell's `<title>`/`og:*`/canonical), or pre-render entry pages from `data/api/v1/archive/YYYY.json` (build-time data, contradicts the current “runtime-only” design), or at minimum have `entry.ts` set `document.title`, canonical, and `og:*` attributes after fetch (helps browsers, not social crawlers).

---

### D5 — [MEDIUM] Google Fonts is an undisclosed third-party request that conflicts with the privacy page

**Where:** `web/src/layouts/BaseLayout.astro:73-77` (`fonts.googleapis.com`, `fonts.gstatic.com` stylesheet on every page); `web/src/pages/privacy.astro:46-54` (requests “go to” GitHub Pages / NASA Science / GitHub only) and `:15`, `:24` (“no tracking”, “no analytics”).

**Evidence:** deployed `<head>` of every page includes

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@500&display=swap" rel="stylesheet">
```

and the privacy policy's “Network requests” list omits Google entirely.

**Impact:** every visitor's IP/user-agent is disclosed to Google on first paint; the claim “no tracking” is materially inaccurate for a privacy-focused project (and a known GDPR exposure in the EU). Also a third-party availability/performance dependency (the site blocks on `fonts.googleapis.com` for CSS).

**Minimal fix:** self-host the two families (the TTFs already ship in `android/app/src/main/res/font/`) with `font-display: swap`, remove the Google origins, and add a `Content-Security-Policy`. If Google Fonts stays, disclose Google in `privacy.astro` and soften the “no tracking” wording.

---

### D6 — [MEDIUM] No security headers and no long-lived caching for hashed assets in production

**Where:** `netlify.toml` has no `[[headers]]` rule for `/*` or `/_astro/*` (only the API rules at 76-99).

**Evidence (production):**

```
$ curl -sSI https://mohakash.netlify.app/ | grep -iE 'content-security|x-content-type|referrer-policy|permissions-policy'
(no output; only HSTS is present)
$ curl -sSI https://mohakash.netlify.app/_astro/about.DWUjS_V9.css | grep -i cache-control
cache-control: public,max-age=0,must-revalidate
```

**Impact:** no CSP (the site loads a third-party font CDN and arbitrary NASA image hosts), no `X-Content-Type-Options: nosniff` (relevant because `/site.webmanifest` is served as `application/octet-stream`, D12), no `Referrer-Policy`/`Permissions-Policy`; content-hashed CSS/JS is revalidated on every navigation instead of being cached `immutable`.

**Minimal fix:**

```toml
[[headers]]
  for = "/*"
  [headers.values]
    X-Content-Type-Options = "nosniff"
    Referrer-Policy = "strict-origin-when-cross-origin"
    Permissions-Policy = "camera=(), microphone=(), geolocation=()"
    Content-Security-Policy = "default-src 'self'; img-src https: data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'"

[[headers]]
  for = "/_astro/*"
  [headers.values]
    Cache-Control = "public, max-age=31536000, immutable"
```

(Adjust `font-src` when Google Fonts is removed; inline Astro scripts need `'unsafe-inline'` or hashes.)

---

### D7 — [MEDIUM] `api-site/` is a tracked, stale, third copy of the generated API, and CI never updates it

**Where:** `api-site/` (37 tracked files, ~26 MB total; `api-site/api/v1/archive-index.json` alone is 5.0 MB); `.github/workflows/update-apod.yml:111-112` writes it (`assemble-site --site-root api-site`) but `:129` commits only `git add data/`.

**Evidence:**

```
$ git ls-files api-site | wc -l        → 37
$ grep generated_at api-site/api/v1/status.json   → 2026-10-22T12:32:18Z
$ grep generated_at data/api/v1/status.json       → 2026-10-26T14:17:59Z
$ curl -s https://mohakash.netlify.app/api/apod/v1/latest.json | python3 -c '…' → date 2026-10-26
```

**Impact:** the repository's `api-site/` is 4 days behind the deployed API and will drift further on every scheduled run. It confuses the “single source of truth” story, adds ~26 MB to every checkout, and would serve stale data if Pages were ever switched to a branch/folder deploy.

**Minimal fix:** either `git add -A api-site/` in the workflow and treat it as a published artifact, or delete it from git, add `api-site/` to `.gitignore`, and let the workflow build it only as a Pages artifact.

---

### D8 — [MEDIUM] Debug and release APKs share `applicationId`, including the CI-published debug APK

**Where:** `android/app/build.gradle.kts:151-163` (`applicationId = appId` in `defaultConfig`; no `applicationIdSuffix`), `.github/workflows/ci.yml:86-99` (uploads `mohakash-debug-apk`), `README.md:312` (`adb install -r app/build/outputs/apk/debug/app-debug.apk`).

**Evidence:**

```
$ /…/aapt2 dump badging android/app/build/outputs/apk/debug/app-debug.apk | head -1
package: name='mohakash.stargzr.space' versionCode='7' versionName='1.0.6'
$ grep -r applicationIdSuffix android/app/build.gradle.kts
(no match)
```

**Impact:** a debug build (CI artifact or local) cannot be installed over the signed release APK, and vice versa — `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (different signing keys, same package). The README does not mention this, and the CI artifact looks installable. It also makes it impossible to keep both installed side by side.

**Minimal fix:** `debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }` (the `namespace`/Kotlin package stay unchanged; `DeepLinkParser` uses `BuildConfig.WEBSITE_BASE_URL`, not the application ID). Adjust the README install note.

---

### D9 — [MEDIUM] 17 compiled `.pyc` files are tracked and the ignore rules are incomplete

**Where:** `.gitignore:1-4` (only `node_modules/`, `dist/`, `build/`, `*.log`); tracked files under `scripts/__pycache__/` and `scripts/tests/__pycache__/`.

**Evidence:**

```
$ git ls-files | grep -cE '__pycache__|\.pyc$'
17
$ python3 -m pytest scripts/tests -q      # side effect:
$ git status --short | grep pyc
 M scripts/mohakash/__pycache__/config.cpython-314.pyc
 M scripts/tests/__pycache__/test_config.cpython-314-pytest-9.1.1.pyc
```

**Impact:** any contributor running the test suite dirties the working tree; Python 3.14 bytecode is committed and will be stale/mismatched for other interpreters. `.kotlin/` (Kotlin 2.x project cache) is also untracked but not ignored (`?? android/.kotlin/`), and `.gradle/`, `.astro/`, `.pytest_cache/`, `*.pyc`, `__pycache__/` have no root-level rules.

**Minimal fix:** `git rm -r --cached scripts/__pycache__ scripts/tests/__pycache__`, then add to `.gitignore`:

```
__pycache__/
*.py[cod]
.pytest_cache/
.gradle/
.kotlin/
.astro/
local.properties
.DS_Store
```

---

### D10 — [MEDIUM] The archive-index localStorage cache sits at ~95 % of the quota, and quota errors are swallowed

**Where:** `web/src/lib/cache.ts:80-97` (`writeCache` catches and ignores every storage error), `web/src/lib/cache.ts:118-125` (compact-copy rationale), `web/src/lib/archive.ts:213-224` (`compactArchiveIndex`), `web/src/scripts/archive.ts:246-248`.

**Evidence (measured against the current data):**

```
$ python3 … # compact archive-index + envelope, JSON.stringify
compact archive-index envelope: chars=2,492,358  utf16_bytes=4,984,716 (4.75 MiB)
Chrome localStorage quota: ~5 MiB per origin
```

`compactArchiveIndex` keeps `date`, `title`, `media_type`, `thumbnail_url` and `image_url`; the long NASA CDN URLs are what make the payload large (11,447 entries).

**Impact:** the documented “offline with cache” archive fallback can silently stop working near the quota limit (Safari/Chrome eviction or `QuotaExceededError`). `fetchWithCache` still returns network data, so the failure is invisible — only the offline state is lost.

**Minimal fix:** store a smaller record (drop `thumbnail_url`/`image_url`; derive nothing or store only `date/title/media_type`), or move the cache to IndexedDB; log/flag a quota failure instead of swallowing it; add a test that serialises a realistic 11k-entry fixture.

---

### D11 — [MEDIUM] Kotlin 2.2.21 is affected by a build-cache deserialisation CVE, while Gradle caching is enabled

**Where:** `android/gradle/libs.versions.toml:4` (`kotlin = "2.2.21"`), `android/gradle.properties:2-4` (`org.gradle.caching=true`), CI uses `gradle/actions/setup-gradle@v4` (`ci.yml:68`, `release.yml:49`).

**Evidence (OSV):**

```
GHSA-r937-wjx7-w2jp / CVE-2026-53914 — "Unsafe Deserialization in Kotlin Build Cache
Enables Code Execution", severity MODERATE, fixed in 2.4.20-Beta1
```

**Impact:** build-time RCE through a poisoned Gradle/Kotlin build-cache entry (upstream advisory; no stable fixed release at the pinned 2.2.21). CI cache poisoning requires write access, so exposure is limited, but this is a release-integrity issue for a project that ships its own signed binaries.

**Minimal fix:** track the Kotlin 2.4.x line and upgrade as soon as a stable fix ships; in the meantime restrict the Gradle cache to trusted branches (no PR-writable cache) or set `org.gradle.caching=false` in the release job. Re-verify §2 tests after the upgrade.

---

### D12 — [LOW] `/site.webmanifest` is served as `application/octet-stream`

**Where:** `web/public/site.webmanifest`; no MIME rule in `netlify.toml`.

**Evidence:**

```
$ curl -sSI https://mohakash.netlify.app/site.webmanifest | grep -i content-type
content-type: application/octet-stream
```

**Impact:** the Web App Manifest spec expects `application/manifest+json`; some browsers/tools reject or ignore the manifest, so installability/theme metadata is unreliable.

**Minimal fix:**

```toml
[[headers]]
  for = "/site.webmanifest"
  [headers.values]
    Content-Type = "application/manifest+json"
```

---

### D13 — [LOW] Privacy policy's permission list omits four permissions present in the merged manifest

**Where:** `web/src/pages/privacy.astro:62-67` lists Internet, network state, notifications and storage only; the merged release manifest also declares `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and (`maxSdkVersion=28`) `READ_EXTERNAL_STORAGE`.

**Evidence:**

```
$ aapt2 dump badging /tmp/relcheck/mohakash.apk | grep uses-permission
… INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS,
    WRITE_EXTERNAL_STORAGE (maxSdk 28), WAKE_LOCK,
    RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, <pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

**Impact:** users see permissions in Android Settings that the policy does not explain; the policy reads as incomplete for a privacy-first app. (The WorkManager permissions are library-injected and benign, but they should be stated.)

**Minimal fix:** add a sentence covering WorkManager/background-work permissions (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`) and the legacy read permission, or remove permissions that are not actually needed.

---

### D14 — [LOW] The release APK is signed with APK Signature Scheme v2 only

**Where:** signing config `android/app/build.gradle.kts:128-140`; verified artifact.

**Evidence:**

```
$ apksigner verify -v /tmp/relcheck/mohakash.apk
Verified using v1 scheme: false
Verified using v2 scheme: true
Verified using v3 scheme: false
Verified using v3.1 scheme: false
Verified using v4 scheme: false
```

**Impact:** v2 is sufficient for `minSdk 24`, but v3 is what supports key rotation and v4 enables incremental installs. Existing users cannot be rotated to a new key without v3 lineage.

**Minimal fix:** `signingConfigs.release { enableV3Signing = true }` (AGP default may need explicit opt-in); optionally run `apksigner` with `--v4-signing-enabled` in the release workflow for the APK asset.

---

### D15 — [LOW] Remix Icon is shipped whole for 14 icons

**Where:** `web/src/layouts/BaseLayout.astro:12` imports `remixicon/fonts/remixicon.css`.

**Evidence:**

```
$ grep -rho 'ri-[a-z0-9-]*' web/src | sort -u | wc -l   → 14 used
$ grep -c '\.ri-' node_modules/remixicon/fonts/remixicon.css → 3,245 rules
$ ls -l node_modules/remixicon/fonts/
  157,554 remixicon.css   3,019,610 remixicon.svg   613,308 .eot
  613,136 .ttf            260,480 .woff             189,216 .woff2
$ find web/dist -type f -printf '%s %p\n' | sort -rn | head -3
  3,019,610 dist/_astro/remixicon.*.svg
    613,308 dist/_astro/remixicon.*.eot
    613,136 dist/_astro/remixicon.*.ttf
```

**Impact:** ~4.7 MB of font assets are published (the 3 MB SVG and 613 KB EOT/TTF are legacy fallbacks modern browsers never use) plus a 157 KB CSS bundle of 3,245 icon rules; only 189 KB woff2 and 14 rules are needed. Netlify deploy size is 5.1 MB, dominated by this.

**Minimal fix:** import only the used icons (or a subset build), drop the SVG/EOT/TTF `src` entries, and prune unused font formats from `dist`; keep one woff2.

---

### D16 — [LOW] Second configured API fallback URL returns 404

**Where:** `config/site.json:26-29` — `https://hello2himel.github.io/api/v1`; surfaced in `BuildConfig.API_BASE_URL_FALLBACKS` (`android/app/build.gradle.kts:46-52`, `android/app/src/main/java/app/mohakash/apod/AppContainer.kt:68-72`).

**Evidence:**

```
$ curl -o /dev/null -w '%{http_code}\n' https://hello2himel.github.io/api/v1/latest.json
404
$ curl -o /dev/null -w '%{http_code}\n' https://hello2himel.github.io/mohakash/api/v1/latest.json
200
```

**Impact:** if the project-site endpoint fails, the app's “fallback” adds a guaranteed-404 request and latency before declaring offline. README §6 describes it as a working “GitHub user site” fallback, which does not exist for this account.

**Minimal fix:** remove the second entry (or point it at a real mirror); add a CI check that every `api_base_url_fallbacks` entry answers 200 for `latest.json` (network job, optional).

---

### D17 — [LOW] Entry/archive SEO plumbing: soft-404s, trailing-slash 301s, no sitemap

**Where:** `netlify.toml:66-70` (`/archive/:date` matches any segment), `web/src/pages/archive/index.astro:5` (`new Date().getFullYear()` at build time), `web/public/robots.txt:1-2` (no `Sitemap:`), no sitemap generator in `astro.config.mjs`.

**Evidence:**

```
$ curl -o /dev/null -w '%{http_code}\n' https://mohakash.netlify.app/archive/not-a-date
200                     # shell renders a runtime error, but status is 200
$ curl -o /dev/null -w '%{http_code}\n' https://mohakash.netlify.app/sitemap.xml
404
$ for p in /about /api /archive /privacy /rights; do curl -sSI …$p | grep -i '^location'; done
location: /about/  … (301 for all five internal links)
```

**Impact:** invalid dates are indexable soft-404s; internal navigation pays a redirect hop; search engines have no sitemap; and because data-only commits are `[skip netlify]` (`update-apod.yml:134`), the archive year dropdown is frozen at the last code build — on 2027-01-01 the selector will not contain 2027 until a code push.

**Minimal fix:** return 404 for non-date second segments (Netlify rule with a date regex or an edge check), link with trailing slashes, add `@astrojs/sitemap` (or a static sitemap) + `robots.txt` reference, and generate the year list client-side from `archive-index` (or add the current year at runtime).

---

### D18 — [LOW] Theme colour does not match the page background

**Where:** `web/src/layouts/BaseLayout.astro:51` and `web/public/site.webmanifest:4-5` use `#0B1020`; `web/src/styles/tokens.css:13` defines `--bg: #0b0d12` (also `android/app/src/main/res/values/colors.xml:4` `mohakash_background #0B0D12`).

**Evidence:** static comparison of the three values; the deployed `<meta name="theme-color" content="#0B1020">` vs computed body background `#0b0d12`.

**Impact:** the mobile browser/status-bar tint is visibly different from the page it sits above; “Brand Deep Space; visually matches the page background” (the comment on that line) is false.

**Minimal fix:** use `#0b0d12` for `theme-color`/manifest, or change `--bg` to `#0B1020` if the brand colour is authoritative.

---

### D19 — [LOW] Release-only hygiene: logging interceptor, unused dependency declaration, duplicate icon

**Where:** `android/app/build.gradle.kts:266` (`implementation(libs.okhttp.logging)` in all variants; level is `NONE` in release at `AppContainer.kt:57-61`), `android/gradle/libs.versions.toml:35` (`material-icons-extended` declared but never used), `android/play-store-icon-512.png` (byte-identical to `fastlane/metadata/android/en-US/images/icon.png` and referenced nowhere).

**Evidence:**

```
$ cmp android/play-store-icon-512.png fastlane/metadata/android/en-US/images/icon.png && echo IDENTICAL
IDENTICAL
$ grep -rn 'material.icons.extended' android/app android/gradle
android/gradle/libs.versions.toml:35 (only declaration, never used)
$ grep -n 'okhttp.logging\|HttpLoggingInterceptor' android/app/build.gradle.kts \
    android/app/src/main/java/app/mohakash/apod/AppContainer.kt
android/app/build.gradle.kts:266:    implementation(libs.okhttp.logging)
…/AppContainer.kt:26:import okhttp3.logging.HttpLoggingInterceptor
…/AppContainer.kt:58:                HttpLoggingInterceptor().apply {
```

(R8 renames the class, so a `strings` search on the release DEX cannot show `HttpLoggingInterceptor`; inclusion follows from the unconditional dependency and the `addInterceptor` call.)

**Impact:** dead dependency/asset declarations drift and confuse release composition; the logging interceptor ships in release (inert at `NONE`, but unnecessary code and one more thing to audit).

**Minimal fix:** `debugImplementation(libs.okhttp.logging)`; delete the unused version-catalog entry and the duplicate icon (keep the Fastlane one).

---

### D20 — [LOW] No `uses-feature` declarations for live wallpaper / touchscreen

**Where:** `android/app/src/main/AndroidManifest.xml` (no `<uses-feature>` elements).

**Evidence:** `aapt2 dump badging …` shows no `feature-group` beyond the implicit empty group; Play therefore has no hardware filter for a live-wallpaper app.

**Impact:** the listing can be offered to devices/hardware without live-wallpaper support; Play filtering is less accurate.

**Minimal fix:** add

```xml
<uses-feature android:name="android.software.live_wallpaper" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="true" />
```

---

### D21 — [LOW] `localeConfig` omits the default `en` locale

**Where:** `android/app/src/main/res/xml/locales_config.xml:3-17` lists `en-GB` and `en-US` but not `en`; the default strings live in `values/` (English).

**Evidence:** 15 `<locale>` entries; the Android per-app language picker will offer “English (United Kingdom)/(United States)” but no plain “English”; `values/` is the fallback, so a device set to `en-AU` silently uses the default.

**Minimal fix:** add `<locale android:name="en" />` (and keep en-GB/en-US if the spelling variants matter).

---

### D22 — [LOW] Backup rules restore favourites/settings asymmetrically

**Where:** `android/app/src/main/res/xml/backup_rules.xml:3-9` and `data_extraction_rules.xml:3-18` include only `database/mohakash.db` and `file/wallpaper/`; DataStore preferences (settings, custom API endpoint, remembered base URL) are excluded.

**Impact:** after a device restore the user's saved pictures return but their settings do not (and the custom API endpoint silently reverts). The privacy page says settings “stay in the app's private storage” without noting that they are not backed up.

**Minimal fix:** include the DataStore file in both rule sets, or document the omission intentionally.

---

### D23 — [LOW] The site's content is JavaScript-only, with no `<noscript>` fallback

**Where:** `web/src/pages/index.astro:15-63`, `web/src/pages/archive/index.astro:11-48`, `web/src/pages/archive/entry.astro:12-64` (skeletons/hidden containers, data fetched at runtime); no `<noscript>` in `web/src` or `dist/`.

**Evidence:** `grep -rn noscript web/src` → no matches; built `index.html` has an empty `#today-content` and skeleton-only main.

**Impact:** with JS disabled or unavailable, the reading room shows permanent skeletons; text-only browsers and some crawlers see no article content. The README frames the runtime fetch as a feature, but an accessible/robust fallback is missing.

**Minimal fix:** add a `<noscript>` notice with links to the API/feed and a short explanation (a full SSR fallback would contradict the current architecture); consider pre-rendering the latest entry's static content.

---

## 2. Suggestions and hardening (not confirmed defects)

**S1 — CI does not build a release variant or run `astro check`.** `ci.yml:83-99` runs unit tests and a debug APK only; R8/shrink regressions and release-only signing failures surface first in `release.yml`. Add `:app:assembleRelease` (unsigned) + `:app:lintVitalRelease` to CI, and `npm run check`/`npm test` in the website job (the check is documented at `README.md:678` but not run).

**S2 — CI has no dependency-audit gate.** Add `npm audit --audit-level=high` for `web/` and an OSV/Gradle scan for `android/`; this would have caught D1/D11.

**S3 — Pin GitHub Actions by full commit SHA.** `ci.yml`, `release.yml`, `update-apod.yml`, `play.yml` use mutable `@v4`/`@v5` tags for `actions/*`; only `r0adkll/upload-google-play` is SHA-pinned (`play.yml:79`). Runner compromise/ref move would execute arbitrary code with release secrets.

**S4 — Make the release certificate check mandatory.** `release.yml:133-136` only enforces `EXPECTED_CERT_SHA256` when the repository variable is set; the docs call it optional (`android/RELEASING.md:72-78`). Fail closed when it is missing, and also verify the AAB's package/versionCode (currently only `jarsigner -verify`).

**S5 — Do not `--clobber` published release assets.** `release.yml:214-219` replaces assets on an existing tag. Use `--draft` + a separate publish step (or refuse to overwrite), so a re-run cannot silently swap a published binary.

**S6 — Publish the R8 mapping alongside the APK.** `release.yml:141-154` stages only APK/AAB/SHA256SUMS; `mapping.txt` stays in the build directory. Attach it (release assets or workflow artifact) so crashes in the deobfuscated build can be symbolicated.

**S7 — Sign release tags.** `v1.0.1`–`v1.0.6` are annotated but not GPG/SSH-signed (`git cat-file -p v1.0.6` has no `gpgsig`); a signed tag + `git verify-tag` gate in `release.yml` strengthens provenance.

**S8 — Verify Gradle dependencies and the wrapper.** `gradle-wrapper.properties:3` has no `distributionSha256Sum`; there is no `verification-metadata.xml`/dependency locking. Add `distributionSha256Sum` and `--write-verification-metadata sha256`.

**S9 — Pin the Python requirements.** `scripts/requirements.txt:5-6` uses `pytest>=8.0`, `PyYAML>=6.0`; CI resolves whatever is newest. Pin exact versions (ideally with hashes).

**S10 — Add DOM tests for the promo/donation scripts.** `web/vitest.config.ts:3-9` uses `environment: 'node'`; `web/tests/` covers only pure logic, so `AppPromo.astro`/`DonatePrompt.astro` behaviour (variant choice, session persistence, suppression) is untested. Add a jsdom/happy-dom suite.

**S11 — Remove the duplicate `site` URL or derive it.** `web/astro.config.mjs:11` hardcodes `https://mohakash.netlify.app` while `site.generated.ts` is generated from `config/site.json`; nothing uses `Astro.site`. The owner checklist step “Update the `site` value in `web/astro.config.mjs`” (`README.md:743`) is a no-op, and the two can drift.

**S12 — Reduce the 5 MB archive-index payload / mobile cost.** `data/api/v1/archive-index.json` is 5,243,251 B (768,813 B gzip); the app parses it once (`AppContainer.kt:91-103`) and the website fetches it on every archive visit. Short keys or dropping `thumbnail_url` from the index would materially cut first-load data; keep the per-year files for details.

**S13 — Mobile-only extras.** `androidx.compose.material.icons.extended` is unused (D19); four TTFs occupy ~1.09 MB of the 3.29 MB APK — subsetting Inter/Newsreader would shrink it; the APK carries 4 native ABIs for two tiny `.so` files, which is fine but worth confirming no `abiFilters` are desired.

**S14 — Dead/duplicated files.** `design-lab/` (15 MB of mockups incl. APOD images) is untracked and not ignored — a stray `git add .` would commit third-party imagery; add it to `.gitignore` or move it out of the repo. `android/play-store-icon-512.png` is a duplicate (D19).

---

## 3. Stale documentation and metadata

**T1 — “Support card can be dismissed for 14 days” is false.** `README.md:222-226` and `web/README.md:43-49` describe a dismissible card; `web/src/components/AppPromo.astro` has no dismiss control (grep for `dismiss` finds only the separate floating `DonatePrompt`), and `AppPromo.astro:97-98` explicitly says the cards are “permanent parts of the page now”. The floating prompt's cooldown is 30 days (`web/src/lib/donation.ts:25`), not 14. Update both docs (or restore the dismiss behaviour).

**T2 — Cache policy table is wrong for production.** `README.md:623-626` and `web/README.md:122-123` claim `max-age=300, must-revalidate`, `archive/YYYY.json` 86400 and `feed.xml` 3600; production returns `max-age=600` for all proxied paths (D3). Also `README.md:710` says “revalidates `latest.json` on every load” — the browser does request `no-cache` (`web/src/lib/api.ts:97-98`), but Netlify's edge may answer from its 600 s cache.

**T3 — Archive statistics are stale.** `README.md:726` says “11 443 records … to 2026-10-22”; current `data/api/v1/status.json` says `record_count: 11447`, `archive_end_date: 2026-10-26`. (The stale number matches the stale `api-site/` snapshot — see D7.)

**T4 — Fastlane README says screenshots are missing.** `fastlane/README.md:19-21`: “The phone screenshots must be captured … before the Play listing can be submitted.” Seven 1080×1920 screenshots exist in `fastlane/metadata/android/en-US/images/phoneScreenshots/` and `README.md:736` says they were added.

**T5 — RELEASING.md examples are from 1.0.1.** `android/RELEASING.md:100-101` and `:154` show `VERSION_CODE=2`/`1.0.1` (actual 7/1.0.6); `:253` says “Push the `v1.0.1` tag”; `:272` says “`1.0.1`, code `2`”; `:283` says “Capture at least two phone screenshots” although they exist. It is presented as the current source of truth, not an example.

**T6 — Play workflow default tag is v1.0.1.** `.github/workflows/play.yml:12` (`default: v1.0.1`); the current release is v1.0.6. A manual dispatch that forgets to edit the field fails at `gh release download`.

**T7 — F-Droid staging metadata is 1.0.4/code 5.** `fdroid/metadata/mohakash.stargzr.space.yml:26-33` and `:52-53`; current is 1.0.6/code 7. This is expected for an unsubmitted MR, but `fdroid/README.md:14-20` and `MERGE_REQUEST.md:67-68` should say “update the version/commit before submitting” (the checklist does say this in one line, but the metadata file itself is easy to submit stale).

**T8 — `npm run preview` is documented but does not exist.** `web/README.md:89` lists `npm run preview`; `web/package.json:7-19` has no `preview` script (scripts are `generate:site`, `dev`, `build`, `test`, `check`, …). Add the script or remove the line.

**T9 — The “GitHub user site” fallback description is aspirational.** `README.md:255-258` describes “a GitHub project site first, then a GitHub user site”; the second URL 404s (D16). Clarify that only the project site exists.

**T10 — `astro.config.mjs` owner step is a no-op.** `README.md:743` instructs the owner to “Update the `site` value in `web/astro.config.mjs`”, but the value is unused (`grep -rn 'Astro.site' web/src` → none; canonical/OG use `SITE.websiteUrl`). Remove the step or make the config the source of truth.

**T11 — Privacy policy wording vs. implementation.** Besides D5 and D13, `privacy.astro:51-54` lists GitHub Pages/NASA/GitHub as the only request targets, while the page itself loads Google Fonts; `:15`/`:24` say “no tracking”, which should be reconciled with any third-party CDN.

**T12 — `README.md:732` “The debug APK builds” and “all green” are true at `00dc87d`**, but the working tree currently fails to compile (see the environment note). If the current in-flight change is pushed without a compile check, this claim becomes false; a CI compile gate on PRs (S1) prevents that.

---

## 4. Verified clean (evidence)

| Check | Command | Result |
|---|---|---|
| Android compile (HEAD) | `./gradlew --no-daemon --offline :app:compileDebugKotlin` (in `/tmp/mohakash-head/android`, JDK 17) | BUILD SUCCESSFUL |
| Android unit tests | `:app:testDebugUnitTest` | 202 tests, 0 failures (23 result files) |
| Unsigned release / R8 / shrink / lintVital | `:app:assembleRelease` in clean copy (no keystore) | BUILD SUCCESSFUL; `app-release-unsigned.apk` 3,435,920 B — matches F-Droid `output:` path |
| Signed release artifact | download `releases/latest/download/mohakash.apk` + `.aab` | APK 3,288,116 B sha256 `aaa9d323…` = `SHA256SUMS.txt`; AAB 6,309,567 B sha256 `2a5cc2f8…` = `SHA256SUMS.txt` |
| Package/version | `aapt2 dump badging` on released APK | `mohakash.stargzr.space`, versionCode 7, versionName 1.0.6, minSdk 24, targetSdk 36 |
| Signer | `apksigner verify --print-certs` | SHA-256 `04:41:88:79:…:C0:AB`, identical to `web/public/.well-known/assetlinks.json:8` and `android/RELEASING.md:33` |
| APK contents | `unzip -l` | no `assets/` test data, no debug/test images, no source maps; R8 obfuscation present; `androidx.compose.ui.tooling` absent; baseline profile present |
| Manifest hardening | `aapt2 dump xmltree` on the released APK **and** the HEAD unsigned build | `debuggable` absent, `allowBackup=true`, `usesCleartextTraffic=false`, `extractNativeLibs=false`, backup/data-extraction rules wired in both; `android:localeConfig` + `res/xml/locales_config` are present **only in HEAD** (added by the unreleased `00dc87d`, consistent with D2 — released v1.0.6 predates localization) |
| Asset-link chain | deployed `/.well-known/assetlinks.json` vs repo | identical; 200 `application/json` |
| Data pipeline | `python3 -m pytest scripts/tests -q`; `sync_apod.py validate`; `render_netlify_config.py --check` | 102 passed; `validation ok`; `netlify.toml is up to date` |
| Website | `npm test`, `npm run build`, `npm run check` | 99 passed; 8 pages built; `astro check` 0 errors / 0 warnings |
| Deployed site = current build | `diff` of local `dist/index.html` vs deployed HTML; hashed CSS names | only Netlify's injected comment differs; `/_astro/about.DWUjS_V9.css` identical |
| Internal links / a11y static checks | crawl of built HTML | no duplicate IDs, every `<img>` has `alt`, every `target=_blank` has `noopener`, one `h1` per page |
| Image metadata | `file` on public assets | og-image 1200×630, icons 192/512/maskable 512, apple-touch 180, feature graphic 1024×500, screenshots 1080×1920 (7) |
| RSS | XML parse of `data/feed.xml` | valid RSS 2.0, 30 items, links to NASA |
| API consistency | index vs year files | 11,447 entries = sum of 32 year files; no missing year |

---

## Appendix A — commands used (abridged)

```bash
# Repository state
git rev-parse HEAD                                  # 00dc87d…
git log --oneline v1.0.6..HEAD | wc -l              # 12
git ls-files | grep -cE '__pycache__|\.pyc$'        # 17

# Clean HEAD snapshot (avoids the concurrently edited working tree)
git archive HEAD | tar -x -C /tmp/mohakash-head
cd /tmp/mohakash-head/android
JAVA_HOME=/home/himel/jdks/jdk-17.0.20.1+1 ./gradlew --no-daemon --offline :app:testDebugUnitTest
JAVA_HOME=… ./gradlew --no-daemon --offline :app:assembleRelease

# Build metadata
aapt2 dump badging android/app/build/outputs/apk/release/app-release.apk
apksigner verify -v --print-certs ./mohakash.apk

# Signing key (password read from the git-ignored file; not printed)
keytool -list -v -keystore android/keystore/mohakash-release.jks \
  -storepass "$(sed -n 's/^storePassword=//p' android/keystore.properties)" \
  -alias "$(sed -n 's/^keyAlias=//p' android/keystore.properties)"

# Web
cd web && npm test && npm run build && npm run check
npm audit --json
curl -sSI https://mohakash.netlify.app/api/apod/v1/latest.json
curl -sSI https://mohakash.netlify.app/site.webmanifest
curl -s  https://mohakash.netlify.app/archive/2026-10-26 | grep -E 'canonical|og:url'

# Release artifacts
curl -sSL -o mohakash.apk https://github.com/hello2himel/mohakash/releases/latest/download/mohakash.apk
sha256sum mohakash.apk                                # aaa9d323… == SHA256SUMS.txt
```

**OSV queries** (`POST https://api.osv.dev/v1/query`): `astro@5.18.2` → 11 vulns; `com.squareup.okhttp3:okhttp@4.12.0`, `io.coil-kt:coil@2.7.0`, `kotlinx-serialization-json@1.9.0`, `work-runtime-ktx@2.10.5`, `room-runtime@2.8.2`, `navigation-compose@2.9.5`, `datastore-preferences@1.1.7`, `core-ktx@1.17.0`, `appcompat@1.7.1`, `AGP@8.13.0` → 0; `kotlin-gradle-plugin@2.2.21` → GHSA-r937-wjx7-w2jp (moderate).

---

## Appendix B — limitations

- **Concurrent edits.** The working tree did not compile at audit time because of uncommitted, in-flight changes (`FeedRandomPick.kt:154-156`, `Float * Dp`). This audit deliberately used the committed `00dc87d` snapshot (compiles, 202 tests pass) and the deployed site; it did not evaluate the uncommitted UI work.
- **No emulator/device run.** Per the brief, no emulator was started. The APK/AAB, manifest, resources and signing were verified statically with `aapt2`/`apksigner`/`unzip`; the GitHub release notes' emulator claims were not re-executed.
- **No Play/F-Droid live submission check.** Play Console and the fdroiddata MR are owner actions; the metadata was validated as YAML and against the current Fastlane/version files, not against Play policy review.
- **`api-site` deployment path not exercised.** The Pages artifact is generated and uploaded by `update-apod.yml`; a full Pages deployment was not run. The upstream API, the Netlify proxy and the site itself were tested live.
- **CDN behaviour.** Netlify's handling of `[[headers]]` on 200-proxy rewrites was inferred from production responses (D3); a one-line probe on a preview deploy is recommended before changing the config.
- **Read-only side effect, reverted.** Running the pipeline tests rewrote two tracked `.pyc` files; they were restored to `HEAD` (`git checkout -- …`). Those files are tracked in the repository, which is itself D9.

---

*Audit produced read-only against `/home/himel/Documents/mohakash`; report at `audit-2026-10-27/build-release-web.md`.*
