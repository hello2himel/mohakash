package app.mohakash.apod.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the background synchronisation.
 *
 * The periodic job checks for a new APOD several times a day. A single 24-hour job can run
 * before NASA publishes the next picture and then miss it for a whole day, so the worker gets
 * four chances per day instead. The metadata request is tiny; the picture itself is only
 * downloaded when the date or URL actually changed, and only while the live wallpaper is active.
 *
 * The worker respects the "Wi-Fi only" setting; the network constraint only requires *some*
 * connection so the metadata can refresh even on mobile data.
 */
object SyncScheduler {

    const val PERIODIC_WORK_NAME = "mohakash-periodic-sync"
    const val ONE_TIME_WORK_NAME = "mohakash-one-time-sync"

    /** Check four times a day so a delayed or early run cannot skip a day. */
    const val SYNC_INTERVAL_HOURS = 6L

    /** Schedules (or updates) the periodic job. */
    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ApodSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            // UPDATE keeps the original enqueue time but replaces the old 24-hour request with
            // the current interval/constraints when an existing installation updates the app.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Runs a sync right now — used on the first launch and by the manual refresh. */
    fun runOnce(context: Context, expedited: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ApodSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Stops the daily job — used when the user switches automatic updates off. */
    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
