package app.mohakash.apod.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.work.SyncController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot events of the onboarding screen. */
sealed interface OnboardingEvent {
    data object Finished : OnboardingEvent
    data object OpenWallpaperPicker : OnboardingEvent
}

/**
 * Drives the onboarding screen.
 */
class OnboardingViewModel(
    private val settingsStore: SettingsStore,
    private val syncController: SyncController,
    private val wallpaperStorage: WallpaperStorage,
) : ViewModel() {

    private val _previewPath = MutableStateFlow<String?>(null)
    val previewPath: StateFlow<String?> = _previewPath.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _previewPath.value = wallpaperStorage.currentImageFile()?.absolutePath
        }
    }

    /** Finishes the onboarding and triggers the first synchronisation. */
    fun complete(openWallpaperPicker: Boolean) {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
            // The very first sync fills the cache and the wallpaper.
            syncController.runOnce()
            if (openWallpaperPicker) {
                _events.send(OnboardingEvent.OpenWallpaperPicker)
            }
            _events.send(OnboardingEvent.Finished)
        }
    }
}
