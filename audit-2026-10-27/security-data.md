# Mohakash Android — security and data-integrity audit

**Audit date:** 2026-10-27 (Asia/Dhaka)
**Target:** `/home/himel/Documents/mohakash/android` (application id `mohakash.stargzr.space`, version 1.0.6 / versionCode 7)
**Baseline:** commit `00dc87d` plus the uncommitted working-tree changes present on 2026-10-27 (localization rework, `values-in`, small UI/lint changes). This audit made **no production edits**; only this report directory was created. The working tree was edited concurrently by another process at ~01:52 during the audit (manifest label removal, locale qualifier fix, Settings relative-time rewrite). Line references below were reconciled to the tree as of ~01:55; if the tree moves again, re-anchor by symbol rather than by absolute line number.
**Report:** `audit-2026-10-27/security-data.md`

---

## 0. Executive summary

**No critical or high-severity vulnerability was confirmed.** The app has a deliberately small attack surface: one exported activity used for verified HTTPS App Links, one exported `WallpaperService` protected by `BIND_WALLPAPER`, no WebView/JS bridge, no exported providers or custom broadcast receivers, no authentication tokens, and no secrets or request/response bodies in release logs.

The most serious confirmed problems are **medium** severity and are mostly about **trusting remote API data too much** and **data-integrity/robustness around persistence and media**:

| Rank | ID | Severity | Finding |
|---|---|---|---|
| 1 | M1 | Medium | Coil loads API-controlled image URLs with **no scheme/host allowlist and no response-size cap**; bypasses the HTTPS/64 MiB policy that protects the explicit download path. Enables local `file:`/`content:` loads, private-network requests, and network/storage exhaustion. |
| 2 | M2 | Medium | **SSRF/private-network requests** via OkHttp's default cross-host redirect following plus no loopback/RFC1918/link-local filtering for API or image URLs. |
| 3 | M3 | Medium | JSON parsing of up to ~5 MiB (`fetchArchiveIndex`) and ~700 KiB (year files) happens **on the main thread** in `ApodRepository`; the API layer does the network on IO but the repository decodes after returning to Main. ANR/jank risk. |
| 4 | M4 | Medium | A **truncated or non-image 200 response is committed as the live wallpaper**; only "non-empty" is checked. The sync engine then considers the picture unchanged and never retries, leaving a blank wallpaper after the next surface recreation. |
| 5 | M5 | Medium | Room uses `fallbackToDestructiveMigration(dropAllTables = true)` with `exportSchema = false`; a future schema change silently deletes all saved pictures, and a corrupt DB has no recovery path (crash loop until app data is cleared). |
| 6 | M6 | Medium | Backup rules copy **only `mohakash.db`**, not the SQLite `-wal`/`-shm` sidecars, while Room normally runs in WAL mode. Recent favourites can be missing after a cloud backup/restore or device transfer. |
| 7 | M7 | Medium | A transient DataStore `IOException` is converted to `emptyPreferences()` and the settings flow **completes**. This makes the custom endpoint look removed, clears the 5 MiB archive-index cache, and can mix responses/caches from the built-in endpoint with the user's custom endpoint. |
| 8 | M8 | Medium | Settings writes **swallow all `IOException`s**, so the UI can report "Saved" for an endpoint change that was never persisted (after caches were already cleared). The same masking affects toggles and last-sync timestamps. |
| 9 | M9 | Medium | Two concurrent "save to gallery" actions write to the **same predictable cache file**, then one deletes it while the other is still copying it. A double-tap can create a corrupt gallery image or a spurious failure. |
| 10 | L1 | Low | `latest.json`/index `date` values are not validated as ISO dates; a malformed date is persisted as a primary key and passed into `Routes.detail(date)` without encoding, so tapping such an entry can throw `IllegalArgumentException` (API-controlled crash / poisoned cache). |

Additional low-severity and hardening items are listed in sections 2 and 3.

**What is genuinely working well** (verified in code, not just documentation):

- `android:usesCleartextTraffic="false"` (`AndroidManifest.xml:24`), and OkHttp 4.12 also enforces the platform cleartext policy, so `http://` requests fail even if a URL is misconfigured.
- The explicit wallpaper/gallery download path validates `https` + image extension (`ImageUrlValidator.kt:28-39`) and enforces 64 MiB before/while writing (`ImageDownloader.kt:42-61`).
- Gallery/wallpaper file names are derived from sanitized dates/hex hashes, not raw API strings (`ApodEntry.kt:53-63`, `WallpaperStorage.kt:73-75`); no path traversal was found in the write path.
- The archive index cache is one of the better-engineered parts: same-directory temp file + rename, sidecar size validation, quarantine of corrupt/oversized files, a `stateMutex`, a `refreshMutex`, and a generation token so `clear()` beats an in-flight refresh (`ArchiveIndexRepository.kt:192-261`, `326-352`, `383-398`). Unit tests cover the clear-during-refresh race.
- Deep links are host- and date-validated in the parser (`DeepLinks.kt:63-90`), and all `PendingIntent`s are `FLAG_IMMUTABLE` (`NotificationHelper.kt:72-102`).
- The wallpaper service is exported only with `android.permission.BIND_WALLPAPER` (`AndroidManifest.xml:69-81`), which is a signature-level permission held by the system.
- API strings are rendered as Compose `Text`; there is no WebView, `Html.fromHtml`, SQL string concatenation, dynamic receiver registration, or `FileProvider` exposure.
- Localization format specifiers were machine-compared across all 15 locales against `values/strings.xml`: no argument-type/position mismatches and no malformed `%` escapes were found.

---

## 1. Scope, threat model, and method

### 1.1 In scope

- API/network layer: HTTPS, custom endpoint handling, host/private-network access, redirects, timeouts, response caps, JSON bounds, malformed data.
- Archive/index cache: atomicity, corruption, races, temp files, quarantine, refresh/clear, process death.
- Room/DataStore: schema/fallbacks, corruption, blocking IO, transactional writes, data loss.
- Images/downloads/wallpaper service: URL validation, file paths, path traversal, MIME/decoding limits, external storage, MediaStore, cache eviction, permissions, service exposure.
- Notifications/deep links/intents: exported components, validation, injection, `PendingIntent` mutability, schemes/hosts.
- Backup/data-extraction rules, logs/secrets/privacy, dependency/manifest issues.
- Localization resources where formatting/escaping could be unsafe.

### 1.2 Threat model used for severity

The app has no accounts, no server-side secrets, and no user-generated content. The realistic remote adversary is:

1. **Control of the JSON endpoint** — either the user configured a custom endpoint (an explicitly supported feature), or the built-in GitHub Pages endpoint/repository is compromised. The API response is fully attacker-controlled in that case.
2. **A malicious app on the device** — can send intents to exported components and read anything the app exposes. It cannot write this app's private storage and cannot read its private files without root.
3. **A hostile network** — TLS protects the built-in endpoint; custom endpoints may be weak. Clear-text is blocked.
4. **A lost/backed-up device** — cloud backup or device transfer can restore the database/wallpaper directory.
5. **No physical/root adversary** — physical access and root are out of scope; otherwise all app-private storage is readable and most mitigations are moot.

Severity therefore reflects *conditional* exploitability: M1/M2 need endpoint control, but that is the normal trust boundary for an API-driven app and is explicitly user-selectable. No finding gives an unprivileged local app code execution, credential theft, or access to another app's data.

### 1.3 Method

- Read every Kotlin file under `android/app/src/main/java` (network, repositories, Room/DataStore, storage, wallpaper, notifications, deep links, UI call sites), the manifest, backup/data-extraction XML, Gradle files, proguard rules, and the release merged manifest.
- Cross-checked the compiled/merged manifest (`android/app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml`) for exported components contributed by AndroidX.
- Inspected dependency bytecode where behavior was security-relevant: OkHttp 4.12 cleartext enforcement and redirect defaults; Coil 2.7 `StringMapper`/`FileFetcher`/`ContentUriFetcher` support and the absence of a response-size cap in `HttpUriFetcher`.
- Verified the release APK signature with `apksigner` and compared its SHA-256 fingerprint with `web/public/.well-known/assetlinks.json`.
- Compared localization format specifiers programmatically across `values*/strings*.xml`.
- Read the existing unit tests to understand intended invariants, then checked whether the implementation actually enforces them. Where a test exists but does not cover a failure mode, that is noted.

### 1.4 Limitations

No emulator/device was used; no dynamic instrumentation, traffic interception, fuzzing, StrictMode run, or dependency-vulnerability database scan was performed. Findings are static and code-path based. A device test is still required for the App Links verification state, MediaStore behavior on specific OEMs, and the wallpaper service lifecycle. Dependency versions (`libs.versions.toml`) were not checked against a live CVE feed; no dynamic version ranges are used.

---

## 2. Findings

Every finding below contains the exact repository-relative path and line range, the concrete behavior observed, a realistic failure/exploit scenario, impact, status (confirmed vs hardening), and a minimal fix. IDs are stable for tracking.

### M1 — API-controlled image URLs bypass all URL validation and have no response-size cap

**Severity:** Medium
**Status:** Confirmed behavior; impact requires control of the API response (compromised endpoint or user-chosen custom endpoint).
**Category:** network / images / DoS / local-file access

**Locations**

- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedPageContent.kt:479-507` — `fullUrl`/`thumbnailUrl` come straight from `entry`/`indexEntry`; `ImageRequest.Builder(context).data(it)` is created without validation.
- `android/app/src/main/java/app/mohakash/apod/ui/detail/EntryImageViewer.kt:98-103` — same pattern.
- `android/app/src/main/java/app/mohakash/apod/ui/components/CommonComponents.kt:372-379` — `AsyncImage(model = url)` in `PictureFrame`, used by Archive/Saved.
- `android/app/src/main/java/app/mohakash/apod/ui/wallpaper/WallpaperPreview.kt:17-20` — the preview model is either a `File` or the raw remote URL string.
- `android/app/src/main/java/app/mohakash/apod/ui/onboarding/OnboardingScreen.kt:116` — remote preview URL.
- `android/app/src/main/java/app/mohakash/apod/MohakashApplication.kt:25-38` — the shared Coil `ImageLoader` has no custom `OkHttpClient`, no URL mapper/validator, and no size policy.
- Contrast: `android/app/src/main/java/app/mohakash/apod/core/ImageUrlValidator.kt:28-39` and `android/app/src/main/java/app/mohakash/apod/data/storage/ImageDownloader.kt:28-61` enforce HTTPS, image extension, and 64 MiB only on the explicit save/wallpaper path.

**Evidence**

Coil 2.7 maps `String` to `Uri` (`coil.map.StringMapper`) and supports `file://` (`FileFetcher`), `content://` (`ContentUriFetcher`), `asset://`, `android.resource://`, and network URIs. `HttpUriFetcher` writes the response to its disk cache with `BufferedSource.readAll(sink)` and has no byte cap (verified in the dependency bytecode). The Coil disk-cache limit of 64 MiB (`MohakashApplication.kt:34`) bounds the *cache*, not an individual response; a single oversized body is written to a temporary cache file before eviction.

**Failure scenario**

The JSON endpoint returns:

```json
{"date":"2026-10-27","title":"x","media_type":"image",
 "image_url":"file:///data/user/0/mohakash.stargzr.space/files/wallpaper/wallpaper-abc.jpg"}
```

The feed/detail screen asks Coil to load the app's private wallpaper file (and would load any readable local image URI). Alternatively:

```json
{"image_url":"https://192.168.1.1/reboot.jpg"}
```

makes the phone issue a GET to a LAN device whenever the page is rendered. Or `image_url` points at a multi-gigabyte response, causing cellular data consumption and cache/temp-file growth on every retry. Because the app has no `READ_MEDIA_IMAGES` permission, arbitrary `content://media/...` reads fail, but exported `content://` providers and app-private `file://` paths are reachable.

**Impact**

- Local file rendered into the UI (information disclosure to the screen; no exfiltration channel in the app).
- SSRF-style GET requests from the device to private hosts (see M2).
- Unbounded network/disk consumption from a single API field; potential storage exhaustion and repeated retries on recomposition.
- Inconsistent security policy: the same URL is rejected by "Save to gallery" but fetched by the feed.

**Minimal fix**

Add one validation choke point and use it for every display URL:

1. At the mapper boundary (`ApiMapper.kt`), drop or null out any image URL that is not `https://` with a non-blank host and an image extension (reuse `ImageUrlValidator.isValidImageUrl`), and consider allowing only known image hosts (NASA asset host, the API host) for the built-in endpoint.
2. Give Coil an explicit `OkHttpClient` via `ImageLoader.Builder.okHttpClient(...)` with a network interceptor that rejects `Content-Length > N` (e.g. 32 MiB) and wraps the body source with a counting/limiting source for chunked responses.
3. Do not pass raw `file:`/`content:` URIs from API data to Coil; use a custom `Mapper` that rejects non-http(s) schemes.

Validation at the mapper (not only in the composable) also protects `SavedScreen`, previews, and future call sites.

---

### M2 — SSRF / private-network access: no host allowlist and OkHttp follows cross-host redirects

**Severity:** Medium
**Status:** Confirmed behavior; conditional on endpoint control or a redirect from a compromised server.
**Category:** network / SSRF / hardening

**Locations**

- `android/app/src/main/java/app/mohakash/apod/AppContainer.kt:51-63` — `OkHttpClient.Builder()` never calls `.followRedirects(false)` or installs a redirect validator. OkHttp 4.12 defaults `followRedirects = true` and `followSslRedirects = true`.
- `android/app/src/main/java/app/mohakash/apod/data/remote/ApodApi.kt:45-70` — the request is built from the base URL, but the **final URL after redirects is never checked** before the body is parsed/cached.
- `android/app/src/main/java/app/mohakash/apod/data/storage/ImageDownloader.kt:32-40` — only the initial URL is validated; redirects are followed, and the final host/extension/Content-Type is not re-checked (only `text/html` is rejected).
- `android/app/src/main/java/app/mohakash/apod/core/ImageUrlValidator.kt:28-39` — checks scheme + non-blank host + extension; it does **not** reject `127.0.0.1`, `[::1]`, RFC1918, `169.254.0.0/16`, IPv6 ULA, or DNS names resolving to those.
- `android/app/src/main/java/app/mohakash/apod/core/ApiEndpoint.kt:26-33` — a custom endpoint may be any HTTPS host, including loopback/private IPs (intended for self-hosted/dev, but not bounded).

**Failure scenario**

A compromised built-in endpoint (or a user-supplied custom endpoint) responds to `GET /latest.json` with:

```
HTTP/1.1 302 Found
Location: https://10.0.0.10/admin/reboot
```

OkHttp follows the redirect; `ApodApi.getString` returns the body to the repository, which may cache it and try to parse it. For images, the API supplies `https://cdn.example/x.jpg` which 302s to `https://192.168.1.1/action`; `ImageDownloader` writes the response and saves it to the gallery. A direct image URL to a private host works without any redirect at all.

**Impact**

The app becomes a request proxy from the phone into the user's LAN. GET-only requests to routers, printers, NAS boxes, IoT devices, and link-local services can trigger state changes (many such devices accept GET actions), and the app may cache/display internal responses. There is no direct exfiltration back to the attacker, which keeps this at medium rather than high. It also violates the "only the public API" expectation.

**Minimal fix**

- Disable automatic redirects (`followRedirects(false)`, `followSslRedirects(true)` only if handled manually) or add a network interceptor that follows at most 3 redirects and accepts only `https` final URLs whose host is in an allowlist (same host as the base URL, or an explicit image CDN allowlist).
- Add a shared `isPublicHttpUrl` check that rejects literal loopback/private/link-local IPs and resolves DNS names before connecting if feasible. For the custom endpoint, allow the user-selected host but still reject redirects to a *different* private host.
- Reject non-image final URLs in `ImageDownloader` by checking the final `response.request.url` and `Content-Type: image/*`.

---

### M3 — Main-thread JSON parsing of multi-megabyte payloads

**Severity:** Medium
**Status:** Confirmed by code path.
**Category:** blocking IO / ANR / reliability

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:43-56` — `fetchLatest()` calls `api.getString(...)` (which internally switches to IO) and then **decodes on the caller's dispatcher**.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:84-88` — `fetchArchiveIndex()` parses the ~5 MiB index on the caller's dispatcher (currently not called by the UI, but still public/dead code).
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:114-122` — `fetchArchiveYear()` parses a ~700 KiB year file on the caller.
- `android/app/src/main/java/app/mohakash/apod/data/remote/ApodApi.kt:45-70` — `getString` uses `withContext(Dispatchers.IO)`; when it returns, execution resumes on the original (Main) dispatcher.
- Call sites on `Dispatchers.Main`:
  - `FeedViewModel.kt:493-506` (`loadLatestInternal` from `viewModelScope.launch`) and `FeedViewModel.kt:709-723` (`hydrateFromYear`).
  - `DetailViewModel.kt:99-141` (`load` from `viewModelScope.launch` → `fetchEntry`).
  - `ArchiveViewModel.kt:106-112` (`selectYear` from `viewModelScope.launch`).
- `FileArchiveIndexRepository.refresh` does it correctly: `ArchiveIndexRepository.kt:197-209` wraps download + decode + sort in `withContext(Dispatchers.IO)`.

**Failure scenario**

A user taps through archive years on a low-end device while a slow network returns a 700 KiB year file. The network read happens off-main, but the ~10k-token JSON decode and 365-record mapping run on the Main thread. Each tap freezes the UI for tens to hundreds of milliseconds; several quick taps can produce an ANR. The 5 MiB index path (if re-enabled) is worse; the in-repo `fetchArchiveIndex` is a ready-made ANR.

**Impact**

Jank and possible ANR ("Application Not Responding") during normal browsing; no memory corruption. This is a reliability/data-integrity issue rather than an exploit.

**Minimal fix**

Move the decode + mapping into the IO/Default context, e.g.:

```kotlin
suspend fun fetchArchiveYear(year: Int): List<ApodEntry> = withContext(Dispatchers.Default) {
    val raw = api.getString("/archive/$year.json")
    val records = json.decodeFromString<ArchiveYearDto>(raw).records.map(ApiMapper::toDomain)
    withContext(Dispatchers.IO) { cachedEntryDao.upsertAll(records.map { CachedEntryEntity.from(it, now()) }) }
    records
}
```

Alternatively, expose typed `suspend fun latest()`/`archiveYear()` on `ApodService` and implement them with `withContext(Dispatchers.IO)`. Do not leave the 5 MiB parse on the caller. Add a StrictMode `detectDiskReads`/`detectNetwork` check in debug tests if practical.

---

### M4 — Corrupt or non-image bytes can be committed as the live wallpaper and never retried

**Severity:** Medium
**Status:** Confirmed by code path.
**Category:** data integrity / wallpaper / malformed data

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/storage/ImageDownloader.kt:37-45` — rejects only `text/html` and oversized bodies; accepts `application/octet-stream`, `text/plain`, truncated JPEGs, and arbitrary bytes.
- `android/app/src/main/java/app/mohakash/apod/data/storage/ImageDownloader.kt:63` — only checks `target.length() == 0`.
- `android/app/src/main/java/app/mohakash/apod/data/storage/WallpaperStorage.kt:65-97` — `commit` only checks that the source file is non-empty and that the URL looks like an image; no decode/validation of the bytes before writing metadata.
- `android/app/src/main/java/app/mohakash/apod/work/ApodSyncEngine.kt:110-114` — if the current `sourceUrl` and `date` match, it returns `Unchanged` without downloading.
- `android/app/src/main/java/app/mohakash/apod/work/ApodSyncEngine.kt:151-168` — a successful download + `commit` is reported as `INSTALLED`.
- `android/app/src/main/java/app/mohakash/apod/wallpaper/MohakashWallpaperService.kt:233-273` — if `decode` fails, `renderNow` returns false and leaves the previous bitmap/file state; a later surface recreation has no valid bitmap.

**Failure scenario**

A CDN error page, a truncated download, or an `image/jpeg` response containing an HTML/JSON error body is returned with HTTP 200 for a URL ending in `.jpg` (the extension check passes). `ImageDownloader` writes it; `WallpaperStorage.commit` accepts it; `WallpaperMeta` now points at the bad file. The wallpaper engine's `BitmapFactory.decodeFile` returns null. The old bitmap may remain until the surface is destroyed; after a rotation/reboot/wallpaper restart, the screen shows the background color. On the next sync, `sync()` sees `sourceUrl`/`date` unchanged and never re-downloads, so the wallpaper stays blank until the next APOD date.

**Impact**

Silent loss of the live wallpaper, with no user-visible error; a daily background job that "succeeds" while showing nothing. Also a gallery entry can contain a non-image payload (M4 adjacent).

**Minimal fix**

Validate the bytes before commit:

```kotlin
val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
BitmapFactory.decodeFile(source.absolutePath, opts)
if (opts.outWidth <= 0 || opts.outHeight <= 0) return Result.failure(IOException("not a decodable image"))
```

Do this in `WallpaperStorage.commit` (or `ImageDownloader` after download) and keep the old metadata/file on failure. Also require `Content-Type` to start with `image/` (allow missing for compatibility, but log/reject suspicious types), and reject `image/svg+xml` if the decoder cannot handle it. Keep the previous `wallpaper-meta.json` and image until the new set is validated.

---

### M5 — Room: destructive migration is the only fallback, schema is not exported, and DB corruption has no recovery

**Severity:** Medium
**Status:** Confirmed configuration; future data-loss risk.
**Category:** Room / migrations / data loss / corruption

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/local/db/MohakashDatabase.kt:15-23` — `version = 1`, `exportSchema = false`.
- `android/app/src/main/java/app/mohakash/apod/data/local/db/MohakashDatabase.kt:38-43` — `.fallbackToDestructiveMigration(dropAllTables = true).build()`.
- No `.addMigrations(...)`, no `RoomDatabase.Callback`, no `DatabaseErrorHandler`, and no `onOpen`/`onDestructiveMigration` hook.

**Failure scenario**

A later release adds a column or an index (e.g. to `FavouriteEntity`). The user updates; Room sees a version bump and no migration, drops all tables, and the "Saved" list becomes empty without any prompt. Separately, a power loss or an interrupted restore leaves `mohakash.db` corrupt; the first `favouriteDao`/`cachedEntryDao` query throws `SQLiteDatabaseCorruptException`, the feed/saved screen crashes, and the only fix is clearing app data (which also loses favourites).

**Impact**

Silent loss of the only user-generated data in the app (saved pictures). Crash-loop on DB corruption. `exportSchema=false` makes migration testing and review impossible, so this will remain unverified.

**Minimal fix**

- Set `exportSchema = true` and check the generated schemas into `android/app/schemas` (the KSP arg already points there).
- Add real `Migration` objects for each version and `MigrationTestHelper` tests.
- If destructive migration must stay as a last resort, add `onDestructiveMigration`/logging and a user-visible explanation; remove `dropAllTables = true` once favourites exist (or back up favourites before dropping).
- Add a defensive `DatabaseErrorHandler`/open retry that quarantines a corrupt DB (`mohakash.db.corrupt`) and recreates it, rather than crashing forever. Consider `setJournalMode(TRUNCATE)` for single-file backup safety (see M6).

---

### M6 — Backup rules omit SQLite `-wal`/`-shm`, risking loss of recent favourites on restore

**Severity:** Medium
**Status:** Confirmed configuration; runtime impact depends on WAL checkpoint state.
**Category:** backup / data integrity / data loss

**Locations**

- `android/app/src/main/res/xml/backup_rules.xml:3-8` — includes only `database` path `mohakash.db` and `file` path `wallpaper/`.
- `android/app/src/main/res/xml/data_extraction_rules.xml:3-18` — same includes for `cloud-backup` and `device-transfer`.
- `android/app/src/main/java/app/mohakash/apod/data/local/db/MohakashDatabase.kt:38-43` — Room default journal mode (`AUTOMATIC` → WAL on most devices; `TRUNCATE` on low-RAM devices).
- `android/app/src/main/AndroidManifest.xml:15-17` — `allowBackup="true"` plus both rule files.

**Failure scenario**

The user saves a new picture; Room commits the insert to the WAL but the main DB file has not checkpointed. Auto Backup (or a device-transfer) copies only `mohakash.db`. On the new device, the restored DB is valid but at the last checkpoint: the recent saved picture is gone. If the backup is taken while the DB is open and only the main file is captured, recent metadata can be silently missing. The absence of `-shm` is normally tolerated by SQLite, but the missing WAL is real data loss.

**Impact**

Data loss of recently saved favourites (and recent cached entries) after restore/transfer, with no warning. No corruption of unrelated data. The wallpaper directory is included, so the picture file can exist while its metadata/date is stale.

**Minimal fix**

Either:

- Include `mohakash.db-wal` and `mohakash.db-shm` in both `backup_rules.xml` and both `data_extraction_rules.xml` sections, or
- Set `RoomDatabase.Builder.setJournalMode(JournalMode.TRUNCATE)` so the DB is a single file, accepting the write-performance trade-off, or
- Register a `BackupAgent`/`onBeforeBackup` checkpoint (`PRAGMA wal_checkpoint(FULL)`) before backup.

Also add a restore test: save a favourite, force backup, restore, assert the favourite is present. `allowBackup=true` is otherwise reasonable for this app (no secrets), but the DataStore is intentionally excluded — document that settings/custom endpoint are not backed up.

---

### M7 — DataStore read failure becomes "empty defaults" and terminates the settings flow

**Severity:** Medium
**Status:** Confirmed by code path.
**Category:** DataStore / data integrity / endpoint isolation

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/local/prefs/PreferencesSettingsStore.kt:40-50` — `dataStore.data.catch { is CorruptionException -> emit(emptyPreferences()); is IOException -> emit(emptyPreferences()); else -> throw }`. `emit` followed by flow completion means the collector never sees later writes.
- `android/app/src/main/java/app/mohakash/apod/data/local/prefs/PreferencesSettingsStore.kt:52` — `current()` uses `settings.first()`; on an IOException it returns defaults.
- `android/app/src/main/java/app/mohakash/apod/data/remote/BaseUrlResolver.kt:25-44` — if `customApiBaseUrl` is null (as it is in the empty defaults), the app silently uses the built-in candidates.
- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedViewModel.kt:884-895` — the endpoint observer sees `customApiBaseUrl` change from non-null to null, calls `archiveIndexRepository.clear()`, resets the feed, and reloads latest/index.
- `android/app/src/main/java/app/mohakash/apod/ui/detail/DetailViewModel.kt:84-95` — same observation, reloads the entry.
- `android/app/src/main/java/app/mohakash/apod/ui/archive/ArchiveViewModel.kt:81-97` — no endpoint observer at all, so it can retain the previous endpoint's list (see L9).

**Failure scenario**

A transient I/O error (storage pressure, a bad sector, a restore in progress) occurs while reading `mohakash_settings.preferences_pb`. All collectors receive `emptyPreferences()` and complete. `BaseUrlResolver` then sees no custom URL and fetches `/latest.json` from the built-in GitHub Pages endpoint. `ApodRepository.fetchLatest` caches that response under the same `latest` key and persists its record in `cached_entries`. When the storage recovers, the app has no live settings flow to notice; after a restart the custom endpoint returns, but the cached latest/entries came from the built-in endpoint. The archive index cache was also cleared and refetched (up to 5 MiB on a metered connection).

**Impact**

Cross-endpoint data mixing despite the explicit "clear caches on endpoint change" design; user settings appear reset; live settings updates stop until the process restarts; unnecessary network/data use. The user may believe the app is still on their custom endpoint while it is not.

**Minimal fix**

- Do not convert `IOException` to defaults. Either retry with backoff (`.retryWhen`) or surface an "unavailable" state; distinguish `CorruptionException` (replace) from transient I/O (retry/rethrow).
- Make the endpoint resolver **fail closed**: if a custom endpoint was previously configured but the settings read failed, abort the request instead of silently using built-ins. Persist a tiny "endpoint configured" marker outside DataStore if necessary, or treat `null` custom + `IOException` differently from `null` custom + successful read.
- Track whether the last settings value was successfully loaded; only clear caches on a *user-initiated* endpoint change, not on a flow error.

---

### M8 — Settings writes silently fail, but the UI reports success

**Severity:** Medium
**Status:** Confirmed by code path.
**Category:** DataStore / data integrity / user trust

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/local/prefs/PreferencesSettingsStore.kt:82-99` — `edit()` catches `CorruptionException` and `IOException` and returns `Unit`; callers cannot tell success from failure.
- `android/app/src/main/java/app/mohakash/apod/ui/settings/AdvancedViewModel.kt:127-150` — after `setCustomApiBaseUrl`/`setPreferredBaseUrl` (which may have silently failed), it sets `savedCustomUrl = customUrl` and emits `AdvancedEvent.Saved`.
- `android/app/src/main/java/app/mohakash/apod/ui/settings/AdvancedViewModel.kt:131-136` — caches are cleared *before* the settings write, so a failed write leaves the old endpoint active with an empty cache.
- Same masking pattern for `SettingsViewModel.kt:87-136` (toggles/HD/wifi/hide videos) and `DetailViewModel.kt:236-251` (scaling/update daily).

**Failure scenario**

The disk is full or read-only (or DataStore replacement fails). The user enters a new self-hosted endpoint and taps Save. `clearMetadataCache()` and `archiveIndexRepository.clear()` run; `setCustomApiBaseUrl` throws `IOException`, which is swallowed; `syncController.runOnce()` enqueues a fetch; the UI shows "Saved" and displays the new URL. On the next launch the endpoint is still the old one (or the default), and the caches are gone. The user cannot tell the save failed.

**Impact**

False success for security-relevant configuration (which server the app trusts), lost caches, confusing behavior. No direct exploit, but a user could believe traffic goes to their endpoint while it goes to the default.

**Minimal fix**

- Change `SettingsStore` write methods to return `Boolean`/`Result<Unit>` (or throw), and have `edit()` rethrow/return failure. At minimum, expose `lastWriteFailed` as a `StateFlow`.
- In `AdvancedViewModel.apply`, write the setting first, verify it was persisted (re-read), then clear caches; only emit `Saved` after success. On failure, show `SAVE_FAILED` and do not mutate `savedCustomUrl`.
- Apply the same pattern to `setLastSync` (currently a failed write just causes repeated refreshes) and the toggle setters.

---

### M9 — Concurrent "save to gallery" writes to one fixed cache file

**Severity:** Medium
**Status:** Confirmed by code path; easy to trigger with a double tap.
**Category:** images / file races / data integrity

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/repository/ImageRepository.kt:24-46` — `target = File(cacheDir(), fileName)` where `fileName` is deterministic (`APOD_YYYY-MM-DD.jpg`), no per-request temp file, no mutex.
- `android/app/src/main/java/app/mohakash/apod/data/storage/ImageDownloader.kt:46-71` — writes directly into `target` via `target.outputStream()`; on any failure deletes `target`.
- `android/app/src/main/java/app/mohakash/apod/data/storage/GallerySaver.kt:30-41` — copies `source` into MediaStore/legacy pictures after download.
- `android/app/src/main/java/app/mohakash/apod/ui/detail/DetailViewModel.kt:172-180` and `FeedViewModel.kt:352-364` — each tap launches an independent coroutine with no "in progress" guard.

**Failure scenario**

The user double-taps "Save image". Two coroutines download the same URL into the same `cacheDir/images/APOD_2026-10-27.jpg` path. Both `outputStream()` calls truncate and interleave writes; one finishes and `GallerySaver` starts copying while the second is still writing (corrupt/truncated image in the gallery), or the first coroutine's `finally { picture.delete() }` removes the file while the second is copying (failure). On a slow network the window is large.

**Impact**

A corrupt image can be published to the user's gallery under a normal filename; the user may not notice until later. The same race can produce spurious "Could not save" errors. No memory-safety issue.

**Minimal fix**

- Download to a unique per-request temp file (`File.createTempFile("gallery-", ".tmp", cacheDir)`) and `renameTo(target)` only after success.
- Guard saves with a `Mutex`/`Semaphore` keyed by `fileName` (or an in-flight `Set<String>` in the ViewModel) and return early/queue for the same date.
- Have `GallerySaver.save` read from the immutable temp file, then delete it; never delete a file another operation may still be reading.

---

### L1 — API `date` is not validated; route/DB poisoning and API-controlled crash

**Severity:** Low
**Status:** Confirmed by code path.
**Category:** malformed data / navigation / deep links

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/remote/ApiMapper.kt:29` — `date = dto.date.clean() ?: ""` for `latest.json`.
- `android/app/src/main/java/app/mohakash/apod/data/remote/ApiMapper.kt:52-64` — index rows keep any non-blank date; only blank dates are filtered.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:44-55` — the latest record is cached and inserted as a primary key without date validation.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:115-121` — year records are mapped and cached without date validation.
- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedPageContent.kt:288-306` and `android/app/src/main/java/app/mohakash/apod/ui/archive/ArchiveScreen.kt:220` — pass the API date to `onOpenEntry`.
- `android/app/src/main/java/app/mohakash/apod/ui/navigation/MohakashApp.kt:156` and `:176` — `navController.navigate(Routes.detail(date))`.
- `android/app/src/main/java/app/mohakash/apod/ui/navigation/Routes.kt:20` — `fun detail(date: String) = "detail/$date"` (no URI encoding).
- `android/app/src/main/java/app/mohakash/apod/ui/detail/DetailViewModel.kt:99-104` — accepts any non-blank date.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:150-155` — only `take(4).toIntOrNull()` is used for the year, so path traversal is not possible, but the record lookup fails.

**Failure scenario**

A compromised endpoint returns an index row with `"date":"2026-10-26/.."` or `"date":"x"`. It passes `isNotBlank()` and is displayed. Tapping it calls `navigate("detail/2026-10-26/..")`; the navigation graph's `detail/{date}` pattern does not match the extra path segment, so `NavController.navigate` can throw `IllegalArgumentException` from the click callback (depending on the Navigation version's URI matching), which would crash the app. Even if it does not throw, the detail screen receives a malformed date and only shows an error. A blank date from `latest.json` is stored as a `cached_entries` primary key (`""`), and the feed may try to open `detail/` on a blank page.

**Impact**

An API-controlled crash on tap and a poisoned cache record. Requires a malformed response (endpoint compromise/custom endpoint); no privilege escalation. Low.

**Minimal fix**

- Validate every incoming date in `ApiMapper` with a strict `^\d{4}-\d{2}-\d{2}$` plus a real-calendar check; drop the record or fall back to a placeholder instead of `""`.
- If `latest.json` has no valid date, reject the document (or use the `generated_at` date) rather than caching an empty key.
- Encode route arguments (`Uri.encode(date)`) or use typed navigation with a `NavType`/custom serializer; wrap `navigate` in a `runCatching`/validation check that falls back to the archive.
- Add tests for invalid dates in `ApiMapperTest` and `DeepLinkParserTest`.

---

### L2 — Wallpaper temp files are not cleaned when `commit` fails

**Severity:** Low
**Status:** Confirmed by code path.
**Category:** disk space / process death

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/storage/WallpaperStorage.kt:77-91` — `incoming` and `metaIncoming` are created and written, but there is no `finally` deleting them on failure; `cleanupOrphans` (lines 107-111) runs only on success.

**Failure scenario**

The disk fills during `source.copyTo(incoming)` or `metaIncoming.writeText`. `runCatching` returns failure; the partially written `incoming-*.tmp` (up to 64 MiB) remains. Repeated failures (e.g. a persistent full disk, or a server that always returns a large body) accumulate temp files. The old wallpaper is correctly preserved, but storage is consumed and the backup includes `wallpaper/` (M6), bloating cloud backups.

**Minimal fix**

Wrap the temp writes in `try/finally` and delete both temp files on any failure; or create them in `cacheDir` and only move the final validated file into `filesDir/wallpaper`. Also sweep `incoming-*.tmp` in `cleanupOrphans`/startup.

---

### L3 — `WallpaperService` blocks the main thread for up to 2 s and can drop the bitmap without recycling

**Severity:** Low
**Status:** Confirmed by code path.
**Category:** ANR / memory / wallpaper lifecycle

**Locations**

- `android/app/src/main/java/app/mohakash/apod/wallpaper/MohakashWallpaperService.kt:147-181` — `onSurfaceDestroyed` calls `runBlocking { withTimeoutOrNull(2000) { drained.await() } }`.
- `android/app/src/main/java/app/mohakash/apod/wallpaper/MohakashWallpaperService.kt:189-213` — `onSurfaceRedrawNeeded` does the same.
- `android/app/src/main/java/app/mohakash/apod/wallpaper/MohakashWallpaperService.kt:172-181` — on timeout it sets `bitmap = null` without `recycle()` and without synchronizing with the render thread.

**Failure scenario**

The render thread is decoding a large image (up to 64 MiB file, sampled) when the surface is destroyed. `onSurfaceDestroyed` blocks the main thread for 2 s waiting for the drain; if the decode takes longer, it gives up and nulls `bitmap` while the render thread may still be using it. The bitmap is not recycled (native memory retained until GC), and the next surface creation decodes again. Repeated surface changes can leak native bitmap memory or cause an ANR in the system wallpaper host.

**Impact**

ANR/jank in the system wallpaper preview and potential memory growth; no data exposure.

**Minimal fix**

- Do not block the main thread: cancel/join via a dedicated `Handler`/coroutine with a strict deadline, and make the render thread own all bitmap state (`@Volatile` or `AtomicReference`).
- On timeout, post the recycle to the render thread or keep the bitmap until it can be safely released; do not write `bitmap` from the main thread.
- Decode to a maximum dimension (already done via `inSampleSize`) and consider a hard `inSampleSize` minimum for very large sources.

---

### L4 — "Wi-Fi only" uses transport type, not the metered capability

**Severity:** Low
**Status:** Confirmed by code path.
**Category:** data usage / policy correctness

**Locations**

- `android/app/src/main/java/app/mohakash/apod/core/NetworkMonitor.kt:24-30` — returns true for `TRANSPORT_WIFI` or `TRANSPORT_ETHERNET`; it does not check `NET_CAPABILITY_NOT_METERED`.
- `android/app/src/main/java/app/mohakash/apod/work/SyncPolicy.kt:27-35` — `wifiOnly` allows a download when `unmetered` is true.
- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedLoadPolicy.kt` (via `FeedViewModel` `isMetered = !isUnmetered()`) — the 5 MiB index is considered unmetered on any Wi-Fi.

**Failure scenario**

The user enables "Wi-Fi only" and connects to a metered Wi-Fi hotspot (hotel, airplane, phone hotspot). The app downloads the archive index and a new wallpaper over that connection, consuming paid data. The setting's label ("Wi-Fi only") is technically honored but its intent ("avoid mobile data") is not.

**Impact**

Unexpected data charges; no security impact.

**Minimal fix**

Use `capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)` (and optionally `NET_CAPABILITY_VALIDATED`) instead of transport checks. Keep the transport check only as a fallback for old API levels (minSdk 24 supports the capability).

---

### L5 — Wallpaper auto-update fails open when settings cannot be read

**Severity:** Low
**Status:** Confirmed by code path; combined with M7/M5 it becomes more likely.
**Category:** user consent / wallpaper

**Locations**

- `android/app/src/main/java/app/mohakash/apod/work/SyncPolicy.kt:18-19` — `(settings?.updateAutomatically ?: true) && isWallpaperActive`.
- `android/app/src/main/java/app/mohakash/apod/work/ApodSyncWorker.kt:24` — `runCatching { settingsStore.current() }.getOrNull()`.
- `android/app/src/main/java/app/mohakash/apod/data/local/prefs/PreferencesSettingsStore.kt:103` — default `updateAutomatically = true`.

**Failure scenario**

A settings read fails (I/O error or corrupt DataStore). `settings` is null; `shouldUpdateWallpaper` returns `true` if the wallpaper is active, so the worker replaces the wallpaper even though the user may have disabled automatic updates. The same default applies after a corruption reset, where the user's "off" preference is lost.

**Impact**

The wallpaper changes without consent (minor privacy/UX). No data loss.

**Minimal fix**

Fail closed for the unknown-settings case: `settings?.updateAutomatically == true && isWallpaperActive`. Treat a null settings object as "do not change the wallpaper"; only the onboarding/first-run path should explicitly opt in. Keep the default for a successfully read store where the key is absent (first run).

---

### L6 — `POST_NOTIFICATIONS` is declared but never requested

**Severity:** Low
**Status:** Confirmed by code path.
**Category:** notifications / privacy / functional

**Locations**

- `android/app/src/main/AndroidManifest.xml:7` — declares `POST_NOTIFICATIONS`.
- `android/app/src/main/java/app/mohakash/apod/data/storage/NotificationHelper.kt:139-140` — posts via `NotificationManager.notify` with no permission check/request.
- `android/app/src/main/java/app/mohakash/apod/ui/components/Permissions.kt:24-50` — the only runtime permission helper requests `WRITE_EXTERNAL_STORAGE` (API ≤ 28); no `POST_NOTIFICATIONS` request anywhere.

**Failure scenario**

On Android 13+ the user installs the app and never sees the notification permission prompt. The "wallpaper updated" notification is silently suppressed by the system (`notify` is a no-op for an app without the permission). The app looks broken.

**Impact**

Functional loss only; no privacy leak. The app does not spam notifications.

**Minimal fix**

Request `POST_NOTIFICATIONS` at the point where the user enables automatic wallpaper updates (not at cold start), and show a rationale. If denied, keep the feature working but hide the notification expectation. Alternatively, declare the permission only if notifications are essential; the current manifest/README claim is inconsistent.

---

### L7 — App Links certificate coverage is incomplete for Play/F-Droid builds

**Severity:** Low
**Status:** Confirmed by artifact comparison.
**Category:** deep links / distribution / hijacking

**Locations**

- `web/public/.well-known/assetlinks.json:7-8` — a single SHA-256 fingerprint.
- `android/app/src/main/AndroidManifest.xml:44-66` — two `autoVerify` filters for `https://${websiteHost}`.
- `android/app/build.gradle.kts` (application id `mohakash.stargzr.space`) and `android/keystore.properties` (release signing).
- `fdroid/metadata/mohakash.stargzr.space.yml` — F-Droid signs its own APK; Play App Signing would use a different key.

**Evidence**

I verified the fingerprint in `assetlinks.json` matches the local release keystore certificate (`apksigner verify --print-certs` → `04:41:88:...:C0:AB`). The same JSON contains no F-Droid or Play App Signing certificate.

**Failure scenario**

A user installs the F-Droid or Play build. App Links verification for `mohakash.netlify.app` fails. Archive links opened from a browser/chat may show a disambiguation dialog or open the browser, and a malicious installed app that declares the same host could be selected as the handler (the user must choose it; Android does not silently let an unverified app take over a verified one). This weakens the deep-link UX/trust story for the two distribution channels.

**Impact**

Deep links can be hijacked by a malicious app on F-Droid/Play installs, or simply fail to open in the app. Low because the user must install/choose the malicious app and the links contain no secrets.

**Minimal fix**

Add the Play App Signing certificate and the F-Droid signing certificate fingerprints to `web/public/.well-known/assetlinks.json` and redeploy. Keep the local release fingerprint. Add the fingerprints to `README`/release checklist and a CI test that compares the deployed JSON to the expected set.

---

### L8 — Release keystore and plaintext passwords are present in the workspace (untracked)

**Severity:** Low
**Status:** Confirmed local hygiene; not committed.
**Category:** secrets / supply chain

**Locations**

- `/home/himel/Documents/mohakash/android/keystore.properties` (182 bytes, mode 0600) — `storePassword`/`keyPassword` in plain text.
- `/home/himel/Documents/mohakash/android/keystore/mohakash-release.jks` (mode 0600).
- `/home/himel/Documents/mohakash/.gitignore` — `keystore.properties`, `keystore/`, `*.jks`, `*.keystore` are ignored.
- `git ls-files` confirms only `android/keystore.properties.example` is tracked; no key or password is in git history.

**Failure scenario**

The checkout is copied to cloud sync, a backup, a build cache, or a shared machine. An attacker who obtains both files can sign APKs that Android accepts as updates for `mohakash.stargzr.space` (if the user sideloads them) and can publish to any channel that uses that key. The app itself is not exposed at runtime.

**Impact**

Supply-chain compromise of the release identity, not a runtime vulnerability. The files are correctly untracked and permission-restricted, so this is a hardening item.

**Minimal fix**

- Store the keystore and passwords in a password manager/CI secret store; keep only the `.example` in the repo.
- Do not include the `android/keystore` directory in workstation/cloud backups without encryption.
- Rotate the key only if exposure is suspected; Play App Signing can mitigate upload-key compromise.
- Add a pre-commit/CI check that `keystore.properties`/`*.jks` are not staged.

---

### L9 — Archive screen can keep the previous endpoint's entries after a failed refresh

**Severity:** Low
**Status:** Confirmed by code path.
**Category:** endpoint isolation / stale data

**Locations**

- `android/app/src/main/java/app/mohakash/apod/ui/archive/ArchiveViewModel.kt:81-97` — `val previous = _state.value.entries`; if `ensureLoaded()` returns null, it keeps `entries` and sets `isOffline = previous.isNotEmpty()`, `hasError = previous.isEmpty()`.
- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedViewModel.kt:884-895` — clears the shared archive index cache and resets the feed on endpoint change.
- `android/app/src/main/java/app/mohakash/apod/ui/settings/AdvancedViewModel.kt:131-138` — clears metadata and index before writing the new endpoint.
- `ArchiveViewModel` has no observer of `customApiBaseUrl` (unlike `FeedViewModel`/`DetailViewModel`).

**Failure scenario**

The user changes the endpoint while the Archive tab's ViewModel is alive in the back stack, then the new endpoint is unreachable. `ArchiveViewModel.load()` gets null from `ensureLoaded` (cache cleared, refresh failed) and keeps the old `entries` list, showing the old server's archive with an "offline" flag. Tapping an old entry opens a detail screen that fetches from the new endpoint and fails, or displays a cached old record.

**Impact**

Cross-endpoint data mixing in the UI, contrary to the isolation design. Low because the data is public APOD metadata and the feed/detail still prefer the new endpoint.

**Minimal fix**

Give `ArchiveViewModel` the same endpoint observer as `FeedViewModel` (clear `entries`, reset selection, reload), or have `load()` clear `entries` when `ensureLoaded` returns null and the cache was just cleared. Track an endpoint generation/token and discard results from a previous generation.

---

### L10 — In-memory/DB caches grow without eviction

**Severity:** Low (hardening)
**Status:** Confirmed by code; no attacker required.
**Category:** memory/disk / OOM hardening

**Locations**

- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedViewModel.kt:78` — `yearRecords: Map<Int, List<ApodEntry>>` retains every fetched year for the life of the ViewModel.
- `android/app/src/main/java/app/mohakash/apod/ui/feed/FeedViewModel.kt:746-762` — `putYear` adds without eviction.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:114-121` — `fetchArchiveYear` upserts all records into `cached_entries`; no eviction.
- `android/app/src/main/java/app/mohakash/apod/data/storage/CacheManager.kt:18-23` — "clear cache" does not clear `cacheDir/feed`, `cached_entries`, or `cache_documents`, and `cacheSizeBytes` underreports.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:96-107` — the latest document is read whole (up to 16 MiB) into a `StringBuilder`; a corrupt/huge row can allocate a lot.

**Failure scenario**

A user scrolls through many years; each year's ~365 full explanations are retained in memory (tens of MB of Kotlin objects) and in SQLite. On a low-RAM device the feed can OOM, and "clear cache" does not reclaim the DB/feed portion. A malicious `latest.json` near the 16 MiB cap can allocate a large string + parsed objects (the OOM path is not caught in `fetchLatest`).

**Impact**

Memory pressure/jank/OOM; cache size UI is misleading. Low.

**Minimal fix**

Bound `yearRecords` to the current + adjacent years or use a small LRU; add a size/row cap or eviction for `cached_entries` (keep favourites separately); include `cacheDir/feed` and DB cache bytes in `CacheManager`; catch `OutOfMemoryError` at the repository boundary or enforce per-field length limits (e.g. max explanation length) when mapping.

---

### L11 — Notification title is unbounded and the notification body has no content intent

**Severity:** Low (hardening)
**Status:** Confirmed by code path.
**Category:** notifications / robustness

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/storage/NotificationHelper.kt:66` — `val title = entry.title` (unbounded API string).
- `android/app/src/main/java/app/mohakash/apod/data/storage/NotificationHelper.kt:104-139` — no `.setContentIntent(openAppPending)`; tapping the notification body does nothing (only the action buttons work).
- `android/app/src/main/java/app/mohakash/apod/data/storage/NotificationHelper.kt:139-140` — `notify` exceptions are not caught.

**Failure scenario**

A malicious/oversized `title` produces a large `Notification` extra. On some Android versions the `NotificationManager` binder transaction can fail (`TransactionTooLargeException`) or the notification is dropped; an exception from `notify` propagates through `ApodSyncEngine.install` and turns a successful wallpaper install into a failed sync. Tapping the body does not open the app, which is a UX defect.

**Impact**

Rare notification failure / false sync failure; low.

**Minimal fix**

Truncate `title` (e.g. 200 chars) and `description` (already 512); wrap `notify` in `runCatching`; add `.setContentIntent(openAppPending)`.

---

### L12 — Wallpaper metadata `fileName` is trusted for a path join

**Severity:** Low (hardening; requires root/backup tampering)
**Status:** Confirmed by code path.
**Category:** file path / backup trust

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/storage/WallpaperStorage.kt:47-50` — `File(directory, meta.fileName)` with `meta.fileName` parsed from `wallpaper-meta.json`.
- `android/app/src/main/java/app/mohakash/apod/data/storage/WallpaperStorage.kt:54-57` — `readMeta()` only parses JSON; no validation of `fileName`.
- `android/app/src/main/res/xml/data_extraction_rules.xml:7-9` and `backup_rules.xml:6-8` — the `wallpaper/` directory (including `wallpaper-meta.json`) is backed up/restored.

**Failure scenario**

An attacker with the ability to supply a crafted backup/device-transfer image (root/physical access; the backup is otherwise protected) sets `fileName` to `../../databases/mohakash.db` or `/sdcard/DCIM/...`. `currentImageFile()` returns that path; the wallpaper service attempts to decode it. It will not exfiltrate the file, but it can display a local image outside the app's wallpaper directory and may read external storage on API ≤ 28 where `WRITE_EXTERNAL_STORAGE` is granted.

**Impact**

Low; no write primitive (the path is only read). A path-traversal read outside the intended directory under a strong adversary.

**Minimal fix**

In `readMeta`/`currentImageFile`, require `fileName` to match `^wallpaper-[0-9a-f]+\.[a-z0-9]{1,5}$` and verify `file.canonicalPath` starts with `directory.canonicalPath + File.separator`; ignore meta that points outside. Do not include the wallpaper metadata in backup unless needed.

---

### L13 — Debug builds log full request URLs, including the custom endpoint

**Severity:** Low (hardening)
**Status:** Confirmed by code path.
**Category:** logging / privacy

**Locations**

- `android/app/src/main/java/app/mohakash/apod/AppContainer.kt:57-60` — `HttpLoggingInterceptor` with `Level.BASIC` when `BuildConfig.DEBUG`.
- The shared client is used for API documents and image downloads.

**Failure scenario**

A debug build is installed and logs are captured (adb, bug report, third-party log reader). The custom endpoint URL, image URLs, and API paths appear in logcat. If a user embeds a token in a custom endpoint path (not supported by validation, but possible in the host/path), it is logged. Release builds use `Level.NONE`.

**Impact**

Low; debug-only information disclosure.

**Minimal fix**

Keep `BASIC` but redact query/userinfo (none allowed by `ApiEndpoint`, but defense in depth), or use a custom logger that logs only the host and status. Never log bodies.

---

### L14 — `fetchEntry` swallows `CancellationException`

**Severity:** Low (hardening)
**Status:** Confirmed by code path.
**Category:** coroutines / cancellation

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:150-155` — `runCatching { fetchArchiveYear(year) }.getOrElse { cachedArchiveYear(year) }`; `runCatching` catches `Throwable`, including `CancellationException`.
- `android/app/src/main/java/app/mohakash/apod/ui/detail/DetailViewModel.kt:104-105` — `loadJob?.cancel()` then reloads; a cancelled in-flight fetch falls back to the DB instead of aborting.
- Similar `runCatching` usage exists in `FeedViewModel` (`:713`, `:722`, `:729`) and `ArchiveViewModel` (`:111`).

**Failure scenario**

The user navigates between detail entries quickly. The previous `loadJob` is cancelled, but `fetchEntry` catches the cancellation and performs a `cachedArchiveYear` DB query, then continues to publish a result for the old date. The ViewModel has guards (`loadedDate`), so the visible impact is extra DB/network work and stale side effects rather than a crash.

**Impact**

Wasted work and potential stale-state races; no security impact.

**Minimal fix**

Use `catch (c: CancellationException) { throw c }` before the fallback, or a helper `runCatchingCancellable` that rethrows cancellation. Audit all `runCatching` blocks in suspend code for the same issue.

---

### L15 — No certificate pinning / network security config (hardening)

**Severity:** Low (hardening; not a confirmed bug)
**Status:** Hardening suggestion.
**Category:** TLS

**Locations**

- `android/app/src/main/AndroidManifest.xml:24` — `usesCleartextTraffic="false"` (good).
- No `android:networkSecurityConfig` and no `<certificate-pinning>`; `AppContainer.kt:51-63` does not configure a `CertificatePinner`.

**Discussion**

The default trust anchors are the system CAs, which is appropriate for public APOD data. User-installed CAs are not trusted by default for targetSdk ≥ 24 unless a config opts in. Pinning the GitHub Pages/NASA asset hosts would reduce MITM risk on hostile networks but adds an operational failure mode (certificate rotation, CDN changes) and is not necessary for public, non-authenticated data. The custom endpoint cannot be pinned because it is user-supplied.

**Recommendation**

Document the decision. If the threat model includes hostile CAs for the built-in endpoint, add a `network_security_config.xml` with a pin set for the built-in API host only, plus a backup pin and a clear rotation procedure. Do not pin user-supplied endpoints.

---

### L16 — Archive-index cache size check mixes characters and bytes; quarantine is not cleared by "clear cache"

**Severity:** Low (hardening)
**Status:** Confirmed by code path.
**Category:** disk space / cache hygiene

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/repository/ArchiveIndexRepository.kt:333-343` — `raw.length` (UTF-16 chars) is compared with `ARCHIVE_INDEX_MAX_CACHE_BYTES` (16 MiB), then `writeText` writes UTF-8 and checks the actual byte size afterwards.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ArchiveIndexRepository.kt:383-398` — a corrupt cache is renamed to `archive-index.json.corrupt` (up to 16 MiB) and kept.
- `android/app/src/main/java/app/mohakash/apod/data/storage/CacheManager.kt:18-23` — `clear()` only removes `image_cache`, `http`, and `images`; it does not remove `cacheDir/feed` or `.corrupt`.

**Failure scenario**

A compromised endpoint returns ~5M four-byte UTF-8 characters. The character check passes (5M < 16M), the app writes ~20 MiB to a temp file, then rejects it after the write. On a nearly full disk this can fill the cache before the failure. A quarantined 16 MiB file is never reclaimed by the "clear cache" button, and the size UI does not show it.

**Impact**

Temporary disk usage/eviction pressure; no data loss beyond the cache.

**Minimal fix**

Check `raw.toByteArray(Charsets.UTF_8).size` (or, better, stream the decoded response into the temp file with a counting sink and write the meta from the same stream) before accepting; cap the decoded document at a smaller limit (the real index is ~5.2 MiB). Include `cacheDir/feed` and `.corrupt` in `CacheManager` and in `cacheSizeBytes`.

---

### L17 — `Json` is lenient and the schema version is not enforced

**Severity:** Low (hardening)
**Status:** Confirmed configuration.
**Category:** JSON parsing / malformed data

**Locations**

- `android/app/src/main/java/app/mohakash/apod/AppContainer.kt:39-43` — `ignoreUnknownKeys = true`, `coerceInputValues = true`, `isLenient = true`.
- `android/app/src/main/java/app/mohakash/apod/data/remote/dto/ApodDtos.kt:15` — `schema_version` is a field but is never compared in `ApiMapper`/repositories.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ApodRepository.kt:29-31` — `ApiMapper.toDomain` accepts missing fields as `""`/`"Untitled"`.

**Discussion**

Lenient JSON accepts non-standard payloads (unquoted strings, loose number syntax). Combined with no schema-version check, an endpoint can change field names/semantics and the app will silently display blank dates/titles or treat an unrecognized `image_status` as `OK` (`ApiMapper.kt:71-90`). This is not code execution, but it makes malformed-data behavior unpredictable and complicates incident response.

**Recommendation**

Use strict JSON (`isLenient = false`) for API documents, keep `ignoreUnknownKeys = true` for forward compatibility, and validate `schema_version` against the supported major version (reject/log unsupported). Validate required fields (`date`, `title`, `media_type`) explicitly.

---

### L18 — Catching `OutOfMemoryError` and continuing is unsafe

**Severity:** Low (hardening)
**Status:** Confirmed code pattern.
**Category:** OOM / robustness

**Locations**

- `android/app/src/main/java/app/mohakash/apod/data/repository/ArchiveIndexRepository.kt:229-231` — catches `OutOfMemoryError` in `refresh()` and returns a failure.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ArchiveIndexRepository.kt:296-299` — catches OOM while reading/quarantining the cache.
- `android/app/src/main/java/app/mohakash/apod/data/repository/ArchiveIndexRepository.kt:371-374` — catches OOM while writing meta.
- `android/app/src/main/java/app/mohakash/apod/data/storage/WallpaperStorage.kt:54-57` — `runCatching` catches `Throwable`, including OOM, around `readText`.

**Discussion**

After an OOM, the process is in an undefined state; performing file I/O and allocating a new exception/string can fail again or mask the real cause. The archive cache quarantine is best-effort and the intent is good, but the safe pattern is to reduce allocations before the failure (size caps, streaming, per-field limits) and let a true OOM terminate/crash rather than continue with a partially initialized heap. `WallpaperStorage.readMeta` catching OOM (via `runCatching`) can also hide a serious memory condition.

**Recommendation**

Keep the size caps and streaming reads; remove `catch (OutOfMemoryError)` from normal control flow, or restrict it to a single top-level "last resort" handler that logs and returns a static failure without further allocation. Ensure `readText` is bounded before allocation (already checked by `cacheFile.length()` in the archive cache, but not for `WallpaperStorage.readMeta` or `CacheDocumentDao` rows).

---

### L19 — `ImageUrlValidator` is string logic, not URL parsing

**Severity:** Low (hardening; overlaps M1/M2)
**Status:** Confirmed by code.
**Category:** URL validation

**Locations**

- `android/app/src/main/java/app/mohakash/apod/core/ImageUrlValidator.kt:19-25` — extension is extracted with `substringBefore('?')`/`substringAfterLast('.')`.
- `android/app/src/main/java/app/mohakash/apod/core/ImageUrlValidator.kt:28-39` — host is `afterScheme.substringBefore('/').substringBefore('?')`; no `URI`/`HttpUrl` parse; no IP/port/userinfo checks.
- `android/app/src/main/java/app/mohakash/apod/data/storage/ImageDownloader.kt:32` — the actual request uses OkHttp's parser, which may normalize a URL differently from the validator.

**Failure scenario**

URLs with userinfo, backslashes, percent-encoded delimiters, IDN, or unusual ports can be classified by the validator as a valid image while OkHttp resolves a different host/path. A URL like `https://user:pass@host/a.jpg` passes the validator even though it carries userinfo; OkHttp may interpret it differently from the string validator. `https://127.0.0.1/a.jpg` passes. The validator is not a security boundary for the download path (the host is API-controlled anyway), but it is the only URL gate and should be unambiguous.

**Impact**

Classification bypass / SSRF surface; no direct exploit beyond M1/M2.

**Minimal fix**

Parse with `okhttp3.HttpUrl` (or `java.net.URI` plus explicit rules): require `https`, non-empty host, no userinfo, allow only default/known ports, require a real image extension on the path, and reject private/link-local addresses (with an explicit opt-in for local development). Use the same normalized `HttpUrl` for the request and for logging. Add tests for userinfo/backslash/IDN/loopback.

---

### L20 — Notification permission declaration/README drift and missing notification content intent

Already covered by L6/L11; listed here only as documentation consistency:

- `android/README.md` section 7 lists only `INTERNET`, `ACCESS_NETWORK_STATE`, and `WRITE_EXTERNAL_STORAGE`, but the manifest also declares `POST_NOTIFICATIONS` (`AndroidManifest.xml:7`) and WorkManager contributes `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`.
- `NotificationHelper` has no `setContentIntent`, so tapping the notification body does nothing (`NotificationHelper.kt:104-139`).

**Fix:** update the permission table and add `.setContentIntent(openAppPending)`.

---

## 3. Controls verified and non-findings

These were checked and did **not** produce a confirmed issue. They are recorded so a reviewer can see coverage.

### 3.1 Manifest and components

- `MainActivity` is the only exported activity, needed for launcher + verified HTTPS App Links (`AndroidManifest.xml:27-67`). It consumes the intent only on a fresh start (`MainActivity.kt:46-48`) and re-parses on `onNewIntent` (`:86-90`).
- The only exported service is `MohakashWallpaperService` with `android:permission="android.permission.BIND_WALLPAPER"` (`AndroidManifest.xml:69-81`). `BIND_WALLPAPER` is signature-level and only the system wallpaper picker holds it.
- No custom providers, receivers, services, `FileProvider`, or `android:exported` on WorkManager internals beyond the standard `DiagnosticsReceiver`/`ProfileInstallReceiver` (both protected by `android.permission.DUMP`) and `SystemJobService` (protected by `BIND_JOB_SERVICE`). Verified against the merged release manifest.
- `android:usesCleartextTraffic="false"` (`:24`) and `targetSdk 36` mean user CAs are not trusted by default. OkHttp 4.12 also checks `Platform.isCleartextTrafficPermitted` (`RealConnection`), so an `http://` redirect is rejected by the client, not just the platform.
- `android:allowBackup="true"` is acceptable for public APOD data and saved public pictures; DataStore (which holds the custom endpoint) is not included in the backup rules, so the endpoint is not restored onto a new device. See M6 for the DB sidecar issue.

### 3.2 Deep links and intents

- `DeepLinkParser.parse` requires scheme `https` (`DeepLinks.kt:66`), compares the host to `BuildConfig.WEBSITE_BASE_URL` (`:67`), and only accepts `""`, `archive`, or `archive/YYYY-MM-DD` with a real calendar date (`:69-90`). Query/fragment/trailing slash handling is covered by tests.
- All `PendingIntent`s are `FLAG_IMMUTABLE` with `FLAG_UPDATE_CURRENT` (`NotificationHelper.kt:72-102`); the view action is only created for `https` with a non-blank host (`:139-150`).
- `openLink` (`MohakashApp.kt:250-261`) accepts only `http`/`https` and is used for API `apod_url`/`hd_image_url`; it cannot launch `file:`, `content:`, or custom schemes.
- `ShareHelper` shares plain text, not a file/URI, so there is no `FileUriExposedException`/content-URI grant issue (`ShareHelper.kt:19-29`).

### 3.3 Files, paths, and external storage

- Gallery file names are `APOD_YYYY-MM-DD.ext` with the date regex-constrained and the extension whitelisted (`ApodEntry.kt:53-63`). No API string becomes a path segment.
- Wallpaper target names are `wallpaper-<hex>.<ext>` with the extension from `ImageUrlValidator` (`WallpaperStorage.kt:73-75`); no traversal.
- The legacy gallery path is only used on API ≤ 28 and the runtime permission is requested (`Permissions.kt:36-48`); API 29+ uses `MediaStore` with `IS_PENDING` (`GallerySaver.kt:44-66`).
- `ImageDownloader` streams to disk with a 64 MiB cap and deletes the target on failure/`CancellationException` (`ImageDownloader.kt:27-72`).
- The archive-index cache uses same-directory temp + rename, a sidecar with `sizeBytes`, quarantine, temp sweep, and a generation token (`ArchiveIndexRepository.kt:326-352`, `383-398`, `406-425`); unit tests cover corrupt/oversized/empty/clear-race cases.

### 3.4 Room/DataStore and transactions

- All Room queries use bind parameters; no string concatenation into SQL. `LIKE :yearPrefix || '%'` uses an Int-derived prefix. No SQL injection.
- Room suspend DAO methods run on Room's query executor; no `allowMainThreadQueries`.
- DataStore has a `ReplaceFileCorruptionHandler` (`PreferencesSettingsStore.kt:24-28`), so a corrupt preferences file is replaced instead of failing forever. (The *reaction* to I/O errors is the issue; see M7/M8.)
- `FavouritesRepository.toggle` is a read-then-write and is not transactional (`FavouritesRepository.kt:38-46`), but Room's `upsert`/`delete` are individually atomic and the UI reconciles from a Flow; I did not find a realistic way to corrupt the DB. It is a minor race, not a finding.
- `CachedEntryDao.upsertAll`/`FavouriteDao` use `OnConflictStrategy.REPLACE`; no foreign keys or cascade issues.

### 3.5 JSON and network

- `ApodApi.readStringCapped` enforces the cap on the **decompressed** stream, checks `Content-Length` when present, and throws before buffering the whole body (`ApodApi.kt:73-96`). The 16 MiB default is above the real 5.2 MiB index.
- `ApodApi.getString` tries candidates in order and preserves `CancellationException`; per-candidate failures do not leak resources (`.use`).
- Timeouts are bounded: connect 20 s, read 30 s, call 60 s (`AppContainer.kt:54-56`). DNS is covered by `callTimeout`.
- JSON parsing is bounded by the 16 MiB response cap; the DTO graph is flat (lists of flat records) and unknown-key skipping is iterative in kotlinx.serialization 1.9, so a deeply nested unknown value does not cause stack overflow in the way some older parsers do. An OOM is still possible near the cap; see L10/L18.
- The API base URL list is build-time from `config/site.json`, not user data. The custom URL is normalized at save time (`ApiEndpoint.kt:15-34`) and rejected if not HTTPS, with userinfo/query/fragment/`..` clauses. The runtime resolver does not re-validate it (`BaseUrlResolver.kt:25-44`), but a malformed stored value becomes an `IllegalArgumentException` inside `Request.Builder` that `ApodApi` catches per candidate and reports as a failure; it does not crash. It should still be re-validated (hardening).
- `HttpLoggingInterceptor` is `NONE` in release; no request/response bodies are logged in production (`AppContainer.kt:57-60`).

### 3.6 Localization

- No format/argument mismatches across locales; a programmatic comparison of every string and plural item in all 15 locales against `values/` found no conversion-type/position mismatches and no malformed `%` escapes. `values-in/` and `locales_config.xml:10` (which lists `in`) are consistent. `ByteFormatting` uses `Locale.US` for decimal separators (`ByteFormatting.kt:24`); date formatting uses explicit `Locale.US` for the API parse and `Locale.getDefault()` for display, avoiding locale-dependent parse failures.
- `SimpleDateFormat` instances are per-call or `ThreadLocal`, not shared unsafely.

### 3.7 Build/dependency hygiene

- Repositories are limited to `google()` and `mavenCentral()` with `FAIL_ON_PROJECT_REPOS` (`settings.gradle.kts:15-20`); no HTTP repositories or dynamic versions.
- Release build is minified/shrunk (`isMinifyEnabled = true`, `isShrinkResources = true`), signed with the release key when configured, and the APK on disk verifies with the certificate matching `assetlinks.json`.
- No API keys, tokens, passwords, or private keys are tracked in git (`git ls-files`/`git grep` clean; the local keystore is ignored — see L8).
- No analytics, crash reporting, ad, or tracking SDK is present in the dependency list.

---

## 4. Residual risk and audit limitations

1. **No dynamic testing.** I did not run the app on a device/emulator, so App Links verification state, OEM MediaStore quirks, Doze/WorkManager timing, and the wallpaper service's actual ANR behavior are inferred from code and Android semantics.
2. **Dependency CVEs were not scanned.** Versions are pinned (`okhttp 4.12.0`, `coil 2.7.0`, `room 2.8.2`, `datastore 1.1.7`, `work 2.10.5`, `kotlinx-serialization 1.9.0`, `compose BOM 2025.09.00`, AGP 8.13.0, Kotlin 2.2.21). No known-vulnerable version was identified by inspection, but a live feed (e.g. `gradle dependencyCheck`/OSV) should be run in CI. Coil 2.x is in maintenance; a future Coil 3 upgrade would change the URL/size behavior described in M1.
3. **Coil internals.** The size/URL conclusions are based on the compiled `coil-base 2.7.0` bytecode (`StringMapper`, `FileFetcher`, `ContentUriFetcher`, `HttpUriFetcher`), not on a running app. The exact temp-file behavior depends on Coil's disk cache and OkHttp; the absence of a per-response cap is clear.
4. **Endpoint-compromise assumptions.** M1/M2 are only exploitable if the API JSON is attacker-controlled. For the built-in endpoint, that requires a compromise of the GitHub Pages deployment/repository or a mis-issued certificate; for a custom endpoint, it is the user's chosen server. This is the normal trust boundary of an API client, but it is worth explicit defense in depth because the app renders and downloads arbitrary URLs from that data.
5. **Backup/WAL behavior is device/OS dependent.** Whether a restore actually loses data depends on when SQLite checkpointed. The configuration gap is real; reproduce with `bmgr`/Auto Backup on a test device before deciding between adding `-wal`/`-shm` or switching to `TRUNCATE`.
6. **Physical/root adversaries are out of scope.** With root, the app-private Room DB, DataStore, wallpaper, and keystore are all readable; none of the items above are mitigations against root.

---

## 5. Recommended remediation order

**Before the next release (low effort, high value)**

1. **M4**: add decode-bounds validation before `WallpaperStorage.commit`; keep the old wallpaper on failure. This is a small, self-contained fix that prevents a silent blank-wallpaper state.
2. **M1**: validate image URLs at the mapper (`https`, host, image extension; reject `file:`/`content:`) and add a Coil `OkHttpClient` with a response-size cap. This closes the largest "API data is trusted" gap.
3. **M8**: make settings writes return success/failure and only emit "Saved" after a durable write. This is a few signatures plus call-site updates.
4. **M9**: unique temp file + atomic rename + per-filename mutex in `ImageRepository`. Small and testable.
5. **M7**: stop converting `IOException` to empty settings; retry or fail closed, and only clear caches on an explicit user action.
6. **L6/L11**: request `POST_NOTIFICATIONS` on the settings toggle and add `setContentIntent`; truncate the title.

**Next maintenance window**

7. **M5/M6**: export the Room schema, add real migrations and corruption recovery, and fix the backup sidecars (or switch to `TRUNCATE`). Add a restore test.
8. **M3**: move JSON decode/mapping into `Dispatchers.Default`/IO for `fetchLatest`, `fetchArchiveYear`, and the dead `fetchArchiveIndex`; add a StrictMode check.
9. **M2**: disable cross-host redirects or add a redirect validator; add private-IP blocking for remote data.
10. **L1/L9/L10**: validate dates, add the archive endpoint observer, and bound `yearRecords`/`cached_entries`.

**Ongoing hardening**

11. **L7**: add Play/F-Droid fingerprints to `assetlinks.json`.
12. **L8**: move keystore/passwords to a secret manager; keep them out of backups.
13. **L4/L5**: use `NET_CAPABILITY_NOT_METERED`; fail closed on null settings for wallpaper updates.
14. **L15/L17/L18/L19**: decide on pinning, use strict JSON + schema version checks, remove OOM-catching from normal flow, parse URLs with `HttpUrl`.
15. **Localization**: add lint/format tests so future string edits cannot introduce `StringFormatMatches`/`IllegalFormatConversionException` regressions.

---

## Appendix A — Finding index (quick reference)

| ID | Severity | Status | Area | Primary locations |
|---|---|---|---|---|
| M1 | Medium | Confirmed | Coil URL/size policy | `FeedPageContent.kt:479-507`, `EntryImageViewer.kt:98-103`, `CommonComponents.kt:372-379`, `MohakashApplication.kt:25-38` |
| M2 | Medium | Confirmed | SSRF/redirects | `AppContainer.kt:51-63`, `ApodApi.kt:45-70`, `ImageDownloader.kt:32-40`, `ImageUrlValidator.kt:28-39` |
| M3 | Medium | Confirmed | Main-thread JSON | `ApodRepository.kt:43-56,84-88,114-122`, `FeedViewModel.kt:493-506,709-723` |
| M4 | Medium | Confirmed | Wallpaper byte validation | `ImageDownloader.kt:37-45,63`, `WallpaperStorage.kt:65-97`, `ApodSyncEngine.kt:110-125` |
| M5 | Medium | Confirmed config | Room migrations/corruption | `MohakashDatabase.kt:15-23,38-43` |
| M6 | Medium | Confirmed config | Backup WAL | `backup_rules.xml:3-8`, `data_extraction_rules.xml:3-18` |
| M7 | Medium | Confirmed | DataStore error handling | `PreferencesSettingsStore.kt:40-50`, `BaseUrlResolver.kt:25-44`, `FeedViewModel.kt:884-895` |
| M8 | Medium | Confirmed | Silent settings writes | `PreferencesSettingsStore.kt:82-99`, `AdvancedViewModel.kt:127-150` |
| M9 | Medium | Confirmed | Gallery save race | `ImageRepository.kt:24-46`, `ImageDownloader.kt:46-71`, `GallerySaver.kt:30-41` |
| L1 | Low | Confirmed | Date validation/route | `ApiMapper.kt:29,52-64`, `MohakashApp.kt:156,176`, `Routes.kt:20` |
| L2 | Low | Confirmed | Wallpaper temp leak | `WallpaperStorage.kt:77-111` |
| L3 | Low | Confirmed | Wallpaper main-thread block | `MohakashWallpaperService.kt:147-213` |
| L4 | Low | Confirmed | Metered detection | `NetworkMonitor.kt:24-30` |
| L5 | Low | Confirmed | Fail-open settings | `SyncPolicy.kt:18-19`, `ApodSyncWorker.kt:24` |
| L6 | Low | Confirmed | Notification permission | `AndroidManifest.xml:7`, `Permissions.kt:24-50` |
| L7 | Low | Confirmed | App Links cert coverage | `assetlinks.json:7-8`, `AndroidManifest.xml:44-66` |
| L8 | Low | Confirmed local | Keystore on disk | `android/keystore.properties`, `android/keystore/mohakash-release.jks` |
| L9 | Low | Confirmed | Archive endpoint stale | `ArchiveViewModel.kt:81-97` |
| L10 | Low | Confirmed | Cache growth | `FeedViewModel.kt:78,746-762`, `CacheManager.kt:18-23` |
| L11 | Low | Confirmed | Notification title/intent | `NotificationHelper.kt:66,104-140` |
| L12 | Low | Confirmed | Wallpaper meta path | `WallpaperStorage.kt:47-57` |
| L13 | Low | Confirmed debug | URL logging | `AppContainer.kt:57-60` |
| L14 | Low | Confirmed | Cancellation swallowing | `ApodRepository.kt:150-155` |
| L15 | Low | Hardening | TLS pinning | `AndroidManifest.xml:24`, `AppContainer.kt:51-63` |
| L16 | Low | Confirmed | Cache size/quarantine | `ArchiveIndexRepository.kt:333-343,383-398`, `CacheManager.kt:18-23` |
| L17 | Low | Hardening | Lenient JSON/schema | `AppContainer.kt:39-43`, `ApodDtos.kt:15` |
| L18 | Low | Hardening | OOM catching | `ArchiveIndexRepository.kt:229-231,296-299`, `WallpaperStorage.kt:54-57` |
| L19 | Low | Hardening | String URL parsing | `ImageUrlValidator.kt:19-39` |
| L20 | Low | Docs | Permission/doc drift | `android/README.md` section 7 vs `AndroidManifest.xml:7` |

--

*End of report.*
