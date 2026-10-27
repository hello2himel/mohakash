package app.mohakash.apod.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.repository.ArchiveIndexRepository
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** The media filter of the archive. */
enum class MediaFilter { ALL, IMAGES, VIDEOS }

/**
 * The state of the archive screen.
 */
data class ArchiveUiState(
    val isLoading: Boolean = true,
    val entries: List<ArchiveIndexEntry> = emptyList(),
    val query: String = "",
    val selectedYear: Int = defaultYear(),
    val availableYears: List<Int> = emptyList(),
    val typeFilter: MediaFilter = MediaFilter.ALL,
    val fromDate: String? = null,
    val toDate: String? = null,
    val jumpToDate: String? = null,
    /** True while the search field is the active surface: no implicit year filter applies. */
    val searchActive: Boolean = false,
    val visibleCount: Int = PAGE_SIZE,
    val isOffline: Boolean = false,
    val hasError: Boolean = false,
) {
    /** The entries that match the current filters, newest first. */
    val filtered: List<ArchiveIndexEntry>
        get() = entries
            .filter { searchActive || it.year == selectedYear }
            .filter { jumpToDate == null || it.date == jumpToDate }
            .filter { fromDate == null || it.date >= fromDate }
            .filter { toDate == null || it.date <= toDate }
            .filter { entry ->
                when (typeFilter) {
                    MediaFilter.ALL -> true
                    MediaFilter.IMAGES -> entry.mediaType.isImage
                    MediaFilter.VIDEOS -> entry.mediaType.isPlayable
                }
            }
            .filter { entry ->
                query.isBlank() || entry.title.contains(query.trim(), ignoreCase = true) ||
                    entry.date.contains(query.trim(), ignoreCase = true)
            }

    /** The entries that are currently rendered — the list grows while the user scrolls. */
    val visible: List<ArchiveIndexEntry>
        get() = filtered.take(visibleCount)

    /** True when more entries can be loaded. */
    val hasMore: Boolean get() = visible.size < filtered.size

    companion object {
        const val PAGE_SIZE = 30

        fun defaultYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
    }
}

/**
 * Drives the archive screen.
 *
 * The lightweight index is loaded first; the full records of a year are only fetched when the
 * user actually looks at that year.
 */
class ArchiveViewModel(
    private val apodRepository: ApodRepository,
    private val archiveIndexRepository: ArchiveIndexRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Loads the archive index from the shared disk/memory cache, fetching it only if absent. */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previous = _state.value.entries
            _state.value = _state.value.copy(isLoading = true, hasError = false)
            val entries = archiveIndexRepository.ensureLoaded(allowNetwork = true)
            if (entries != null) {
                applyEntries(entries)
                _state.value = _state.value.copy(isLoading = false, isOffline = false, hasError = false)
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isOffline = previous.isNotEmpty(),
                    hasError = previous.isEmpty(),
                )
            }
        }
    }

    /** Enters search mode: no year, type or date filter is applied by default. */
    fun beginSearch() {
        val current = _state.value
        if (current.searchActive && current.typeFilter == MediaFilter.ALL &&
            current.fromDate == null && current.toDate == null && current.jumpToDate == null
        ) return
        _state.value = current.copy(
            searchActive = true,
            typeFilter = MediaFilter.ALL,
            fromDate = null,
            toDate = null,
            jumpToDate = null,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
    }

    /** Applies a search term. */
    fun setQuery(query: String) {
        if (query.isNotBlank() && !_state.value.searchActive) beginSearch()
        _state.value = _state.value.copy(
            query = query,
            jumpToDate = null,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
    }

    /** Selects a year and fetches the full records of that year in the background. */
    fun selectYear(year: Int) {
        _state.value = _state.value.copy(
            selectedYear = year,
            searchActive = false,
            jumpToDate = null,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
        viewModelScope.launch {
            // The full records are only needed when the user opens an entry of that year.
            if (apodRepository.hasCachedYear(year)) return@launch
            runCatching { apodRepository.fetchArchiveYear(year) }
        }
    }

    /** Applies the media filter. */
    fun setTypeFilter(filter: MediaFilter) {
        _state.value = _state.value.copy(
            typeFilter = filter,
            jumpToDate = null,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
    }

    /** Applies an inclusive date range. */
    fun setDateRange(from: String?, to: String?) {
        if ((from != null && !ISO_DATE.matches(from)) || (to != null && !ISO_DATE.matches(to))) return
        val start = from ?: to
        val end = to ?: from
        _state.value = _state.value.copy(
            fromDate = start,
            toDate = end,
            jumpToDate = null,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
    }

    /** Jumps to a date. */
    fun jumpToDate(date: String) {
        val year = date.take(4).toIntOrNull() ?: return
        _state.value = _state.value.copy(
            jumpToDate = date,
            query = "",
            searchActive = false,
            selectedYear = year,
            fromDate = null,
            toDate = null,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
        viewModelScope.launch {
            if (apodRepository.hasCachedYear(year)) return@launch
            runCatching { apodRepository.fetchArchiveYear(year) }
        }
    }

    /** Resets every filter back to the default — the current year when it exists. */
    fun reset() {
        val years = _state.value.availableYears
        val currentYear = ArchiveUiState.defaultYear()
        _state.value = _state.value.copy(
            query = "",
            searchActive = false,
            typeFilter = MediaFilter.ALL,
            fromDate = null,
            toDate = null,
            jumpToDate = null,
            selectedYear = if (currentYear in years) currentYear else years.firstOrNull() ?: currentYear,
            visibleCount = ArchiveUiState.PAGE_SIZE,
        )
    }

    /** Loads the next page of the list. */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore) return
        _state.value = current.copy(visibleCount = current.visibleCount + ArchiveUiState.PAGE_SIZE)
    }

    private fun applyEntries(entries: List<ArchiveIndexEntry>) {
        val years = entries.map { it.year }.filter { it > 0 }.distinct().sortedDescending()
        val current = _state.value
        val currentYear = ArchiveUiState.defaultYear()
        val selectedYear = when {
            current.selectedYear in years -> current.selectedYear
            // The approved design starts on the current year.
            currentYear in years -> currentYear
            else -> years.firstOrNull() ?: currentYear
        }
        _state.value = current.copy(
            entries = entries,
            availableYears = years,
            selectedYear = selectedYear,
        )
    }
}

/** The media type of an index entry, used by the tests. */
internal fun ArchiveIndexEntry.isVideoLike(): Boolean = mediaType == MediaType.VIDEO || mediaType == MediaType.OTHER
