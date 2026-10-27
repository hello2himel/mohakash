package app.mohakash.apod.carousel

import app.mohakash.apod.data.repository.ArchiveIndexRepository
import app.mohakash.apod.data.repository.LatestEntrySource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.TimeZone

/**
 * Discovers and validates the historical pictures for the current local month/day.
 *
 * Metadata comes from the existing archive index (already cached by the feed); validation uses
 * HEAD/ranged requests, not full image downloads. A valid cached collection is reused for a few
 * hours, and temporary validation/network failures never permanently delete old valid entries.
 */
class CarouselDiscovery(
    private val archiveIndexRepository: ArchiveIndexRepository,
    private val validator: CarouselImageValidator,
    private val collectionStore: CarouselCollectionStore,
    private val latestSource: LatestEntrySource? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
) {

    suspend fun load(forceRefresh: Boolean = false): CarouselCollection? {
        val monthDay = CarouselMath.monthDay(now(), timeZone())
        val cached = collectionStore.read()
        val cachedForToday = cached?.takeIf { it.monthDay == monthDay }
        val latestCachedToday = runCatching { latestSource?.cachedLatest() }
            .getOrNull()
            ?.date
            ?.takeIf { it.length == 10 && it.substring(5) == monthDay }
        val latestIsMissing = latestCachedToday != null &&
            cachedForToday?.items?.none { it.date == latestCachedToday } != false
        if (!forceRefresh && cachedForToday != null && !latestIsMissing &&
            now() - cachedForToday.validatedAt in 0..CACHE_TTL_MILLIS
        ) {
            return cachedForToday
        }

        val index = archiveIndexRepository.ensureLoaded(allowNetwork = true)
            ?: return cachedForToday
        // The archive index is cached for a day; add the latest record explicitly so today's
        // newly published APOD can join the carousel even before the index is refreshed.
        val latest = runCatching {
            latestSource?.fetchLatest(forceNetwork = true)
        }.getOrNull() ?: runCatching { latestSource?.cachedLatest() }.getOrNull()
        val latestItem = latest?.takeIf {
            it.date.length == 10 &&
                it.date.substring(5) == monthDay &&
                it.mediaType.isImage &&
                it.supportsWallpaper &&
                !it.displayImageUrl.isNullOrBlank()
        }?.let { entry ->
            CarouselItem(
                date = entry.date,
                title = entry.title.ifBlank { entry.date },
                imageUrl = entry.displayImageUrl.orEmpty(),
                credit = entry.displayCredit,
                apodUrl = entry.apodUrl,
            )
        }
        val candidates = (CarouselMath.candidates(index, monthDay) + listOfNotNull(latestItem))
            .distinctBy { it.date }
            .sortedByDescending { it.date }
        if (candidates.isEmpty()) {
            val empty = CarouselCollection(monthDay, now())
            if (cachedForToday == null || cachedForToday.items.isEmpty()) collectionStore.write(empty)
            return cachedForToday ?: empty
        }

        val semaphore = Semaphore(MAX_CONCURRENT_VALIDATIONS)
        val checked = coroutineScope {
            candidates.map { item ->
                async {
                    semaphore.withPermit {
                        item to validator.validate(item.imageUrl)
                    }
                }
            }.awaitAll()
        }
        val freshValid = checked.mapNotNull { (item, result) ->
            item.takeIf { result == CarouselValidation.Valid }
        }
        val temporary = checked.any { (_, result) -> result == CarouselValidation.TemporaryFailure }

        // Union with the previous valid collection so a single temporary failure does not make
        // an otherwise usable historical picture disappear for the rest of the day.
        val merged = (freshValid + cachedForToday?.items.orEmpty())
            .distinctBy { it.date }
            .sortedByDescending { it.date }
        val collection = CarouselCollection(
            monthDay = monthDay,
            validatedAt = now(),
            items = merged,
            hadTemporaryFailures = temporary || freshValid.isEmpty(),
        )
        collectionStore.write(collection)
        return collection
    }

    companion object {
        const val CACHE_TTL_MILLIS: Long = 6L * 60L * 60L * 1000L
        const val MAX_CONCURRENT_VALIDATIONS: Int = 4
    }
}
