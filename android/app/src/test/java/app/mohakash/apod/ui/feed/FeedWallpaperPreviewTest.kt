package app.mohakash.apod.ui.feed

import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The wallpaper sheet must preview the entry on screen, never the previously installed picture.
 *
 * These are the cases of the deleted `ui/today/TodayWallpaperPreviewTest`, moved with the feed.
 * The helpers are pure, so no ViewModel or database is involved.
 */
class FeedWallpaperPreviewTest {

    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        // The human date follows the device locale; pin it so the assertion is stable.
        Locale.setDefault(Locale.UK)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    private fun entry(
        date: String = "2026-10-23",
        imageUrl: String = "https://example.org/new.jpg",
        mediaType: MediaType = MediaType.IMAGE,
        credit: String? = null,
    ) = ApodEntry(
        date = date,
        title = "A New Picture",
        explanation = "Explanation.",
        mediaType = mediaType,
        imageUrl = imageUrl,
        hdImageUrl = null,
        thumbnailUrl = null,
        apodUrl = null,
        copyright = credit,
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
    fun `the selected entry is previewed instead of the old wallpaper`() {
        val preview = feedWallpaperPreview(
            entry = entry(),
            wallpaperMeta = WallpaperMeta(
                date = "2026-01-01",
                title = "Old Archive Picture",
                sourceUrl = "https://example.org/old.jpg",
                fileName = "old.jpg",
                updatedAt = 1L,
            ),
            wallpaperPreviewPath = "/tmp/old-archive.jpg",
        )

        assertEquals("https://example.org/new.jpg", preview.remoteUrl)
        assertNull(preview.localPath)
    }

    @Test
    fun `the local file is used when it already belongs to the entry`() {
        val entry = entry()
        val preview = feedWallpaperPreview(
            entry = entry,
            wallpaperMeta = WallpaperMeta(
                date = entry.date,
                title = entry.title,
                sourceUrl = entry.imageUrl!!,
                fileName = "current.jpg",
                updatedAt = 1L,
            ),
            wallpaperPreviewPath = "/tmp/current.jpg",
        )

        assertEquals(File("/tmp/current.jpg"), preview.model)
    }

    @Test
    fun `a video entry previews its poster`() {
        val video = entry(mediaType = MediaType.VIDEO, imageUrl = "https://example.org/poster.jpg")
        val preview = feedWallpaperPreview(video, null, null)
        assertEquals("https://example.org/poster.jpg", preview.remoteUrl)
    }

    @Test
    fun `without an entry the installed wallpaper file is used`() {
        val preview = feedWallpaperPreview(entry = null, wallpaperMeta = null, wallpaperPreviewPath = "/tmp/current.jpg")
        assertEquals(File("/tmp/current.jpg"), preview.model)
    }

    @Test
    fun `the caption follows the selected entry`() {
        val caption = feedWallpaperCaption(
            entry = entry(),
            wallpaperMeta = WallpaperMeta(
                date = "2026-01-01",
                title = "Old Archive Picture",
                sourceUrl = "https://example.org/old.jpg",
                fileName = "old.jpg",
                updatedAt = 1L,
            ),
        )

        assertEquals("2026-10-23 · A New Picture", caption)
    }

    @Test
    fun `the feed header uses a human date and omits a missing credit`() {
        assertEquals("23 October 2026 · Roberto Palaia", feedCreditLine("2026-10-23", "Roberto Palaia"))
        assertEquals("23 October 2026", feedCreditLine("2026-10-23", null))
        assertEquals("Roberto Palaia", feedCreditLine(null, "Roberto Palaia"))
    }

    @Test
    fun `a malformed date falls back to the raw value`() {
        assertEquals("not-a-date", feedDisplayDate("not-a-date"))
        assertEquals("", feedDisplayDate(null))
        assertTrue(feedCreditLine("2026-10-23", "  ").contains("2026"))
    }
}
