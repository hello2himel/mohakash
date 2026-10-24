package app.mohakash.apod.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.mohakash.apod.data.local.db.entity.CacheDocumentEntity

/** Data access object for the raw cached JSON documents. */
@Dao
interface CacheDocumentDao {

    @Query("SELECT * FROM cache_documents WHERE key = :key LIMIT 1")
    suspend fun get(key: String): CacheDocumentEntity?

    /**
     * The length of the cached document in characters, or null when it is not cached.
     *
     * Large documents cannot be read in one row: Android's CursorWindow cannot hold more than
     * a couple of megabytes, so the archive index is read in chunks with [chunk].
     */
    @Query("SELECT length(json) FROM cache_documents WHERE key = :key LIMIT 1")
    suspend fun length(key: String): Int?

    /** A slice of the cached document, starting at the 1-based character [start]. */
    @Query("SELECT substr(json, :start, :length) FROM cache_documents WHERE key = :key LIMIT 1")
    suspend fun chunk(key: String, start: Int, length: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: CacheDocumentEntity)

    @Query("DELETE FROM cache_documents WHERE key = :key")
    suspend fun delete(key: String)

    /** Removes every cached document, e.g. after the API endpoint changes. */
    @Query("DELETE FROM cache_documents")
    suspend fun clear()
}
