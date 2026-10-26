package app.mohakash.apod.ui.feed

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed order and the filters are the load-bearing logic of the combined screen; these tests
 * pin the anti-calendar-math behaviour (the real archive has nine missing dates) and the bounds.
 */
class FeedPagingTest {

    private fun index(
        date: String,
        title: String = "Title $date",
        mediaType: MediaType = MediaType.IMAGE,
        credit: String? = "Credit $date",
    ) = ArchiveIndexEntry(
        date = date,
        title = title,
        mediaType = mediaType,
        thumbnailUrl = "https://example.org/$date-thumb.jpg",
        imageUrl = null,
        apodUrl = "https://apod.nasa.gov/apod/ap$date.html",
        copyright = credit,
        detailPath = null,
    )

    private fun entry(
        date: String,
        title: String = "Title $date",
        mediaType: MediaType = MediaType.IMAGE,
        explanation: String = "Explanation of $date",
    ) = ApodEntry(
        date = date,
        title = title,
        explanation = explanation,
        mediaType = mediaType,
        imageUrl = "https://example.org/$date.jpg",
        hdImageUrl = null,
        thumbnailUrl = "https://example.org/$date-thumb.jpg",
        apodUrl = null,
        copyright = "Credit $date",
        credit = null,
        altText = null,
        source = null,
        sourceId = null,
        sourceMediaType = null,
        imageStatus = ImageStatus.OK,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )

    @Test
    fun `mergeDates unions and orders newest first`() {
        val merged = FeedPaging.mergeDates(
            listOf("2026-10-22", "2026-10-20"),
            listOf("2026-10-23", "2026-10-22", "2026-10-21"),
        )
        assertEquals(
            listOf("2026-10-23", "2026-10-22", "2026-10-21", "2026-10-20"),
            merged,
        )
    }

    @Test
    fun `buildDateItems keeps the index order and de-duplicates`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(index("2026-10-23"), index("2026-10-22"), index("2026-10-21")),
            latest = null,
            yearRecords = emptyMap(),
        )
        assertEquals(listOf("2026-10-23", "2026-10-22", "2026-10-21"), items.map { it.date })
        assertEquals("Title 2026-10-23", items.first().indexEntry?.title)
    }

    @Test
    fun `latest json wins for its own date over a stale index row`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(index("2026-10-22")),
            latest = entry("2026-10-22", title = "Fresh title"),
            yearRecords = emptyMap(),
        )
        assertEquals(1, items.size)
        assertEquals("Fresh title", items.first().indexEntry?.title)
    }

    @Test
    fun `a stale index gets the new latest date appended at the head`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(index("2026-10-22"), index("2026-10-21")),
            latest = entry("2026-10-23"),
            yearRecords = emptyMap(),
        )
        assertEquals(listOf("2026-10-23", "2026-10-22", "2026-10-21"), items.map { it.date })
    }

    @Test
    fun `year records provide the order before the index arrives`() {
        val items = FeedPaging.buildDateItems(
            index = emptyList(),
            latest = entry("2026-10-23"),
            yearRecords = mapOf(
                2026 to listOf(entry("2026-10-23"), entry("2026-10-22")),
                2025 to listOf(entry("2025-12-31")),
            ),
        )
        assertEquals(listOf("2026-10-23", "2026-10-22", "2025-12-31"), items.map { it.date })
    }

    @Test
    fun `gap dates never create a page and the nearest date is gap safe`() {
        // The real archive jumps from 1995-06-16 to 1995-06-20 (three missing days).
        val items = FeedPaging.buildDateItems(
            index = listOf(index("1995-06-20"), index("1995-06-16")),
            latest = null,
            yearRecords = emptyMap(),
        )
        assertEquals(2, items.size)
        assertEquals("1995-06-16", FeedPaging.nearestDate(items, "1995-06-17"))
        assertEquals("1995-06-16", FeedPaging.nearestDate(items, "1995-06-16"))
        assertEquals("1995-06-20", FeedPaging.nearestDate(items, "1995-06-20"))
    }

    @Test
    fun `nearestDate handles targets outside the archive`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(index("2026-10-23"), index("2026-10-22")),
            latest = null,
            yearRecords = emptyMap(),
        )
        assertEquals("2026-10-23", FeedPaging.nearestDate(items, "2030-01-01"))
        assertEquals("2026-10-22", FeedPaging.nearestDate(items, "1990-01-01"))
        assertNull(FeedPaging.nearestDate(items, "not-a-date"))
        assertNull(FeedPaging.nearestDate(emptyList(), "2026-10-23"))
    }

    @Test
    fun `filters apply the year and the media type`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(
                index("2026-10-23", mediaType = MediaType.IMAGE),
                index("2026-10-22", mediaType = MediaType.VIDEO),
                index("2025-12-31", mediaType = MediaType.IMAGE),
                index("2025-12-30", mediaType = MediaType.OTHER),
            ),
            latest = null,
            yearRecords = emptyMap(),
        )

        assertEquals(
            listOf("2026-10-23", "2026-10-22", "2025-12-31", "2025-12-30"),
            FeedPaging.filtered(items, FeedFilters()).map { it.date },
        )
        assertEquals(
            listOf("2026-10-23", "2026-10-22"),
            FeedPaging.filtered(items, FeedFilters(year = 2026)).map { it.date },
        )
        assertEquals(
            listOf("2026-10-23", "2025-12-31"),
            FeedPaging.filtered(items, FeedFilters(media = FeedMediaFilter.IMAGES)).map { it.date },
        )
        // "Videos" covers both video and interactive entries, matching the old archive filter.
        assertEquals(
            listOf("2026-10-22", "2025-12-30"),
            FeedPaging.filtered(items, FeedFilters(media = FeedMediaFilter.VIDEOS)).map { it.date },
        )
    }

    @Test
    fun `buildPages always ends with the end card`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(index("2026-10-23"), index("2026-10-22")),
            latest = null,
            yearRecords = emptyMap(),
        )
        val pages = FeedPaging.buildPages(items, emptyMap(), emptyMap(), "2026-10-23")
        assertEquals(3, pages.size)
        assertTrue(pages.last().isEnd)
        assertEquals("2026-10-23", pages.first().date)
        assertTrue(pages.first().isLatest)
        assertTrue(pages[1].page.key != pages.last().page.key)
    }

    @Test
    fun `an empty filtered list is represented by the end card only`() {
        assertEquals(0, FeedPaging.buildPages(emptyList(), emptyMap(), emptyMap(), null).size)
        assertTrue(FeedPaging.endOnlyPage().isEnd)
    }

    @Test
    fun `page index for a date and for the end card`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(index("2026-10-23"), index("2026-10-22"), index("2026-10-21")),
            latest = null,
            yearRecords = emptyMap(),
        )
        val pages = FeedPaging.buildPages(items, emptyMap(), emptyMap(), "2026-10-23")
        assertEquals(0, FeedPaging.pageIndexForDate(pages, "2026-10-23"))
        assertEquals(2, FeedPaging.pageIndexForDate(pages, "2026-10-21"))
        assertEquals(0, FeedPaging.pageIndexForDate(pages, "1990-01-01"))
        assertEquals(3, pages.lastIndex)
    }

    @Test
    fun `prefetch window is current-2 to current+8 and clamps at both ends`() {
        assertEquals(0..8, FeedPaging.prefetchWindow(currentPage = 0, pageCount = 100))
        assertEquals(3..13, FeedPaging.prefetchWindow(currentPage = 5, pageCount = 100))
        assertEquals(7..9, FeedPaging.prefetchWindow(currentPage = 9, pageCount = 10))
        assertEquals(IntRange.EMPTY, FeedPaging.prefetchWindow(currentPage = 0, pageCount = 0))
    }

    @Test
    fun `years in the prefetch window cross a year boundary once`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(
                index("2026-01-02"),
                index("2026-01-01"),
                index("2025-12-31"),
                index("2025-12-30"),
            ),
            latest = null,
            yearRecords = emptyMap(),
        )
        val pages = FeedPaging.buildPages(items, emptyMap(), emptyMap(), "2026-01-02")
        assertEquals(
            listOf(2026, 2025),
            FeedPaging.yearsInWindow(pages, FeedPaging.prefetchWindow(0, pages.size)),
        )
    }

    @Test
    fun `search matches title date and credit, and is capped`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(
                index("2026-10-23", title = "The Orion Nebula", credit = "Roberto Palaia"),
                index("2026-10-22", title = "A Quiet Sea of Clouds", credit = "Jane Doe"),
                index("2025-12-31", title = "Rings of Saturn"),
            ),
            latest = null,
            yearRecords = emptyMap(),
        )

        assertEquals(1, FeedPaging.search(items, "orion").size)
        assertEquals(1, FeedPaging.search(items, "2026-10-22").size)
        assertEquals(1, FeedPaging.search(items, "palaia").size)
        assertEquals(3, FeedPaging.search(items, "").size)
        assertEquals(2, FeedPaging.search(items, "a", limit = 2).size)
        // Filters combine with the query.
        assertEquals(1, FeedPaging.search(items, "rings", FeedFilters(year = 2025)).size)
        assertEquals(0, FeedPaging.search(items, "rings", FeedFilters(year = 2026)).size)
    }

    @Test
    fun `available years are unique and newest first`() {
        val items = FeedPaging.buildDateItems(
            index = listOf(
                index("2026-10-23"),
                index("2025-12-31"),
                index("2025-01-01"),
                index("1995-06-16"),
            ),
            latest = null,
            yearRecords = emptyMap(),
        )
        assertEquals(listOf(2026, 2025, 1995), FeedPaging.availableYears(items))
    }

    @Test
    fun `iso dates are validated`() {
        assertTrue(FeedPaging.isIsoDate("2026-10-23"))
        assertTrue(!FeedPaging.isIsoDate("2026-13-40"))
        assertTrue(!FeedPaging.isIsoDate("not-a-date"))
        assertTrue(!FeedPaging.isIsoDate(null))
        assertEquals(2026, FeedPaging.yearOf("2026-10-23"))
        assertNull(FeedPaging.yearOf("nope"))
    }
}
