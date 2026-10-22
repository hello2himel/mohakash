package app.mohakash.apod.domain.model

/**
 * The lightweight entry stored in `archive-index.json`.
 *
 * The index carries no explanations on purpose: it is small enough to download in one request
 * and is used to render the archive list before the full year file is fetched.
 */
data class ArchiveIndexEntry(
    val date: String,
    val title: String,
    val mediaType: MediaType,
    val thumbnailUrl: String?,
    val imageUrl: String?,
    val apodUrl: String?,
    val copyright: String?,
    val detailPath: String?,
) {
    val year: Int get() = date.take(4).toIntOrNull() ?: 0

    val displayCredit: String? get() = copyright?.takeIf { it.isNotBlank() }
}
