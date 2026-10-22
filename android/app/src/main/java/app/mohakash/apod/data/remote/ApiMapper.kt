package app.mohakash.apod.data.remote

import app.mohakash.apod.data.remote.dto.ApodRecordDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexEntryDto
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType

/**
 * Maps the raw API payloads onto the domain model.
 *
 * The rules are deliberately conservative:
 * * a missing media type becomes [MediaType.OTHER];
 * * a missing `image_status` is derived from the presence of an image URL;
 * * blank strings become `null` so the UI can omit them instead of showing an empty line.
 */
object ApiMapper {

    fun toDomain(dto: ApodRecordDto): ApodEntry {
        val imageUrl = dto.imageUrl.clean()
        val hdImageUrl = dto.hdImageUrl.clean()
        val thumbnailUrl = dto.thumbnailUrl.clean()
        val mediaType = MediaType.from(dto.mediaType)
        val imageStatus = resolveImageStatus(dto.imageStatus, imageUrl, hdImageUrl, thumbnailUrl)

        return ApodEntry(
            date = dto.date.clean() ?: "",
            title = dto.title.clean() ?: "Untitled",
            explanation = dto.explanation.clean() ?: "",
            mediaType = mediaType,
            imageUrl = imageUrl,
            hdImageUrl = hdImageUrl,
            thumbnailUrl = thumbnailUrl,
            apodUrl = dto.apodUrl.clean(),
            copyright = dto.copyright.clean(),
            credit = dto.credit.clean(),
            altText = dto.altText.clean(),
            source = dto.source.clean(),
            sourceId = dto.sourceId.clean(),
            sourceMediaType = dto.sourceMediaType.clean(),
            imageStatus = imageStatus,
            updatedAt = dto.updatedAt.clean(),
            lastSuccessfulSync = dto.lastSuccessfulSync.clean(),
            latestAvailableDate = dto.latestAvailableDate.clean(),
            recordCount = dto.recordCount,
            sourceStatus = dto.sourceStatus.clean(),
        )
    }

    fun toDomain(dto: ArchiveIndexEntryDto): ArchiveIndexEntry = ArchiveIndexEntry(
        date = dto.date.clean() ?: "",
        title = dto.title.clean() ?: "Untitled",
        mediaType = MediaType.from(dto.mediaType),
        thumbnailUrl = dto.thumbnailUrl.clean(),
        imageUrl = dto.imageUrl.clean(),
        apodUrl = dto.apodUrl.clean(),
        copyright = dto.copyright.clean(),
        detailPath = dto.detailPath.clean(),
    )

    fun toDomain(dto: ArchiveIndexDto): List<ArchiveIndexEntry> =
        dto.entries.map { toDomain(it) }.filter { it.date.isNotBlank() }

    /**
     * Derives the image status.
     *
     * The API states the status explicitly; when it is absent the presence of an image URL decides.
     */
    private fun resolveImageStatus(
        raw: String?,
        imageUrl: String?,
        hdImageUrl: String?,
        thumbnailUrl: String?,
    ): ImageStatus {
        val explicit = raw.clean()
        if (explicit != null) {
            return when (explicit.lowercase()) {
                "placeholder" -> ImageStatus.PLACEHOLDER
                "missing" -> ImageStatus.MISSING
                else -> ImageStatus.OK
            }
        }
        return if (imageUrl == null && hdImageUrl == null && thumbnailUrl == null) {
            ImageStatus.MISSING
        } else {
            ImageStatus.OK
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
