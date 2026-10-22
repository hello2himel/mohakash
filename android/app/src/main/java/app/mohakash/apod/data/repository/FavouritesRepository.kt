package app.mohakash.apod.data.repository

import app.mohakash.apod.data.local.db.dao.FavouriteDao
import app.mohakash.apod.data.local.db.entity.FavouriteEntity
import app.mohakash.apod.domain.model.ApodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Saved pictures.
 *
 * The toggle is optimistic: the database is written first and the UI updates from the resulting
 * flow, so the heart reacts immediately even on a slow disk.
 */
class FavouritesRepository(
    private val favouriteDao: FavouriteDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeAll(): Flow<List<ApodEntry>> =
        favouriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByDate(date: String): Flow<ApodEntry?> =
        favouriteDao.observeByDate(date).map { it?.toDomain() }

    fun observeCount(): Flow<Int> = favouriteDao.observeCount()

    suspend fun isFavourite(date: String): Boolean = favouriteDao.isFavourite(date)

    /** The saved entry with the given date, if any. */
    suspend fun getByDate(date: String): ApodEntry? = favouriteDao.getByDate(date)?.toDomain()

    /**
     * Adds or removes the entry from the saved list.
     *
     * @return `true` when the entry is saved afterwards.
     */
    suspend fun toggle(entry: ApodEntry): Boolean {
        return if (favouriteDao.isFavourite(entry.date)) {
            favouriteDao.deleteByDate(entry.date)
            false
        } else {
            favouriteDao.upsert(FavouriteEntity.from(entry, now()))
            true
        }
    }

    suspend fun remove(date: String) = favouriteDao.deleteByDate(date)
}
