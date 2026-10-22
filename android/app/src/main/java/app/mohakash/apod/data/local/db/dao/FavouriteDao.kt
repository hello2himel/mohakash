package app.mohakash.apod.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.mohakash.apod.data.local.db.entity.FavouriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the saved pictures.
 *
 * The interface is intentionally small so it can be replaced by an in-memory fake in unit tests.
 */
@Dao
interface FavouriteDao {

    @Query("SELECT * FROM favourites ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavouriteEntity>>

    @Query("SELECT * FROM favourites WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<FavouriteEntity?>

    @Query("SELECT * FROM favourites WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): FavouriteEntity?

    @Query("SELECT COUNT(*) FROM favourites")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM favourites")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE date = :date)")
    suspend fun isFavourite(date: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM favourites")
    suspend fun clear()
}
