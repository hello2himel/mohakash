package app.mohakash.apod.ui.archive

import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The archive search must not silently inherit the browse year, and ranges are inclusive. */
class ArchiveUiStateTest {

    private fun index(date: String, title: String = "Title $date") = ArchiveIndexEntry(
        date = date,
        title = title,
        mediaType = MediaType.IMAGE,
        thumbnailUrl = null,
        imageUrl = null,
        apodUrl = null,
        copyright = null,
        detailPath = null,
    )

    private val entries = listOf(
        index("2026-10-27"),
        index("2025-12-31"),
        index("2024-12-31"),
        index("2017-01-01"),
    )

    @Test
    fun `browse applies the selected year`() {
        val state = ArchiveUiState(entries = entries, selectedYear = 2026)
        assertEquals(listOf("2026-10-27"), state.filtered.map { it.date })
    }

    @Test
    fun `search ignores the selected year and starts unfiltered`() {
        val state = ArchiveUiState(
            entries = entries,
            selectedYear = 2026,
            searchActive = true,
        )
        assertEquals(entries.map { it.date }, state.filtered.map { it.date })
    }

    @Test
    fun `date range is inclusive and combines with the query`() {
        val state = ArchiveUiState(
            entries = entries,
            selectedYear = 2026,
            searchActive = true,
            fromDate = "2017-01-01",
            toDate = "2025-12-31",
            query = "2025",
        )
        assertEquals(listOf("2025-12-31"), state.filtered.map { it.date })
    }
}
