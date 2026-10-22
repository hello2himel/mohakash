package app.mohakash.apod.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.mohakash.apod.data.local.db.entity.CachedEntryEntity

/** Data access object for the cached APOD records. */
@Dao
interface CachedEntryDao {

    @Query("SELECT * FROM cached_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): CachedEntryEntity?

    @Query("SELECT * FROM cached_entries")
    suspend fun getAll(): List<CachedEntryEntity>

    @Query("SELECT * FROM cached_entries WHERE date IN (:dates)")
    suspend fun getByDates(dates: List<String>): List<CachedEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedEntryEntity>)

    @Query("DELETE FROM cached_entries")
    suspend fun clear()
}
