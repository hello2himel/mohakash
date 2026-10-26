package app.mohakash.apod.ui.feed

import app.mohakash.apod.core.TimeFormatting

/**
 * The network policy of the feed, kept away from the ViewModel so it can be tested with plain
 * values (a clock, a metered flag, the cached snapshot).
 *
 * The rules exist because the archive index is a 5 MiB download: it is fetched once, cached on
 * disk, refreshed at most once a day, and never on a metered connection unless the user explicitly
 * asked for it (search, the filter sheet, pull-to-refresh, or swiping all the way to the end).
 */
object FeedLoadPolicy {

    /** A cached index older than this is refreshed (the API itself caches for 5 minutes). */
    const val INDEX_STALE_AFTER_MILLIS: Long = 24L * 60L * 60L * 1000L

    /** True when the cached index needs a refresh because of its age. */
    fun isIndexStale(savedAt: Long?, now: Long): Boolean {
        if (savedAt == null) return true
        val age = now - savedAt
        // A clock that jumped backwards must not trigger an endless refresh loop.
        if (age < 0) return false
        return age > INDEX_STALE_AFTER_MILLIS
    }

    /**
     * The one decision about fetching the archive index.
     *
     * A refresh happens when there is no usable cache OR the cache is stale OR the cached index
     * does not know the latest APOD yet. On a metered connection it is deferred until the user
     * explicitly asks for it — the cached index (or the current year) keeps the feed working.
     */
    fun shouldRefreshIndex(
        hasCachedIndex: Boolean,
        cachedSavedAt: Long?,
        isMetered: Boolean,
        userRequested: Boolean,
        now: Long,
        indexHeadDate: String?,
        latestDate: String?,
    ): Boolean {
        val stale = if (hasCachedIndex) isIndexStale(cachedSavedAt, now) else true
        val missingLatest = hasCachedIndex &&
            indexHeadDate != null &&
            latestDate != null &&
            indexHeadDate < latestDate
        if (!stale && !missingLatest) return false
        return !isMetered || userRequested
    }

    /** True when `latest.json` should be refreshed on open / pull-to-refresh. */
    fun shouldRefreshLatest(lastSyncAt: Long?, now: Long, force: Boolean = false): Boolean =
        force || TimeFormatting.isStale(lastSyncAt, now)

    /**
     * The years that should be fetched for the prefetch window around [currentPage].
     *
     * Years already cached (in Room or fetched during this session) are filtered out, and the
     * result is ordered by distance from the current page so the nearest year is fetched first.
     */
    fun yearsToPrefetch(
        pages: List<FeedPageUi>,
        currentPage: Int,
        cachedYears: Set<Int>,
        back: Int = FeedPaging.PREFETCH_BACK,
        forward: Int = FeedPaging.PREFETCH_FORWARD,
    ): List<Int> {
        val window = FeedPaging.prefetchWindow(currentPage, pages.size, back, forward)
        val currentYear = (pages.getOrNull(currentPage)?.page as? FeedPage.Entry)?.date
            ?.let(FeedPaging::yearOf)
        return FeedPaging.yearsInWindow(pages, window)
            .filterNot { it in cachedYears }
            .sortedBy { year -> if (currentYear == null) 0 else kotlin.math.abs(year - currentYear) }
    }

    /**
     * The next older year that should be appended, or `null`.
     *
     * Only used while the index is not available: the visible list is the union of the fetched
     * year files, so the end card must move forward before the user reaches it. The decision fires
     * when the settled page is within [threshold] pages of the last entry.
     */
    fun adjacentYearToFetch(
        pages: List<FeedPageUi>,
        currentPage: Int,
        loadedYears: Set<Int>,
        threshold: Int = ADJACENT_YEAR_THRESHOLD,
    ): Int? {
        val entryCount = pages.count { it.page is FeedPage.Entry }
        if (entryCount == 0) return null
        if (currentPage < entryCount - threshold) return null
        val oldest = loadedYears.minOrNull() ?: return null
        return (oldest - 1).takeIf { it >= OLDEST_ARCHIVE_YEAR && it !in loadedYears }
    }

    /** The first year the public archive covers (the feed stops here). */
    const val OLDEST_ARCHIVE_YEAR = 1995

    /** How close to the current tail the user has to be before the next year is fetched. */
    const val ADJACENT_YEAR_THRESHOLD = 5
}
