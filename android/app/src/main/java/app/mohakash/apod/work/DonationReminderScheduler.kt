package app.mohakash.apod.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * A long, low-frequency donation reminder check.
 *
 * WorkManager persists the unique periodic request across process death and reboot. It is
 * intentionally not exact: the worker re-checks engagement and cooldowns when it runs.
 */
object DonationReminderScheduler {
    const val WORK_NAME = "mohakash-donation-reminder"
    private const val INITIAL_DELAY_DAYS = 7L
    private const val INTERVAL_DAYS = 14L

    fun schedule(context: Context, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<DonationReminderWorker>(
            INTERVAL_DAYS,
            TimeUnit.DAYS,
        )
            .setInitialDelay(INITIAL_DELAY_DAYS, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP preserves the initial delay on ordinary launches. REPLACE is used only when
            // the user explicitly turns reminders back on, so a just-cancelled request cannot
            // race the new one.
            if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}
