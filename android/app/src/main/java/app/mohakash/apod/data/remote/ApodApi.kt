package app.mohakash.apod.data.remote

import app.mohakash.apod.data.remote.dto.ApodRecordDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Thin HTTP client for the public JSON API.
 *
 * Every request is tried against the candidate base URLs in order until one answers; the winner
 * is remembered so the next request starts with it.
 */
class ApodApi(
    private val baseUrlResolver: BaseUrlResolver,
    private val httpClient: OkHttpClient,
    private val json: Json,
) : ApodService {

    override suspend fun latest(): ApodEntry = withContext(Dispatchers.IO) {
        val dto = json.decodeFromString<ApodRecordDto>(getString("/latest.json"))
        ApiMapper.toDomain(dto)
    }

    override suspend fun archiveIndex(): List<ArchiveIndexEntry> = withContext(Dispatchers.IO) {
        val dto = json.decodeFromString<ArchiveIndexDto>(getString("/archive-index.json"))
        ApiMapper.toDomain(dto)
    }

    /**
     * Downloads a raw document, probing every candidate base URL until one answers.
     *
     * The decompressed body is capped at [maxBytes] while it is being read, so an endless or
     * unexpectedly huge response is rejected with an [IOException] before it can be buffered whole.
     */
    override suspend fun getString(
        path: String,
        maxBytes: Long,
        forceNetwork: Boolean,
    ): String = withContext(Dispatchers.IO) {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        var lastError: Throwable? = null
        val candidates = baseUrlResolver.orderedCandidates()
        for (baseUrl in candidates) {
            val url = baseUrl.trimEnd('/') + path
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .apply {
                        if (forceNetwork) cacheControl(CacheControl.FORCE_NETWORK)
                    }
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for $url")
                    }
                    val body = response.body ?: throw IOException("Empty response body for $url")
                    val text = body.readStringCapped(maxBytes, url)
                    if (text.isBlank()) throw IOException("Empty response body for $url")
                    baseUrlResolver.remember(baseUrl)
                    return@withContext text
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No usable API base URL (tried ${candidates.size})")
    }
}

private fun ResponseBody.readStringCapped(maxBytes: Long, url: String): String {
    val declaredLength = contentLength()
    if (declaredLength > maxBytes) {
        throw IOException("Response for $url is $declaredLength bytes; the limit is $maxBytes")
    }
    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    // Content-Length is -1 for chunked/gzip responses; a small initial buffer grows up to the cap.
    val initialCapacity = if (declaredLength in 0..maxBytes) declaredLength.toInt() else 0
    val output = ByteArrayOutputStream(initialCapacity)
    var total = 0L
    byteStream().use { input ->
        val buffer = ByteArray(RESPONSE_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("Response for $url exceeds the limit of $maxBytes bytes")
            }
            output.write(buffer, 0, read)
        }
    }
    return output.toString(charset.name())
}

private const val RESPONSE_BUFFER_BYTES = 8 * 1024
