package app.mohakash.apod.data.storage

import app.mohakash.apod.BuildConfig
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareHelperTest {

    private val website = BuildConfig.WEBSITE_BASE_URL.trimEnd('/')

    @Test
    fun `archive URL points at the public website entry page`() {
        assertEquals(
            "$website/archive/2026-10-25",
            ShareHelper.archiveUrl("2026-10-25"),
        )
    }

    @Test
    fun `share text carries the archive URL instead of the original APOD page`() {
        val text = ShareHelper.buildShareText(entry())

        assertTrue(text.contains("$website/archive/2026-10-25"))
        assertFalse(text.contains("apod.nasa.gov"))
    }

    @Test
    fun `share text keeps the title, date and credit`() {
        val text = ShareHelper.buildShareText(entry())

        assertTrue(text.contains("\"The First Green Star\""))
        assertTrue(text.contains("2026-10-25"))
        assertTrue(text.contains("Image: Mark A. Garlick"))
    }

    @Test
    fun `share text still ends with the link when there is no credit`() {
        val text = ShareHelper.buildShareText(entry().copy(copyright = null, credit = null))

        assertTrue(text.endsWith("$website/archive/2026-10-25"))
        assertFalse(text.contains("Image:"))
    }

    private fun entry() = ApodEntry(
        date = "2026-10-25",
        title = "The First Green Star",
        explanation = "Some explanation.",
        mediaType = MediaType.IMAGE,
        imageUrl = "https://example.org/photo.jpg?w=1600",
        hdImageUrl = "https://example.org/photo.jpg?w=2400",
        thumbnailUrl = "https://example.org/photo.jpg?w=480",
        apodUrl = "https://apod.nasa.gov/apod/ap261025.html",
        copyright = "Mark A. Garlick",
        credit = "Mark A. Garlick",
        altText = "A star",
        source = "NASA APOD",
        sourceId = "1497603",
        sourceMediaType = "image",
        imageStatus = ImageStatus.OK,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )
}
