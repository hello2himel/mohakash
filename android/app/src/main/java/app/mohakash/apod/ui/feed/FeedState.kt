package app.mohakash.apod.ui.feed

import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.WallpaperScaling

/** The media filter of the feed. */
enum class FeedMediaFilter { ALL, IMAGES, VIDEOS }

/**
 * The year/media filter that the feed, the filter sheet and the search sheet share.
 *
 * There is deliberately one filter type: the combined screen has exactly one list, so search and
 * the year chips can never disagree about which pictures exist.
 */
data class FeedFilters(
    /** `null` means *All years* — the feed is one continuous archive, not a per-year list. */
    val year: Int? = null,
    val media: FeedMediaFilter = FeedMediaFilter.ALL,
    /** Inclusive ISO date bounds. A single date means [fromDate] == [toDate]. */
    val fromDate: String? = null,
    val toDate: String? = null,
) {
    val hasDateRange: Boolean get() = fromDate != null || toDate != null

    val isActive: Boolean
        get() = year != null || media != FeedMediaFilter.ALL || hasDateRange

    /** True when [entry] satisfies the year, media and date part of the filter. */
    fun matches(entry: ArchiveIndexEntry?): Boolean {
        if (entry == null) return true
        if (year != null && entry.year != year) return false
        // ISO dates sort lexicographically, which keeps this comparison allocation-free.
        if (fromDate != null && entry.date < fromDate) return false
        if (toDate != null && entry.date > toDate) return false
        return when (media) {
            FeedMediaFilter.ALL -> true
            FeedMediaFilter.IMAGES -> entry.mediaType.isImage
            FeedMediaFilter.VIDEOS -> entry.mediaType.isPlayable
        }
    }
}

/** One date in the feed, with the lightweight index row that describes it. */
data class FeedDateItem(
    val date: String,
    val indexEntry: ArchiveIndexEntry?,
)

/** The hydration state of one page's full record. */
enum class Hydration { IDLE, LOADING, READY, FAILED }

/** A page of the feed: either one APOD entry or the deliberate end-of-archive card. */
sealed interface FeedPage {
    val key: String

    data class Entry(val date: String) : FeedPage {
        override val key: String get() = date
    }

    data object End : FeedPage {
        override val key: String = "END"
    }
}

/** One rendered pager page. */
data class FeedPageUi(
    val page: FeedPage,
    val indexEntry: ArchiveIndexEntry? = null,
    val entry: ApodEntry? = null,
    val hydration: Hydration = Hydration.IDLE,
    val isLatest: Boolean = false,
) {
    /** The APOD date of this page, or `null` for the end card. */
    val date: String? get() = (page as? FeedPage.Entry)?.date

    /** True when the page is the end-of-archive card. */
    val isEnd: Boolean get() = page is FeedPage.End
}

/** A one-shot request from the ViewModel to move the pager. */
data class FeedJumpRequest(
    val token: Long,
    val date: String,
    val animate: Boolean,
)

/** The state of the full-height search sheet. */
data class FeedSearchState(
    val query: String = "",
    val filters: FeedFilters = FeedFilters(),
    val results: List<FeedDateItem> = emptyList(),
    val isSearching: Boolean = false,
    /** True when the index could not be loaded and no cached copy exists. */
    val isUnavailable: Boolean = false,
)

/** The complete state of the feed. */
data class FeedUiState(
    val pages: List<FeedPageUi> = emptyList(),
    /** The unfiltered date list, shared with the filter sheet so it can show a live count. */
    val allItems: List<FeedDateItem> = emptyList(),
    val filters: FeedFilters = FeedFilters(),
    val availableYears: List<Int> = emptyList(),
    /** First and last date of the archive, used to bound the jump-to-date picker. */
    val archiveFirstDate: String? = null,
    val archiveLastDate: String? = null,
    val isLoadingLatest: Boolean = true,
    val isLoadingIndex: Boolean = false,
    /** False until the authoritative index (or the whole visible archive) is available. */
    val isIndexComplete: Boolean = false,
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasFatalError: Boolean = false,
    val favourites: Set<String> = emptySet(),
    val scaling: WallpaperScaling = WallpaperScaling.FILL,
    val wallpaperMeta: WallpaperMeta? = null,
    val wallpaperPreviewPath: String? = null,
    val updateDaily: Boolean = true,
    val syncedAt: Long? = null,
    val currentDate: String? = null,
    /** The stable key of the page the pager is on ("END" for the end card). */
    val currentPageKey: String? = null,
    /** Shows the one-time "swipe up" cue until the user interacts or it times out. */
    val showSwipeHint: Boolean = false,
    /** True while the rocket is plotting a course and a random entry is being validated. */
    val isPickingRandom: Boolean = false,
    val jumpRequest: FeedJumpRequest? = null,
    /** Bumped only when the *list of dates* changes, never when one page hydrates. */
    val listVersion: Long = 0L,
    /** Non-null while the wallpaper sheet is open for the entry of that date. */
    val wallpaperSheetDate: String? = null,
    val search: FeedSearchState = FeedSearchState(),
) {
    /** The feed has something to render (an entry page or the end card). */
    val hasContent: Boolean get() = pages.isNotEmpty()

    /** True when the filters hide every picture of a non-empty archive. */
    val isFilteredEmpty: Boolean get() = filters.isActive && pages.size == 1 && pages[0].isEnd

    /** The page that the pager should be showing, if it is known. */
    fun indexOfDate(date: String?): Int = FeedPaging.pageIndexForDate(pages, date)
}

/** One-shot events of the feed. */
sealed interface FeedEvent {
    data class ShowMessage(val text: String) : FeedEvent
    data object OpenWallpaperPicker : FeedEvent
    data class ShowDonationPrompt(val fromRandom: Boolean) : FeedEvent
}
