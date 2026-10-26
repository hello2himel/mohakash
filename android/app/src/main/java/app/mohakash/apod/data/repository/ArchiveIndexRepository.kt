package app.mohakash.apod.data.repository

import app.mohakash.apod.data.remote.ApiMapper
import app.mohakash.apod.data.remote.ApodService
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/** The archive index as it is kept in memory: entries plus provenance of the cached copy. */
data class ArchiveIndexSnapshot(
    val entries: List<ArchiveIndexEntry>,
    val savedAt: Long,
    val apiBaseUrl: String?,
)

/**
 * The archive index (`archive-index.json`, roughly 5 MiB, 11k rows).
 *
 * The feed is the archive, so the index is loaded once, cached on disk and shared by every part of
 * the screen (the pager order, the year filter, search and jump-to-date). It is deliberately not
 * stored in Room: it is a document, not a record set, and keeping it out of SQLite leaves the
 * database schema untouched.
 */
interface ArchiveIndexRepository {

    /** The parsed index, newest first. Empty until [loadCached] or [refresh] succeeded. */
    val entries: StateFlow<List<ArchiveIndexEntry>>

    /** The cached copy including its `savedAt` timestamp; `null` when nothing is cached. */
    val snapshot: StateFlow<ArchiveIndexSnapshot?>

    /** True while a network refresh is running. */
    val isLoading: StateFlow<Boolean>

    /** True when at least one refresh failed since the repository was created. */
    val hasError: StateFlow<Boolean>

    /** True when a parsed index is currently in memory. */
    fun hasCachedIndex(): Boolean

    /** Reads the disk cache (no network). Returns `null` when there is no usable cache. */
    suspend fun loadCached(): List<ArchiveIndexEntry>?

    /**
     * Downloads the index and replaces the disk cache atomically.
     *
     * On failure the previous cache is kept untouched and the failure is returned.
     */
    suspend fun refresh(): Result<List<ArchiveIndexEntry>>

    /**
     * Returns the in-memory index, loading the disk cache and — when [allowNetwork] is true and no
     * cache exists — the network. Never throws.
     */
    suspend fun ensureLoaded(allowNetwork: Boolean = true): List<ArchiveIndexEntry>?

    /** Removes the memory copy and the disk file (used when the API endpoint changes). */
    suspend fun clear()
}

/**
 * [ArchiveIndexRepository] backed by one JSON file in the cache directory.
 *
 * The file is a small wrapper around the payload so the app can decide how old the copy is without
 * trusting the HTTP cache headers:
 *
 * ```json
 * { "savedAt": 1234567890, "apiBaseUrl": "https://.../api/v1", "entries": [ ... ] }
 * ```
 *
 * Writes go to a temporary file in the same directory first and are then renamed, so a crash can
 * never leave a half-written index behind.
 */
class FileArchiveIndexRepository(
    private val api: ApodService,
    private val json: Json,
    private val cacheFile: File,
    private val now: () -> Long = System::currentTimeMillis,
) : ArchiveIndexRepository {

    private val memory = MutableStateFlow<List<ArchiveIndexEntry>>(emptyList())
    override val entries: StateFlow<List<ArchiveIndexEntry>> = memory.asStateFlow()

    private val snapshotState = MutableStateFlow<ArchiveIndexSnapshot?>(null)
    override val snapshot: StateFlow<ArchiveIndexSnapshot?> = snapshotState.asStateFlow()

    private val loadingState = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = loadingState.asStateFlow()

    private val errorState = MutableStateFlow(false)
    override val hasError: StateFlow<Boolean> = errorState.asStateFlow()

    private val loadMutex = Mutex()
    private val refreshMutex = Mutex()

    override fun hasCachedIndex(): Boolean = memory.value.isNotEmpty() || cacheFile.isFile

    override suspend fun loadCached(): List<ArchiveIndexEntry>? {
        if (memory.value.isNotEmpty()) return memory.value
        return loadMutex.withLock {
            if (memory.value.isNotEmpty()) return@withLock memory.value
            val cached = readCache() ?: return@withLock null
            memory.value = cached.entries
            snapshotState.value = cached
            cached.entries
        }
    }

    override suspend fun refresh(): Result<List<ArchiveIndexEntry>> = refreshMutex.withLock {
        loadingState.value = true
        try {
            // Parsing 5 MiB of JSON must never happen on the main thread.
            val parsed = withContext(Dispatchers.IO) {
                val raw = api.getString("/archive-index.json")
                val dto = json.decodeFromString<ArchiveIndexDto>(raw)
                val entries = ApiMapper.toDomain(dto).sortedByDescending { it.date }
                if (entries.isEmpty()) throw IOException("The archive index is empty")
                val savedAt = now()
                writeCache(raw, savedAt, dto.apiBaseUrl)
                Triple(entries, savedAt, dto.apiBaseUrl)
            }
            val (entries, savedAt, apiBaseUrl) = parsed
            memory.value = entries
            snapshotState.value = ArchiveIndexSnapshot(entries, savedAt, apiBaseUrl)
            errorState.value = false
            Result.success(entries)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            errorState.value = true
            Result.failure(error)
        } finally {
            loadingState.value = false
        }
    }

    override suspend fun ensureLoaded(allowNetwork: Boolean): List<ArchiveIndexEntry>? {
        loadCached()?.let { return it }
        if (!allowNetwork) return null
        return refresh().getOrNull()
    }

    override suspend fun clear() {
        loadMutex.withLock {
            memory.value = emptyList()
            snapshotState.value = null
            errorState.value = false
            withContext(Dispatchers.IO) {
                cacheFile.delete()
                cacheFile.parentFile?.listFiles()
                    ?.filter { it.name.startsWith(CACHE_TEMP_PREFIX) }
                    ?.forEach { it.delete() }
            }
        }
    }

    /** The cache is only considered for writes when it still belongs to the active endpoint. */
    private suspend fun readCache(): ArchiveIndexSnapshot? = withContext(Dispatchers.IO) {
        if (!cacheFile.isFile || cacheFile.length() == 0L) return@withContext null
        try {
            val root = json.parseToJsonElement(cacheFile.readText()) as? JsonObject
                ?: return@withContext null
            val savedAt = root["savedAt"]?.jsonPrimitive?.longOrNull
                ?: cacheFile.lastModified().takeIf { it > 0L }
                ?: return@withContext null
            val apiBaseUrl = root["apiBaseUrl"]?.jsonPrimitive?.takeIf { it.isString }?.content
                ?.takeIf { it.isNotBlank() }
            val entriesElement = root["entries"] as? JsonArray ?: return@withContext null
            val dto = json.decodeFromJsonElement(
                ArchiveIndexDto.serializer(),
                buildJsonObject { put("entries", entriesElement) },
            )
            val entries = ApiMapper.toDomain(dto).sortedByDescending { it.date }
            if (entries.isEmpty()) return@withContext null
            ArchiveIndexSnapshot(entries, savedAt, apiBaseUrl)
        } catch (error: Exception) {
            // A corrupt or outdated wrapper is ignored (and refetched later), never fatal.
            if (error is CancellationException) throw error
            null
        }
    }

    /** Writes the payload wrapper atomically: temp file, then rename. */
    private suspend fun writeCache(raw: String, savedAt: Long, apiBaseUrl: String?) = withContext(Dispatchers.IO) {
        val directory = cacheFile.parentFile ?: return@withContext
        directory.mkdirs()
        val root = json.parseToJsonElement(raw) as? JsonObject
        val entries = root?.get("entries") as? JsonArray ?: JsonArray(emptyList())
        val wrapper = buildJsonObject {
            put("savedAt", JsonPrimitive(savedAt))
            if (!apiBaseUrl.isNullOrBlank()) put("apiBaseUrl", JsonPrimitive(apiBaseUrl))
            put("entries", entries)
        }
        val temporary = File.createTempFile(CACHE_TEMP_PREFIX, CACHE_TEMP_SUFFIX, directory)
        try {
            temporary.writeText(wrapper.toString())
            if (!temporary.renameTo(cacheFile)) {
                temporary.copyTo(cacheFile, overwrite = true)
                temporary.delete()
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private companion object {
        const val CACHE_TEMP_PREFIX = "archive-index-"
        const val CACHE_TEMP_SUFFIX = ".tmp"
    }
}
