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

    @Query("SELECT * FROM cached_entries WHERE date IN (:dates)")
    suspend fun getByDates(dates: List<String>): List<CachedEntryEntity>

    /**
     * Returns the cached records of a year, newest first.
     *
     * Selects only the entries of the requested year instead of every cached entry, so a large
     * cache never overflows a CursorWindow on long explanations.
     */
    @Query("SELECT * FROM cached_entries WHERE date LIKE :yearPrefix || '%' ORDER BY date DESC")
    suspend fun getByYearPrefix(yearPrefix: String): List<CachedEntryEntity>

    /**
     * True when at least one record of the given year is cached.
     *
     * Avoids loading full explanations just to check existence.
     */
    @Query("SELECT COUNT(*) > 0 FROM cached_entries WHERE date LIKE :yearPrefix || '%'")
    suspend fun hasYearPrefix(yearPrefix: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedEntryEntity>)

    @Query("DELETE FROM cached_entries")
    suspend fun clear()
}
