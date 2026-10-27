package app.mohakash.apod.carousel

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import kotlinx.serialization.Serializable

/** One validated APOD photograph that may be shown in the date-matched carousel. */
@Serializable
data class CarouselItem(
    val date: String,
    val title: String,
    val imageUrl: String,
    val credit: String? = null,
    val apodUrl: String? = null,
) {
    /** A minimal full record for the existing wallpaper installer. */
    fun toApodEntry(): ApodEntry = ApodEntry(
        date = date,
        title = title,
        explanation = "",
        mediaType = MediaType.IMAGE,
        imageUrl = imageUrl,
        hdImageUrl = null,
        thumbnailUrl = null,
        apodUrl = apodUrl,
        copyright = credit,
        credit = null,
        altText = null,
        source = "NASA APOD",
        sourceId = null,
        sourceMediaType = "image",
        imageStatus = ImageStatus.OK,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )
}

/** The validated collection for one month/day, cached locally. */
@Serializable
data class CarouselCollection(
    val monthDay: String,
    val validatedAt: Long,
    val items: List<CarouselItem> = emptyList(),
    /** Some candidates could not be checked; retry on the next rotation instead of caching forever. */
    val hadTemporaryFailures: Boolean = false,
)

/** The cross-run selection state. The date key prevents carrying a choice into another day. */
@Serializable
data class CarouselSelection(
    val monthDay: String? = null,
    val lastSelectedDate: String? = null,
    val lastRotationAt: Long? = null,
    val intervalMillis: Long = 0L,
)
