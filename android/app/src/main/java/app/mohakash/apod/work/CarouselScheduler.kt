package app.mohakash.apod.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * One one-time carousel rotation at a time.
 *
 * The interval is dynamic (24 h / number of valid historical pictures), so a periodic
 * WorkManager request would be wrong. A one-time request with a calculated delay is persisted by
 * WorkManager and rescheduled by the worker after each successful rotation.
 */
object CarouselScheduler {

    const val WORK_NAME = "mohakash-carousel-rotation"

    const val KEY_PRESERVE_NEXT_AT = "preserveNextAt"

    fun scheduleNow(context: Context, preserveNextAt: Long? = null) =
        enqueue(context, 0L, ExistingWorkPolicy.REPLACE, preserveNextAt)

    fun scheduleAfter(context: Context, delayMillis: Long) =
        // The worker calls this while it is running. REPLACE would cancel that running worker;
        // APPEND_OR_REPLACE lets the current run finish and then starts the next rotation.
        enqueue(context, delayMillis.coerceAtLeast(0L), ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** Do not reset the next rotation just because the app was opened. */
    fun ensureScheduled(context: Context) =
        enqueue(context, 0L, ExistingWorkPolicy.KEEP)

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(
        context: Context,
        delayMillis: Long,
        policy: ExistingWorkPolicy,
        preserveNextAt: Long? = null,
    ) {
        val request = OneTimeWorkRequestBuilder<CarouselRotationWorker>()
            .setConstraints(
                Constraints.Builder()
                    // Carousel rotation needs a picture download; while offline the work waits
                    // for connectivity instead of failing and waiting six hours.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(KEY_PRESERVE_NEXT_AT to (preserveNextAt ?: -1L)),
            )
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}
