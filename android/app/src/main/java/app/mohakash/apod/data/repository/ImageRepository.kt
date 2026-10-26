package app.mohakash.apod.data.repository

import android.content.Context
import app.mohakash.apod.core.ImageUrlValidator
import app.mohakash.apod.data.storage.GallerySaver
import app.mohakash.apod.data.storage.ImageDownloader
import app.mohakash.apod.domain.model.ApodEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads pictures for the *save to gallery* action.
 *
 * The image is only fetched when the user actually asks for it — the app never prefetches.
 */
class ImageRepository(
    private val context: Context,
    private val downloader: ImageDownloader,
    private val gallerySaver: GallerySaver,
) {

    /** Copies the picture of [entry] into the device gallery. */
    suspend fun saveToGallery(entry: ApodEntry): Result<Unit> = withContext(Dispatchers.IO) {
        val url = entry.displayImageUrl
        if (url == null || !ImageUrlValidator.isValidImageUrl(url)) {
            return@withContext Result.failure(IllegalStateException("This entry has no picture that can be saved."))
        }
        val fileName = entry.galleryFileName()
        // A unique temporary path prevents a double tap from writing and deleting the same
        // cache file while the first gallery save is still running.
        val target = runCatching {
            File.createTempFile("gallery-", ".img", cacheDir())
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        val downloaded = downloader.download(url, target)
        if (downloaded.isFailure) {
            return@withContext Result.failure(downloaded.exceptionOrNull() ?: IllegalStateException("The download failed."))
        }
        val picture = downloaded.getOrThrow()
        val saved = try {
            gallerySaver.save(fileName, picture)
        } finally {
            // The gallery copy is independent; do not leave a second full-size file in the cache.
            runCatching { picture.delete() }
        }
        saved.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) },
        )
    }

    private fun cacheDir(): File = File(context.cacheDir, IMAGE_CACHE_DIRECTORY).apply { mkdirs() }

    companion object {
        const val IMAGE_CACHE_DIRECTORY = "images"
    }
}
