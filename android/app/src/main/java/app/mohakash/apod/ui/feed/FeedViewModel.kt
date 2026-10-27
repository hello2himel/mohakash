package app.mohakash.apod.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.repository.ArchiveIndexRepository
import app.mohakash.apod.data.repository.FavouritesRepository
import app.mohakash.apod.data.repository.ImageRepository
import app.mohakash.apod.data.storage.ShareHelper
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.core.ImageUrlValidator
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.work.ApodSyncEngine
import app.mohakash.apod.work.InstallOutcome
import app.mohakash.apod.work.SyncController
import app.mohakash.apod.work.SyncPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Drives the combined feed.
 *
 * The screen is the archive: one vertically paged list of every APOD, newest first. This
 * ViewModel owns the whole list (index order, year hydration, filters, search and the current
 * page), so the pager, the sheets and the Saved tab all read the same data and the same
 * favourites.
 *
 * Three rules keep the network cost predictable (see `FeedLoadPolicy`):
 * 1. `latest.json` and the current year file come first — page 0 renders without the index.
 * 2. The index is fetched at most once a day, lazily, and is cached on disk.
 * 3. Swiping never fetches: a page hydrates from Room, and only a year that was never fetched
 *    triggers one request — coalesced across the whole prefetch window.
 */
class FeedViewModel(
    private val apodRepository: ApodRepository,
    private val archiveIndexRepository: ArchiveIndexRepository,
    private val favouritesRepository: FavouritesRepository,
    private val imageRepository: ImageRepository,
    private val settingsStore: SettingsStore,
    private val shareHelper: ShareHelper,
    private val wallpaperStorage: WallpaperStorage,
    private val syncEngine: ApodSyncEngine,
    private val syncController: SyncController,
    /** Reports whether Mohakash is already the selected live wallpaper. */
    private val isWallpaperActive: () -> Boolean = { false },
    /** Reports whether the active connection is unmetered (Wi-Fi/ethernet). */
    private val isUnmetered: () -> Boolean = { true },
    /** Reports whether there is any connection at all. */
    private val isConnected: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // The raw sources of the list. Only touched on the main dispatcher.
    private var indexEntries: List<ArchiveIndexEntry> = emptyList()
    private var yearRecords: Map<Int, List<ApodEntry>> = emptyMap()
    private var latestRecord: ApodEntry? = null
    private var latestDate: String? = null
    private var filters = FeedFilters()

    /** Full records of recently visited pages (LRU, bounded by [HYDRATED_LIMIT]). */
    private val hydrated = LinkedHashMap<String, ApodEntry>()
    private var hydrationStatus: Map<String, Hydration> = emptyMap()
    private val hydrationAttempts = mutableMapOf<String, Int>()

    private var currentDate: String? = null
    private var currentPageKey: String? = null
    private var listVersion = 0L
    private var jumpToken = 0L
    private var isIndexComplete = false
    private var indexRequested = false
    private var indexUnavailable = false
    private var pendingWallpaperDate: String? = null

    /** Set as soon as the first-run cue is dismissed; never shows it again this session. */
    private var swipeHintDismissed = false

    private var randomJob: Job? = null
    private var latestJob: Job? = null
    private var indexJob: Job? = null
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var wallpaperSyncJob: Job? = null

    private val latestMutex = Mutex()
    private val yearMutex = Mutex()

    init {
        _state.value = _state.value.copy(isOffline = !isConnected())
        observeSettings()
        observeFavourites()
        // A fresh process may have last synced hours ago; the latest document is tiny, so force
        // one network read on launch and bypass a stale HTTP cache.
        loadLatest(force = true)
        loadIndex(userRequested = false)
    }

    // ---------------------------------------------------------------------------------------
    // Public actions
    // ---------------------------------------------------------------------------------------

    /**
     * Dismisses the one-time swipe cue and records it. Called by the cue timer, a swipe, or any
     * explicit navigation so the hint never comes back on the next launch.
     */
    fun dismissSwipeHint() {
        if (swipeHintDismissed) return
        swipeHintDismissed = true
        _state.update { it.copy(showSwipeHint = false) }
        viewModelScope.launch {
            runCatching { settingsStore.setFeedSwipeHintSeen(true) }
        }
    }

    /**
     * Picks a random picture, validates that a real image can actually be shown, then moves the
     * feed there. Videos are excluded, so the random action obeys the same "pictures only" rule
     * as the rest of the feed. The index + year caches keep repeated rolls cheap.
     */
    fun pickRandom() {
        if (randomJob?.isActive == true) return
        randomJob = viewModelScope.launch {
            _state.update { it.copy(isPickingRandom = true) }
            try {
                val index = archiveIndexRepository.ensureLoaded(allowNetwork = true).orEmpty()
                val current = currentDate
                val candidates = index
                    .filter {
                        it.mediaType.isImage &&
                            it.date.isNotBlank() &&
                            it.date != current &&
                            (it.imageUrl != null || it.thumbnailUrl != null)
                    }
                    .shuffled()
                var picked: ApodEntry? = null
                for (candidate in candidates.take(MAX_RANDOM_ATTEMPTS)) {
                    val entry = runCatching { apodRepository.fetchEntry(candidate.date) }.getOrNull()
                    if (
                        entry != null &&
                        entry.supportsWallpaper &&
                        ImageUrlValidator.isValidImageUrl(entry.displayImageUrl)
                    ) {
                        picked = entry
                        break
                    }
                }
                if (picked != null) {
                    jumpToDate(picked.date)
                } else {
                    _events.trySend(FeedEvent.ShowMessage(MESSAGE_RANDOM_UNAVAILABLE))
                }
            } finally {
                _state.update { it.copy(isPickingRandom = false) }
            }
        }
    }

    /** Cancels an in-flight random pick and closes its overlay. */
    fun cancelRandomPick() {
        randomJob?.cancel()
        _state.update { it.copy(isPickingRandom = false) }
    }

    /** Called when the pager settles on a page: records the position and hydrates it. */
    fun onPageSettled(pageIndex: Int) {
        val pages = _state.value.pages
        val page = pages.getOrNull(pageIndex) ?: return
        when (val target = page.page) {
            is FeedPage.Entry -> {
                currentDate = target.date
                currentPageKey = target.date
                _state.update { it.copy(currentDate = target.date, currentPageKey = target.date) }
                hydrate(target.date)
                // Years are appended by the prefetcher as the user approaches the tail; the big
                // index is only fetched on an explicit action (search, filter, end of archive).
                prefetchAround(pageIndex)
            }

            FeedPage.End -> {
                currentPageKey = FeedPage.End.key
                currentDate = null
                _state.update { it.copy(currentDate = null, currentPageKey = FeedPage.End.key) }
                loadIndex(userRequested = true)
                prefetchAround(pageIndex)
            }
        }
    }

    /**
     * Pull-to-refresh. A successful refresh always returns the feed to the newest APOD — even
     * when the reader was several pages down — and then synchronises the live wallpaper from the
     * same record. Failures leave the current page and the installed wallpaper untouched.
     */
    fun refreshLatest() {
        if (refreshJob?.isActive == true) return
        _state.value = _state.value.copy(isRefreshing = true)
        refreshJob = viewModelScope.launch {
            try {
                val refreshed = loadLatestInternal(force = true)
                if (refreshed) {
                    // A filter can hide the newest APOD. An explicit refresh is a request to
                    // see the latest picture, so make it visible before jumping.
                    if (filters.isActive) {
                        filters = FeedFilters()
                        rebuildPages(bumpListVersion = true)
                    }
                    // Wait until adoptLatest()/rebuildPages() has applied the new list.
                    latestDate?.let { emitJump(it, animate = false) }
                    syncWallpaperFromLatest()
                }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
                refreshIndexAfterLatestChange()
                if (indexUnavailable) loadIndex(userRequested = true)
            }
        }
    }

    /** Loads the index now — called when search or the filter sheet is opened. */
    fun ensureIndex() {
        loadIndex(userRequested = true)
    }

    /** Applies the year/media filter and repositions the pager. */
    fun applyFilters(newFilters: FeedFilters, jumpTarget: String? = null) {
        filters = newFilters
        val previousDate = currentDate
        rebuildPages(bumpListVersion = true)
        val pages = _state.value.pages
        if (pages.isEmpty()) return

        if (jumpTarget != null) {
            jumpToDate(jumpTarget)
            return
        }

        if (pages.size == 1 && pages[0].isEnd) {
            // The filter matches nothing: the end card explains it.
            currentDate = null
            currentPageKey = FeedPage.End.key
            _state.update {
                it.copy(currentDate = null, currentPageKey = FeedPage.End.key, listVersion = listVersion)
            }
            return
        }

        val newestPage = pages.firstNotNullOfOrNull { it.date }
        val keepCurrent = !newFilters.isActive && previousDate != null &&
            pages.any { it.date == previousDate }
        val target = if (keepCurrent) previousDate else newestPage
        target?.let { emitJump(it, animate = false) }
    }

    /** Clears every filter; keeps the current date when it is still visible. */
    fun clearFilters() {
        applyFilters(FeedFilters())
    }

    /** Resolves [date] gap-safely, makes it visible and moves the pager there. */
    fun jumpToDate(date: String) {
        val clean = date.trim()
        if (!FeedPaging.isIsoDate(clean)) return
        val all = allDateItems()
        val resolved = FeedPaging.nearestDate(all, clean) ?: return
        if (!FeedPaging.filtered(all, filters).any { it.date == resolved }) {
            // The date is hidden by the current filter; a jump is an explicit request for it.
            filters = FeedFilters()
            rebuildPages(bumpListVersion = true)
        }
        if (resolved != clean) {
            _events.trySend(FeedEvent.ShowMessage(MESSAGE_JUMP_GAP.format(clean, resolved)))
        }
        emitJump(resolved, animate = false)
    }

    /** Returns the feed to the newest APOD and clears any active feed filters. */
    fun showLatest() {
        if (filters.isActive) {
            filters = FeedFilters()
            rebuildPages(bumpListVersion = true)
        }
        val latest = latestDate
            ?: allDateItems().firstOrNull()?.date
            ?: _state.value.pages.firstNotNullOfOrNull { it.date }
        if (latest == null) {
            refreshLatest()
        } else {
            jumpToDate(latest)
        }
    }

    /** Called by the pager once it has applied [FeedJumpRequest]. */
    fun consumeJumpRequest(token: Long) {
        _state.update { current ->
            if (current.jumpRequest?.token == token) current.copy(jumpRequest = null) else current
        }
    }

    /**
     * Opens search with a clean slate. Search is a discovery surface, not a filtered view of
     * whatever year/media/date the feed happened to be showing.
     */
    fun openSearch() {
        _state.update {
            it.copy(
                search = FeedSearchState(
                    query = "",
                    filters = FeedFilters(),
                    results = emptyList(),
                    isSearching = false,
                ),
            )
        }
        ensureIndex()
        updateSearchResults()
    }

    /** Applies the debounced search query. */
    fun setSearchQuery(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query, isSearching = true)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            ensureIndex()
            indexJob?.join()
            updateSearchResults()
            _state.update { it.copy(search = it.search.copy(isSearching = false)) }
        }
    }

    /** Applies the year/media chips inside the search sheet. */
    fun setSearchFilters(filters: FeedFilters) {
        _state.update { it.copy(search = it.search.copy(filters = filters, isSearching = true)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            updateSearchResults()
            _state.update { it.copy(search = it.search.copy(isSearching = false)) }
        }
    }

    /** Adds the page to Saved, or removes it again. Optimistic, then reconciled with the repo. */
    fun toggleFavourite(date: String) {
        val entry = entryFor(date)
        if (entry == null) {
            _events.trySend(FeedEvent.ShowMessage(MESSAGE_DETAILS_LOADING))
            hydrate(date, force = true)
            return
        }
        val previous = _state.value.favourites
        val save = date !in previous
        _state.update {
            it.copy(favourites = if (save) it.favourites + date else it.favourites - date)
        }
        viewModelScope.launch {
            runCatching { favouritesRepository.toggle(entry) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(favourites = if (saved) it.favourites + date else it.favourites - date)
                    }
                    _events.trySend(
                        FeedEvent.ShowMessage(if (saved) MESSAGE_FAVOURITE_ADDED else MESSAGE_FAVOURITE_REMOVED),
                    )
                }
                .onFailure {
                    // The favourites flow may already have corrected the set; restore it anyway.
                    _state.update { it.copy(favourites = previous) }
                    _events.trySend(FeedEvent.ShowMessage(MESSAGE_FAVOURITE_FAILED))
                }
        }
    }

    /** Saves the picture of one page into the gallery. */
    fun saveImage(date: String) {
        val entry = entryFor(date)
        if (entry == null) {
            _events.trySend(FeedEvent.ShowMessage(MESSAGE_DETAILS_LOADING))
            hydrate(date)
            return
        }
        viewModelScope.launch {
            imageRepository.saveToGallery(entry)
                .onSuccess { _events.trySend(FeedEvent.ShowMessage(MESSAGE_SAVED)) }
                .onFailure { _events.trySend(FeedEvent.ShowMessage(MESSAGE_SAVE_FAILED)) }
        }
    }

    /** Opens the share sheet for one page. Works from the index before the page is hydrated. */
    fun share(date: String) {
        val entry = entryFor(date) ?: syntheticEntryFor(date)
        if (entry == null) {
            _events.trySend(FeedEvent.ShowMessage(MESSAGE_DETAILS_LOADING))
            return
        }
        shareHelper.share(entry)
    }

    /** Opens the wallpaper sheet for one page, hydrating it first when necessary. */
    fun openWallpaperSheet(date: String) {
        if (entryFor(date) != null) {
            _state.update { it.copy(wallpaperSheetDate = date) }
            return
        }
        pendingWallpaperDate = date
        hydrate(date, force = true)
        _events.trySend(FeedEvent.ShowMessage(MESSAGE_DETAILS_LOADING))
    }

    fun dismissWallpaperSheet() {
        _state.update { it.copy(wallpaperSheetDate = null) }
    }

    /** Persists the scaling choice — the same setting the live wallpaper uses. */
    fun setScaling(scaling: WallpaperScaling) {
        _state.update { it.copy(scaling = scaling) }
        viewModelScope.launch { settingsStore.setWallpaperScaling(scaling) }
    }

    /** Switches the automatic wallpaper update on or off from the sheet. */
    fun setUpdateDaily(enabled: Boolean) {
        _state.update { it.copy(updateDaily = enabled) }
        viewModelScope.launch {
            settingsStore.setUpdateAutomatically(enabled)
            syncController.apply(settingsStore.current())
            if (enabled) syncController.runOnce()
        }
    }

    /** Applies the entry of the open wallpaper sheet to the live wallpaper. */
    fun previewAndSetWallpaper() {
        val date = _state.value.wallpaperSheetDate
        val entry = date?.let(::entryFor)
        viewModelScope.launch {
            if (entry == null) {
                _events.trySend(FeedEvent.ShowMessage(MESSAGE_LOAD_FAILED))
                return@launch
            }
            val settings = settingsStore.current()
            when (syncEngine.install(entry, settings.preferHd, settings.hideVideos)) {
                InstallOutcome.INSTALLED -> {
                    dismissWallpaperSheet()
                    updateWallpaperState()
                    if (isWallpaperActive()) {
                        _events.trySend(FeedEvent.ShowMessage(MESSAGE_WALLPAPER_UPDATED))
                    } else {
                        _events.trySend(FeedEvent.OpenWallpaperPicker)
                    }
                }

                InstallOutcome.UNCHANGED -> {
                    dismissWallpaperSheet()
                    updateWallpaperState()
                    if (isWallpaperActive()) {
                        _events.trySend(FeedEvent.ShowMessage(MESSAGE_WALLPAPER_ALREADY_SET))
                    } else {
                        _events.trySend(FeedEvent.OpenWallpaperPicker)
                    }
                }

                InstallOutcome.NOT_USABLE -> {
                    _events.trySend(FeedEvent.ShowMessage(MESSAGE_NO_WALLPAPER))
                }

                InstallOutcome.FAILED -> {
                    _events.trySend(FeedEvent.ShowMessage(MESSAGE_WALLPAPER_FAILED))
                }
            }
        }
    }

    /** Retries the full-page error state. */
    fun retry() {
        _state.update { it.copy(hasFatalError = false, isLoadingLatest = true) }
        loadLatest(force = true)
        loadIndex(userRequested = true)
    }

    /** Retries one page whose details failed to load. */
    fun retryHydration(date: String) {
        hydrationAttempts[date] = 0
        setHydration(date, Hydration.IDLE)
        hydrate(date, force = true)
    }

    /** Called on every resume: re-reads the wallpaper state and refreshes stale metadata. */
    fun onResume() {
        viewModelScope.launch {
            updateWallpaperState()
            _state.update { it.copy(isOffline = !isConnected()) }
            val settings = settingsStore.current()
            _state.update {
                it.copy(
                    scaling = settings.wallpaperScaling,
                    updateDaily = settings.updateAutomatically,
                    syncedAt = settings.lastSyncAt ?: it.syncedAt,
                )
            }
            if (latestJob?.isActive != true && refreshJob?.isActive != true &&
                FeedLoadPolicy.shouldRefreshLatest(settings.lastSyncAt, now())
            ) {
                loadLatest(force = false)
            } else {
                // The feed may already be fresh while the wallpaper worker was deferred by Doze.
                syncWallpaperFromLatest()
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------

    private fun loadLatest(force: Boolean = false) {
        if (latestJob?.isActive == true) return
        latestJob = viewModelScope.launch {
            if (loadLatestInternal(force)) syncWallpaperFromLatest()
        }
    }

    private suspend fun loadLatestInternal(force: Boolean): Boolean = latestMutex.withLock {
        val cached = apodRepository.cachedLatest()
        if (cached != null) adoptLatest(cached)
        val settings = settingsStore.current()
        val needsRefresh = FeedLoadPolicy.shouldRefreshLatest(settings.lastSyncAt, now(), force)
        if (!needsRefresh && cached != null) {
            _state.update { it.copy(isLoadingLatest = false) }
            return@withLock true
        }
        if (cached == null) _state.update { it.copy(isLoadingLatest = true) }
        try {
            // Explicit refresh bypasses OkHttp's GitHub Pages cache. Otherwise a pull-to-refresh
            // can return a cached latest.json for several minutes after the backend changed.
            val fresh = apodRepository.fetchLatest(forceNetwork = force)
            adoptLatest(fresh)
            val updated = settingsStore.current()
            _state.update {
                it.copy(
                    isLoadingLatest = false,
                    isOffline = false,
                    hasFatalError = false,
                    syncedAt = updated.lastSyncAt ?: it.syncedAt,
                    scaling = updated.wallpaperScaling,
                    updateDaily = updated.updateAutomatically,
                    wallpaperMeta = wallpaperStorage.readMeta(),
                    wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
                )
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isLoadingLatest = false,
                    isOffline = true,
                    hasFatalError = it.pages.isEmpty() && indexEntries.isEmpty(),
                )
            }
            if (force) _events.trySend(FeedEvent.ShowMessage(MESSAGE_REFRESH_FAILED))
            false
        }
    }

    /**
     * Keeps the live wallpaper on the same record as the feed.
     *
     * This is deliberately silent: it runs after a pull-to-refresh, a startup refresh, or a
     * resume when the periodic worker may have been deferred. If the wallpaper is not selected,
     * automatic updates are off, or the connection is metered under "Wi-Fi only", it does
     * nothing. The sync engine re-checks the current metadata before downloading anything.
     */
    private fun syncWallpaperFromLatest() {
        val entry = latestRecord ?: return
        if (!isWallpaperActive()) return
        if (wallpaperSyncJob?.isActive == true) return
        wallpaperSyncJob = viewModelScope.launch {
            val settings = runCatching { settingsStore.current() }.getOrNull() ?: return@launch
            if (!settings.updateAutomatically) return@launch
            if (settings.carouselMode) return@launch
            if (!isConnected()) return@launch
            if (!SyncPolicy.pictureDownloadAllowed(settings, connected = true, unmetered = isUnmetered())) {
                return@launch
            }
            runCatching {
                syncEngine.syncEntry(entry, updateWallpaper = true, networkAvailable = true)
            }
            updateWallpaperState()
        }
    }

    private fun loadIndex(userRequested: Boolean) {
        if (userRequested) indexRequested = true
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch {
            val cached = archiveIndexRepository.loadCached()
            if (cached != null) applyIndex(cached)

            val snapshot = archiveIndexRepository.snapshot.value
            val shouldRefresh = FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = cached != null,
                cachedSavedAt = snapshot?.savedAt,
                isMetered = !isUnmetered(),
                userRequested = indexRequested,
                now = now(),
                indexHeadDate = cached?.firstOrNull()?.date,
                latestDate = latestDate,
            )
            if (!shouldRefresh) {
                indexUnavailable = cached == null
                _state.update { it.copy(isLoadingIndex = false) }
                updateSearchResults()
                return@launch
            }

            _state.update { it.copy(isLoadingIndex = true) }
            val result = archiveIndexRepository.refresh()
            if (result.isSuccess) {
                applyIndex(result.getOrThrow())
                indexUnavailable = false
                _state.update { it.copy(isOffline = false, isLoadingIndex = false) }
            } else {
                indexUnavailable = cached == null
                // The latest record and its picture can be perfectly healthy even when the
                // 5 MiB index refresh failed; do not turn the whole feed into an offline page.
                _state.update { it.copy(isLoadingIndex = false) }
                if (indexRequested && cached == null) {
                    _events.trySend(FeedEvent.ShowMessage(MESSAGE_INDEX_FAILED))
                }
            }
            updateSearchResults()
        }
    }

    private fun applyIndex(entries: List<ArchiveIndexEntry>) {
        if (entries.isEmpty()) return
        indexEntries = entries
        isIndexComplete = true
        indexUnavailable = false
        if (latestDate == null) latestDate = latestRecord?.date ?: entries.firstOrNull()?.date
        if (currentDate == null) currentDate = latestDate
        rebuildPages(bumpListVersion = true)
        _state.update {
            it.copy(
                isIndexComplete = true,
                isLoadingIndex = false,
                hasFatalError = false,
                isLoadingLatest = if (latestRecord == null) it.isLoadingLatest else false,
            )
        }
        // The page list just grew from "one page" to "11k pages"; hydrate the window around the
        // current page so the next few swipes already show their teaser and full image.
        prefetchAround(FeedPaging.pageIndexForDate(_state.value.pages, currentPageKey))
    }

    private fun refreshIndexAfterLatestChange() {
        if (latestDate == null) return
        val head = indexEntries.firstOrNull()?.date
        if (indexEntries.isEmpty()) {
            loadIndex(userRequested = false)
            return
        }
        val snapshot = archiveIndexRepository.snapshot.value
        if (head != null && head < latestDate!! &&
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = true,
                cachedSavedAt = snapshot?.savedAt,
                isMetered = !isUnmetered(),
                userRequested = true,
                now = now(),
                indexHeadDate = head,
                latestDate = latestDate,
            )
        ) {
            loadIndex(userRequested = true)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Page list
    // ---------------------------------------------------------------------------------------

    private fun allDateItems(): List<FeedDateItem> =
        FeedPaging.buildDateItems(indexEntries, latestRecord, yearRecords)

    private fun rebuildPages(bumpListVersion: Boolean) {
        val items = allDateItems()
        val filtered = FeedPaging.filtered(items, filters)
        val pages = when {
            items.isEmpty() -> emptyList()
            filtered.isEmpty() -> listOf(FeedPaging.endOnlyPage())
            else -> FeedPaging.buildPages(filtered, hydrated, hydrationStatus, latestDate)
        }
        if (bumpListVersion) listVersion++
        _state.update { current ->
            current.copy(
                pages = pages,
                allItems = items,
                filters = filters,
                availableYears = FeedPaging.availableYears(items),
                archiveFirstDate = items.lastOrNull()?.date,
                archiveLastDate = items.firstOrNull()?.date,
                listVersion = listVersion,
                currentDate = currentDate,
                currentPageKey = currentPageKey,
                isIndexComplete = isIndexComplete,
                hasFatalError = if (pages.isNotEmpty()) false else current.hasFatalError,
            )
        }
        updateSearchResults()
    }

    private fun adoptLatest(entry: ApodEntry) {
        latestRecord = entry
        if (latestDate == null || entry.date >= latestDate!!) latestDate = entry.date
        if (currentDate == null) currentDate = entry.date
        storeHydrated(entry)
        markHydrated(entry.date)
        rebuildPages(bumpListVersion = true)
    }

    // ---------------------------------------------------------------------------------------
    // Hydration
    // ---------------------------------------------------------------------------------------

    private fun hydrate(date: String, force: Boolean = false) {
        if (hydrated.containsKey(date)) {
            markHydrated(date)
            return
        }
        if (hydrationStatus[date] == Hydration.LOADING) return
        if (!force) {
            val attempts = hydrationAttempts[date] ?: 0
            if (attempts >= MAX_HYDRATION_ATTEMPTS) return
            hydrationAttempts[date] = attempts + 1
        } else {
            hydrationAttempts[date] = 0
        }
        setHydration(date, Hydration.LOADING)
        viewModelScope.launch {
            try {
                val cached = apodRepository.findCachedEntry(date)
                if (cached != null) {
                    finishHydration(cached)
                    return@launch
                }
                val year = FeedPaging.yearOf(date)
                if (year == null) {
                    setHydration(date, Hydration.FAILED)
                    return@launch
                }
                val entry = hydrateFromYear(date, year)
                if (entry != null) finishHydration(entry) else setHydration(date, Hydration.FAILED)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Keep whatever the year gave us before the failure, then fail the page.
                yearRecords[FeedPaging.yearOf(date)]?.firstOrNull { it.date == date }?.let {
                    finishHydration(it)
                    return@launch
                }
                setHydration(date, Hydration.FAILED)
            }
        }
    }

    /** Returns the full record of [date], fetching its year at most once (serialised). */
    private suspend fun hydrateFromYear(date: String, year: Int): ApodEntry? {
        yearRecords[year]?.firstOrNull { it.date == date }?.let { return it }
        return yearMutex.withLock {
            yearRecords[year]?.firstOrNull { it.date == date }?.let { return@withLock it }
            val cachedYear = runCatching { apodRepository.cachedArchiveYear(year) }.getOrDefault(emptyList())
            val fromCache = cachedYear.firstOrNull { it.date == date }
            if (fromCache != null) {
                putYear(year, cachedYear)
                return@withLock fromCache
            }
            val fetched = apodRepository.fetchArchiveYear(year)
            putYear(year, fetched)
            fetched.firstOrNull { it.date == date }
                ?: runCatching { apodRepository.findCachedEntry(date) }.getOrNull()
        }
    }

    /** Ensures the full year is in memory/Room without necessarily hydrating a page. */
    private suspend fun ensureYearCached(year: Int) {
        if (yearRecords.containsKey(year)) return
        val cached = runCatching { apodRepository.cachedArchiveYear(year) }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) {
            putYear(year, cached)
            return
        }
        yearMutex.withLock {
            if (yearRecords.containsKey(year)) return@withLock
            try {
                putYear(year, apodRepository.fetchArchiveYear(year))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // A page retry offers the explicit second attempt.
            }
        }
    }

    private fun putYear(year: Int, records: List<ApodEntry>) {
        if (records.isEmpty()) return
        yearRecords = yearRecords + (year to records)
        val waiting = records.filter { hydrationStatus[it.date] == Hydration.LOADING }
        if (waiting.isNotEmpty()) {
            waiting.forEach { storeHydrated(it) }
            hydrationStatus = hydrationStatus + waiting.associate { it.date to Hydration.READY }
        }
        rebuildPages(bumpListVersion = indexEntries.isEmpty())
        waiting.forEach { date ->
            if (pendingWallpaperDate == date.date) showPendingWallpaperSheet(date.date)
        }
        if (indexEntries.isEmpty()) {
            // The visible list just got longer: hydrate the window around the current page.
            prefetchAround(FeedPaging.pageIndexForDate(_state.value.pages, currentPageKey))
        }
    }

    private fun finishHydration(entry: ApodEntry) {
        storeHydrated(entry)
        markHydrated(entry.date)
    }

    private fun storeHydrated(entry: ApodEntry) {
        hydrated.remove(entry.date)
        hydrated[entry.date] = entry
        while (hydrated.size > HYDRATED_LIMIT) {
            val eldest = hydrated.keys.firstOrNull() ?: break
            hydrated.remove(eldest)
        }
    }

    private fun markHydrated(date: String) {
        hydrationStatus = hydrationStatus + (date to Hydration.READY)
        patchPage(date)
        if (pendingWallpaperDate == date) showPendingWallpaperSheet(date)
    }

    private fun setHydration(date: String, hydration: Hydration) {
        hydrationStatus = hydrationStatus + (date to hydration)
        patchPage(date)
    }

    /** Replaces the hydration fields of one page without rebuilding the 11k-page list. */
    private fun patchPage(date: String) {
        val entry = hydrated[date]
        val hydration = hydrationStatus[date]
        _state.update { current ->
            if (current.pages.none { it.date == date }) return@update current
            current.copy(
                pages = current.pages.map { page ->
                    if (page.date == date) {
                        page.copy(entry = entry, hydration = hydration ?: page.hydration)
                    } else {
                        page
                    }
                },
            )
        }
    }

    private fun showPendingWallpaperSheet(date: String) {
        pendingWallpaperDate = null
        _state.update { it.copy(wallpaperSheetDate = date) }
    }

    private fun prefetchAround(pageIndex: Int) {
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        // Deliberately not tied to a single job: every settle schedules the work it needs, and the
        // year mutex + the hydration guards coalesce concurrent requests for the same year/page.
        viewModelScope.launch {
            val years = FeedLoadPolicy.yearsToPrefetch(pages, pageIndex, yearRecords.keys)
            for (year in years) {
                ensureYearCached(year)
            }
            // Hydrate the pages of the window from the local data that is available now.
            val window = FeedPaging.prefetchWindow(pageIndex, pages.size)
            for (index in window) {
                val date = pages.getOrNull(index)?.date ?: continue
                if (hydrated.containsKey(date)) continue
                val entry = FeedPaging.yearOf(date)?.let { year ->
                    yearRecords[year]?.firstOrNull { it.date == date }
                } ?: apodRepository.findCachedEntry(date)
                if (entry != null) finishHydration(entry) else hydrate(date)
            }
            if (!isIndexComplete) {
                FeedLoadPolicy.adjacentYearToFetch(pages, pageIndex, yearRecords.keys)
                    ?.let { ensureYearCached(it) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Search, favourites and settings
    // ---------------------------------------------------------------------------------------

    private fun updateSearchResults() {
        val search = _state.value.search
        val items = allDateItems()
        val results = FeedPaging.search(items, search.query, search.filters)
        _state.update {
            it.copy(
                search = it.search.copy(
                    results = results,
                    isUnavailable = indexUnavailable && !isIndexComplete,
                ),
            )
        }
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            favouritesRepository.observeAll().collect { saved ->
                _state.update { it.copy(favourites = saved.map { entry -> entry.date }.toSet()) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update {
                    it.copy(
                        scaling = settings.wallpaperScaling,
                        updateDaily = settings.updateAutomatically,
                        syncedAt = settings.lastSyncAt ?: it.syncedAt,
                        showSwipeHint = when {
                            settings.feedSwipeHintSeen -> false
                            swipeHintDismissed -> it.showSwipeHint
                            else -> true
                        },
                    )
                }
            }
        }
        // The advanced settings screen can switch the API endpoint while the feed is in the back
        // stack. Both caches are dropped so entries from two servers can never be mixed.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.customApiBaseUrl }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    archiveIndexRepository.clear()
                    resetForEndpointChange()
                    loadLatest(force = true)
                    loadIndex(userRequested = false)
                }
        }
    }

    private fun resetForEndpointChange() {
        // Cancel every flight that could still deliver a record from the previous endpoint.
        // Without this the early-return guards in loadLatest/loadIndex would also block the
        // replacement request.
        latestJob?.cancel()
        latestJob = null
        indexJob?.cancel()
        indexJob = null
        refreshJob?.cancel()
        refreshJob = null
        searchJob?.cancel()
        searchJob = null
        indexEntries = emptyList()
        yearRecords = emptyMap()
        hydrated.clear()
        hydrationStatus = emptyMap()
        hydrationAttempts.clear()
        latestRecord = null
        latestDate = null
        currentDate = null
        currentPageKey = null
        pendingWallpaperDate = null
        filters = FeedFilters()
        isIndexComplete = false
        indexRequested = false
        indexUnavailable = false
        listVersion++
        _state.update {
            it.copy(
                pages = emptyList(),
                allItems = emptyList(),
                filters = FeedFilters(),
                availableYears = emptyList(),
                archiveFirstDate = null,
                archiveLastDate = null,
                isLoadingLatest = true,
                isLoadingIndex = false,
                isIndexComplete = false,
                isOffline = !isConnected(),
                hasFatalError = false,
                currentDate = null,
                currentPageKey = null,
                listVersion = listVersion,
                wallpaperSheetDate = null,
                search = FeedSearchState(),
            )
        }
    }

    private fun updateWallpaperState() {
        _state.update {
            it.copy(
                wallpaperMeta = wallpaperStorage.readMeta(),
                wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
            )
        }
    }

    private fun entryFor(date: String): ApodEntry? =
        hydrated[date] ?: _state.value.pages.firstOrNull { it.date == date }?.entry

    private fun indexEntryFor(date: String): ArchiveIndexEntry? =
        indexEntries.firstOrNull { it.date == date }
            ?: _state.value.pages.firstOrNull { it.date == date }?.indexEntry

    /** A full record synthesised from the lightweight index row — used by share before hydration. */
    private fun syntheticEntryFor(date: String): ApodEntry? {
        val indexEntry = indexEntryFor(date) ?: return null
        return ApodEntry(
            date = indexEntry.date,
            title = indexEntry.title,
            explanation = "",
            mediaType = indexEntry.mediaType,
            imageUrl = indexEntry.imageUrl,
            hdImageUrl = null,
            thumbnailUrl = indexEntry.thumbnailUrl,
            apodUrl = indexEntry.apodUrl,
            copyright = indexEntry.copyright,
            credit = null,
            altText = null,
            source = null,
            sourceId = null,
            sourceMediaType = null,
            imageStatus = if (indexEntry.imageUrl != null || indexEntry.thumbnailUrl != null) {
                ImageStatus.OK
            } else {
                ImageStatus.MISSING
            },
            updatedAt = null,
            lastSuccessfulSync = null,
            latestAvailableDate = null,
            recordCount = null,
            sourceStatus = null,
        )
    }

    private fun emitJump(date: String, animate: Boolean) {
        val pages = _state.value.pages
        val targetIndex = FeedPaging.pageIndexForDate(pages, date)
        val fromIndex = pages.indexOfFirst { it.page.key == currentPageKey }
            .takeIf { it >= 0 }
            ?: targetIndex
        val animateNow = animate && abs(targetIndex - fromIndex) <= ANIMATE_MAX_PAGE_DISTANCE
        jumpToken++
        currentDate = date
        currentPageKey = date
        _state.update {
            it.copy(
                currentDate = date,
                currentPageKey = date,
                jumpRequest = FeedJumpRequest(jumpToken, date, animateNow),
            )
        }
    }

    companion object {
        const val MESSAGE_SAVED = "Saved to Pictures/Mohakash"
        const val MESSAGE_SAVE_FAILED = "Could not save the image."
        const val MESSAGE_LOAD_FAILED = "Could not load the latest picture."
        const val MESSAGE_REFRESH_FAILED = "Couldn't refresh"
        const val MESSAGE_FAVOURITE_ADDED = "Saved"
        const val MESSAGE_FAVOURITE_REMOVED = "Removed from saved"
        const val MESSAGE_FAVOURITE_FAILED = "Could not update the saved list."
        const val MESSAGE_WALLPAPER_FAILED = "Could not prepare the wallpaper."
        const val MESSAGE_WALLPAPER_UPDATED = "Live wallpaper updated."
        const val MESSAGE_WALLPAPER_ALREADY_SET = "Live wallpaper is already up to date."
        const val MESSAGE_NO_WALLPAPER =
            "This entry can't be used as wallpaper. Your current wallpaper stays as it is."
        const val MESSAGE_DETAILS_LOADING = "Still loading the details of this entry."
        const val MESSAGE_INDEX_FAILED = "Could not load the full archive."
        const val MESSAGE_JUMP_GAP = "No picture on %1\$s · showing %2\$s"

        /** Keep at most this many full records in memory while browsing. */
        const val HYDRATED_LIMIT = 40

        /** Three automatic attempts, then the page asks for an explicit retry. */
        const val MAX_HYDRATION_ATTEMPTS = 3

        /** Jumps longer than this are instant; animating thousands of pages would take ages. */
        const val ANIMATE_MAX_PAGE_DISTANCE = 20

        const val SEARCH_DEBOUNCE_MILLIS = 150L
    }
}

/** How many random candidates may be hydrated before giving up. */
private const val MAX_RANDOM_ATTEMPTS = 8

/** Shown when every random candidate turned out to have no usable picture. */
private const val MESSAGE_RANDOM_UNAVAILABLE = "No random picture is available right now."
