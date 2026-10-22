package app.mohakash.apod.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A raw JSON document cached verbatim, e.g. `latest.json` or `archive-index.json`.
 *
 * Keeping the original document means the cached copy can be re-read without a lossy round trip
 * through the domain model.
 */
@Entity(tableName = "cache_documents")
data class CacheDocumentEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
) {
    companion object {
        const val KEY_LATEST = "latest"
        const val KEY_ARCHIVE_INDEX = "archive-index"
    }
}
