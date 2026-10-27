package app.mohakash.apod.work

import app.mohakash.apod.domain.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep the background wallpaper update safe and reliable.
 */
class SyncPolicyTest {

    @Test
    fun `the wallpaper is updated only when automation is on and the wallpaper is active`() {
        assertTrue(SyncPolicy.shouldUpdateWallpaper(AppSettings(updateAutomatically = true), true))
        assertFalse(SyncPolicy.shouldUpdateWallpaper(AppSettings(updateAutomatically = false), true))
        assertFalse(SyncPolicy.shouldUpdateWallpaper(AppSettings(updateAutomatically = true), false))
    }

    @Test
    fun `carousel mode blocks the regular latest-wallpaper install`() {
        val settings = AppSettings(updateAutomatically = true, carouselMode = true)
        assertFalse(SyncPolicy.shouldUpdateWallpaper(settings, isWallpaperActive = true))
    }

    @Test
    fun `defaults allow automatic updates`() {
        val settings = AppSettings()

        assertTrue(settings.updateAutomatically)
        assertTrue(SyncPolicy.shouldUpdateWallpaper(settings, true))
    }

    @Test
    fun `wi-fi only is off by default so pictures download on mobile data too`() {
        assertFalse(AppSettings().wifiOnly)
        assertTrue(
            SyncPolicy.pictureDownloadAllowed(
                settings = AppSettings(),
                connected = true,
                unmetered = false,
            ),
        )
    }

    @Test
    fun `wi-fi only blocks metered pictures but allows unmetered ones`() {
        val settings = AppSettings(wifiOnly = true)

        assertFalse(SyncPolicy.pictureDownloadAllowed(settings, connected = true, unmetered = false))
        assertTrue(SyncPolicy.pictureDownloadAllowed(settings, connected = true, unmetered = true))
    }

    @Test
    fun `no connection never allows a picture download`() {
        assertFalse(
            SyncPolicy.pictureDownloadAllowed(
                settings = AppSettings(),
                connected = false,
                unmetered = true,
            ),
        )
    }
}
