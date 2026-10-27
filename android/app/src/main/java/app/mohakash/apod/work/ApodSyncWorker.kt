package app.mohakash.apod.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mohakash.apod.MohakashApplication
import app.mohakash.apod.wallpaper.WallpaperStatus

/**
 * The background job that keeps the metadata — and optionally the live wallpaper — up to date.
 *
 * It runs several times a day (see [SyncScheduler]). Every failure keeps the last successful
 * picture, so the wallpaper is never replaced by an empty or broken image.
 */
class ApodSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? MohakashApplication
            ?: return Result.failure()
        val container = application.container
        val settings = runCatching { container.settingsStore.current() }.getOrNull()

        val engine = container.syncEngine

        // The metadata is always refreshed; the picture is only downloaded when the connection
        // suits the "Wi-Fi only" preference.
        val pictureAllowed = SyncPolicy.pictureDownloadAllowed(
            settings = settings,
            connected = container.networkMonitor.isConnected(),
            unmetered = container.networkMonitor.isUnmetered(),
        )

        // Refreshing the cached metadata is always useful. The picture itself is only replaced
        // while Mohakash is the selected system wallpaper, so skipping onboarding does not
        // silently install a wallpaper the user never chose.
        val updateWallpaper = SyncPolicy.shouldUpdateWallpaper(
            settings = settings,
            isWallpaperActive = WallpaperStatus.isActive(applicationContext),
        )

        val outcome = engine.sync(
            updateWallpaper = updateWallpaper,
            networkAvailable = pictureAllowed,
        )

        return when (outcome) {
            is SyncOutcome.Updated -> Result.success()
            is SyncOutcome.Unchanged -> Result.success()
            is SyncOutcome.WallpaperUnavailable -> Result.success()
            is SyncOutcome.Failed -> {
                // Transient network/API failures stay pending. WorkManager applies the 15-minute
                // exponential backoff and the CONNECTED constraint, so the attempt resumes when
                // connectivity returns rather than waiting for the next 4-hour period.
                Result.retry()
            }
        }
    }
}
