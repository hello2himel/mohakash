package app.mohakash.apod.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mohakash.apod.MohakashApplication

/**
 * Rotates the active live wallpaper through historical APOD photographs from today's month/day.
 *
 * All scheduling decisions live in [app.mohakash.apod.carousel.CarouselController]; this worker
 * only supplies WorkManager's execution opportunity.
 */
class CarouselRotationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? MohakashApplication
            ?: return Result.success()
        return runCatching {
            application.container.carouselController.rotate()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
