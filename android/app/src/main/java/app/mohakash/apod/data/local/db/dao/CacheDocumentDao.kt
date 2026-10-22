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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: CacheDocumentEntity)

    @Query("DELETE FROM cache_documents WHERE key = :key")
    suspend fun delete(key: String)
}
