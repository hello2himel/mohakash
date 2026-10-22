package app.mohakash.apod.data.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the image cache.
 *
 * Clearing the cache never touches the saved pictures (they live in the database) and never
 * touches the live wallpaper (it lives in the private files directory).
 */
class CacheManager(context: Context) {

    private val appContext = context.applicationContext

    private val cacheDirectories: List<File>
        get() = listOf(
            File(appContext.cacheDir, "image_cache"),
            File(appContext.cacheDir, "http"),
            File(appContext.cacheDir, "images"),
        )

    /** The number of bytes the cached images occupy. */
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDirectories.sumOf { directorySize(it) }
    }

    /** Removes the cached images and returns the number of freed bytes. */
    suspend fun clear(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        cacheDirectories.forEach { directory ->
            freed += directorySize(directory)
            directory.listFiles()?.forEach { it.deleteRecursively() }
        }
        freed
    }

    private fun directorySize(directory: File): Long {
        if (!directory.isDirectory) return 0L
        var total = 0L
        directory.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        return total
    }
}
