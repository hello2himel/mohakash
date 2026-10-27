package app.mohakash.apod.carousel

import android.content.Context
import app.mohakash.apod.core.NetworkMonitor
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.history.HistoryRepository
import app.mohakash.apod.wallpaper.WallpaperStatus
import app.mohakash.apod.work.ApodSyncEngine
import app.mohakash.apod.work.CarouselScheduler
import app.mohakash.apod.work.InstallOutcome
import java.util.TimeZone
import kotlin.random.Random

/** Result of one carousel rotation attempt. */
sealed interface CarouselOutcome {
    data class Updated(val item: CarouselItem) : CarouselOutcome
    data object Disabled : CarouselOutcome
    data object InactiveWallpaper : CarouselOutcome
    data object NoImages : CarouselOutcome
    data object Failed : CarouselOutcome
}

/**
 * Orchestrates the carousel: discover the current date's valid photographs, pick one at random,
 * install it through the existing atomic wallpaper engine, and schedule the next rotation.
 *
 * The controller is called by [app.mohakash.apod.work.CarouselRotationWorker]. It deliberately
 * does not poll: exactly one one-time WorkManager job is kept in the queue.
 */
class CarouselController(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val discovery: CarouselDiscovery,
    private val selectionStore: CarouselSelectionStore,
    private val nextRunStore: CarouselNextRunStore,
    private val syncEngine: ApodSyncEngine,
    private val wallpaperStorage: WallpaperStorage,
    private val historyRepository: HistoryRepository,
    private val isWallpaperActive: () -> Boolean = { WallpaperStatus.isActive(context) },
    private val isConnected: () -> Boolean = { NetworkMonitor(context).isConnected() },
    private val isUnmetered: () -> Boolean = { NetworkMonitor(context).isUnmetered() },
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
    private val random: Random = Random.Default,
) {

    /** Runs one rotation, schedules the next interval (or a retry), and returns what happened. */
    suspend fun rotate(): CarouselOutcome {
        val settings = settingsStore.current()
        if (!settings.carouselMode || !settings.updateAutomatically) {
            CarouselScheduler.cancel(context)
            nextRunStore.clear()
            return CarouselOutcome.Disabled
        }
        if (!isWallpaperActive()) {
            scheduleNext(INACTIVE_RETRY_MILLIS)
            return CarouselOutcome.InactiveWallpaper
        }
        if (!isConnected()) {
            // The queued worker has a CONNECTED constraint: it stays pending without spinning
            // and runs as soon as a network becomes available.
            scheduleNext(0L)
            return CarouselOutcome.Failed
        }
        if (settings.wifiOnly && !isUnmetered()) {
            // Never spend mobile data when the user asked for Wi-Fi-only downloads.
            scheduleNext(FAILED_RETRY_MILLIS)
            return CarouselOutcome.Failed
        }

        val collection = discovery.load()
        if (collection == null || collection.items.isEmpty()) {
            scheduleNext(EMPTY_RETRY_MILLIS)
            return CarouselOutcome.NoImages
        }

        val monthDay = collection.monthDay
        val previous = selectionStore.read()?.takeIf { it.monthDay == monthDay }
        var remaining = collection.items
        repeat(minOf(MAX_INSTALL_ATTEMPTS, remaining.size)) {
            val item = CarouselMath.next(remaining, previous?.lastSelectedDate, random)
                ?: return@repeat
            val meta = wallpaperStorage.readMeta()
            val outcome = if (meta?.date == item.date && meta.sourceUrl == item.imageUrl) {
                // The selected carousel picture is already on disk. Never re-download it just to
                // advance the rotation clock.
                InstallOutcome.UNCHANGED
            } else {
                runCatching {
                    syncEngine.install(
                        entry = item.toApodEntry(),
                        preferHd = settings.preferHd,
                        hideVideos = true,
                        notify = false,
                    )
                }.getOrDefault(InstallOutcome.FAILED)
            }
            if (outcome == InstallOutcome.INSTALLED || outcome == InstallOutcome.UNCHANGED) {
                val interval = CarouselMath.intervalMillis(collection.items.size)
                selectionStore.write(
                    CarouselSelection(
                        monthDay = monthDay,
                        lastSelectedDate = item.date,
                        lastRotationAt = now(),
                        intervalMillis = interval ?: 0L,
                    ),
                )
                historyRepository.recordCarousel(item)
                if (interval != null && interval > 0L) {
                    scheduleNext(interval, interval)
                } else {
                    CarouselScheduler.cancel(context)
                    nextRunStore.clear()
                }
                return CarouselOutcome.Updated(item)
            }
            remaining = remaining.filterNot { it.date == item.date }
        }

        scheduleNext(FAILED_RETRY_MILLIS)
        return CarouselOutcome.Failed
    }

    /** Called when the user enables the mode: start a rotation as soon as WorkManager allows. */
    fun enable() {
        CarouselScheduler.scheduleNow(context)
        nextRunStore.write(CarouselNextRun(nextAt = now(), intervalMillis = 0L))
    }

    /** Called when the user disables the mode: remove the carousel-only job. */
    fun disable() {
        CarouselScheduler.cancel(context)
        nextRunStore.clear()
    }

    /** Called on app launch/resume; keeps an existing queued rotation instead of resetting it. */
    fun ensureScheduled() {
        CarouselScheduler.ensureScheduled(context)
    }

    /**
     * Gives the system picker a moment to finish before the first carousel rotation checks
     * whether Mohakash is actually the active wallpaper.
     */
    fun scheduleFirstRotation() {
        scheduleNext(FIRST_ROTATION_DELAY_MILLIS)
    }

    private fun scheduleNext(delayMillis: Long, intervalMillis: Long = 0L) {
        val delay = delayMillis.coerceAtLeast(0L)
        CarouselScheduler.scheduleAfter(context, delay)
        nextRunStore.write(CarouselNextRun(nextAt = now() + delay, intervalMillis = intervalMillis))
    }

    /** Tests can use this to check settings without touching Android storage. */
    suspend fun settings(): AppSettings = settingsStore.current()

    companion object {
        const val MAX_INSTALL_ATTEMPTS = 3
        const val INACTIVE_RETRY_MILLIS: Long = 6L * 60L * 60L * 1000L
        const val EMPTY_RETRY_MILLIS: Long = 6L * 60L * 60L * 1000L
        const val FAILED_RETRY_MILLIS: Long = 30L * 60L * 1000L
        const val FIRST_ROTATION_DELAY_MILLIS: Long = 60L * 1000L
    }
}
