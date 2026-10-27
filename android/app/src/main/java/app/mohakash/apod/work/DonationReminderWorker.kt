package app.mohakash.apod.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mohakash.apod.MohakashApplication

/** Checks the long donation cooldown when WorkManager decides to run the periodic request. */
class DonationReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MohakashApplication).container
        val settings = container.settingsStore.current()
        if (!settings.donationRemindersEnabled) return Result.success()
        // "New APOD notifications" is an explicit master notification preference.
        if (!settings.notificationsEnabled) return Result.success()
        if (!container.notificationHelper.areNotificationsEnabled()) return Result.success()
        if (!container.donationTracker.offerNotification()) return Result.success()
        container.notificationHelper.notifyDonation()
        return Result.success()
    }
}
