package app.mohakash.apod.work

import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.storage.ImageDownloader
import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.wallpaper.WallpaperChoice
import app.mohakash.apod.domain.wallpaper.WallpaperImageSelector
import app.mohakash.apod.wallpaper.WallpaperSignal
import java.io.File

/**
 * The result of one synchronisation run.
 */
sealed interface SyncOutcome {

    /** The metadata was refreshed and a new wallpaper picture was installed. */
    data class Updated(val entry: ApodEntry) : SyncOutcome

    /** The metadata was refreshed but the picture did not change, so nothing was downloaded. */
    data class Unchanged(val entry: ApodEntry) : SyncOutcome

    /** The metadata was refreshed but the new entry cannot be used as a wallpaper. */
    data class WallpaperUnavailable(val entry: ApodEntry, val reason: String) : SyncOutcome

    /** Something went wrong; the previous picture and the cached metadata are kept. */
    data class Failed(val error: Throwable) : SyncOutcome
}

/**
 * The result of installing one picture as the live wallpaper.
 */
enum class InstallOutcome {
    /** A new picture was downloaded and stored. */
    INSTALLED,

    /** The very same picture is already installed — nothing was downloaded. */
    UNCHANGED,

    /** The entry has no picture that may be used as a wallpaper. */
    NOT_USABLE,

    /** The download or the storage failed; the previous picture is kept. */
    FAILED,
}

/**
 * The heart of the synchronisation.
 *
 * It is deliberately free of Android framework types (apart from the storage classes) so it can
 * be unit tested with fakes.
 *
 * The guarantees:
 * * the image is only downloaded when the date **or** the image URL changed;
 * * the picture is validated before it is downloaded;
 * * the metadata and the picture are swapped atomically;
 * * every failure keeps the last successful picture.
 */
class ApodSyncEngine(
    private val apodRepository: ApodRepository,
    private val settingsStore: SettingsStore,
    private val wallpaperStorage: WallpaperStorage,
    private val imageDownloader: ImageDownloader,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Runs one synchronisation.
     *
     * @param updateWallpaper whether the wallpaper picture may be replaced.
     * @param networkAvailable whether a download is currently possible.
     */
    suspend fun sync(
        updateWallpaper: Boolean = true,
        networkAvailable: Boolean = true,
    ): SyncOutcome {
        val entry = try {
            apodRepository.fetchLatest()
        } catch (error: Exception) {
            return SyncOutcome.Failed(error)
        }

        if (!updateWallpaper) {
            return SyncOutcome.Unchanged(entry)
        }

        val settings = settingsStore.current()
        val choice = WallpaperImageSelector.select(entry, settings.preferHd)
        if (choice !is WallpaperChoice.Use) {
            // Video without a poster, a missing or placeholder picture: keep the old wallpaper.
            return SyncOutcome.WallpaperUnavailable(entry, "The new entry has no picture that can be used as wallpaper.")
        }

        val current = wallpaperStorage.readMeta()
        if (current != null && current.sourceUrl == choice.url && current.date == entry.date) {
            // Nothing changed: skip the download completely.
            return SyncOutcome.Unchanged(entry)
        }

        if (!networkAvailable) {
            return SyncOutcome.WallpaperUnavailable(entry, "No usable connection for the picture.")
        }

        return when (install(entry, settings.preferHd, choice.url)) {
            InstallOutcome.INSTALLED -> SyncOutcome.Updated(entry)
            InstallOutcome.UNCHANGED -> SyncOutcome.Unchanged(entry)
            InstallOutcome.NOT_USABLE -> SyncOutcome.WallpaperUnavailable(entry, "The new entry has no picture that can be used as wallpaper.")
            InstallOutcome.FAILED -> SyncOutcome.Failed(IllegalStateException("The picture could not be stored."))
        }
    }

    /**
     * Downloads the picture of [entry] and stores it as the new live wallpaper.
     *
     * The current picture is only replaced after the new one was downloaded and validated, so a
     * failure never leaves the device without a wallpaper.
     */
    suspend fun install(
        entry: ApodEntry,
        preferHd: Boolean,
        imageUrl: String? = null,
    ): InstallOutcome {
        val choice = WallpaperImageSelector.select(entry, preferHd)
        val url = imageUrl ?: (choice as? WallpaperChoice.Use)?.url
        if (url.isNullOrBlank()) return InstallOutcome.NOT_USABLE

        val target = File(wallpaperStorage.storageDirectory, DOWNLOAD_FILE_NAME)
        val downloaded = imageDownloader.download(url, target)
        if (downloaded.isFailure) {
            target.delete()
            return InstallOutcome.FAILED
        }

        val meta = WallpaperMeta(
            date = entry.date,
            title = entry.title,
            credit = entry.displayCredit,
            sourceUrl = url,
            fileName = "",
            updatedAt = now(),
        )
        val committed = wallpaperStorage.commit(downloaded.getOrThrow(), meta)
        target.delete()
        if (committed.isFailure) return InstallOutcome.FAILED

        WallpaperSignal.notifyChanged()
        return InstallOutcome.INSTALLED
    }

    companion object {
        const val DOWNLOAD_FILE_NAME = "incoming-download.tmp"
    }
}
