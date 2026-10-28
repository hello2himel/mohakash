package app.mohakash.apod.data.repository

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.domain.wallpaper.WallpaperChoice
import app.mohakash.apod.domain.wallpaper.WallpaperImageSelector

/**
 * Finds the newest displayable picture at or before a latest entry.
 *
 * APOD occasionally publishes a video. The feed may still show it, but the onboarding preview and
 * live wallpaper must not try to use an unusable video as the wallpaper. In that case this resolver
 * walks backwards through the cached archive index and returns the newest previous image.
 */
class LatestImageResolver(
    private val archiveIndexRepository: ArchiveIndexRepository,
    private val apodRepository: ApodRepository,
) {
    suspend fun resolve(entry: ApodEntry, settings: AppSettings): ApodEntry? {
        if (isUsable(entry, settings)) return entry
        val index = archiveIndexRepository.ensureLoaded(allowNetwork = true) ?: return null
        val candidates = index.asSequence()
            .filter { candidate ->
                candidate.date.length == 10 &&
                    candidate.date < entry.date &&
                    candidate.mediaType.isImage &&
                    (candidate.imageUrl != null || candidate.thumbnailUrl != null)
            }
            .take(MAX_LOOKBACK)
        for (candidate in candidates) {
            val full = runCatching { apodRepository.fetchEntry(candidate.date) }.getOrNull()
            if (full != null && isUsable(full, settings)) return full
        }
        return null
    }

    fun isUsable(entry: ApodEntry, settings: AppSettings): Boolean =
        entry.mediaType.isImage &&
            WallpaperImageSelector.select(entry, settings.preferHd, settings.hideVideos) is WallpaperChoice.Use

    private companion object {
        const val MAX_LOOKBACK = 12
    }
}
