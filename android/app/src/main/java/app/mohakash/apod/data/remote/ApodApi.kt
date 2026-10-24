package app.mohakash.apod.data.remote

import app.mohakash.apod.data.remote.dto.ApodRecordDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.data.remote.dto.ArchiveYearDto
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /** Downloads a raw document, probing every candidate base URL until one answers. */
    override suspend fun getString(path: String): String = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        val candidates = baseUrlResolver.orderedCandidates()
        for (baseUrl in candidates) {
            val url = baseUrl.trimEnd('/') + path
            try {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for $url")
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) throw IOException("Empty response body for $url")
                    baseUrlResolver.remember(baseUrl)
                    return@withContext body
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
