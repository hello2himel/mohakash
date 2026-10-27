package app.mohakash.apod.history

import app.mohakash.apod.carousel.CarouselItem
import app.mohakash.apod.domain.model.ApodEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted history of intentional discovery actions.
 *
 * Only random picks and carousel wallpaper choices are recorded; ordinary APOD feed browsing is
 * not. The same date/source is moved to the top instead of duplicated, so a single-image carousel
 * cannot flood the list.
 */
class HistoryRepository(
    private val store: HistoryStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _items = MutableStateFlow(store.read())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    @Synchronized
    fun recordRandom(entry: ApodEntry) {
        record(entry.toRandomHistory(now()))
    }

    @Synchronized
    fun recordCarousel(item: CarouselItem) {
        record(item.toCarouselHistory(now()))
    }

    @Synchronized
    fun record(item: HistoryItem) {
        val deduped = _items.value.filterNot {
            it.date == item.date && it.source == item.source
        }
        val next = (listOf(item) + deduped).take(MAX_ITEMS)
        _items.value = next
        store.write(next)
    }

    private companion object {
        const val MAX_ITEMS = 100
    }
}
