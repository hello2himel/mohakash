# Mohakash — Android app

The native Android app of **Mohakash**: NASA's *Astronomy Picture of the Day* explorer with a
genuine Android **live wallpaper**.

* Single-activity Jetpack Compose app (Material 3, dark theme only)
* MVVM with `ViewModel` + `StateFlow`, a repository layer and a manual dependency container
* The app talks to the public JSON API that the repository publishes on GitHub Pages — there is no
  account, no analytics and no tracking.

Current release: **2026.10.27.2** (version code **9**). The app has a full-screen feed, a dedicated Archive tab and
Saved.

---

## Languages

The app UI is localized through standard Android resources and follows the phone language. On
Android 13+ it also appears in the system **per-app language** picker through
`res/xml/locales_config.xml`.

Supported UI locales:

| Language | Locale |
|---|---|
| English | `en`, `en-GB`, `en-US` |
| Bengali | `bn` |
| German | `de` |
| Spanish | `es` |
| French | `fr` |
| Hindi | `hi` |
| Indonesian | `in` |
| Italian | `it` |
| Japanese | `ja` |
| Korean | `ko` |
| Portuguese | `pt` (Brazilian standard) |
| Turkish | `tr` |
| Vietnamese | `vi` |
| Simplified Chinese | `zh-rCN` |

The NASA APOD titles, explanations and credits are editorial content from the public API and stay
in English; only Mohakash's own interface and message strings are translated. English uses the
neutral `values/` resources, with British (`en-GB`) and American (`en-US`) spelling overrides.

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 or 21 (the Gradle build needs a JDK ≥ 17; JDK 17/21 are known to work) |
| Android SDK Platform | `android-36` (compileSdk / targetSdk 36) |
| Android SDK Build-Tools | `35.0.0` or `36.0.0` |
| Gradle | 8.14.3 (the wrapper downloads it for you) |
| minSdk | 24 (Android 7.0) |

Point the build at your SDK with `android/local.properties`:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

`local.properties` is git-ignored.

---

## 2. Build the debug APK

```bash
cd android
./gradlew :app:assembleDebug
```

The APK lands in:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

The HTML report is written to `android/app/build/reports/tests/testDebugUnitTest/index.html`.

### Signed release builds

The release package is `mohakash.stargzr.space`. Signing, versioning, Play and
F-Droid instructions are in [`RELEASING.md`](RELEASING.md).

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

The signed APK and AAB are written to `app/build/outputs/apk/release/` and
`app/build/outputs/bundle/release/`. The release key and its passwords are
git-ignored; see `keystore.properties.example`.

### Configuration

Every owner-specific URL is read from **`config/site.json`** in the repository root — at Gradle
*configuration* time. `android/app/build.gradle.kts` parses the file and generates the
`BuildConfig` fields:

| BuildConfig field | Source |
|---|---|
| `API_BASE_URL_FALLBACKS` | `android.api_base_url_fallbacks` (comma separated) |
| `REPOSITORY_URL` | `github.owner` + `github.repo` |
| `CONTENT_RIGHTS_URL` | `REPOSITORY_URL` + `/blob/master/CONTENT_RIGHTS.md` |
| `WEBSITE_BASE_URL` | `website.base_url` |

The app tries the candidate base URLs in order until one answers and remembers the winner in
DataStore; the next request starts with the remembered one and falls back to the others if it
fails. **No URL is hard-coded in the Kotlin sources.**

---

## 3. Using the live wallpaper

1. Open the app and tap **Wallpaper** on any feed picture (or **Set live wallpaper** during
   onboarding), or
2. go to **Settings → Wallpaper → Mohakash** in the system wallpaper picker.

Mohakash is a real Android `WallpaperService` (**live wallpaper**), not a bitmap set through
`WallpaperManager`. It draws a still astronomy picture and redraws it whenever the picture changes;
there is no animation loop.

Android asks for confirmation the **first time only** (the system live-wallpaper preview). After
that, the app recognises that its service is the active wallpaper and swaps new pictures in place
— choosing a picture in the feed or an automatic APOD update does not open the system dialog again.

* The picture that is used is the newest **validated static image** stored in the app's private
  storage (`filesDir/wallpaper/`).
* **Fill** crops the picture to the screen, **Fit** shows the whole picture and letterboxes the
  rest with the app background (`#0B0D12`). The feed and the wallpaper-sheet preview draw the
  same Fill/Fit mode, and the engine observes the setting, so changing it in the sheet or in
  *Settings → Display → Image scaling* updates an already-active wallpaper and the feed
  immediately.
* A `WorkManager` job checks for a new APOD **four times a day** (every 6 hours) instead of once,
  so an early or delayed run cannot skip a day. Only the tiny metadata document is fetched each
  time; the picture is downloaded only when the date or URL actually changed, then swapped in
  **atomically**. It runs without opening or refreshing the app, as long as the user has
  *Update automatically* on and Mohakash is the selected wallpaper. Android can still delay
  background work for battery reasons.
* The worker respects *Wi-Fi only* (off by default, so the daily picture also arrives on mobile
  data) and never downloads over a metered connection when it is on.
* A video entry cannot become a wallpaper; with *Hide videos* on (the default) video entries are
  skipped and the previous picture stays, otherwise their poster is used.
* If the wallpaper is not active, background sync still refreshes the cached metadata but does
  not replace the user's wallpaper.

---

## 4. Screens

| Screen | What it does |
|---|---|
| **Onboarding** | Shown once. Phone preview, headline, *Set live wallpaper* / *Skip for now*, download note. Triggers the first synchronisation. |
| **Top chrome** | Search (left), lowercase **mohakash** wordmark (centre), Settings (right), over a black-to-transparent top scrim. Tapping the picture hides this chrome and the dock; tapping again restores them. |
| **Feed** | Full-screen vertical pager with one APOD per page. Page 0 is the latest picture; swiping up goes to older pictures, swiping down to newer ones, and the list never wraps. On a first visit a one-time **Swipe up / to see more pictures** cue appears with two small feed bounces and fades after six seconds or on the first drag. Top chrome has **search on the left, the mohakash wordmark in the centre and Settings on the right**. A black-to-transparent bottom gradient carries the credit, title and two-line teaser; the APOD date runs vertically on the left with a short accent line. A labelled right-hand rail holds **Favorite**, **Download** (image entries) and **Share**. Next to the text is a compact **Set wallpaper** pill and a text **Learn more** link. Tapping the picture fades out the gradient and the bottom bar for a clean full-screen view; tapping again (or Back) restores them. Fill/Fit follows the shared image-scaling setting. |
| **Feed (video)** | Poster with the play glyph and *Video* chip, *Watch on APOD* instead of save / wallpaper. Videos play on the APOD page, not in the app. |
| **Feed (offline)** | Pages render from the on-disk index and cached years with an *Offline copy* chip; the teaser is hidden until a page is hydrated, and *Retry* re-attempts the network. |
| **Archive** | Dedicated tab between Feed and Saved. Search titles, quick year chips, *Filters*, and a newest-first list of the selected year. The shared disk-cached index opens instantly; the filter sheet has all years, media types and jump-to-date. |
| **End card** | The real last page of the feed: *Beginning of the archive* (the first picture is from 16 June 1995), *Back to latest* and *Open Saved*. A filtered feed shows *No older pictures match this filter* instead. |
| **Wallpaper sheet** | Fill/Fit preview that matches the engine, current image (date · title), Fill/Fit selector, *Update daily* switch, *Set live wallpaper* (first time) or *Apply now* (already active), helper text. |
| **Full entry** | Back / heart / share (and the settings gear), the **uncropped** picture inset with page padding and rounded corners, at its true aspect ratio with pinch and double-tap zoom (1×–4×, clamped panning; double-tap resets). Zoomed content is hard-clipped to the picture area. The picture is capped at 62% of the viewport: tall images keep that height and narrow their width; short images take the full content width. Below it: human date · credit, title, *Save image* + *Set as wallpaper* for pictures (the existing permission and sheet flows) or *Watch on original page* and the wallpaper note for videos, the full explanation, and a **Details** section with Date, Media type, Credit, Updated and Source rows plus *Original APOD page* / *HD image* links. |
| **Search sheet** | Full-height sheet over the feed: *Search titles or dates* field (150 ms debounce), year chips, All / Images / Videos, and result rows (thumbnail, ISO date, title, credit, video glyph). Tapping a result closes the sheet and moves the feed to that date; with a cached index it works offline. |
| **Filter sheet** | All years as a wrapped chip grid, All / Images / Videos, and a jump-to-date picker bounded to the archive range. A gap date resolves to the newest existing earlier date and says so. *Reset* and *Show N pictures*. |
| **Saved** | The second segment inside the Archive hub: two-column square grid; tiles open the full entry view. |
| **Saved (empty)** | The Saved segment shows heart glyph, *Nothing saved yet*, hint, *Browse the feed*. |
| **Settings** | Display section (image scaling for the feed and the live wallpaper), wallpaper section (update automatically, prefer HD, Wi-Fi only, hide videos), storage section (image cache + clear), advanced section (API endpoint), about section (last synced, content rights, source code, version). It has a root tab variant without a back arrow and a stack variant with one. |
| **Clear cache dialog** | *Clear image cache?* with the exact size, *Cancel* / *Clear*. |
| **Bottom navigation** | **Launch Cradle**: one smooth floating plate instead of a full-width tab bar. Feed and Archive are full-height cells with a small accent underline for the selected one. A 64 dp blue-gradient key sits centred over the plate, carrying the black mohakash mark; it is the only accent surface in the dock. The plate grows at 1.3×/1.6×/2.0× font scale so labels never clip. The bar is transparent, so feed imagery and archive rows continue beneath it; lists reserve extra bottom padding. Archive is a combined hub with **Archive / Saved** segments; Settings remains the top-right gear on Feed and Archive. |

---

## 5. App Links and sharing

Public website links open in the app when it is installed. The manifest declares
`https://mohakash.netlify.app/` and `https://mohakash.netlify.app/archive...` as App Links, and
`MainActivity` routes them through `DeepLinkParser`:

| Website URL | Screen |
|---|---|
| `/` | Feed, opened on the latest picture |
| `/archive` and `/archive/` | Archive |
| `/archive/YYYY-MM-DD` (including query/fragment) | Full entry |

The host is generated from `config/site.json`: Gradle exposes it as the `websiteHost` manifest
placeholder and as `BuildConfig.WEBSITE_BASE_URL`, so the intent filters and the parser always
match. Links that do not map to an app screen (`/about`, `/privacy`, `/api`, ...) stay in the
browser, and the intent filters only claim `/` and `/archive...`.

Android verifies the links against
[`web/public/.well-known/assetlinks.json`](../web/public/.well-known/assetlinks.json). The file
contains the application ID and the SHA-256 certificate fingerprint of the build users install.
It currently lists the GitHub release key; add the Play App Signing certificate (if Play re-signs
the bundle) and the F-Droid certificate when those listings go live, then redeploy the website.
The parser and manifest are covered by `DeepLinkParserTest` and `DeepLinkManifestTest`. With the
signed release APK installed and `assetlinks.json` deployed, Android 15 reports
`pm get-app-links mohakash.stargzr.space` as `verified` and opens the links without a chooser.

The share button (in the feed overlay and the full entry view) shares the public
`/archive/YYYY-MM-DD` URL as `text/plain`, together with the title, date and credit. It does not
download or attach the picture; tapping the link opens the entry page (in the app, if installed).

---

## 6. Architecture

```
app/src/main/java/app/mohakash/apod/
├── AppContainer.kt          manual dependency container
├── MohakashApplication.kt   application object + Coil image loader
├── MainActivity.kt          the single activity
├── core/                    time formatting, byte formatting, URL validation, network monitor
├── data/
│   ├── remote/              HTTP client, base-URL fallback, DTOs, JSON → domain mapping
│   ├── local/               Room database (favourites, cached records, cached documents)
│   │   └── prefs/           DataStore (Preferences) settings
│   ├── repository/          APOD metadata, archive index + disk cache, favourites, images
│   └── storage/             wallpaper storage, gallery saver, share helper, cache manager
├── domain/
│   ├── model/               ApodEntry, ArchiveIndexEntry, MediaType, ImageStatus, settings
│   └── wallpaper/           wallpaper picture selection + the Fill/Fit geometry
├── ui/
│   ├── theme/               colours, typography, dimensions
│   ├── components/          shared Compose components
│   ├── navigation/          navigation graph, feed/saved bottom bar, deep links
│   ├── feed/                vertical pager, overlay chrome, search/filter sheets, load policy
│   ├── detail/              uncropped zoomable viewer, metadata, actions, formatting helpers
│   ├── onboarding/ saved/ settings/ wallpaper/
├── wallpaper/               WallpaperService + engine, the redraw signal
└── work/                    WorkManager worker, scheduler and the sync engine
```

`FileArchiveIndexRepository` keeps the 5 MiB `archive-index.json` in one atomically written file
under `cacheDir/feed/`, shared by the feed, its search and its filter sheet. The detail viewer is
split into `EntryImageViewer`, `EntryMetaSection` and `EntryActionBar`, with the pure geometry and
formatting in `EntryImageSizing` and `EntryDetailFormatting`.

Data flow: `ViewModel → Repository → (ApodService | Room | DataStore) → StateFlow → Compose`.

---

## 7. Permissions

The app itself declares only:

| Permission | Why |
|---|---|
| `INTERNET` | Talks to the public JSON API and downloads pictures. |
| `ACCESS_NETWORK_STATE` | Knows whether the device is online and whether the connection is Wi-Fi (for *Wi-Fi only* and the feed's metered index policy). |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Only for the legacy gallery path on Android 7–9. On Android 10+ the picture is stored through `MediaStore`, which needs no permission at all. |

`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` and `FOREGROUND_SERVICE` are contributed by **AndroidX
WorkManager** — the app has no boot receiver of its own, but WorkManager's reschedule receiver is
what makes the periodic update survive a reboot.

`android:usesCleartextTraffic="false"` — only HTTPS is used.

The 1.0.6 UI adds no permission. The feed draws its picture edge to edge under the status bar
and applies its own inset to the top chrome; the other screens pad the system bars
(`statusBarsPadding()` / `navigationBarsPadding()`) and the bottom bar stays solid.

---

## 8. Known limitations

* **Videos link out.** A video or interactive entry shows its poster in the feed and the entry
  view; playback happens on the original APOD page. There is no inline player and no streaming
  per swipe.
* **The archive index is a lazy 5 MiB download.** `archive-index.json` is fetched once, kept in
  a single disk file and reused; it is refreshed at most once a day and is not refreshed on a
  metered connection unless the user opens search / filter, pulls to refresh or reaches the end
  card. The first screen comes from `latest.json` and the current-year file, so the feed opens
  before the index arrives.
* **Search covers title, date and credit only.** Explanations live in the per-year files and are
  not downloaded to answer a query, so explanation text is not searchable.
* **Offline coverage is what has been cached.** The feed list and thumbnails come from the disk
  index and Room, but full details and teasers appear only for pages whose year was fetched
  before. Older years need a connection, and search needs the index to have been downloaded at
  least once.
* **Clearing the image cache does not remove the archive index.** *Clear image cache* deletes the
  Coil / HTTP image files only; the index file is metadata and is kept. An API endpoint change is
  what clears it.
* ***Hide videos* only affects the wallpaper.** The feed always shows video entries; use the
  feed's Videos filter instead.
* **Background updates are deferred by Android.** `PeriodicWorkRequest` checks every 6 hours, but
  Android decides when the job actually runs. It can be delayed by battery optimisation, Doze or
  the app being swiped away. The exact update time is **not** guaranteed; four daily checks give
  the next APOD several chances to arrive the same day.
* The first picture is only downloaded when the device is online; until then the wallpaper stays
  empty (or keeps the previous picture).
* `MediaStore` writes need a few seconds on some devices; the app reports success or failure
  with a snackbar, it never stays silent.
* The *Prefer HD images*, *Update automatically* and *Hide videos* switches trigger an immediate
  metadata refresh so the change is visible right away.
* The content-rights link points at `CONTENT_RIGHTS.md` on the repository's default branch.
* The app has been exercised on an Android 15 emulator (feed, uncropped entry viewer, live
  wallpaper, daily worker, scaling and settings); a physical device can still behave differently
  with OEM battery optimisation.

---

## 9. Tests

`./gradlew :app:testDebugUnitTest` runs 202 pure-JVM unit tests (no emulator needed):

| Test file | Covers |
|---|---|
| `ApiMapperTest` | JSON → domain normalisation, missing fields, `media_type` and `image_status` variants, the real API payload. |
| `TimeFormattingTest` | `isStale`, the *Synced X ago* caption, ISO and API date parsing. |
| `WallpaperImageSelectorTest` | HD on/off, thumbnail fallback, video/iframe poster, missing/placeholder keeps the previous picture, an article URL is never used as an image. |
| `WallpaperGeometryTest` | Fill crop and Fit letterbox rectangles, `inSampleSize`. |
| `ImageUrlValidatorTest` | Only real image assets are downloaded; page URLs are rejected. |
| `ApodSyncEngineTest` | Download skipping when the date and the URL are unchanged, atomic metadata update, failure keeps the last picture, no download without a connection. |
| `ApodRepositoryTest` | Caching of `latest.json` / `archive-index.json`, recovery after a failed request. |
| `ArchiveIndexRepositoryTest` | The index disk cache: raw payload + sidecar round-trip, legacy wrapper fallback, oversized/corrupt quarantine, stale-temp sweep, simulated OOM recovery, clear-during-refresh race, a failed fetch keeps the old cache, `clear()`. |
| `FavouritesRepositoryTest` | Saving and removing pictures, the full metadata survives for offline use. |
| `DeepLinkParserTest` | Website URL → app destination mapping: root to the feed, `/archive` to the Archive tab, entry URLs to the full entry, query/fragment/trailing slash, invalid dates, foreign hosts and schemes. |
| `DeepLinkManifestTest` | The manifest keeps the verified https App Links and the `/` + `/archive` paths. |
| `ShareHelperTest` | Archive URL construction and the share text: archive link instead of the NASA page, title/date/credit preserved. |
| `FeedPagingTest` | Newest-first date list, merge/dedupe, the nine missing dates, year/media filters, gap-safe nearest date, end card last, prefetch window. |
| `FeedLoadPolicyTest` | Index refresh only when stale / missing the latest date and never on a metered connection without an explicit request; year prefetch and adjacent-year decisions. |
| `FeedWallpaperPreviewTest` | The wallpaper preview of the feed: the selected entry instead of the installed wallpaper, the local file for an installed entry, the caption, the human date and the missing-credit line. |
| `EntryImageSizingTest` | Uncropped box at any aspect ratio, the viewport height cap, zoom clamp 1×–4×, pan clamp, double-tap target. |
| `EntryDetailFormattingTest` | Display date and ISO fallback, credit/media labels, metadata rows with omitted empty values, placeholder/missing notes, offline-note condition. |
| `SyncPolicyTest` | Automatic updates only while the wallpaper is active; Wi-Fi only off by default and honoured on metered connections. |
