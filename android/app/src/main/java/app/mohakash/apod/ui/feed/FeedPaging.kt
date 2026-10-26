package app.mohakash.apod.ui.feed

import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.MediaType

/**
 * The pure engine behind the feed list.
 *
 * Everything here is a plain function of its arguments: no coroutines, no Android types, no
 * network. The ViewModel asks these functions for a new list and only publishes the result, which
 * keeps the pager order, the filters and the jump-to-date logic unit-testable.
 *
 * The archive index is the only source of order. Neighbours are never computed with calendar math
 * — nine dates are missing from the real archive, so `date ± 1 day` would land on pages that do
 * not exist.
 */
object FeedPaging {

    /** True when [date] is a real ISO calendar date (`yyyy-MM-dd`); the archive only has those. */
    fun isIsoDate(date: String?): Boolean {
        val value = date?.trim().orEmpty()
        if (value.length != 10 || value[4] != '-' || value[7] != '-') return false
        if (value.substring(0, 4).toIntOrNull() == null) return false
        // A strict UTC parse rejects 2026-13-40 as well as 2026-02-30.
        return TimeFormatting.parseApiDate(value) != null
    }

    /** The year of an ISO date, or `null` when it cannot be parsed. */
    fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * Union of two date lists, de-duplicated and sorted newest first.
     *
     * ISO dates sort lexicographically, so this needs no calendar parsing.
     */
    fun mergeDates(a: List<String>, b: List<String>): List<String> =
        (a + b).filter { it.isNotBlank() }.distinct().sortedDescending()

    /** The lightweight index row for a full record. */
    fun ApodEntry.toIndexEntry(): ArchiveIndexEntry = ArchiveIndexEntry(
        date = date,
        title = title,
        mediaType = mediaType,
        thumbnailUrl = thumbnailUrl,
        // The index normally has no image_url; keeping the hydrated one here is what turns a
        // stale page into a full-quality one after the year file arrives.
        imageUrl = imageUrl,
        apodUrl = apodUrl,
        copyright = copyright ?: credit,
        detailPath = null,
    )

    /**
     * Builds the newest-first date list.
     *
     * Priority: the index is authoritative for order and metadata, a full year record fills the
     * dates the index does not know yet, and `latest.json` always wins for its own date (the index
     * can lag one day behind).
     */
    fun buildDateItems(
        index: List<ArchiveIndexEntry>,
        latest: ApodEntry?,
        yearRecords: Map<Int, List<ApodEntry>>,
    ): List<FeedDateItem> {
        val byDate = LinkedHashMap<String, ArchiveIndexEntry>()
        for (entry in index) {
            if (entry.date.isNotBlank()) byDate.putIfAbsent(entry.date, entry)
        }
        // Year files are only a fallback for the order; they matter before the index arrives and
        // for entries newer than a stale index.
        for (records in yearRecords.values) {
            for (record in records) {
                if (record.date.isNotBlank()) byDate.putIfAbsent(record.date, record.toIndexEntry())
            }
        }
        latest?.takeIf { it.date.isNotBlank() }?.let { byDate[it.date] = it.toIndexEntry() }
        return byDate.keys.sortedDescending().map { date -> FeedDateItem(date, byDate[date]) }
    }

    /** Applies the year/media filter. Order is preserved (newest first). */
    fun filtered(items: List<FeedDateItem>, filters: FeedFilters): List<FeedDateItem> =
        if (!filters.isActive) items else items.filter { filters.matches(it.indexEntry) }

    /**
     * The newest existing date that is not newer than [target] — gap-safe.
     *
     * - target inside a gap → the previous existing date
     * - target newer than the archive → the newest date
     * - target older than the archive → the oldest date
     */
    fun nearestDate(items: List<FeedDateItem>, target: String?): String? {
        val clean = target?.trim().orEmpty()
        if (!isIsoDate(clean)) return null
        val newest = items.firstOrNull()?.date ?: return null
        if (clean >= newest) return newest
        return items.firstOrNull { it.date <= clean }?.date ?: items.lastOrNull()?.date
    }

    /** The pager index of [date]; `0` when the date is unknown (the pager clamps itself). */
    fun pageIndexForDate(pages: List<FeedPageUi>, date: String?): Int {
        if (date.isNullOrBlank()) return 0
        pages.forEachIndexed { index, page ->
            if ((page.page as? FeedPage.Entry)?.date == date) return index
        }
        return 0
    }

    /**
     * Builds the pager pages: one page per date plus the end-of-archive card.
     *
     * An empty result means there is no data at all (the screen shows the full-page error state).
     * "The filters match nothing" is [endOnlyPage] instead — a real last page, not an error.
     */
    fun buildPages(
        items: List<FeedDateItem>,
        hydrated: Map<String, ApodEntry>,
        hydration: Map<String, Hydration>,
        latestDate: String?,
    ): List<FeedPageUi> {
        if (items.isEmpty()) return emptyList()
        val pages = ArrayList<FeedPageUi>(items.size + 1)
        for (item in items) {
            val entry = hydrated[item.date]
            pages += FeedPageUi(
                page = FeedPage.Entry(item.date),
                indexEntry = item.indexEntry,
                entry = entry,
                hydration = hydration[item.date]
                    ?: if (entry != null) Hydration.READY else Hydration.IDLE,
                isLatest = item.date == latestDate,
            )
        }
        pages += endPage()
        return pages
    }

    /** The only page of an empty filtered result: the end card, with the "no matches" copy. */
    fun endOnlyPage(): FeedPageUi = endPage()

    private fun endPage(): FeedPageUi = FeedPageUi(page = FeedPage.End, hydration = Hydration.READY)

    /** The pages the prefetcher looks at around the settled page: `[current-2 .. current+8]`. */
    fun prefetchWindow(
        currentPage: Int,
        pageCount: Int,
        back: Int = PREFETCH_BACK,
        forward: Int = PREFETCH_FORWARD,
    ): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY
        val start = (currentPage - back).coerceAtLeast(0)
        val end = (currentPage + forward).coerceAtMost(pageCount - 1)
        return start..end
    }

    /** The distinct years touched by [window], in window order. */
    fun yearsInWindow(pages: List<FeedPageUi>, window: IntRange): List<Int> =
        window.mapNotNull { index ->
            (pages.getOrNull(index)?.page as? FeedPage.Entry)?.date?.let(::yearOf)
        }.distinct()

    /** Every year present in the date list, newest first. */
    fun availableYears(items: List<FeedDateItem>): List<Int> =
        items.mapNotNull { yearOf(it.date) }.distinct().sortedDescending()

    /**
     * Search over the lightweight index: title, ISO date and credit.
     *
     * Explanation text is deliberately not searchable: it only exists in the year files, and
     * downloading every year to run a query would be far more expensive than the index itself.
     */
    fun search(
        items: List<FeedDateItem>,
        query: String,
        filters: FeedFilters = FeedFilters(),
        limit: Int = SEARCH_RESULT_LIMIT,
    ): List<FeedDateItem> {
        val candidates = filtered(items, filters)
        val text = query.trim()
        if (text.isEmpty()) return candidates.take(limit)
        return candidates.filter { item ->
            item.date.contains(text, ignoreCase = true) ||
                item.indexEntry?.title?.contains(text, ignoreCase = true) == true ||
                item.indexEntry?.displayCredit?.contains(text, ignoreCase = true) == true
        }.take(limit)
    }

    /** A placeholder index row used when a page has no index metadata (defensive). */
    fun syntheticIndexEntry(date: String, entry: ApodEntry?): ArchiveIndexEntry = entry?.toIndexEntry()
        ?: ArchiveIndexEntry(
            date = date,
            title = "",
            mediaType = MediaType.OTHER,
            thumbnailUrl = null,
            imageUrl = null,
            apodUrl = null,
            copyright = null,
            detailPath = null,
        )

    const val PREFETCH_BACK = 2
    const val PREFETCH_FORWARD = 8

    /** The search sheet never renders more than this many rows. */
    const val SEARCH_RESULT_LIMIT = 300
}
