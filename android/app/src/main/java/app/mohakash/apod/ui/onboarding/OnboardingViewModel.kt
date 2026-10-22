package app.mohakash.apod.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.domain.wallpaper.WallpaperChoice
import app.mohakash.apod.domain.wallpaper.WallpaperImageSelector
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import app.mohakash.apod.work.ApodSyncEngine
import app.mohakash.apod.work.InstallOutcome
import app.mohakash.apod.work.SyncController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The state of the welcome screen. */
data class OnboardingUiState(
    val preview: WallpaperPreview = WallpaperPreview(),
    val isPreparing: Boolean = false,
)

/** One-shot events of the onboarding screen. */
sealed interface OnboardingEvent {
    data object Finished : OnboardingEvent
    data object OpenWallpaperPicker : OnboardingEvent
    data class ShowMessage(val text: String) : OnboardingEvent
}

/**
 * Drives the onboarding screen.
 *
 * The phone preview shows the latest APOD immediately — from the local wallpaper when it is
 * already installed, otherwise from the network. "Set live wallpaper" waits for that picture to
 * be stored before the system picker opens, so the picker can never show the previous wallpaper.
 */
class OnboardingViewModel(
    private val settingsStore: SettingsStore,
    private val syncController: SyncController,
    private val wallpaperStorage: WallpaperStorage,
    private val apodRepository: ApodRepository,
    private val syncEngine: ApodSyncEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The entry whose picture is currently shown in the phone preview. */
    private var previewEntry: ApodEntry? = null

    init {
        loadPreview()
    }

    private fun loadPreview() {
        viewModelScope.launch {
            val settings = settingsStore.current()
            val meta = wallpaperStorage.readMeta()
            val localPath = wallpaperStorage.currentImageFile()?.absolutePath

            // A cached entry is enough to show the preview while the network request runs.
            val cached = runCatching { apodRepository.cachedLatest() }.getOrNull()
            if (cached != null) {
                previewEntry = cached
                showPreview(cached, settings, meta?.date, meta?.sourceUrl, localPath)
            }

            val latest = runCatching { apodRepository.fetchLatest() }.getOrNull() ?: return@launch
            previewEntry = latest
            showPreview(latest, settings, meta?.date, meta?.sourceUrl, localPath)
        }
    }

    private fun showPreview(
        entry: ApodEntry,
        settings: AppSettings,
        installedDate: String?,
        installedUrl: String?,
        localPath: String?,
    ) {
        val selectedUrl = selectedImageUrl(entry, settings)

        // Show the installed file when it is the picture of this entry, otherwise show the
        // picture that "Preview and set" is about to install.
        val preview = if (
            localPath != null &&
            installedDate == entry.date &&
            installedUrl != null &&
            installedUrl == selectedUrl
        ) {
            WallpaperPreview(localPath = localPath)
        } else {
            WallpaperPreview(remoteUrl = selectedUrl)
        }
        _state.value = _state.value.copy(preview = preview)
    }

    /**
     * Finishes the onboarding.
     *
     * When the user wants the wallpaper, the latest picture is installed and verified before the
     * system picker is opened. Skipping only schedules the regular background synchronisation.
     */
    fun complete(openWallpaperPicker: Boolean) {
        if (_state.value.isPreparing) return
        viewModelScope.launch {
            if (!openWallpaperPicker) {
                settingsStore.setOnboardingCompleted(true)
                syncController.runOnce()
                _events.send(OnboardingEvent.Finished)
                return@launch
            }

            _state.value = _state.value.copy(isPreparing = true)
            val settings = settingsStore.current()
            val entry = previewEntry
                ?: runCatching { apodRepository.fetchLatest() }.getOrNull()
                ?: runCatching { apodRepository.cachedLatest() }.getOrNull()

            if (entry == null) {
                _state.value = _state.value.copy(isPreparing = false)
                _events.send(OnboardingEvent.ShowMessage(MESSAGE_LOAD_FAILED))
                return@launch
            }

            previewEntry = entry
            when (syncEngine.install(entry, settings.preferHd, settings.hideVideos)) {
                InstallOutcome.INSTALLED, InstallOutcome.UNCHANGED -> {
                    val path = wallpaperStorage.currentImageFile()?.absolutePath
                    _state.value = OnboardingUiState(
                        preview = if (path != null) {
                            WallpaperPreview(localPath = path)
                        } else {
                            _state.value.preview
                        },
                    )
                    settingsStore.setOnboardingCompleted(true)
                    _events.send(OnboardingEvent.OpenWallpaperPicker)
                    _events.send(OnboardingEvent.Finished)
                }

                InstallOutcome.NOT_USABLE -> {
                    _state.value = _state.value.copy(isPreparing = false)
                    _events.send(OnboardingEvent.ShowMessage(MESSAGE_NO_WALLPAPER))
                }

                InstallOutcome.FAILED -> {
                    _state.value = _state.value.copy(isPreparing = false)
                    _events.send(OnboardingEvent.ShowMessage(MESSAGE_WALLPAPER_FAILED))
                }
            }
        }
    }

    private fun selectedImageUrl(entry: ApodEntry, settings: AppSettings): String? {
        val choice = WallpaperImageSelector.select(entry, settings.preferHd, settings.hideVideos)
        return when (choice) {
            is WallpaperChoice.Use -> choice.url
            // Even when the entry itself is not usable as wallpaper, its poster is the best
            // available preview on the welcome screen.
            WallpaperChoice.Keep -> entry.thumbnailUrl ?: entry.displayImageUrl
        }
    }

    companion object {
        const val MESSAGE_LOAD_FAILED = "Could not load today's picture. Check your connection."
        const val MESSAGE_NO_WALLPAPER =
            "This entry can't be used as wallpaper. Your current wallpaper stays as it is."
        const val MESSAGE_WALLPAPER_FAILED = "Could not prepare the wallpaper. Please try again."
    }
}
