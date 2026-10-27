package app.mohakash.apod.history

import app.mohakash.apod.carousel.CarouselItem
import app.mohakash.apod.domain.model.ApodEntry
import kotlinx.serialization.Serializable

/** Where a history item came from. Normal sequential APOD browsing is deliberately not recorded. */
@Serializable
enum class HistorySource { RANDOM, CAROUSEL }

@Serializable
data class HistoryItem(
    val date: String,
    val title: String,
    val imageUrl: String?,
    val source: HistorySource,
    val seenAt: Long,
)

fun ApodEntry.toRandomHistory(seenAt: Long): HistoryItem = HistoryItem(
    date = date,
    title = title.ifBlank { date },
    imageUrl = displayImageUrl ?: thumbnailUrl,
    source = HistorySource.RANDOM,
    seenAt = seenAt,
)

fun CarouselItem.toCarouselHistory(seenAt: Long): HistoryItem = HistoryItem(
    date = date,
    title = title.ifBlank { date },
    imageUrl = imageUrl,
    source = HistorySource.CAROUSEL,
    seenAt = seenAt,
)
