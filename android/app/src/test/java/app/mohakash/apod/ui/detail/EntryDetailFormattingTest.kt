package app.mohakash.apod.ui.detail

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The wording of the entry viewer: human dates, credit lines, metadata rows and note conditions.
 */
class EntryDetailFormattingTest {

    private fun entry(
        date: String = "2026-10-26",
        title: String = "M42: The Great Nebula in Orion",
        mediaType: MediaType = MediaType.IMAGE,
        imageStatus: ImageStatus = ImageStatus.OK,
        copyright: String? = "Roberto Palaia",
        credit: String? = null,
        updatedAt: String? = "2026-10-26T06:00:00Z",
        source: String? = "apod",
        explanation: String = "First paragraph.\n\nSecond paragraph.",
    ): ApodEntry = ApodEntry(
        date = date,
        title = title,
        explanation = explanation,
        mediaType = mediaType,
        imageUrl = "https://example.org/image.jpg",
        hdImageUrl = "https://example.org/image-hd.jpg",
        thumbnailUrl = "https://example.org/image-thumb.jpg",
        apodUrl = "https://apod.nasa.gov/apod/ap20261026.html",
        copyright = copyright,
        credit = credit,
        altText = "A glowing nebula",
        source = source,
        sourceId = "apod-2026-10-26",
        sourceMediaType = "image",
        imageStatus = imageStatus,
        updatedAt = updatedAt,
        lastSuccessfulSync = "2026-10-26T06:05:00Z",
        latestAvailableDate = date,
        recordCount = 1,
        sourceStatus = "ok",
    )

    @Test
    fun `an APOD date is formatted as a human date`() {
        assertEquals("26 October 2026", EntryDetailFormatting.formatDisplayDate("2026-10-26", Locale.US))
        assertEquals("16 June 1995", EntryDetailFormatting.formatDisplayDate("1995-06-16", Locale.US))
    }

    @Test
    fun `the display date follows the given locale`() {
        assertEquals("26 octobre 2026", EntryDetailFormatting.formatDisplayDate("2026-10-26", Locale.FRENCH))
    }

    @Test
    fun `an unparseable date falls back to the raw value`() {
        assertEquals("not-a-date", EntryDetailFormatting.formatDisplayDate("not-a-date", Locale.US))
        assertEquals("", EntryDetailFormatting.formatDisplayDate("   ", Locale.US))
    }

    @Test
    fun `the updated timestamp is formatted as a human date or omitted`() {
        assertEquals(
            "26 October 2026",
            EntryDetailFormatting.formatUpdated("2026-10-26T06:00:00Z", Locale.US),
        )
        assertEquals(
            "26 October 2026",
            EntryDetailFormatting.formatUpdated("2026-10-26", Locale.US),
        )
        assertNull(EntryDetailFormatting.formatUpdated(null, Locale.US))
        assertNull(EntryDetailFormatting.formatUpdated("garbage", Locale.US))
    }

    @Test
    fun `the header line is the human date followed by the credit`() {
        assertEquals(
            "26 October 2026 · Credit: Roberto Palaia",
            EntryDetailFormatting.headerLine(entry(), Locale.US),
        )
    }

    @Test
    fun `the header line omits a missing credit`() {
        assertEquals(
            "26 October 2026",
            EntryDetailFormatting.headerLine(entry(copyright = null, credit = null), Locale.US),
        )
    }

    @Test
    fun `the copyright wins over the credit field`() {
        val withBoth = entry(copyright = "Jane Doe", credit = "John Doe")

        assertEquals("26 October 2026 · Credit: Jane Doe", EntryDetailFormatting.headerLine(withBoth, Locale.US))
        assertEquals("Jane Doe", withBoth.displayCredit)
    }

    @Test
    fun `media types map to their labels`() {
        assertEquals(EntryMediaLabel.IMAGE, EntryDetailFormatting.mediaLabel(MediaType.IMAGE))
        assertEquals(EntryMediaLabel.VIDEO, EntryDetailFormatting.mediaLabel(MediaType.VIDEO))
        assertEquals(EntryMediaLabel.INTERACTIVE, EntryDetailFormatting.mediaLabel(MediaType.OTHER))
    }

    @Test
    fun `metadata rows are ordered and carry the formatted values`() {
        val rows = EntryDetailFormatting.metadataRows(entry(), Locale.US)

        assertEquals(
            listOf(
                EntryMetadataKey.DATE,
                EntryMetadataKey.MEDIA_TYPE,
                EntryMetadataKey.CREDIT,
                EntryMetadataKey.UPDATED,
                EntryMetadataKey.SOURCE,
            ),
            rows.map { it.key },
        )
        assertEquals(EntryMetadataValue.Text("2026-10-26"), rows[0].value)
        assertEquals(EntryMetadataValue.Media(MediaType.IMAGE), rows[1].value)
        assertEquals(EntryMetadataValue.Text("Roberto Palaia"), rows[2].value)
        assertEquals(EntryMetadataValue.Text("26 October 2026"), rows[3].value)
        assertEquals(EntryMetadataValue.Text("apod"), rows[4].value)
    }

    @Test
    fun `rows without a value are omitted but the credit says Not provided`() {
        val rows = EntryDetailFormatting.metadataRows(
            entry(copyright = null, credit = null, updatedAt = null, source = null),
            Locale.US,
        )

        assertEquals(
            listOf(EntryMetadataKey.DATE, EntryMetadataKey.MEDIA_TYPE, EntryMetadataKey.CREDIT),
            rows.map { it.key },
        )
        assertEquals(EntryMetadataValue.NotProvided, rows[2].value)
    }

    @Test
    fun `a placeholder or missing image shows the matching note`() {
        assertNull(EntryDetailFormatting.imageStatusNote(ImageStatus.OK))
        assertEquals(EntryImageNote.PLACEHOLDER, EntryDetailFormatting.imageStatusNote(ImageStatus.PLACEHOLDER))
        assertEquals(EntryImageNote.MISSING, EntryDetailFormatting.imageStatusNote(ImageStatus.MISSING))
    }

    @Test
    fun `the offline note only applies to a cached entry shown after a failed fetch`() {
        assertTrue(EntryDetailFormatting.offlineNoteVisible(isShowingCachedCopy = true, entry = entry()))
        assertFalse(EntryDetailFormatting.offlineNoteVisible(isShowingCachedCopy = false, entry = entry()))
        assertFalse(EntryDetailFormatting.offlineNoteVisible(isShowingCachedCopy = true, entry = null))
    }

    @Test
    fun `the video note applies to video and interactive entries only`() {
        assertFalse(EntryDetailFormatting.videoNoteVisible(entry(mediaType = MediaType.IMAGE)))
        assertTrue(EntryDetailFormatting.videoNoteVisible(entry(mediaType = MediaType.VIDEO)))
        assertTrue(EntryDetailFormatting.videoNoteVisible(entry(mediaType = MediaType.OTHER)))
        assertFalse(EntryDetailFormatting.videoNoteVisible(null))
    }

    @Test
    fun `the explanation keeps its paragraphs and wording unchanged`() {
        val long = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(8).trim()
        val paragraphs = EntryDetailFormatting.explanationParagraphs(
            entry(explanation = "$long\n\nSecond paragraph."),
        )

        assertEquals(2, paragraphs.size)
        assertEquals(long, paragraphs[0])
        assertEquals("Second paragraph.", paragraphs[1])
        assertTrue(EntryDetailFormatting.explanationParagraphs(entry(explanation = "")).isEmpty())
    }
}
