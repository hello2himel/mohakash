package app.mohakash.apod.domain.wallpaper

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which picture may be used as the live wallpaper.
 */
class WallpaperImageSelectorTest {

    private fun entry(
        mediaType: MediaType = MediaType.IMAGE,
        imageStatus: ImageStatus = ImageStatus.OK,
        imageUrl: String? = "https://example.org/photo.jpg?w=1600",
        hdImageUrl: String? = "https://example.org/photo.jpg?w=2400",
        thumbnailUrl: String? = "https://example.org/photo.jpg?w=480",
        apodUrl: String? = "https://science.nasa.gov/image-article/apod-2026-october-22/",
        date: String = "2026-10-22",
    ) = ApodEntry(
        date = date,
        title = "A title",
        explanation = "Text",
        mediaType = mediaType,
        imageUrl = imageUrl,
        hdImageUrl = hdImageUrl,
        thumbnailUrl = thumbnailUrl,
        apodUrl = apodUrl,
        copyright = null,
        credit = null,
        altText = null,
        source = "NASA APOD",
        sourceId = null,
        sourceMediaType = null,
        imageStatus = imageStatus,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )

    @Test
    fun `an image entry uses the normal picture`() {
        val choice = WallpaperImageSelector.select(entry(), preferHd = false)

        assertTrue(choice is WallpaperChoice.Use)
        assertEquals("https://example.org/photo.jpg?w=1600", (choice as WallpaperChoice.Use).url)
        assertEquals("image", choice.reason)
    }

    @Test
    fun `an image entry uses the hd picture when it is preferred`() {
        val choice = WallpaperImageSelector.select(entry(), preferHd = true)

        assertEquals("https://example.org/photo.jpg?w=2400", (choice as WallpaperChoice.Use).url)
    }

    @Test
    fun `the hd preference falls back to the normal picture`() {
        val choice = WallpaperImageSelector.select(
            entry(hdImageUrl = null),
            preferHd = true,
        )

        assertEquals("https://example.org/photo.jpg?w=1600", (choice as WallpaperChoice.Use).url)
    }

    @Test
    fun `the normal picture falls back to the thumbnail`() {
        val choice = WallpaperImageSelector.select(
            entry(imageUrl = null, hdImageUrl = null),
            preferHd = false,
        )

        assertEquals("https://example.org/photo.jpg?w=480", (choice as WallpaperChoice.Use).url)
    }

    @Test
    fun `a video entry falls back to the poster`() {
        val choice = WallpaperImageSelector.select(
            entry(mediaType = MediaType.VIDEO),
            preferHd = false,
        )

        assertTrue(choice is WallpaperChoice.Use)
        assertEquals("https://example.org/photo.jpg?w=480", (choice as WallpaperChoice.Use).url)
        assertEquals("poster", choice.reason)
    }

    @Test
    fun `an iframe entry falls back to the poster`() {
        val choice = WallpaperImageSelector.select(
            entry(mediaType = MediaType.OTHER),
            preferHd = false,
        )

        assertEquals("https://example.org/photo.jpg?w=480", (choice as WallpaperChoice.Use).url)
    }

    @Test
    fun `a video without a poster keeps the previous wallpaper`() {
        val choice = WallpaperImageSelector.select(
            entry(mediaType = MediaType.VIDEO, thumbnailUrl = null),
            preferHd = false,
        )

        assertEquals(WallpaperChoice.Keep, choice)
    }

    @Test
    fun `a missing picture keeps the previous wallpaper`() {
        val choice = WallpaperImageSelector.select(
            entry(imageStatus = ImageStatus.MISSING, imageUrl = null, hdImageUrl = null, thumbnailUrl = null),
            preferHd = false,
        )

        assertEquals(WallpaperChoice.Keep, choice)
    }

    @Test
    fun `a placeholder picture keeps the previous wallpaper`() {
        val choice = WallpaperImageSelector.select(
            entry(imageStatus = ImageStatus.PLACEHOLDER),
            preferHd = false,
        )

        assertEquals(WallpaperChoice.Keep, choice)
    }

    @Test
    fun `the article url is never used as an image`() {
        val choice = WallpaperImageSelector.select(
            entry(imageUrl = null, hdImageUrl = null, thumbnailUrl = null),
            preferHd = false,
        )

        assertEquals(WallpaperChoice.Keep, choice)
    }

    @Test
    fun `a page url in the image field is rejected`() {
        val choice = WallpaperImageSelector.select(
            entry(imageUrl = "https://science.nasa.gov/image-article/apod-2026-october-22/"),
            preferHd = false,
        )

        assertEquals(WallpaperChoice.Keep, choice)
    }

    @Test
    fun `a missing entry keeps the previous wallpaper`() {
        assertEquals(WallpaperChoice.Keep, WallpaperImageSelector.select(null, preferHd = true))
    }

    @Test
    fun `a changed url is detected`() {
        val current = entry()

        assertTrue(
            WallpaperImageSelector.hasChanged(
                currentDate = "2026-10-21",
                currentUrl = "https://example.org/photo.jpg?w=1600",
                entry = current,
                preferHd = false,
            ),
        )
    }

    @Test
    fun `an unchanged picture is not downloaded again`() {
        val current = entry()

        assertFalse(
            WallpaperImageSelector.hasChanged(
                currentDate = "2026-10-22",
                currentUrl = "https://example.org/photo.jpg?w=1600",
                entry = current,
                preferHd = false,
            ),
        )
    }

    @Test
    fun `switching to hd counts as a change`() {
        val current = entry()

        assertTrue(
            WallpaperImageSelector.hasChanged(
                currentDate = "2026-10-22",
                currentUrl = "https://example.org/photo.jpg?w=1600",
                entry = current,
                preferHd = true,
            ),
        )
    }

    @Test
    fun `a missing current picture counts as a change`() {
        assertTrue(
            WallpaperImageSelector.hasChanged(
                currentDate = null,
                currentUrl = null,
                entry = entry(),
                preferHd = false,
            ),
        )
    }
}
