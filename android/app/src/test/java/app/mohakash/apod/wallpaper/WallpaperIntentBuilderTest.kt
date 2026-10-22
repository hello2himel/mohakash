package app.mohakash.apod.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperIntentBuilderTest {

    @Test
    fun `direct change carries the wallpaper component flag`() {
        val spec = WallpaperIntentBuilder.directChange("app.mohakash.apod")

        assertEquals(WallpaperIntentBuilder.ACTION_CHANGE_LIVE_WALLPAPER, spec.action)
        assertTrue(spec.attachWallpaperComponent)
        assertNull(spec.category)
    }

    @Test
    fun `plain chooser has no extras and the wallpaper category`() {
        val spec = WallpaperIntentBuilder.plainChooser()

        assertEquals(WallpaperIntentBuilder.ACTION_MAIN, spec.action)
        assertEquals(WallpaperIntentBuilder.CATEGORY_WALLPAPER_SERVICE, spec.category)
        assertFalse(spec.attachWallpaperComponent)
    }

    @Test
    fun `attempts go from the direct preview to the safe chooser to settings`() {
        val attempts = WallpaperIntentBuilder.attemptsFor("app.mohakash.apod", directComponentSet = true)

        assertEquals(
            listOf(
                WallpaperIntentBuilder.ACTION_CHANGE_LIVE_WALLPAPER,
                WallpaperIntentBuilder.ACTION_MAIN,
                WallpaperIntentBuilder.ACTION_DISPLAY_SETTINGS,
            ),
            attempts.map { it.action },
        )
    }

    @Test
    fun `without the direct component only safe attempts remain`() {
        val attempts = WallpaperIntentBuilder.attemptsFor("app.mohakash.apod", directComponentSet = false)

        assertEquals(
            listOf(WallpaperIntentBuilder.ACTION_MAIN, WallpaperIntentBuilder.ACTION_DISPLAY_SETTINGS),
            attempts.map { it.action },
        )
        assertTrue(attempts.none { it.attachWallpaperComponent })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank package names are rejected`() {
        WallpaperIntentBuilder.attemptsFor(" ", directComponentSet = true)
    }

    @Test
    fun `service class points at the live wallpaper service`() {
        assertEquals(
            "app.mohakash.apod.wallpaper.MohakashWallpaperService",
            WallpaperIntentBuilder.SERVICE_CLASS,
        )
    }
}
