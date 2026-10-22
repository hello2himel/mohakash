package app.mohakash.apod.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw JSON shape of a single APOD record as served by `latest.json` and
 * `archive/<year>.json`.
 *
 * Every field is optional: the API may omit fields and the schema will not change, so the
 * normalisation layer has to cope with missing data.
 */
@Serializable
data class ApodRecordDto(
    @SerialName("schema_version") val schemaVersion: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("explanation") val explanation: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("hd_image_url") val hdImageUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("apod_url") val apodUrl: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("credit") val credit: String? = null,
    @SerialName("alt_text") val altText: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_media_type") val sourceMediaType: String? = null,
    @SerialName("image_status") val imageStatus: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("last_successful_sync") val lastSuccessfulSync: String? = null,
    @SerialName("latest_available_date") val latestAvailableDate: String? = null,
    @SerialName("record_count") val recordCount: Int? = null,
    @SerialName("source_status") val sourceStatus: String? = null,
    @SerialName("api_base_url") val apiBaseUrl: String? = null,
)

/** Raw JSON shape of `archive-index.json`. */
@Serializable
data class ArchiveIndexDto(
    @SerialName("schema_version") val schemaVersion: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("api_base_url") val apiBaseUrl: String? = null,
    @SerialName("entries") val entries: List<ArchiveIndexEntryDto> = emptyList(),
)

/** One row of `archive-index.json`. */
@Serializable
data class ArchiveIndexEntryDto(
    @SerialName("date") val date: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("apod_url") val apodUrl: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("detail_path") val detailPath: String? = null,
)

/** Raw JSON shape of `archive/<year>.json`. */
@Serializable
data class ArchiveYearDto(
    @SerialName("schema_version") val schemaVersion: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("records") val records: List<ApodRecordDto> = emptyList(),
)
