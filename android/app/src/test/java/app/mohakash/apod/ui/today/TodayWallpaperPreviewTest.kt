package app.mohakash.apod.ui.today

import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The wallpaper sheet must preview the entry on screen, never the previously installed picture.
 */
class TodayWallpaperPreviewTest {

    private fun entry(
        date: String = "2026-10-23",
        imageUrl: String = "https://example.org/new.jpg",
    ) = ApodEntry(
        date = date,
        title = "A New Picture",
        explanation = "Explanation.",
        mediaType = MediaType.IMAGE,
        imageUrl = imageUrl,
        hdImageUrl = null,
        thumbnailUrl = null,
        apodUrl = null,
        copyright = null,
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
        val state = TodayUiState(
            entry = entry(),
            wallpaperPreviewPath = "/tmp/old-archive.jpg",
            wallpaperMeta = WallpaperMeta(
                date = "2026-01-01",
                title = "Old Archive Picture",
                sourceUrl = "https://example.org/old.jpg",
                fileName = "old.jpg",
                updatedAt = 1L,
            ),
        )

        val preview = todayWallpaperPreview(state)

        assertEquals("https://example.org/new.jpg", preview.remoteUrl)
        assertTrue(preview.localPath == null)
    }

    @Test
    fun `the local file is used when it already belongs to the entry`() {
        val entry = entry()
        val state = TodayUiState(
            entry = entry,
            wallpaperPreviewPath = "/tmp/current.jpg",
            wallpaperMeta = WallpaperMeta(
                date = entry.date,
                title = entry.title,
                sourceUrl = entry.imageUrl!!,
                fileName = "current.jpg",
                updatedAt = 1L,
            ),
        )

        val preview = todayWallpaperPreview(state)

        assertEquals(File("/tmp/current.jpg"), preview.model)
    }

    @Test
    fun `the caption follows the selected entry`() {
        val state = TodayUiState(
            entry = entry(),
            wallpaperMeta = WallpaperMeta(
                date = "2026-01-01",
                title = "Old Archive Picture",
                sourceUrl = "https://example.org/old.jpg",
                fileName = "old.jpg",
                updatedAt = 1L,
            ),
        )

        assertEquals("2026-10-23 · A New Picture", wallpaperCaption(state))
    }
}
