package app.mohakash.apod.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.repository.FavouritesRepository
import app.mohakash.apod.data.repository.ImageRepository
import app.mohakash.apod.data.storage.ShareHelper
import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.work.ApodSyncEngine
import app.mohakash.apod.work.InstallOutcome
import app.mohakash.apod.work.SyncController
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The state of the Today screen.
 */
data class TodayUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val entry: ApodEntry? = null,
    val isOffline: Boolean = false,
    val isFavourite: Boolean = false,
    val syncedAt: Long? = null,
    val wallpaperMeta: WallpaperMeta? = null,
    val wallpaperPreviewPath: String? = null,
    val scaling: WallpaperScaling = WallpaperScaling.FILL,
    val updateDaily: Boolean = true,
    val hasError: Boolean = false,
)

/** One-shot events of the Today screen. */
sealed interface TodayEvent {
    data class ShowMessage(val text: String) : TodayEvent
    data object OpenWallpaperPicker : TodayEvent
}

/**
 * Drives the Today screen.
 */
class TodayViewModel(
    private val apodRepository: ApodRepository,
    private val favouritesRepository: FavouritesRepository,
    private val imageRepository: ImageRepository,
    private val settingsStore: SettingsStore,
    private val shareHelper: ShareHelper,
    private val wallpaperStorage: WallpaperStorage,
    private val syncEngine: ApodSyncEngine,
    private val syncController: SyncController,
    /** Reports whether Mohakash is already the selected live wallpaper. */
    private val isWallpaperActive: () -> Boolean = { false },
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var favouriteJob: Job? = null
    private var loadJob: Job? = null

    init {
        load()
        // The advanced settings screen can switch the API endpoint while Today is in the back
        // stack. Reload from the new server as soon as that happens.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.customApiBaseUrl }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    loadJob?.cancel()
                    loadJob = null
                    load(force = true)
                }
        }
    }

    /**
     * Loads the latest entry.
     *
     * The cached copy is shown immediately; the network is only used when the cache is missing or
     * stale.
     */
    fun load(force: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            try {
                val settings = settingsStore.current()
                val cached = apodRepository.cachedLatest()
                if (cached != null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        entry = cached,
                        syncedAt = settings.lastSyncAt,
                        scaling = settings.wallpaperScaling,
                        updateDaily = settings.updateAutomatically,
                        wallpaperMeta = wallpaperStorage.readMeta(),
                        wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
                    )
                    observeFavourite(cached)
                }

                val stale = force || TimeFormatting.isStale(settings.lastSyncAt, System.currentTimeMillis())
                if (!stale && cached != null) return@launch

                try {
                    val fresh = apodRepository.fetchLatest()
                    val updated = settingsStore.current()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        entry = fresh,
                        isOffline = false,
                        hasError = false,
                        syncedAt = updated.lastSyncAt,
                        scaling = updated.wallpaperScaling,
                        updateDaily = updated.updateAutomatically,
                        wallpaperMeta = wallpaperStorage.readMeta(),
                        wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
                    )
                    observeFavourite(fresh)
                } catch (error: Exception) {
                    if (cached != null) {
                        _state.value = _state.value.copy(isLoading = false, isOffline = true)
                    } else {
                        _state.value = _state.value.copy(isLoading = false, hasError = true)
                        _events.send(TodayEvent.ShowMessage(MESSAGE_LOAD_FAILED))
                    }
                }
            } finally {
                if (_state.value.isRefreshing) {
                    _state.value = _state.value.copy(isRefreshing = false)
                }
            }
        }
    }

    /** Pull-to-refresh: forces a network refresh and keeps the cached content on screen. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        _state.value = _state.value.copy(isRefreshing = true)
        load(force = true)
    }

    /** Called when the screen opens again: refreshes only when the cached copy is stale. */
    fun refreshOnOpen() {
        if (loadJob?.isActive == true) return
        load()
    }

    /** Retries the load after a network failure. */
    fun retry() {
        if (loadJob?.isActive == true) return
        _state.value = _state.value.copy(isLoading = true, hasError = false)
        load(force = true)
    }

    /** Adds the entry to the saved list or removes it again. */
    fun toggleFavourite() {
        val entry = _state.value.entry ?: return
        viewModelScope.launch {
            val previous = _state.value.isFavourite
            _state.value = _state.value.copy(isFavourite = !previous)
            runCatching { favouritesRepository.toggle(entry) }
                .onSuccess { saved ->
                    _state.value = _state.value.copy(isFavourite = saved)
                    _events.send(
                        TodayEvent.ShowMessage(if (saved) MESSAGE_FAVOURITE_ADDED else MESSAGE_FAVOURITE_REMOVED),
                    )
                }
                .onFailure {
                    // Quiet fallback: the heart goes back to its previous state.
                    _state.value = _state.value.copy(isFavourite = previous)
                    _events.send(TodayEvent.ShowMessage(MESSAGE_FAVOURITE_FAILED))
                }
        }
    }

    /** Saves the picture into the gallery. */
    fun saveImage() {
        val entry = _state.value.entry ?: return
        viewModelScope.launch {
            imageRepository.saveToGallery(entry)
                .onSuccess { _events.send(TodayEvent.ShowMessage(MESSAGE_SAVED)) }
                .onFailure { _events.send(TodayEvent.ShowMessage(MESSAGE_SAVE_FAILED)) }
        }
    }

    /** Opens the share sheet with the public archive URL of the entry. */
    fun share() {
        val entry = _state.value.entry ?: return
        shareHelper.share(entry)
    }

    /** Changes the scaling mode used by the live wallpaper. */
    fun setScaling(scaling: WallpaperScaling) {
        viewModelScope.launch {
            settingsStore.setWallpaperScaling(scaling)
            _state.value = _state.value.copy(scaling = scaling)
        }
    }

    /** Switches the automatic wallpaper update on or off. */
    fun setUpdateDaily(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setUpdateAutomatically(enabled)
            _state.value = _state.value.copy(updateDaily = enabled)
            // Keep WorkManager in sync with the switch shown in this sheet as well.
            syncController.apply(settingsStore.current())
            if (enabled) syncController.runOnce()
        }
    }

    /**
     * Applies the picture of the current entry to the live wallpaper.
     *
     * The first time the wallpaper is not active yet, Android must ask the user to confirm the
     * live wallpaper; the picture is installed before the system picker opens so the preview is
     * already correct. Once Mohakash is the active wallpaper, the picture is swapped in place and
     * the engine is signalled to redraw — no system dialog is opened.
     */
    fun previewAndSetWallpaper() {
        val entry = _state.value.entry
        viewModelScope.launch {
            if (entry == null) {
                _events.send(TodayEvent.ShowMessage(MESSAGE_LOAD_FAILED))
                return@launch
            }

            val settings = settingsStore.current()
            when (syncEngine.install(entry, settings.preferHd, settings.hideVideos)) {
                InstallOutcome.INSTALLED -> {
                    updateWallpaperState()
                    if (isWallpaperActive()) {
                        _events.send(TodayEvent.ShowMessage(MESSAGE_WALLPAPER_UPDATED))
                    } else {
                        _events.send(TodayEvent.OpenWallpaperPicker)
                    }
                }

                InstallOutcome.UNCHANGED -> {
                    updateWallpaperState()
                    if (isWallpaperActive()) {
                        _events.send(TodayEvent.ShowMessage(MESSAGE_WALLPAPER_ALREADY_SET))
                    } else {
                        _events.send(TodayEvent.OpenWallpaperPicker)
                    }
                }

                InstallOutcome.NOT_USABLE -> _events.send(TodayEvent.ShowMessage(MESSAGE_NO_WALLPAPER))
                InstallOutcome.FAILED -> _events.send(TodayEvent.ShowMessage(MESSAGE_WALLPAPER_FAILED))
            }
        }
    }

    private fun updateWallpaperState() {
        _state.value = _state.value.copy(
            wallpaperMeta = wallpaperStorage.readMeta(),
            wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
        )
    }

    private fun observeFavourite(entry: ApodEntry) {
        favouriteJob?.cancel()
        favouriteJob = viewModelScope.launch {
            favouritesRepository.observeByDate(entry.date).collect { favourite ->
                _state.value = _state.value.copy(isFavourite = favourite != null)
            }
        }
    }

    companion object {
        const val MESSAGE_SAVED = "Saved to Pictures/Mohakash"
        const val MESSAGE_SAVE_FAILED = "Could not save the image."
        const val MESSAGE_LOAD_FAILED = "Could not load the latest picture."
        const val MESSAGE_FAVOURITE_ADDED = "Saved"
        const val MESSAGE_FAVOURITE_REMOVED = "Removed from saved"
        const val MESSAGE_FAVOURITE_FAILED = "Could not update the saved list."
        const val MESSAGE_WALLPAPER_FAILED = "Could not prepare the wallpaper."
        const val MESSAGE_WALLPAPER_UPDATED = "Live wallpaper updated."
        const val MESSAGE_WALLPAPER_ALREADY_SET = "Live wallpaper is already up to date."
        const val MESSAGE_NO_WALLPAPER =
            "This entry can't be used as wallpaper. Your current wallpaper stays as it is."
    }
}
