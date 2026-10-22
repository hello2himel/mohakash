package app.mohakash.apod.work

import android.content.Context
import app.mohakash.apod.domain.model.AppSettings

/**
 * Applies the sync preferences to the WorkManager schedule.
 */
class SyncController(private val context: Context) {

    /** Starts or stops the daily job so it matches the settings. */
    fun apply(settings: AppSettings) {
        if (settings.updateAutomatically) {
            SyncScheduler.schedulePeriodic(context)
        } else {
            SyncScheduler.cancelPeriodic(context)
        }
    }

    /** Runs a synchronisation right now. */
    fun runOnce() = SyncScheduler.runOnce(context)
}
