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
) {

    suspend fun latest(): ApodEntry = withContext(Dispatchers.IO) {
        val dto = json.decodeFromString<ApodRecordDto>(getString("/latest.json"))
        ApiMapper.toDomain(dto)
    }

    suspend fun archiveIndex(): List<ArchiveIndexEntry> = withContext(Dispatchers.IO) {
        val dto = json.decodeFromString<ArchiveIndexDto>(getString("/archive-index.json"))
        ApiMapper.toDomain(dto)
    }

    suspend fun archiveYear(year: Int): List<ApodEntry> = withContext(Dispatchers.IO) {
        val dto = json.decodeFromString<ArchiveYearDto>(getString("/archive/$year.json"))
        dto.records.map { ApiMapper.toDomain(it) }
    }

    /** Downloads a raw document, probing every candidate base URL until one answers. */
    suspend fun getString(path: String): String = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (baseUrl in baseUrlResolver.orderedCandidates()) {
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
        throw lastError ?: IOException("No usable API base URL (tried ${baseUrlResolver.candidates.size})")
    }
}
