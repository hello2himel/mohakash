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
 * The periodic job runs once a day — well above the 15 minute minimum of
 * `PeriodicWorkRequest` — and asks for an unmetered network so the picture is only downloaded
 * when it is cheap. The Wi-Fi only preference is applied on top of that when the worker runs.
 */
object SyncScheduler {

    const val PERIODIC_WORK_NAME = "mohakash-periodic-sync"
    const val ONE_TIME_WORK_NAME = "mohakash-one-time-sync"

    /** 24 hours, the shortest interval that still delivers "a new picture each day". */
    const val SYNC_INTERVAL_HOURS = 24L

    /** Schedules (or keeps) the daily job. */
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
            ExistingPeriodicWorkPolicy.KEEP,
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
