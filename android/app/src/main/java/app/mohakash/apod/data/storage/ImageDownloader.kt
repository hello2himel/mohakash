package app.mohakash.apod.data.storage

import android.graphics.BitmapFactory
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
 * picture never has to fit into memory, and the stream is hard-capped so a hostile or broken
 * server cannot fill the device's storage.
 */
class ImageDownloader(private val httpClient: OkHttpClient) : PictureDownloader {

    /**
     * Downloads [url] into [target].
     *
     * The target file is removed when the download fails or exceeds [MAX_IMAGE_BYTES] so a half
     * written file can never be mistaken for a complete one.
     */
    override suspend fun download(url: String, target: File): Result<File> = withContext(Dispatchers.IO) {
        if (!ImageUrlValidator.isValidImageUrl(url)) {
            return@withContext Result.failure(IOException("Refusing to download a non-image URL: $url"))
        }
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                // A changed picture must never be masked by an older cached response.
                .header("Cache-Control", "no-cache")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} while downloading $url")
                }
                val contentType = response.header("Content-Type")?.lowercase()
                if (contentType != null && contentType.startsWith("text/html")) {
                    throw IOException("Refusing to store an HTML document as an image: $url")
                }
                val body = response.body ?: throw IOException("Empty body for $url")
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_IMAGE_BYTES) {
                    throw IOException("Image at $url is $declaredLength bytes; the limit is $MAX_IMAGE_BYTES")
                }
                target.parentFile?.mkdirs()
                var total = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) {
                                throw IOException("Image at $url exceeds the limit of $MAX_IMAGE_BYTES bytes")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                if (target.length() == 0L) throw IOException("Downloaded an empty file for $url")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IOException("Downloaded file is not a decodable image: $url")
                }
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

    private companion object {
        /** 64 MiB: comfortably above the largest APOD source image, small enough for the cache. */
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024

        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
    }
}
