# Mohakash Android — UX, functional and accessibility audit

**Date:** 2026-10-27 (Asia/Dhaka)
**Snapshot:** git `00dc87d` ("localize the interface and add a first-visit scroll hint") **plus uncommitted working-tree changes present at 01:50**. The tree was being edited while this audit ran (see §6), so a few findings may already be fixed in the working copy. Line numbers are from that snapshot; if a line has moved, search for the quoted symbol.
**Method:** read-only static review of all Compose screens, ViewModels, resources, manifest and tests; geometry derived from the layout constants; no emulator, no screenshot testing, no Gradle build. The last committed unit-test run was 404/404 passing (`android/app/build/test-results`, 01:17).
**Scope:** every screen and sheet listed in the brief, navigation/deep links, state restoration, font scale, RTL, landscape, touch targets, semantics, 15 resource locales, failure/empty/loading states, permissions, snackbars, image scaling, video handling, favourites/download/share.

Severity: **critical** = data loss/crash/blocked release; **high** = a core flow is broken or unusable on common devices; **medium** = a flow works but misleads, loses state, or fails for a class of users/locales; **low** = polish/dead code.

## 1. Summary

| # | Sev | Area | Finding | Where |
|---|-----|------|---------|-------|
| H1 | High | Onboarding | No scroll container; the fixed 180 × 380 dp preview + copy overflow the weighted area and render under the CTA buttons on small screens, in landscape, and at font scale ≥ ~1.3 | `OnboardingScreen.kt:96-151, 153-180` |
| H2 | High | Feed (landscape) | Bottom reading overlay (~286 dp with the Launch Cradle) and top chrome (~100 dp) collide on a 360 dp-high viewport; the picture is fully covered | `FeedPageContent.kt:149-200, 226-350` |
| H3 | High | Archive | An index load failure is rendered as "No entries match your filters." with a "Clear filters" button that does nothing and no Retry; `hasError`/`isOffline` are never read | `ArchiveScreen.kt:193-206, 321-346`; `ArchiveViewModel.kt:31-32, 91-94` |
| H4 | High | Filter sheet | "Jump to date" is stored forever and re-applied on every later OK, silently undoing a year/type change; the OK label counts titles containing an ISO date, so it says "Show 0 pictures" | `FilterSheet.kt:123, 127, 241-252`; `ArchiveViewModel.kt:121-133` |
| H5 | High | Rotation / process death | Sheet/dialog state is `remember` (not `rememberSaveable`); no ViewModel uses `SavedStateHandle`, so process death loses the feed position and the archive filters | `FeedScreen.kt:100-101`; `ArchiveScreen.kt:84`; `DetailScreen.kt:92`; `SettingsScreen.kt:75` |
| H6 | High | Archive filter state | After choosing an "Older" year, no year chip is selected anywhere; the archive's quick row only covers the 3 newest years, so the active filter is invisible | `FilterSheet.kt:128, 158-169`; `ArchiveScreen.kt:282-295` |
| M1 | Medium | Localization | 28 snackbar/status message constants in ViewModels (plus 2 screen-level permission messages and the share-text prefix) are hard-coded English; 14 translated locales never see them | `FeedViewModel.kt:1003-1017, 1036`; `DetailViewModel.kt:264-274`; `OnboardingViewModel.kt:178-182`; `FeedScreen.kt:485`; `DetailScreen.kt:415` |
| M2 | Medium | Detail | Placeholder/missing **image** entries are told "Video entries can't be used as wallpaper." | `DetailViewModel.kt:197-198, 222` |
| M3 | Medium | Feed | A failed archive-index refresh sets `isOffline = true` for the whole feed: Download disappears, "Set wallpaper" becomes "Retry", and pages read "Description unavailable offline" although the picture loaded | `FeedViewModel.kt:565-567`; `FeedPageContent.kt:270-303, 331` |
| M4 | Medium | Permissions | `POST_NOTIFICATIONS` is declared but never requested; the wallpaper-update notification is silently dropped on Android 13+ | `AndroidManifest.xml:7`; `NotificationHelper.kt:65-140` |
| M5 | Medium | A11y | Touch targets below the app's own 48 dp token: 44 dp glass buttons, 46 dp rail buttons, 40 dp text actions/segments/cache button, 44 dp dialog buttons, 40 dp wallpaper toggle row | see §3 M5 |
| M6 | Medium | Layout/i18n | Feed rail labels are 10 sp in a 56 dp column with `maxLines = 1` and no overflow; German/French/Portuguese/Japanese labels are clipped, all locales clip at ≥1.3× | `FeedPageContent.kt:408, 429-436` |
| M7 | Medium | A11y | Control outlines and input fills are ~1.1–1.5:1 against their background (WCAG 1.4.11 needs 3:1 for the control boundary) | `Color.kt:25, 28`; `FeedSearchSheet.kt:269` |
| M8 | Medium | A11y/UX | Neither search field autofocuses (keyboard never opens on open); `contentDescription` on the `BasicTextField` replaces the editable-text semantics, so TalkBack stops reading what is typed | `FeedSearchSheet.kt:255-262`; `ArchiveScreen.kt:236-245`; `AdvancedScreen.kt:259-271` |
| M9 | Medium | RTL | `supportsRtl="true"` but the back/chevron/external icons have no `autoMirrored`; with an RTL system locale the back arrow points right | `res/drawable/ic_back_24.xml`, `ic_chevron_right_24.xml`, `ic_external_24.xml` |
| M10 | Medium | Archive search | Search is silently scoped to the implicitly selected year (default: current year); searching a historical title returns nothing with no visible year filter | `ArchiveViewModel.kt:26, 35-48`; `ArchiveScreen.kt:282-295` |
| M11 | Medium | Random picker | Up to 8 sequential network fetches (60 s call timeout each) behind a blocking modal, with a single generic failure message | `FeedViewModel.kt:139-175, 1033, 1036` |
| M12 | Medium | Settings | "Clear image cache" deletes Coil's and OkHttp's cache directories underneath the live cache instances | `CacheManager.kt:18-38`; `MohakashApplication.kt:31-36`; `AppContainer.kt:53` |
| M13 | Medium | Advanced | Endpoint change does not cancel in-flight feed jobs; `loadLatest`/`loadIndex` early-return, so old-endpoint data can repopulate the feed after the reset | `FeedViewModel.kt:882-895, 898-934` |
| M14 | Medium | First-visit cue | The swipe cue is positioned at 38 % of the screen height; on phones shorter than ~780 dp it is drawn on top of the title/teaser it is meant to sit above | `FeedScreen.kt:278, 304-315` |
| M15 | Medium | Filter sheet | The year-picker dialog has no outer scroll; title/"Close" can go off-screen in landscape or at large font scale | `FilterSheet.kt:285-294, 306-347` |
| L1 | Low | Feed | A failed page image has no retry action (the detail screen has one) | `FeedPageContent.kt:489-548` |
| L2 | Low | Feed | "Back to latest" with a filter jumps to the newest *filtered* page | `FeedScreen.kt:199-202` |
| L3 | Low | Detail | Favourite/share do nothing while the entry is loading, with no disabled state or feedback | `DetailScreen.kt:153-170, 248-262` |
| L4 | Low | Permissions | Permanently denied storage permission (API ≤ 28) only produces a snackbar; no "Open settings" action | `FeedScreen.kt:124-133`; `DetailScreen.kt:94-97` |
| L5 | Low | Saved hub | The top bar always says "Archive", even on the Saved segment; the Saved count/title is dead because `showTopBar = false` | `ArchiveHubScreen.kt:58-68, 95-101`; `SavedScreen.kt:89-113` |
| L6 | Low | Semantics | The "Filters" and "Older" chips are announced as radio buttons ("not selected") although they open dialogs; the random dialog has no pane title | `ArchiveScreen.kt:289-295`; `FilterSheet.kt:165-169`; `FeedRandomPick.kt:155-198` |
| L7 | Low | Dead code | `Routes.SAVED`, `SETTINGS_TAB`, `nav_saved`, `formatSyncedAgo`, `headerLine` (hard-coded `"Credit: "`) are unused; `saved_count` is unreachable | `Routes.kt:13, 16`; `TimeFormatting.kt:33-47`; `EntryDetailFormatting.kt:58-69` |
| S1-4 | — | Speculative | Pager/refresh reconciliation race; 11 k-page `map` per hydration; search-sheet + IME; system animation scale | §5 |

Nothing in this audit rises to **critical**: no crash, data-loss or security defect was confirmed in the UI layer.

## 2. Confirmed defects — details

### H1 — Onboarding overflows into the CTA on small screens, landscape and large fonts
`OnboardingContent` is a non-scrollable `Column`. The visual block is a `Box(Modifier.weight(1f))` containing a `Column` whose first child is a fixed `180.dp × aspectRatio(9/19)` phone (≈ 380 dp tall), followed by the 30 sp headline and the subtitle (`OnboardingScreen.kt:96-151`). The bottom block adds a 52 dp primary + 52 dp quiet button + 2-line note + 8 dp gaps (≈ 160 dp) (`:153-180`).

- Portrait 360 × 640 dp: available for the weighted box ≈ 640 − insets(48) − 56 (padding) − 160 (CTAs) ≈ 376 dp; content needs ≈ 540 dp → ~164 dp of overflow. The CTA is composed after the Box and therefore draws over the overflowing headline/subtitle.
- Landscape (~360 dp height): the box gets ≈ 90 dp for ≈ 540 dp of content — the preview covers the whole content area and the buttons.
- Font scale 1.3–2.0 multiplies the text block; even a 800 dp-tall phone overflows.

The screen has no `verticalScroll` and the top preview is not allowed to shrink (`width(180.dp)` with a fixed ratio, `Modifier.weight` only on the outer box).

**Fix (minimal):** wrap the whole content in `verticalScroll` (so the buttons stay reachable), and make the preview height adaptive, e.g. `Modifier.fillMaxWidth(0.55f).heightIn(max = 320.dp)` or use `BoxWithConstraints` to scale the 180 dp width by `maxHeight`. Alternatively make the preview `weight(1f, fill = false)` inside a scrollable column.

### H2 — The feed is unusable in landscape
In the feed page the bottom reading block (date rail + credit + 2-line title + 2-line teaser + action row) is bottom-anchored with `bottom = 14.dp + bottomBarInset` (`FeedPageContent.kt:226-235`; the Launch Cradle contributes ~72 dp + navigation inset through `MohakashBottomBar.kt:91-113`). Estimated block height: credit 16 + title 2×26 + teaser 2×22 + action row 48 + 3×6 gaps + 6 top padding ≈ 200 dp, so the block spans roughly the bottom 286 dp. The top chrome adds a 200 dp scrim plus a 44 dp row under the status bar (`:149-200`).

On a 360 dp-high landscape viewport the two overlap and the image is entirely hidden; the "Set wallpaper"/"Learn more" row is squeezed into the top bar. There is no orientation lock in the manifest, so rotation is a supported path.

**Fix:** in landscape (or `maxHeight < ~500.dp`) reduce the overlay: one-line title, hide the teaser, collapse the date rail, and cap the top scrim; or keep the chrome but make the page scrollable. A `BoxWithConstraints` branch on `maxHeight` is enough.

### H3 — Archive shows the wrong empty state and cannot recover when the index fails
`ArchiveViewModel.load()` (called once from `ArchiveScreen.kt:87`) sets `hasError = true` / `isOffline = …` when `archiveIndexRepository.ensureLoaded` returns null (`ArchiveViewModel.kt:81-97`), but `ArchiveContent` never reads either field (`ArchiveScreen.kt:193-206`). With no entries it always renders `ArchiveEmptyState`, whose copy is "No entries match your filters." (query blank) and whose only action is **Clear filters**, i.e. `viewModel.reset()` — which cannot create data. There is no Retry, and the screen does not call `load()` again; the only recovery is switching the hub segment or leaving and re-entering the tab.

Repro: fresh install (or clear app data), airplane mode, open Archive. Expected an offline/error state with Retry; actual a misleading "no matches" state and a dead button.

**Fix (minimal):** add `state.hasError` / `state.isOffline` branches before the empty state, with "Retry" calling `viewModel.load()`; keep "Clear filters" only for a query/filter that is actually set.

### H4 — Filter sheet: the jump date is sticky and poisons later filters; the count is wrong
`ArchiveViewModel.jumpToDate()` persists both `query = date` and `jumpToDate = date` (`ArchiveViewModel.kt:121-128`). `FilterSheetContent` seeds its local state from `state.jumpToDate` and **always** re-applies it in the primary button (`FilterSheet.kt:123, 250`).

Repro: Archive → Filters → Jump to date → pick 2015-06-01 → OK (list now shows that date, search field shows "2015-06-01"). Re-open Filters → tap the 2026 year chip → OK. `onYearSelected(2026)` runs, then `onJumpToDate("2015-06-01")` sets the year back to 2015 and the query back to the date. The user cannot change the year from the sheet until they press Reset.

The button label is computed from `preview = state.copy(query = jumpToDate.orEmpty())` (`:127`), so with a jump date selected the count is "entries whose title/date contains 2015-06-01" → 1, or 0 when the jumped year differs from `selectedYear`. The label therefore says "Show 0 pictures" for a valid date (`:242-246`).

**Fix:** treat the jump date as a one-shot: clear `state.jumpToDate` when applied (or when the sheet opens), and do not feed it into the count. Pass `jumpToDate` separately from the year/type preview; clamp the date to the archive range with `nearestDate`.

### H5 — Rotation closes every sheet; process death loses the reading position and filters
Only two `rememberSaveable` calls exist in the app (`FeedScreen.kt:103` chrome, `ArchiveHubScreen.kt:44` segment). All sheet/dialog visibility is plain `remember`:

- `FeedScreen.kt:100` `showSearch` → search closes on rotation (the query survives in the VM, but the user loses the sheet and the keyboard state).
- `ArchiveScreen.kt:84` `showFilters` → filter sheet closes, including staged (unapplied) year/type.
- `DetailScreen.kt:92` `showWallpaperSheet` → wallpaper sheet closes.
- `SettingsScreen.kt:75` `showClearCacheDialog` → dialog closes.
- `FeedScreen.kt:101` `pendingSaveDate` → rotating while the storage-permission dialog is up drops the save.

No ViewModel constructor takes a `SavedStateHandle` (grep for `SavedStateHandle` returns nothing), so on process death the feed re-opens on the latest picture instead of the last-read date, and the archive re-opens with an empty query and the current year; the detail reloads from the network. Rotation of the feed itself also briefly shows page 0 until `scrollToPage` restores the date (`FeedScreen.kt:345-355`), and re-shows the bottom bar for one frame if chrome was hidden (`feedFullscreen` is `remember`, `MohakashApp.kt:68`).

**Fix (minimal):** `rememberSaveable` for the sheet flags; `SavedStateHandle` for `currentDate`/`currentPageKey` and the archive query/year; use `pagerState` with an `initialPage` derived from the saved date instead of scrolling after the fact.

### H6 — The active Archive filter has no visible selected state
`olderSelected` is `selectedYear !in state.availableYears` (`FilterSheet.kt:128`). The year picked through the "Older" picker *is* in `availableYears` (all 1995–2026 rows are present), so it is never highlighted. The six quick chips cover only the newest years, so a 2010 filter leaves **no** chip selected in either the sheet or the archive row (`ArchiveScreen.kt:282-295` shows only `availableYears.take(3)` plus a permanently unselected "Filters" chip).

Repro: Archive → Filters → Older → 2010 → OK. The list is 2010 but every chip looks unselected; on reopening the sheet there is still no selected year.

**Fix:** compute `olderSelected` as "selectedYear is not in the quick chips" rather than "not in the archive", and add a selected state/indicator to the "Filters" chip (e.g. a dot or `selected = selectedYear !in quickYears`).

### M1 — Snackbar and status copy is hard-coded English
There is no resource plumbing for one-shot messages: `FeedEvent.ShowMessage(val text: String)` and the equivalents carry raw strings. 14 constants in `FeedViewModel`'s companion plus `MESSAGE_RANDOM_UNAVAILABLE` (`FeedViewModel.kt:1003-1017, 1036`), 10 in `DetailViewModel` (`DetailViewModel.kt:264-274`), 3 in `OnboardingViewModel` (`OnboardingViewModel.kt:178-182`), 2 screen-level permission messages (`FeedScreen.kt:485`, `DetailScreen.kt:415`), and `ShareHelper`'s `"Image: "` prefix (`ShareHelper.kt:96-106`). All of these stay English in bn/de/es/fr/hi/id/it/ja/ko/pt/tr/vi/zh.

This is not hypothetical: the localized `R.string.feed_random_unavailable` was just deleted as "unused" in the working tree while `FeedViewModel` still displays its own English literal — a direct consequence of the missing plumbing.

**Fix:** make events carry `@StringRes` ids (plus format args) or a small `UiText` type, resolve with `stringResource`/`context.getString` in the composable; move the strings to `strings.xml`, translate, and add a lint/unit guard.

### M2 — Wrong snackbar for placeholder/missing images
`DetailViewModel.setAsWallpaper()` rejects `!entry.supportsWallpaper` (i.e. `mediaType != IMAGE || imageStatus != OK`) with `MESSAGE_VIDEO_NO_WALLPAPER` (`DetailViewModel.kt:197-198`), and the `InstallOutcome.NOT_USABLE` branch does the same (`:222`). For an **image** whose publisher supplied a placeholder or no picture, the user is told "Video entries can't be used as wallpaper." Repro: any entry with `image_status = "placeholder"`; open Detail → Set as wallpaper.

**Fix:** branch on `mediaType` vs `imageStatus` and add a dedicated "This picture is a placeholder / missing" message. Disable the button for entries that cannot be used (the feed's `WallpaperPill` has the same dead-end, though its message is generic).

### M3 — A failed index refresh marks the whole feed "offline"
`loadIndex` sets `isOffline = true` on any refresh failure, even when a cached index is present and `/latest.json` + the page images succeeded (`FeedViewModel.kt:565-567`). The page then hides Download (`FeedPageContent.kt:331`), replaces the wallpaper pill with **Retry** (`:293-296`), and shows "Description unavailable offline" when the teaser is absent (`:270-275`). The Retry action is `refreshLatest()` (`FeedScreen.kt:185`), which only reloads `/latest.json`; it refreshes the index solely when the cached index head is older than the latest date (`refreshIndexAfterLatestChange`, `FeedViewModel.kt:597-618`), so a plain index failure (e.g. HTTP 500 with an up-to-date index) is not retried and the "offline" state persists until the next `onResume`/index load.

Repro: serve `latest.json` normally but make `/archive-index.json` return HTTP 500 (or drop it once on a flaky 5 MB download). The feed has pictures but behaves as if the device were offline.

**Fix:** do not let the index result drive the page's `isOffline`; keep a separate `isIndexOffline` (or only set `isOffline` when there is no data at all), and make Retry re-run `ensureIndex()` as well.

### M4 — The notification permission is declared but never requested
`POST_NOTIFICATIONS` is in the manifest (`AndroidManifest.xml:7`) and `NotificationHelper.notifyWallpaperChanged` calls `manager.notify` unconditionally (`NotificationHelper.kt:65-140`), but no code requests the permission, checks `areNotificationsEnabled()`, or explains the missing notification. On API 33+ the daily "wallpaper updated" notification silently never appears; the settings action in the notification is therefore also unreachable.

**Fix:** request `POST_NOTIFICATIONS` once from Settings ("Update automatically" on) or onboarding, gate the notify call, and add a note/row when notifications are blocked.

### M5 — Touch targets below 48 dp
The app defines `MohakashDimens.TouchTarget = 48.dp` but several interactive surfaces are smaller:

| Control | Size | Location |
|---|---|---|
| Feed glass icon button (search/settings) | 44 dp | `FeedPageContent.kt:608` + `Dimens.kt:103` |
| Feed rail action (heart/download/share) | 46 dp | `FeedPageContent.kt:413` |
| `TextAction` ("Learn more", "Retry", "Cancel", "Close") | 40 dp min | `CommonComponents.kt:239` |
| `SegmentedControl` (Archive/Saved, Fill/Fit, media type) | 40 dp min | `CommonComponents.kt:323` |
| Settings "Clear" (cache) | 40 dp min | `SettingsScreen.kt:341` |
| Wallpaper sheet "Update daily" row | 40 dp min | `WallpaperSheet.kt:206` |
| Dialog text buttons (clear cache) | 44 dp min | `SettingsScreen.kt:430` |

The feed's most important secondary action ("Learn more") and the Archive's only tab switch are both under the minimum. **Fix:** raise the clickable boxes to `heightIn(min = TouchTarget)` (or add `Modifier.minimumInteractiveComponentSize()`), keeping the visual chip smaller.

### M6 — Localized labels are clipped in the feed rail
`FeedRailAction` puts a 10 sp single-line label in a `Modifier.width(56.dp)` column with `maxLines = 1` and default clipping (`FeedPageContent.kt:408, 429-436`). At 10 sp Inter, ~5.5 dp/char, so ~10 characters fit: German "Herunterladen" (12), "In Favoriten" (12), French "Télécharger" (11), Portuguese "Compartilhar" (12), Japanese "ダウンロード" (6 CJK ≈ 60 dp) all clip at 1.0×, and every locale clips at the system font scale of 1.3+. The same pattern affects the rotated date rail (10 sp, `requiredWidth(140.dp)`, no ellipsis, `FeedPageContent.kt:356-394`).

**Fix:** allow two lines with `TextOverflow.Ellipsis`, or widen the rail and shorten the translations (e.g. "Laden"/"DL", "Téléch."), or drop labels below a width threshold.

### M7 — Control boundaries and input fills have near-invisible contrast
`Border #232838` on `Surface #151922` ≈ **1.2:1**; `BorderStrong #323849` on `Surface` ≈ **1.5:1**; the search/endpoint fields fill `Background #0B0D12` on `Surface` ≈ **1.1:1**. WCAG 1.4.11 requires 3:1 for the visual boundary of a control. The unselected chips, segmented control, text fields and the sheet drag handle therefore effectively have no boundary for low-vision users (the labels are fine — `TextMuted` is ~6.9:1).

**Fix:** raise `BorderStrong` to ≥ `#606A80` (≈3.2:1 on #151922) for controls, or use a filled surface that is ≥3:1 from its background; keep the decorative `Border` for images only. Verify with a contrast checker per token.

### M8 — Search/endpoint fields: no autofocus and a contentDescription that hides typed text
`SearchField` (`FeedSearchSheet.kt:255-262`) and the Archive variant (`ArchiveScreen.kt:236-245`) are `BasicTextField`s with a hint painted in the decoration box and no `FocusRequester`/autofocus, so opening search still needs a tap before the keyboard appears. Both also set `Modifier.semantics { contentDescription = hint }`, which overrides the field's description; TalkBack announces "Search titles or dates, edit box" and the entered query is no longer the node's description (the value may still be readable as EditableText, but the override is an anti-pattern). `AdvancedScreen`'s `EndpointField` does the same (`AdvancedScreen.kt:259-271`). Neither search field handles `ImeAction.Search` (`keyboardActions` absent), so the IME key only hides the keyboard.

**Fix:** add `focusRequester` + `LaunchedEffect { focusRequester.requestFocus() }` (and optionally show the IME), remove the `contentDescription` override in favour of a visible/placeholder label and `semantics { }` on the container, and add `KeyboardActions(onSearch = { … })`.

### M9 — RTL: directional icons are not mirrored
The manifest declares `android:supportsRtl="true"`, and layout mirroring is otherwise done correctly (start/end everywhere), but none of the vector drawables sets `android:autoMirrored="true"`. With an RTL system locale (Arabic/Hebrew; the app ships no RTL locale, but the base resources still follow the system layout direction) the top-bar back arrow points right and the settings chevron points the wrong way. `ic_back_24.xml`, `ic_chevron_right_24.xml`, `ic_external_24.xml`.

**Fix:** add `android:autoMirrored="true"` to those three (and audit any future directional icon); the rotated date rail is acceptable.

### M10 — Archive search is silently limited to one year
`ArchiveUiState.filtered` first filters `it.year == selectedYear` and only then applies the query (`ArchiveViewModel.kt:35-48`). `selectedYear` defaults to the calendar year (`:26`, `applyEntries`), and the only year UI is the 3-chip quick row plus the filter sheet. Searching "Andromeda" on the Archive tab therefore searches 2026 only, and if the year is not one of the three visible chips there is no indication why a known 2014 picture is missing.

**Fix:** either search across all years in the archive search (and let the year chip be an explicit scope), or surface the active year as a removable filter chip above the list. At minimum, show "Searching 2026" in the field/hint.

### M11 — Random picker blocks on up to eight sequential network calls
`pickRandom` shuffles the index and calls `apodRepository.fetchEntry(candidate.date)` in a loop of up to 8 (`FeedViewModel.kt:139-175`, `MAX_RANDOM_ATTEMPTS = 8`) while `FeedRandomPickOverlay` is modal. Each call has OkHttp's 60 s call timeout; on a slow or captive network the overlay can stay up for minutes with only "Cancel" as feedback. The failure copy is a single English string (`:1036`).

**Fix:** fetch candidates in parallel with a short per-request timeout (e.g. 8 s) or pre-validate from the index alone (the index already has `mediaType`, `imageUrl`/`thumbnailUrl`), cap the total time, and show progress ("1 of 8"). Also localize the message (M1).

### M12 — Clearing the cache deletes files under live cache instances
`CacheManager.clear()` deletes `cacheDir/image_cache` and `cacheDir/http` contents directly (`CacheManager.kt:18-38`). Those directories belong to Coil's `DiskCache` (`MohakashApplication.kt:31-36`) and OkHttp's `Cache` (`AppContainer.kt:53`), both of which keep an open journal/DB. Deleting the files out from under them can leave the journal inconsistent; Coil may keep serving/reporting stale entries or fail writes until it rebuilds.

**Fix:** inject the `ImageLoader`/`OkHttpClient` and call `imageLoader.diskCache?.clear()` and `okHttpClient.cache?.evictAll()` (and `imageLoader.memoryCache?.clear()`), rather than walking `cacheDir`. Then recompute the size from the caches.

### M13 — Endpoint change can be overwritten by in-flight feed jobs
`observeSettings` reacts to a `customApiBaseUrl` change by clearing the repository, calling `resetForEndpointChange()` and then `loadLatest(force = true)` / `loadIndex(...)` (`FeedViewModel.kt:882-895`). `resetForEndpointChange` (`:898-934`) clears the in-memory data but does **not** cancel `latestJob`, `indexJob`, `refreshJob` or `searchJob`; `loadLatest`/`loadIndex` both early-return while a job is active (`:489`, `:537`). A request started against the old endpoint can complete after the reset, call `adoptLatest`/`applyIndex`, and repopulate the feed with old-server data; the new endpoint is not fetched until the next refresh.

Repro: open the feed, pull to refresh on a slow network, immediately go to Settings → Advanced and save a new endpoint, then return to the feed. The comment above `resetForEndpointChange` claims this cannot happen.

**Fix:** cancel and null the jobs before resetting, and await/launch the new loads after the cancellation (`latestJob?.cancel()`, etc.).

### M14 — The first-visit swipe cue is drawn over the reading overlay on short phones
`hintBottomPadding = (maxHeight * 0.38f).coerceIn(140.dp, 320.dp)` (`FeedScreen.kt:278`) and the pill is composed after the pager, so it is above the reading overlay. The reading block spans roughly the bottom 286 dp (H2), so on a 640 dp-tall device the cue sits at 243 dp — inside the title/teaser block — for its 6 s timeout; it only clears the text on devices taller than ~780 dp. The demo animation also lifts the pager 24 % up (`:414-433`), opening a blank band behind the Launch Cradle.

**Fix:** position the cue relative to the actual overlay (e.g. `bottomBarInset + readingBlockHeight + 16.dp`), or place it above the content area with a measured `onGloballyPositioned` anchor; keep the demo lift but translate the overlay with it or lower it to ~12 %.

### M15 — The year-picker dialog can exceed the screen
`YearPickerDialog` is a `Dialog` containing a `Column` with a title, a `FlowRow(heightIn(max = 360.dp).verticalScroll(...))` and a "Close" button (`FilterSheet.kt:306-347`). In landscape (≈360 dp high) or at 2× font scale, 24 + 30 + 12 + 360 + 12 + 44 ≈ 500 dp does not fit, and the outer Column does not scroll, so the title and Close move off-screen. (The 1995–2020 grid itself scrolls, so only the frame is affected.)

**Fix:** put the dialog body in a `verticalScroll` and cap the grid at `maxHeight * 0.5`, or use a full-height bottom sheet in landscape.

### Low findings (confirmed, no separate repro needed)
- **L1** `FeedImageLayer` has no retry after `failed`; the page shows "Image unavailable" with no action (`FeedPageContent.kt:489-548`). The detail screen's viewer has Retry; reuse it.
- **L2** `onBackToLatest` takes `state.pages.firstNotNullOfOrNull { it.date }`, i.e. the newest *filtered* page; with a filter active the button labelled "Back to latest" does not go to the latest picture (`FeedScreen.kt:199-202`). Use `state.archiveLastDate`/`latestDate`, or relabel "Back to top".
- **L3** Detail favourite/share are enabled while `state.entry == null` and silently return in the VM (`DetailScreen.kt:153-170`; `DetailViewModel.kt:153-186`). Disable or show a loading state.
- **L4** If the user permanently denies `WRITE_EXTERNAL_STORAGE` (API ≤ 28), the launcher returns immediately and the only feedback is the English snackbar; there is no `shouldShowRequestPermissionRationale`/Settings path (`Permissions.kt`, `FeedScreen.kt:124-133`).
- **L5** The combined hub's top bar always says "Archive" and the Saved segment has no title or count: `SavedScreen` is called with `showTopBar = false` (`ArchiveHubScreen.kt:95-101`), so `saved_count`/`saved_title` are dead in the only Saved entry point. Show the count next to the segmented control and update the title with the selected segment.
- **L6** Chips that open dialogs use `Role.RadioButton` (`ArchiveScreen.kt:289-295`, `FilterSheet.kt:165-169`), so TalkBack announces "Filters, radio button, not selected" and "Older, not selected" even when an older filter is active. Also the random-picker `Dialog` and `YearPickerDialog` have no `paneTitle`, so the dialog title is not announced. The page-wide tap-to-toggle-chrome `Box` is a full-screen `Role.Button` (`FeedPageContent.kt:135-147`), which dominates the TalkBack traversal order.
- **L7** Unused after the latest refactors: `Routes.SAVED`, `Routes.SETTINGS_TAB`, `R.string.nav_saved`, `TimeFormatting.formatSyncedAgo` (the Settings screen now uses `DateUtils`), `EntryDetailFormatting.headerLine` (and its hard-coded `"Credit: "`), `saved_count` (see L5). `EntryDetailFormatting.headerLine` is tested but not used; the test locks in an English format.
- **L8** `ShareHelper.buildShareText` hard-codes `"Image: "` and ASCII quotes and is not localized (`ShareHelper.kt:96-106`); sharing is a user-visible text surface in every locale.

## 3. Speculative concerns (need a device/emulator to confirm)

**S1 — Pager position may end up one page off after pull-to-refresh.** `onPageSettled` writes `currentPageKey` from the settled index; inserting a new latest at the head shifts every index by one. The reconciliation effect (`FeedScreen.kt:345-355`) and the settled-page flow can race: the effect may capture the old key, scroll to the corrected index, and then a late `onPageSettled` from the shifted position overwrites the key with the neighbouring date, after which the effect scrolls to the neighbour. Worth a test with a fake pager, but it cannot be confirmed statically.

**S2 — Per-hydration 11 k-page copy.** `patchPage` maps the whole `pages` list (≈11,000 `FeedPageUi` objects) for every page settle/hydration (`FeedViewModel.kt:790-805`). That is one 11 k-element list allocation per swipe on a low-end device; it may be visible as jank. `patchPage` could update in place via an index map or move the hydration state out of the page object.

**S3 — Search sheet and the IME.** `ModalBottomSheet` content is `fillMaxHeight(0.92f)` with the result list at the bottom (`FeedSearchSheet.kt:120-124`). Depending on the Compose version and the window inset handling, the keyboard may cover the lower results; I could not verify without a device.

**S4 — System "remove animations" is ignored.** Both infinite animations (`FeedSwipeHint`, `RandomCourse`) and the cue's 24 % lift run regardless of `Settings.Global.ANIMATOR_DURATION_SCALE`. Not a WCAG failure by itself, but a reduced-motion branch would be expected.

**S5 — `Detekt`/lint blind spots.** The current lint report only flags folder naming, unused resources and modifier ordering. Compose accessibility (touch targets, contrast, content descriptions, RTL mirroring) and translation clipping are outside lint's reach; they need UI tests or a manual pass.

## 4. Test gaps

The committed suite (404 tests, all passing) is good on pure logic — paging, load policy, date math, image sizing, wallpaper geometry, API mapping, deep-link parsing. What it does **not** cover, and which allowed the defects above:

1. **No navigation tests.** `DeepLinkParserTest` asserts that `DeepLinkTarget.Archive.destination == Routes.ARCHIVE`, but nothing tests `NavController.navigateTo`, which is where the actual screen is chosen. The working tree just changed `/archive` from `Routes.TODAY` to `Routes.ARCHIVE`; without a test this can regress again. A `TestNavHostController` test for all three targets (cold start, warm `onNewIntent`, back stack after each) is the highest-value addition.
2. **No ViewModel tests at all** (Feed, Archive, Detail, Advanced, Settings, Onboarding). The filter/jump regression (H4), the archive error state (H3), the endpoint reset race (M13) and the random-candidate loop (M11) are all deterministic and testable with the existing fakes in `testing/Fakes.kt`.
3. **No Compose UI/instrumentation tests.** `androidTestImplementation` is configured but there is no `src/androidTest`. Missing: screen-level assertions for loading/empty/offline (H3), sheet open/dismiss, `StateRestorationTester` rotation/process-death cases (H5), touch-target checks (`AccessibilityChecks`/`assertTouchWidthHeight`), semantics assertions (M8/L6), and layout tests at 320 dp width, landscape, `fontScale = 1.3/2.0` and RTL.
4. **No localization test.** A simple resource-parity test (all `values-*` have the base keys and the same format specifiers) would have caught the `values-id` folder that lint found (now renamed to `values-in`), and a test that runs the UI with `Locale("de")` would catch M6. There is no test that the string resources are actually used by the ViewModels (M1).
5. **No layout/screenshot baseline.** The onboarding and feed-landscape failures (H1/H2) are only visible at specific sizes; Roborazzi/Paparazzi baselines for 320×640, 360×800, landscape and fontScale 2 would lock them down.
6. **No permission-flow tests.** Nothing verifies the `POST_NOTIFICATIONS` request (M4) or the legacy-storage denial path (L4).
7. **`TimeFormattingTest` locks in English literals** for `formatSyncedAgo`, which the Settings screen no longer uses (it now uses `DateUtils`). Either delete the function/test or localize it.
8. **No cache-coordination test.** `CacheManager` is exercised only through the UI; a test with a real `DiskCache` would show the journal problem (M12).

## 5. Checked and found sound (to bound the audit)

Deep-link parsing (host/path/ISO validation, query/fragment tolerance, unknown paths), the manifest's App Links declarations, `WallpaperIntentBuilder` fallbacks, wallpaper install/keep semantics, the share intent's scheme filtering, `openLink`'s scheme allow-list, image URL validation, paging order/gap handling, the 3-attempt hydration cap, the endpoint URL validator, the wallpaper geometry and zoom bounds, and the image sizing math are all coherent and covered by tests. The 15 resource locales are complete (no missing keys or format-specifier mismatches as of this snapshot), and the `values-id` → `values-in` folder rename is the correct resource qualifier for Indonesian.

## 6. Changes observed during the audit (de-duplication note)

The working tree changed several times while this audit ran. These fixes are already present at 01:50 and are **not** counted above:

- `navigateTo(DeepLinkTarget.Archive)` now goes to `Routes.ARCHIVE` (was `Routes.TODAY` at HEAD `00dc87d`, contradicting README:278). Please land this with the navigation test in §4.1.
- `values-id/` renamed to `values-in/` (lint `LocaleFolder`). The resource qualifier must be `in`; note that `localeConfig` takes a BCP-47 tag while resources use the legacy qualifier, so the `id` ↔ `in` choice in `res/xml/locales_config.xml` should be verified in the per-app language picker on a device (Indonesian must select `values-in`, not fall back to English).
- Lint annotations: `@SuppressLint("StaticFieldLeak")` on `NotificationHelper`, `Modifier.offset` → `graphicsLayer` in `RandomCourse`, `mutableIntStateOf` for the random request.
- Settings "Last synced" switched from the English `TimeFormatting.formatSyncedAgo` to locale-aware `DateUtils` plus two new strings (added to all locales). `formatSyncedAgo` is now dead code (L7).
- Unused strings `nav_saved`, `feed_offline_chip`, `feed_random_unavailable` were removed; the first two are genuinely unused, the third is only unused because the message is hard-coded (M1).
