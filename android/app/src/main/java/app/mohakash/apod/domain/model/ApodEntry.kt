package app.mohakash.apod.domain.model

/**
 * A single Astronomy Picture of the Day.
 *
 * The shape mirrors the public JSON API (`latest.json` and the records inside
 * `archive/<year>.json`) but is normalised: unknown media types become [MediaType.OTHER],
 * missing image status becomes [ImageStatus.OK] and nullable strings stay nullable so the UI can
 * omit a credit instead of inventing one.
 */
data class ApodEntry(
    val date: String,
    val title: String,
    val explanation: String,
    val mediaType: MediaType,
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
    val imageStatus: ImageStatus,
    val updatedAt: String?,
    val lastSuccessfulSync: String?,
    val latestAvailableDate: String?,
    val recordCount: Int?,
    val sourceStatus: String?,
) {
    /** The credit line shown under the title. Never invented: `null` when the API has none. */
    val displayCredit: String?
        get() = copyright?.takeIf { it.isNotBlank() } ?: credit?.takeIf { it.isNotBlank() }

    /** Paragraphs of the explanation, split on blank lines. */
    val paragraphs: List<String>
        get() = explanation.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** True when the entry can be turned into a live wallpaper. */
    val supportsWallpaper: Boolean
        get() = mediaType.isImage && imageStatus.isUsable

    /** The picture that is shown on screen, saved or shared. */
    val displayImageUrl: String?
        get() = imageUrl ?: hdImageUrl ?: thumbnailUrl

    /**
     * The file name used when the picture is stored in the gallery:
     * `APOD_YYYY-MM-DD.<ext>` with the real extension of the asset.
     */
    fun galleryFileName(): String {
        val extension = displayImageUrl
            ?.substringBefore('?')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") }
            ?: "jpg"
        return "APOD_$date.${if (extension == "jpeg") "jpg" else extension}"
    }
}
