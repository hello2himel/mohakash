package app.mohakash.apod.data.storage

import java.io.File

/**
 * The place where the live wallpaper picture lives.
 *
 * The interface keeps the synchronisation logic independent of the file system, so it can be
 * tested with an in-memory fake.
 */
interface WallpaperPictureStore {

    /** The directory that holds the picture. */
    val storageDirectory: File

    /** The metadata of the current picture, if any. */
    fun readMeta(): WallpaperMeta?

    /**
     * Stores [source] as the new wallpaper picture.
     *
     * The swap has to be atomic: the previous picture stays in place when the commit fails.
     */
    suspend fun commit(source: File, meta: WallpaperMeta): Result<WallpaperMeta>
}
