package app.mohakash.apod.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.mohakash.apod.data.local.db.dao.CacheDocumentDao
import app.mohakash.apod.data.local.db.dao.CachedEntryDao
import app.mohakash.apod.data.local.db.dao.FavouriteDao
import app.mohakash.apod.data.local.db.entity.CacheDocumentEntity
import app.mohakash.apod.data.local.db.entity.CachedEntryEntity
import app.mohakash.apod.data.local.db.entity.FavouriteEntity

/** The on-device database: saved pictures, cached records and cached JSON documents. */
@Database(
    entities = [
        FavouriteEntity::class,
        CachedEntryEntity::class,
        CacheDocumentEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MohakashDatabase : RoomDatabase() {

    abstract fun favouriteDao(): FavouriteDao

    abstract fun cachedEntryDao(): CachedEntryDao

    abstract fun cacheDocumentDao(): CacheDocumentDao

    companion object {
        private const val NAME = "mohakash.db"

        @Volatile
        private var instance: MohakashDatabase? = null

        fun get(context: Context): MohakashDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MohakashDatabase::class.java,
                NAME,
            ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
        }
    }
}
