package app.mohakash.apod.domain.wallpaper

import app.mohakash.apod.core.ImageUrlValidator
import app.mohakash.apod.domain.model.ApodEntry

/**
 * The outcome of picking the picture that should be used as live wallpaper.
 */
sealed interface WallpaperChoice {

    /** Keep the picture that is already in place. */
    data object Keep : WallpaperChoice

    /** Download and use this picture. */
    data class Use(val url: String, val reason: String) : WallpaperChoice
}

/**
 * Decides which picture of an entry may be used as live wallpaper.
 *
 * The rules:
 * * an image entry uses `hd_image_url` when HD is preferred, otherwise `image_url`;
 * * a video/iframe entry falls back to its poster (`thumbnail_url`);
 * * an entry without a usable picture (`image_status` is `missing` or `placeholder`) keeps the
 *   previous wallpaper;
 * * an article URL is never used as an image asset.
 */
object WallpaperImageSelector {

    fun select(entry: ApodEntry?, preferHd: Boolean): WallpaperChoice {
        val target = entry ?: return WallpaperChoice.Keep

        // A missing or placeholder picture must never replace a working wallpaper.
        if (!target.imageStatus.isUsable) {
            return WallpaperChoice.Keep
        }

        val candidate = when {
            target.mediaType.isImage -> {
                if (preferHd) {
                    target.hdImageUrl ?: target.imageUrl ?: target.thumbnailUrl
                } else {
                    target.imageUrl ?: target.hdImageUrl ?: target.thumbnailUrl
                }
            }
            // Video and iframe entries only have a poster image.
            else -> target.thumbnailUrl
        }

        if (candidate.isNullOrBlank()) return WallpaperChoice.Keep
        if (!ImageUrlValidator.isValidImageUrl(candidate)) return WallpaperChoice.Keep

        val reason = if (target.mediaType.isImage) "image" else "poster"
        return WallpaperChoice.Use(candidate, reason)
    }

    /**
     * True when the picture for [entry] differs from the one that is currently installed.
     *
     * Both the date and the image URL are compared: the API may publish a new rendering of the
     * same day, and the URL then changes.
     */
    fun hasChanged(currentDate: String?, currentUrl: String?, entry: ApodEntry, preferHd: Boolean): Boolean {
        val choice = select(entry, preferHd)
        if (choice !is WallpaperChoice.Use) return false
        if (currentUrl == null) return true
        if (currentUrl != choice.url) return true
        return currentDate != null && currentDate != entry.date
    }
}
