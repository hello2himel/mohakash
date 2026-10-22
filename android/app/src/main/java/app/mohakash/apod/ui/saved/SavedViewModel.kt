package app.mohakash.apod.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.data.repository.FavouritesRepository
import app.mohakash.apod.domain.model.ApodEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state of the saved screen.
 */
data class SavedUiState(
    val isLoading: Boolean = true,
    val entries: List<ApodEntry> = emptyList(),
)

/**
 * Drives the saved screen.
 *
 * The list comes straight from the database, so it is available offline.
 */
class SavedViewModel(
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            favouritesRepository.observeAll().collect { entries ->
                _state.value = SavedUiState(isLoading = false, entries = entries)
            }
        }
    }
}
