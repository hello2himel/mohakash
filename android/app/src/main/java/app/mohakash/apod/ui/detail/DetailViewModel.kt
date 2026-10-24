package app.mohakash.apod.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * The state of the entry detail screen.
 */
data class DetailUiState(
    val isLoading: Boolean = true,
    val entry: ApodEntry? = null,
    val isFavourite: Boolean = false,
    val hasError: Boolean = false,
    val scaling: WallpaperScaling = WallpaperScaling.FILL,
    val updateDaily: Boolean = true,
    val wallpaperPreviewPath: String? = null,
    val wallpaperMeta: WallpaperMeta? = null,
)

/** One-shot events of the entry detail screen. */
sealed interface DetailEvent {
    data class ShowMessage(val text: String) : DetailEvent
    data object OpenWallpaperPicker : DetailEvent
}

/**
 * Drives the entry detail screen.
 */
class DetailViewModel(
    private val apodRepository: ApodRepository,
    private val favouritesRepository: FavouritesRepository,
    private val imageRepository: ImageRepository,
    private val settingsStore: SettingsStore,
    private val shareHelper: ShareHelper,
    private val wallpaperStorage: WallpaperStorage,
    private val syncEngine: ApodSyncEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var favouriteJob: Job? = null
    private var loadJob: Job? = null
    private var loadedDate: String? = null
    private var requestedDate: String? = null

    init {
        // An endpoint change invalidates the loaded record; reload it when the user returns.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.customApiBaseUrl }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val date = requestedDate ?: return@collect
                    loadedDate = null
                    _state.value = _state.value.copy(isLoading = true, hasError = false)
                    load(date)
                }
        }
    }

    /** Loads the entry with the given date. */
    fun load(date: String) {
        if (date.isBlank()) return
        requestedDate = date
        if (loadedDate == date) return
        loadedDate = date
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val settings = settingsStore.current()
            _state.value = _state.value.copy(
                isLoading = true,
                scaling = settings.wallpaperScaling,
                updateDaily = settings.updateAutomatically,
                wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
                wallpaperMeta = wallpaperStorage.readMeta(),
            )

            // A saved entry is always available — even offline.
            val favourite = favouritesRepository.getByDate(date)
            val cached = apodRepository.findCachedEntry(date)
            val local = favourite ?: cached
            if (local != null) {
                _state.value = _state.value.copy(isLoading = false, entry = local, hasError = false)
                observeFavourite(local)
            }

            val remote = runCatching { apodRepository.fetchEntry(date) }.getOrNull()
            if (remote != null) {
                _state.value = _state.value.copy(isLoading = false, entry = remote, hasError = false)
                observeFavourite(remote)
            } else if (local == null) {
                _state.value = _state.value.copy(isLoading = false, hasError = true)
                _events.send(DetailEvent.ShowMessage(MESSAGE_LOAD_FAILED))
            }
        }
    }

    /** Retries the load of the current entry after a failure. */
    fun retry() {
        val date = requestedDate ?: return
        loadedDate = null
        _state.value = _state.value.copy(isLoading = true, hasError = false)
        load(date)
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
                        DetailEvent.ShowMessage(if (saved) MESSAGE_FAVOURITE_ADDED else MESSAGE_FAVOURITE_REMOVED),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isFavourite = previous)
                    _events.send(DetailEvent.ShowMessage(MESSAGE_FAVOURITE_FAILED))
                }
        }
    }

    /** Saves the picture into the gallery. */
    fun saveImage() {
        val entry = _state.value.entry ?: return
        viewModelScope.launch {
            imageRepository.saveToGallery(entry)
                .onSuccess { _events.send(DetailEvent.ShowMessage(MESSAGE_SAVED)) }
                .onFailure { _events.send(DetailEvent.ShowMessage(MESSAGE_SAVE_FAILED)) }
        }
    }

    /** Opens the share sheet with the picture and the credit. */
    fun share() {
        val entry = _state.value.entry ?: return
        viewModelScope.launch {
            val file = imageRepository.prepareShareImage(entry).getOrNull()
            shareHelper.share(entry, file)
        }
    }

    /**
     * Installs the picture of this entry as the live wallpaper and opens the system preview.
     */
    fun setAsWallpaper() {
        val entry = _state.value.entry ?: return
        if (!entry.supportsWallpaper) {
            viewModelScope.launch { _events.send(DetailEvent.ShowMessage(MESSAGE_VIDEO_NO_WALLPAPER)) }
            return
        }
        viewModelScope.launch {
            val settings = settingsStore.current()
            val outcome = syncEngine.install(entry, settings.preferHd, settings.hideVideos)
            when (outcome) {
                app.mohakash.apod.work.InstallOutcome.INSTALLED -> {
                    _state.value = _state.value.copy(
                        wallpaperPreviewPath = wallpaperStorage.currentImageFile()?.absolutePath,
                        wallpaperMeta = wallpaperStorage.readMeta(),
                    )
                    _events.send(DetailEvent.OpenWallpaperPicker)
                }
                app.mohakash.apod.work.InstallOutcome.UNCHANGED -> _events.send(DetailEvent.OpenWallpaperPicker)
                app.mohakash.apod.work.InstallOutcome.NOT_USABLE ->
                    _events.send(DetailEvent.ShowMessage(MESSAGE_VIDEO_NO_WALLPAPER))
                app.mohakash.apod.work.InstallOutcome.FAILED ->
                    _events.send(DetailEvent.ShowMessage(MESSAGE_WALLPAPER_FAILED))
            }
        }
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
        }
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
        const val MESSAGE_LOAD_FAILED = "Could not load this entry."
        const val MESSAGE_FAVOURITE_ADDED = "Saved"
        const val MESSAGE_FAVOURITE_REMOVED = "Removed from saved"
        const val MESSAGE_FAVOURITE_FAILED = "Could not update the saved list."
        const val MESSAGE_VIDEO_NO_WALLPAPER =
            "Video entries can't be used as wallpaper. Your current wallpaper stays as it is."
        const val MESSAGE_WALLPAPER_FAILED = "Could not prepare the wallpaper."
    }
}
