package app.mohakash.apod.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.core.ApiEndpoint
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.repository.ArchiveIndexRepository
import app.mohakash.apod.work.SyncController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The state of the advanced settings screen. */
data class AdvancedUiState(
    val isLoading: Boolean = true,
    val input: String = "",
    val savedCustomUrl: String? = null,
    val defaultUrl: String = "",
    val error: AdvancedError? = null,
    val isSaving: Boolean = false,
) {
    /** True when a custom endpoint is currently active. */
    val isCustom: Boolean get() = savedCustomUrl != null
}

/** A validation or save failure shown under the endpoint field. */
enum class AdvancedError {
    INVALID_URL,
    SAVE_FAILED,
}

/** One-shot events of the advanced settings screen. */
sealed interface AdvancedEvent {
    data object Saved : AdvancedEvent
    data object Reset : AdvancedEvent
    data object AlreadyDefault : AdvancedEvent
    data object AlreadyUsing : AdvancedEvent
}

/**
 * Drives the advanced settings screen.
 *
 * Changing the endpoint is deliberately more than a preference write: the cached metadata of
 * the previous server is cleared and a sync is scheduled so Today, Archive and the wallpaper
 * never mix entries from two different APIs.
 */
class AdvancedViewModel(
    private val settingsStore: SettingsStore,
    private val apodRepository: ApodRepository,
    private val archiveIndexRepository: ArchiveIndexRepository,
    private val syncController: SyncController,
    private val defaultUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AdvancedUiState(defaultUrl = defaultUrl))
    val state: StateFlow<AdvancedUiState> = _state.asStateFlow()

    private val _events = Channel<AdvancedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    /** Reads the saved endpoint into the field. */
    fun load() {
        viewModelScope.launch {
            val settings = settingsStore.current()
            _state.value = _state.value.copy(
                isLoading = false,
                input = settings.customApiBaseUrl.orEmpty(),
                savedCustomUrl = settings.customApiBaseUrl,
                defaultUrl = defaultUrl,
            )
        }
    }

    /** Updates the field and clears a previous validation error. */
    fun setInput(value: String) {
        _state.value = _state.value.copy(input = value, error = null)
    }

    /** Validates and saves the entered endpoint. A blank field means "use the default". */
    fun save() {
        val current = _state.value
        if (current.isSaving) return

        val raw = current.input.trim()
        if (raw.isEmpty()) {
            if (current.savedCustomUrl == null) {
                _events.trySend(AdvancedEvent.AlreadyDefault)
            } else {
                apply(null)
            }
            return
        }

        val normalized = ApiEndpoint.normalize(raw)
        if (normalized == null) {
            _state.value = current.copy(error = AdvancedError.INVALID_URL)
            return
        }
        if (normalized == current.savedCustomUrl) {
            _events.trySend(AdvancedEvent.AlreadyUsing)
            return
        }
        apply(normalized)
    }

    /** Removes the custom endpoint and goes back to the built-in one. */
    fun reset() {
        val current = _state.value
        if (current.isSaving) return
        if (current.savedCustomUrl == null) {
            _state.value = current.copy(input = "", error = null)
            _events.trySend(AdvancedEvent.AlreadyDefault)
            return
        }
        apply(null)
    }

    private fun apply(customUrl: String?) {
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                // Clear the old server's metadata first: the settings flow emits as soon as the
                // endpoint is written, and the feed must not reload the previous cache in between.
                apodRepository.clearMetadataCache()
                // The feed also keeps the archive index in a disk cache; drop it so entries from
                // the old server can never mix with the new one.
                archiveIndexRepository.clear()
                settingsStore.setCustomApiBaseUrl(customUrl)
                settingsStore.setPreferredBaseUrl(null)
                // Fetch the new endpoint's latest record in the background. The wallpaper worker
                // uses the same resolver, so it follows automatically. Scheduling is best-effort:
                // the endpoint is already saved, so a WorkManager hiccup must not look like a
                // failed save.
                runCatching { syncController.runOnce() }
                _state.value = _state.value.copy(
                    isLoading = false,
                    isSaving = false,
                    input = customUrl.orEmpty(),
                    savedCustomUrl = customUrl,
                )
                _events.send(if (customUrl == null) AdvancedEvent.Reset else AdvancedEvent.Saved)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = AdvancedError.SAVE_FAILED,
                )
            }
        }
    }
}
