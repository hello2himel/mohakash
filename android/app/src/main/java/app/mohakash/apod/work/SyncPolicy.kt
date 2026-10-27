package app.mohakash.apod.work

import app.mohakash.apod.domain.model.AppSettings

/**
 * Pure decisions used by [ApodSyncWorker].
 *
 * They live in their own object so the two rules that keep the automatic wallpaper update
 * trustworthy can be unit tested without Android framework types.
 */
object SyncPolicy {

    /**
     * The live wallpaper may only be replaced automatically when the user allowed automatic
     * updates **and** Mohakash is actually the selected system wallpaper. Skipping onboarding or
     * choosing another wallpaper must not silently install a picture.
     */
    fun shouldUpdateWallpaper(settings: AppSettings?, isWallpaperActive: Boolean): Boolean =
        (settings?.updateAutomatically ?: true) &&
            isWallpaperActive &&
            settings?.carouselMode != true

    /**
     * Whether a picture may be downloaded with the current connection.
     *
     * "Wi-Fi only" is off by default; when it is on, metered connections are refused. Metadata
     * refresh is handled separately and is never blocked by this rule.
     */
    fun pictureDownloadAllowed(
        settings: AppSettings?,
        connected: Boolean,
        unmetered: Boolean,
    ): Boolean {
        if (!connected) return false
        val wifiOnly = settings?.wifiOnly ?: false
        return !wifiOnly || unmetered
    }
}
