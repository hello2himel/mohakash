package app.mohakash.apod.data.storage

import app.mohakash.apod.core.ImageUrlValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads a single image into a file.
 *
 * The download always runs off the main thread. The response is streamed to disk so a large
 * picture never has to fit into memory.
 */
class ImageDownloader(private val httpClient: OkHttpClient) {

    /**
     * Downloads [url] into [target].
     *
     * The target file is removed when the download fails so a half written file can never be
     * mistaken for a complete one.
     */
    suspend fun download(url: String, target: File): Result<File> = withContext(Dispatchers.IO) {
        if (!ImageUrlValidator.isValidImageUrl(url)) {
            return@withContext Result.failure(IOException("Refusing to download a non-image URL: $url"))
        }
        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} while downloading $url")
                }
                val contentType = response.header("Content-Type")?.lowercase()
                if (contentType != null && contentType.startsWith("text/html")) {
                    throw IOException("Refusing to store an HTML document as an image: $url")
                }
                val body = response.body ?: throw IOException("Empty body for $url")
                target.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.length() == 0L) throw IOException("Downloaded an empty file for $url")
            }
            Result.success(target)
        } catch (cancellation: CancellationException) {
            target.delete()
            throw cancellation
        } catch (error: Exception) {
            target.delete()
            Result.failure(error)
        }
    }
}
