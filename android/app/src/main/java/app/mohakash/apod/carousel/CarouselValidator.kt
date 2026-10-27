package app.mohakash.apod.carousel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Validation result for one candidate image URL. */
sealed interface CarouselValidation {
    data object Valid : CarouselValidation
    data object Invalid : CarouselValidation

    /** Network/timeout/5xx; do not permanently discard the candidate. */
    data object TemporaryFailure : CarouselValidation
}

/** Checks that a URL actually serves an image without downloading the full-resolution picture. */
interface CarouselImageValidator {
    suspend fun validate(url: String): CarouselValidation
}

/**
 * HEAD-first validator with a tiny ranged GET fallback for CDNs that reject HEAD or omit the
 * content type. No full image is downloaded just to build the collection.
 */
class HttpCarouselImageValidator(private val httpClient: OkHttpClient) : CarouselImageValidator {

    override suspend fun validate(url: String): CarouselValidation = withContext(Dispatchers.IO) {
        if (!app.mohakash.apod.core.ImageUrlValidator.isValidImageUrl(url)) {
            return@withContext CarouselValidation.Invalid
        }
        val head = runCatching {
            httpClient.newCall(
                Request.Builder()
                    .url(url)
                    .head()
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build(),
            ).execute().use { response ->
                when {
                    response.isSuccessful && isImageType(response.header("Content-Type")) -> {
                        // HEAD responses always have a zero-byte body. Use the declared
                        // Content-Length header instead, otherwise every valid CDN image is
                        // incorrectly rejected.
                        val declaredLength = response.header("Content-Length")?.toLongOrNull()
                        if (declaredLength == 0L) CarouselValidation.Invalid
                        else CarouselValidation.Valid
                    }

                    response.code in 200..299 -> rangeGet(url)
                    response.code == 405 || response.code == 501 || response.code == 403 -> rangeGet(url)
                    response.code == 408 || response.code == 429 || response.code >= 500 ->
                        CarouselValidation.TemporaryFailure
                    response.code in 400..499 -> CarouselValidation.Invalid
                    else -> CarouselValidation.Invalid
                }
            }
        }.getOrElse { error ->
            if (error is IOException) CarouselValidation.TemporaryFailure else CarouselValidation.Invalid
        }
        head ?: CarouselValidation.TemporaryFailure
    }

    private fun rangeGet(url: String): CarouselValidation {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-1023")
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 408 || response.code == 429 || response.code >= 500) {
                    CarouselValidation.TemporaryFailure
                } else if (!response.isSuccessful) {
                    CarouselValidation.Invalid
                } else {
                    val body = response.body ?: return CarouselValidation.Invalid
                    val bytes = body.byteStream().use { input ->
                        val buffer = ByteArray(16)
                        val read = input.read(buffer)
                        if (read <= 0) ByteArray(0) else buffer.copyOf(read)
                    }
                    if (isImageType(response.header("Content-Type")) || looksLikeImage(bytes)) {
                        CarouselValidation.Valid
                    } else {
                        CarouselValidation.Invalid
                    }
                }
            }
        }.getOrElse { error ->
            if (error is IOException) CarouselValidation.TemporaryFailure else CarouselValidation.Invalid
        }
    }

    private fun isImageType(contentType: String?): Boolean =
        contentType?.trim()?.lowercase()?.startsWith("image/") == true

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        val webp = bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
        return jpeg || png || gif || webp
    }
}
