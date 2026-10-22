package app.mohakash.apod.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.storage.CacheManager
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.work.SyncController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The state of the settings screen.
 */
data class SettingsUiState(
    val updateAutomatically: Boolean = true,
    val preferHd: Boolean = false,
    val wifiOnly: Boolean = true,
    val scaling: WallpaperScaling = WallpaperScaling.FILL,
    val hideVideos: Boolean = true,
    val cacheSizeBytes: Long = 0L,
    val lastSyncAt: Long? = null,
)

/** One-shot events of the settings screen. */
sealed interface SettingsEvent {
    data class ShowMessage(val text: String) : SettingsEvent
    data object CacheCleared : SettingsEvent
}

/**
 * Drives the settings screen.
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val cacheManager: CacheManager,
    private val syncController: SyncController,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    /** Reloads the settings and the cache size. */
    fun refresh() {
        viewModelScope.launch {
            val settings = settingsStore.current()
            _state.value = _state.value.copy(
                updateAutomatically = settings.updateAutomatically,
                preferHd = settings.preferHd,
                wifiOnly = settings.wifiOnly,
                scaling = settings.wallpaperScaling,
                hideVideos = settings.hideVideos,
                lastSyncAt = settings.lastSyncAt,
                cacheSizeBytes = cacheManager.cacheSizeBytes(),
            )
        }
    }

    /** Switches the automatic wallpaper update on or off. */
    fun setUpdateAutomatically(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setUpdateAutomatically(enabled)
            _state.value = _state.value.copy(updateAutomatically = enabled)
            // The daily job follows the setting.
            syncController.apply(settingsStore.current())
            if (enabled) {
                // Fetch the current picture right away so the wallpaper is up to date.
                syncController.runOnce()
            }
        }
    }

    /** Switches the HD preference on or off. */
    fun setPreferHd(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setPreferHd(enabled)
            _state.value = _state.value.copy(preferHd = enabled)
            // The HD variant is a different picture — refresh the wallpaper now.
            syncController.runOnce()
        }
    }

    /** Switches the Wi-Fi only download on or off. */
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setWifiOnly(enabled)
            _state.value = _state.value.copy(wifiOnly = enabled)
        }
    }

    /** Hides or shows video entries in the archive and the wallpaper. */
    fun setHideVideos(hidden: Boolean) {
        viewModelScope.launch {
            settingsStore.setHideVideos(hidden)
            _state.value = _state.value.copy(hideVideos = hidden)
            // A video entry may currently be the wallpaper; re-run the sync so the selector
            // can pick a picture instead.
            syncController.runOnce()
        }
    }

    /** Changes the scaling mode of the live wallpaper. */
    fun setScaling(scaling: WallpaperScaling) {
        viewModelScope.launch {
            settingsStore.setWallpaperScaling(scaling)
            _state.value = _state.value.copy(scaling = scaling)
        }
    }

    /** Removes the cached images. Saved pictures and the wallpaper are kept. */
    fun clearCache() {
        viewModelScope.launch {
            cacheManager.clear()
            _state.value = _state.value.copy(cacheSizeBytes = cacheManager.cacheSizeBytes())
            _events.send(SettingsEvent.CacheCleared)
        }
    }
}
