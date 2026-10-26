# Mohakash — Android app

The native Android app of **Mohakash**: NASA's *Astronomy Picture of the Day* explorer with a
genuine Android **live wallpaper**.

* Single-activity Jetpack Compose app (Material 3, dark theme only)
* MVVM with `ViewModel` + `StateFlow`, a repository layer and a manual dependency container
* The app talks to the public JSON API that the repository publishes on GitHub Pages — there is no
  account, no analytics and no tracking.

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

1. Open the app and tap **Set live wallpaper** (Today screen or the onboarding screen), or
2. go to **Settings → Wallpaper → Mohakash** in the system wallpaper picker.

Mohakash is a real Android `WallpaperService` (**live wallpaper**), not a bitmap set through
`WallpaperManager`. It draws a still astronomy picture and redraws it whenever the picture changes;
there is no animation loop.

Android asks for confirmation the **first time only** (the system live-wallpaper preview). After
that, the app recognises that its service is the active wallpaper and swaps new pictures in place
— choosing an archive entry or an automatic APOD update does not open the system dialog again.

* The picture that is used is the newest **validated static image** stored in the app's private
  storage (`filesDir/wallpaper/`).
* **Fill** crops the picture to the screen, **Fit** shows the whole picture and letterboxes the
  rest with the app background (`#0B0D12`). The wallpaper-sheet preview draws the same Fill/Fit
  mode, and the engine observes the setting so changing it in the sheet or in *Settings → Image
  scaling* redraws an already-active wallpaper immediately.
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
| **Today** | Hero picture, date · credit, title, three-line explanation preview, heart / download / share row, *Set live wallpaper* pill, *Synced X ago*. |
| **Today (offline)** | Banner *“You're offline. Showing the saved copy from {date}.”* with a **Retry** button, then the cached entry. |
| **Wallpaper sheet** | Fill/Fit preview that matches the engine, current image (date · title), Fill/Fit selector, *Update daily* switch, *Set live wallpaper* (first time) or *Apply now* (already active), helper text. |
| **Entry detail** | Back / heart / share, picture, date · credit, title, *Save image* + *Set as wallpaper*, the full explanation, *Original APOD page* and *HD image* rows. |
| **Video entry** | Poster with play glyph and *Video* chip, *Watch on original page*, the note that videos cannot be wallpapers. No save / wallpaper buttons. |
| **Archive** | Search pill, year chips, *Filters*, list rows (72 × 56 thumbnail, date over title), incremental loading. |
| **Filter sheet** | Year chips, jump-to-date, All / Images / Videos, *Reset* and *Show N entries*. |
| **Saved** | Header with the count and *available offline*, two-column square grid. |
| **Saved (empty)** | Heart glyph, *Nothing saved yet*, hint, *Browse archive*. |
| **Settings** | Wallpaper section (update automatically, prefer HD, Wi-Fi only, hide videos, image scaling), storage section (image cache + clear), about section (last synced, content rights, source code, version). |
| **Clear cache dialog** | *Clear image cache?* with the exact size, *Cancel* / *Clear*. |
| **Bottom navigation** | Today · Archive · Saved (72 dp, no shadow). Detail and Settings have a back arrow and no bottom bar. |

---

## 5. App Links and sharing

Public website links open in the app when it is installed. The manifest declares
`https://mohakash.netlify.app/` and `https://mohakash.netlify.app/archive...` as App Links, and
`MainActivity` routes them through `DeepLinkParser`:

| Website URL | Screen |
|---|---|
| `/` | Today |
| `/archive` and `/archive/` | Archive |
| `/archive/YYYY-MM-DD` (including query/fragment) | Entry detail |

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

The share button shares the public `/archive/YYYY-MM-DD` URL as `text/plain`, together with the
title, date and credit. It does not download or attach the picture; tapping the link opens the
entry page (in the app, if installed).

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
│   ├── repository/          APOD metadata, favourites, image download / save
│   └── storage/             wallpaper storage, gallery saver, share helper, cache manager
├── domain/
│   ├── model/               ApodEntry, ArchiveIndexEntry, MediaType, ImageStatus, settings
│   └── wallpaper/           wallpaper picture selection + the Fill/Fit geometry
├── ui/
│   ├── theme/               colours, typography, dimensions
│   ├── components/          shared Compose components
│   ├── navigation/          navigation graph and bottom bar
│   ├── onboarding/ today/ detail/ archive/ saved/ settings/ wallpaper/
├── wallpaper/               WallpaperService + engine, the redraw signal
└── work/                    WorkManager worker, scheduler and the sync engine
```

Data flow: `ViewModel → Repository → (ApodService | Room | DataStore) → StateFlow → Compose`.

---

## 7. Permissions

The app itself declares only:

| Permission | Why |
|---|---|
| `INTERNET` | Talks to the public JSON API and downloads pictures. |
| `ACCESS_NETWORK_STATE` | Knows whether the device is online and whether the connection is Wi-Fi (for *Wi-Fi only*). |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Only for the legacy gallery path on Android 7–9. On Android 10+ the picture is stored through `MediaStore`, which needs no permission at all. |

`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` and `FOREGROUND_SERVICE` are contributed by **AndroidX
WorkManager** — the app has no boot receiver of its own, but WorkManager's reschedule receiver is
what makes the periodic update survive a reboot.

`android:usesCleartextTraffic="false"` — only HTTPS is used.

---

## 8. Known limitations

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
* The app has been exercised on an Android 15 emulator (live wallpaper, daily worker, scaling and
  settings); a physical device can still behave differently with OEM battery optimisation.

---

## 9. Tests

`./gradlew :app:testDebugUnitTest` runs 127 pure-JVM unit tests (no emulator needed):

| Test file | Covers |
|---|---|
| `ApiMapperTest` | JSON → domain normalisation, missing fields, `media_type` and `image_status` variants, the real API payload. |
| `TimeFormattingTest` | `isStale`, the *Synced X ago* caption, ISO and API date parsing. |
| `WallpaperImageSelectorTest` | HD on/off, thumbnail fallback, video/iframe poster, missing/placeholder keeps the previous picture, an article URL is never used as an image. |
| `WallpaperGeometryTest` | Fill crop and Fit letterbox rectangles, `inSampleSize`. |
| `ImageUrlValidatorTest` | Only real image assets are downloaded; page URLs are rejected. |
| `ApodSyncEngineTest` | Download skipping when the date and the URL are unchanged, atomic metadata update, failure keeps the last picture, no download without a connection. |
| `ApodRepositoryTest` | Caching of `latest.json` / `archive-index.json`, recovery after a failed request. |
| `FavouritesRepositoryTest` | Saving and removing pictures, the full metadata survives for offline use. |
| `DeepLinkParserTest` | Website URL → app destination mapping: root, archive index, entries, query/fragment/trailing slash, invalid dates, foreign hosts and schemes. |
| `DeepLinkManifestTest` | The manifest keeps the verified https App Links and the `/` + `/archive` paths. |
| `ShareHelperTest` | Archive URL construction and the share text: archive link instead of the NASA page, title/date/credit preserved. |
| `SyncPolicyTest` | Automatic updates only while the wallpaper is active; Wi-Fi only off by default and honoured on metered connections. |
