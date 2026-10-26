package app.mohakash.apod.data.storage

import android.content.Context
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Manages the image and network caches.
 *
 * Clearing goes through Coil's and OkHttp's own cache APIs so their journal/DB files stay
 * consistent. Only the extra files written by the explicit save action live outside those
 * managed caches and are removed directly.
 */
class CacheManager(
    context: Context,
    private val httpClient: OkHttpClient,
) {

    private val appContext = context.applicationContext

    private val imageLoader get() = Coil.imageLoader(appContext)

    /** The number of bytes the cached images occupy. */
    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val coil = imageLoader.diskCache?.size ?: 0L
        val http = httpClient.cache?.size() ?: 0L
        coil + http + directorySize(File(appContext.cacheDir, "images"))
    }

    /** Removes the cached images and returns the number of freed bytes. */
    suspend fun clear(): Long = withContext(Dispatchers.IO) {
        val before = cacheSizeBytes()
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
        httpClient.cache?.evictAll()
        File(appContext.cacheDir, "images").deleteRecursively()
        val after = cacheSizeBytes()
        (before - after).coerceAtLeast(0L)
    }

    private fun directorySize(directory: File): Long {
        if (!directory.isDirectory) return 0L
        var total = 0L
        directory.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        return total
    }
}
