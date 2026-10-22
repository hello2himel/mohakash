package app.mohakash.apod.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType

/**
 * A cached copy of a full APOD record.
 *
 * Used for the entries that were fetched for a year file and for the last successful
 * `latest.json` record so the app can render them while offline.
 */
@Entity(tableName = "cached_entries")
data class CachedEntryEntity(
    @PrimaryKey val date: String,
    val title: String,
    val explanation: String,
    val mediaType: String,
    val imageUrl: String?,
    val hdImageUrl: String?,
    val thumbnailUrl: String?,
    val apodUrl: String?,
    val copyright: String?,
    val credit: String?,
    val altText: String?,
    val source: String?,
    val sourceId: String?,
    val sourceMediaType: String?,
    val imageStatus: String,
    val updatedAt: String?,
    val cachedAt: Long,
) {
    fun toDomain(): ApodEntry = ApodEntry(
        date = date,
        title = title,
        explanation = explanation,
        mediaType = MediaType.from(mediaType),
        imageUrl = imageUrl,
        hdImageUrl = hdImageUrl,
        thumbnailUrl = thumbnailUrl,
        apodUrl = apodUrl,
        copyright = copyright,
        credit = credit,
        altText = altText,
        source = source,
        sourceId = sourceId,
        sourceMediaType = sourceMediaType,
        imageStatus = ImageStatus.from(imageStatus),
        updatedAt = updatedAt,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )

    companion object {
        fun from(entry: ApodEntry, cachedAt: Long): CachedEntryEntity = CachedEntryEntity(
            date = entry.date,
            title = entry.title,
            explanation = entry.explanation,
            mediaType = entry.mediaType.name,
            imageUrl = entry.imageUrl,
            hdImageUrl = entry.hdImageUrl,
            thumbnailUrl = entry.thumbnailUrl,
            apodUrl = entry.apodUrl,
            copyright = entry.copyright,
            credit = entry.credit,
            altText = entry.altText,
            source = entry.source,
            sourceId = entry.sourceId,
            sourceMediaType = entry.sourceMediaType,
            imageStatus = entry.imageStatus.name,
            updatedAt = entry.updatedAt,
            cachedAt = cachedAt,
        )
    }
}
