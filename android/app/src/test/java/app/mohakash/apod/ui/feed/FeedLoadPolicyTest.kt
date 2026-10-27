package app.mohakash.apod.ui.feed

import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The network policy of the feed: when the big index may be fetched, and which year is prefetched
 * when the user approaches a boundary. These are the rules that keep "no network per swipe"
 * honest, so they are tested with plain values.
 */
class FeedLoadPolicyTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun index(date: String) = ArchiveIndexEntry(
        date = date,
        title = "Title",
        mediaType = MediaType.IMAGE,
        thumbnailUrl = null,
        imageUrl = null,
        apodUrl = null,
        copyright = null,
        detailPath = null,
    )

    private fun page(date: String) = FeedPageUi(
        page = FeedPage.Entry(date),
        indexEntry = index(date),
        hydration = Hydration.READY,
    )

    // ---- index refresh -------------------------------------------------------------------

    @Test
    fun `no cache on a metered connection is not fetched without an explicit request`() {
        assertFalse(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = false,
                cachedSavedAt = null,
                isMetered = true,
                userRequested = false,
                now = now,
                indexHeadDate = null,
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `an explicit search or filter overrides the metered deferral`() {
        assertTrue(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = false,
                cachedSavedAt = null,
                isMetered = true,
                userRequested = true,
                now = now,
                indexHeadDate = null,
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `a missing index is fetched immediately when unmetered`() {
        assertTrue(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = false,
                cachedSavedAt = null,
                isMetered = false,
                userRequested = false,
                now = now,
                indexHeadDate = null,
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `a stale index is refreshed after 24 hours`() {
        val savedAt = now - day - 1
        assertTrue(FeedLoadPolicy.isIndexStale(savedAt, now))
        assertTrue(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = true,
                cachedSavedAt = savedAt,
                isMetered = false,
                userRequested = false,
                now = now,
                indexHeadDate = "2026-10-23",
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `a fresh index is not refetched`() {
        val savedAt = now - 60L * 60L * 1000L
        assertFalse(FeedLoadPolicy.isIndexStale(savedAt, now))
        assertFalse(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = true,
                cachedSavedAt = savedAt,
                isMetered = false,
                userRequested = true,
                now = now,
                indexHeadDate = "2026-10-23",
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `a stale cache on a metered connection keeps working until the user asks`() {
        val savedAt = now - day - 1
        assertFalse(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = true,
                cachedSavedAt = savedAt,
                isMetered = true,
                userRequested = false,
                now = now,
                indexHeadDate = "2026-10-22",
                latestDate = "2026-10-23",
            ),
        )
        assertTrue(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = true,
                cachedSavedAt = savedAt,
                isMetered = true,
                userRequested = true,
                now = now,
                indexHeadDate = "2026-10-22",
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `an index that does not know the latest APOD is refreshed`() {
        val savedAt = now - 60_000L
        assertTrue(
            FeedLoadPolicy.shouldRefreshIndex(
                hasCachedIndex = true,
                cachedSavedAt = savedAt,
                isMetered = false,
                userRequested = false,
                now = now,
                indexHeadDate = "2026-10-22",
                latestDate = "2026-10-23",
            ),
        )
    }

    @Test
    fun `a clock that jumped backwards does not cause an endless refresh`() {
        assertFalse(FeedLoadPolicy.isIndexStale(savedAt = now + day, now = now))
    }

    @Test
    fun `latest refresh follows the six hour staleness rule`() {
        assertTrue(FeedLoadPolicy.shouldRefreshLatest(null, now))
        assertTrue(FeedLoadPolicy.shouldRefreshLatest(now - 7L * 60L * 60L * 1000L, now))
        assertTrue(FeedLoadPolicy.shouldRefreshLatest(now - 2L * 60L * 60L * 1000L, now))
        assertFalse(FeedLoadPolicy.shouldRefreshLatest(now - 60_000L, now))
        assertTrue(FeedLoadPolicy.shouldRefreshLatest(now - 60_000L, now, force = true))
    }

    // ---- year prefetch -------------------------------------------------------------------

    @Test
    fun `years in the window are prefetched within two pages of a year boundary`() {
        val pages = listOf(
            page("2026-01-02"),
            page("2026-01-01"),
            page("2025-12-31"),
            page("2025-12-30"),
            FeedPageUi(page = FeedPage.End),
        )
        assertEquals(listOf(2026, 2025), FeedLoadPolicy.yearsToPrefetch(pages, currentPage = 0, cachedYears = emptySet()))
    }

    @Test
    fun `a cached year is never fetched again`() {
        val pages = listOf(
            page("2026-01-02"),
            page("2026-01-01"),
            page("2025-12-31"),
            page("2025-12-30"),
            FeedPageUi(page = FeedPage.End),
        )
        assertEquals(
            listOf(2026),
            FeedLoadPolicy.yearsToPrefetch(pages, currentPage = 0, cachedYears = setOf(2025)),
        )
        assertEquals(
            emptyList<Int>(),
            FeedLoadPolicy.yearsToPrefetch(pages, currentPage = 0, cachedYears = setOf(2026, 2025)),
        )
    }

    @Test
    fun `the next older year is appended only near the tail`() {
        val pages = (10 downTo 1).map { dayOfMonth ->
            page("2026-01-%02d".format(dayOfMonth))
        } + FeedPageUi(page = FeedPage.End)

        assertNull(FeedLoadPolicy.adjacentYearToFetch(pages, currentPage = 0, loadedYears = setOf(2026)))
        assertEquals(
            2025,
            FeedLoadPolicy.adjacentYearToFetch(pages, currentPage = 6, loadedYears = setOf(2026)),
        )
        // Nothing older than the first year of the archive.
        assertNull(FeedLoadPolicy.adjacentYearToFetch(pages, currentPage = 9, loadedYears = setOf(1995)))
        assertNull(FeedLoadPolicy.adjacentYearToFetch(emptyList(), currentPage = 0, loadedYears = setOf(2026)))
    }
}
