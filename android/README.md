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
sdk.dir=/home/himel/Android/Sdk
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

Android shows its own confirmation dialog (*"Set this wallpaper?"*). The wallpaper is a **still
picture** — there is no animation and no continuous redraw.

* The picture that is used is the newest **validated static image** that was stored in the app's
  private storage (`filesDir/wallpaper/`).
* **Fill** crops the picture to the screen, **Fit** shows the whole picture and letterboxes the
  rest with the app background (`#0B0D12`). The mode can be changed in the app (wallpaper sheet or
  Settings → Image scaling) and is picked up by the engine.
* The wallpaper is refreshed once a day by a `WorkManager` job. The new picture is downloaded,
  validated and swapped in **atomically**; a failed download, a missing picture or a video entry
  never replaces a working wallpaper.
* A video entry cannot become a wallpaper — the app keeps the previous picture and says so.

---

## 4. Screens

| Screen | What it does |
|---|---|
| **Onboarding** | Shown once. Phone preview, headline, *Set live wallpaper* / *Skip for now*, Wi-Fi hint. Triggers the first synchronisation. |
| **Today** | Hero picture, date · credit, title, three-line explanation preview, heart / download / share row, *Set live wallpaper* pill, *Synced X ago*. |
| **Today (offline)** | Banner *“You're offline. Showing the saved copy from {date}.”* with a **Retry** button, then the cached entry. |
| **Wallpaper sheet** | Preview, current image (date · title), Fill/Fit, *Update daily* switch, *Preview and set*, helper text. |
| **Entry detail** | Back / heart / share, picture, date · credit, title, *Save image* + *Set as wallpaper*, the full explanation, *Original APOD page* and *HD image* rows. |
| **Video entry** | Poster with play glyph and *Video* chip, *Watch on original page*, the note that videos cannot be wallpapers. No save / wallpaper buttons. |
| **Archive** | Search pill, year chips, *Filters*, list rows (72 × 56 thumbnail, date over title), incremental loading. |
| **Filter sheet** | Year chips, jump-to-date, All / Images / Videos, *Reset* and *Show N entries*. |
| **Saved** | Header with the count and *available offline*, two-column square grid. |
| **Saved (empty)** | Heart glyph, *Nothing saved yet*, hint, *Browse archive*. |
| **Settings** | Wallpaper section (update automatically, prefer HD, Wi-Fi only, image scaling), storage section (image cache + clear), about section (last synced, content rights, source code, version). |
| **Clear cache dialog** | *Clear image cache?* with the exact size, *Cancel* / *Clear*. |
| **Bottom navigation** | Today · Archive · Saved (72 dp, no shadow). Detail and Settings have a back arrow and no bottom bar. |

---

## 5. Architecture

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
│   ├── repository/          APOD metadata, favourites, image download / save / share
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

## 6. Permissions

The app itself declares only:

| Permission | Why |
|---|---|
| `INTERNET` | Talks to the public JSON API and downloads pictures. |
| `ACCESS_NETWORK_STATE` | Knows whether the device is online and whether the connection is Wi-Fi (for *Wi-Fi only*). |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Only for the legacy gallery path on Android 7–9. On Android 10+ the picture is stored through `MediaStore`, which needs no permission at all. |

`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` and `FOREGROUND_SERVICE` are contributed by **AndroidX
WorkManager** — the app has no boot receiver of its own, but WorkManager's reschedule receiver is
what makes the daily update survive a reboot.

`android:usesCleartextTraffic="false"` — only HTTPS is used.

---

## 7. Known limitations

* **Background updates are deferred by Android.** `PeriodicWorkRequest` asks for a 24-hour
  interval, but Android decides when the job actually runs. It can be delayed by battery
  optimisation, Doze or the app being swiped away. The exact update time is **not** guaranteed.
* The first picture is only downloaded when the device is online; until then the wallpaper stays
  empty (or keeps the previous picture).
* `MediaStore` writes need a few seconds on some devices; the app reports success or failure
  with a snackbar, it never stays silent.
* The *Prefer HD images* and *Update automatically* switches trigger an immediate refresh so the
  change is visible right away.
* The content-rights link points at `CONTENT_RIGHTS.md` in the repository and assumes the default
  branch is called `master`.
* The app was **not** tested on a physical device or an emulator in this environment — see the
  build report for what has and has not been verified.

---

## 8. Tests

`./gradlew :app:testDebugUnitTest` runs 76 pure-JVM unit tests (no emulator needed):

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
