package app.mohakash.apod.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mohakash.apod.carousel.CarouselMath
import app.mohakash.apod.carousel.CarouselNextRun
import app.mohakash.apod.carousel.CarouselNextRunStore
import app.mohakash.apod.carousel.CarouselSelection
import app.mohakash.apod.carousel.CarouselSelectionStore
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.history.HistoryItem
import app.mohakash.apod.history.HistoryRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.TimeZone

/** The carousel's next rotation, shown as a small progress header in History. */
data class NextWallpaperUi(
    /** Null while the first rotation is still being prepared or the schedule is unknown. */
    val progress: Float?,
    val remainingMillis: Long,
)

/** Exposes discovery history and the live carousel countdown to the History tab. */
class HistoryViewModel(
    historyRepository: HistoryRepository,
    private val settingsStore: SettingsStore,
    private val selectionStore: CarouselSelectionStore,
    private val nextRunStore: CarouselNextRunStore,
    private val isWallpaperActive: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> = historyRepository.items

    private val _nextWallpaper = MutableStateFlow<NextWallpaperUi?>(null)
    val nextWallpaper: StateFlow<NextWallpaperUi?> = _nextWallpaper.asStateFlow()

    private var carouselEnabled = false
    private var selection: CarouselSelection? = null
    private var nextRun: CarouselNextRun? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                carouselEnabled = settings.carouselMode && settings.updateAutomatically
                publish()
            }
        }
        viewModelScope.launch {
            var tick = 0
            while (currentCoroutineContext().isActive) {
                if (tick % REFRESH_SELECTION_EVERY_TICKS == 0) {
                    selection = runCatching { selectionStore.read() }.getOrNull()
                    nextRun = runCatching { nextRunStore.read() }.getOrNull()
                }
                publish()
                tick += 1
                delay(TICK_MILLIS)
            }
        }
    }

    private fun publish() {
        if (!carouselEnabled || !isWallpaperActive()) {
            _nextWallpaper.value = null
            return
        }
        val current = selection
        val total = current?.intervalMillis ?: 0L
        val last = current?.lastRotationAt
        val timestamp = now()
        val today = CarouselMath.monthDay(timestamp, timeZone())
        val scheduledNext = nextRun?.takeIf { it.intervalMillis > 0L }?.nextAt
        if (scheduledNext != null) {
            // The scheduled run is authoritative. This covers retries and the waiting-for-Wi-Fi
            // case instead of showing a stale selection countdown.
            val remaining = (scheduledNext - timestamp).coerceAtLeast(0L)
            val progress = if (scheduledNext > timestamp) {
                (1f - remaining.toFloat() / nextRun!!.intervalMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            _nextWallpaper.value = NextWallpaperUi(progress = progress, remainingMillis = remaining)
            return
        }
        if (total <= 0L || last == null || current?.monthDay != today) {
            _nextWallpaper.value = NextWallpaperUi(progress = null, remainingMillis = 0L)
            return
        }
        val elapsed = (timestamp - last).coerceIn(0L, total)
        val remaining = (total - elapsed).coerceAtLeast(0L)
        _nextWallpaper.value = NextWallpaperUi(
            progress = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            remainingMillis = remaining,
        )
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val REFRESH_SELECTION_EVERY_TICKS = 5
    }
}
