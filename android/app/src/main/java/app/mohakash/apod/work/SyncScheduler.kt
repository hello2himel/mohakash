package app.mohakash.apod.work

import android.content.Context
import androidx.work.BackoffPolicy
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
 * APOD itself has no fixed publication minute. A single 24-hour job can run before NASA
 * publishes and then miss the picture for a whole day. The worker checks every four hours
 * instead; the metadata request is tiny, the picture is only downloaded when the date or URL
 * changed, and only while the live wallpaper is active. WorkManager may defer any run because
 * of Doze, battery restrictions or process death, so the app also syncs on launch, resume and
 * pull-to-refresh.
 *
 * The worker respects the "Wi-Fi only" setting; the network constraint only requires *some*
 * connection so the metadata can refresh even on mobile data.
 */
object SyncScheduler {

    const val PERIODIC_WORK_NAME = "mohakash-periodic-sync"
    const val ONE_TIME_WORK_NAME = "mohakash-one-time-sync"

    /** Six checks a day: enough to converge without battery-heavy polling. */
    const val SYNC_INTERVAL_HOURS = 4L

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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
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
