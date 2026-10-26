package app.mohakash.apod.data.repository

import app.mohakash.apod.data.remote.ApiMapper
import app.mohakash.apod.data.remote.ApodService
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexEntryDto
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Hard upper bound for a cached index. Anything larger is treated as corrupt and quarantined. */
internal const val ARCHIVE_INDEX_MAX_CACHE_BYTES: Long = 16L * 1024 * 1024

/** The sidecar is tiny; a larger file cannot be a valid meta document. */
internal const val ARCHIVE_INDEX_MAX_META_BYTES: Long = 64L * 1024

internal const val ARCHIVE_INDEX_TEMP_PREFIX = "archive-index-"
internal const val ARCHIVE_INDEX_TEMP_SUFFIX = ".tmp"
internal const val ARCHIVE_INDEX_META_SUFFIX = ".meta.json"
internal const val ARCHIVE_INDEX_QUARANTINE_SUFFIX = ".corrupt"

/** The archive index as it is kept in memory: entries plus provenance of the cached copy. */
data class ArchiveIndexSnapshot(
    val entries: List<ArchiveIndexEntry>,
    val savedAt: Long,
    val apiBaseUrl: String?,
)

/**
 * The on-disk shape of the index.
 *
 * A current cache is the raw server document, so only [entries] and [serverApiBaseUrl] are present.
 * Older app versions wrapped the payload; those files are still readable because the wrapper's
 * `savedAt`/`apiBaseUrl` fields are part of the same document. Decoding the union in one pass means
 * the raw 5 MiB payload is never parsed twice and never re-serialised.
 */
@Serializable
private data class CachedIndexDocument(
    @SerialName("savedAt") val savedAt: Long? = null,
    @SerialName("apiBaseUrl") val legacyApiBaseUrl: String? = null,
    @SerialName("api_base_url") val serverApiBaseUrl: String? = null,
    @SerialName("entries") val entries: List<ArchiveIndexEntryDto> = emptyList(),
)

/** The small sidecar file next to the raw payload. */
@Serializable
private data class CachedIndexMeta(
    @SerialName("savedAt") val savedAt: Long,
    @SerialName("apiBaseUrl") val apiBaseUrl: String? = null,
    @SerialName("sizeBytes") val sizeBytes: Long? = null,
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
 * The cache file holds the server's document byte for byte; a sidecar file remembers when it was
 * saved and which endpoint served it:
 *
 * ```text
 * archive-index.json       {"schema_version": ..., "entries": [ ... ]}
 * archive-index.meta.json  {"savedAt": 1234567890, "apiBaseUrl": "https://.../api/v1"}
 * ```
 *
 * Writing the raw payload avoids a full parse + re-serialisation of the 5 MiB document on every
 * refresh. Reads decode the file exactly once. Writes go to a temporary file in the same directory
 * and are then renamed, so a crash can never leave a half-written index behind. Corrupt, empty or
 * oversized files are quarantined (`*.corrupt`) instead of being retried on every launch, and a
 * pathological file can never take the app down because reads and writes catch [OutOfMemoryError].
 *
 * [clear] and [refresh] coordinate through one mutex plus a generation token: a clear always wins
 * and a refresh that started before it is discarded instead of resurrecting the old endpoint's data.
 */
class FileArchiveIndexRepository(
    private val api: ApodService,
    json: Json,
    private val cacheFile: File,
    private val now: () -> Long = System::currentTimeMillis,
) : ArchiveIndexRepository {

    private val cacheJson: Json = Json(from = json) { ignoreUnknownKeys = true }

    private val memory = MutableStateFlow<List<ArchiveIndexEntry>>(emptyList())
    override val entries: StateFlow<List<ArchiveIndexEntry>> = memory.asStateFlow()

    private val snapshotState = MutableStateFlow<ArchiveIndexSnapshot?>(null)
    override val snapshot: StateFlow<ArchiveIndexSnapshot?> = snapshotState.asStateFlow()

    private val loadingState = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = loadingState.asStateFlow()

    private val errorState = MutableStateFlow(false)
    override val hasError: StateFlow<Boolean> = errorState.asStateFlow()

    /** Guards memory, the generation token and every disk mutation. */
    private val stateMutex = Mutex()

    /** Serialises refreshes so two downloads cannot race to write the cache. */
    private val refreshMutex = Mutex()

    /** Incremented by [clear]; a refresh that observed an older value must not write anything. */
    private val generation = AtomicLong(0L)

    /** The sidecar next to [cacheFile]. Internal so tests can assert the on-disk format. */
    internal val metaFile: File = File(
        cacheFile.parentFile,
        cacheFile.name.substringBeforeLast('.', cacheFile.name) + ARCHIVE_INDEX_META_SUFFIX,
    )

    /** Where a corrupt/oversized cache is moved instead of being parsed on every launch. */
    internal val quarantineFile: File =
        File(cacheFile.parentFile, cacheFile.name + ARCHIVE_INDEX_QUARANTINE_SUFFIX)

    override fun hasCachedIndex(): Boolean {
        if (memory.value.isNotEmpty()) return true
        return try {
            cacheFile.isFile && cacheFile.length() in 1..ARCHIVE_INDEX_MAX_CACHE_BYTES
        } catch (outOfMemory: OutOfMemoryError) {
            false
        }
    }

    override suspend fun loadCached(): List<ArchiveIndexEntry>? {
        if (memory.value.isNotEmpty()) return memory.value
        return withContext(Dispatchers.IO) {
            stateMutex.withLock {
                if (memory.value.isNotEmpty()) return@withLock memory.value
                val cached = readCacheFromDisk() ?: return@withLock null
                memory.value = cached.entries
                snapshotState.value = cached
                cached.entries
            }
        }
    }

    override suspend fun refresh(): Result<List<ArchiveIndexEntry>> = refreshMutex.withLock {
        val generationAtStart = generation.get()
        loadingState.value = true
        try {
            // Parsing 5 MiB of JSON must never happen on the main thread.
            val fetched = withContext(Dispatchers.IO) {
                val raw = api.getString(ARCHIVE_INDEX_PATH)
                val document = cacheJson.decodeFromString(CachedIndexDocument.serializer(), raw)
                val entries = ApiMapper.toDomain(ArchiveIndexDto(entries = document.entries))
                    .sortedByDescending { it.date }
                if (entries.isEmpty()) throw IOException("The archive index is empty")
                FetchedIndex(
                    raw = raw,
                    entries = entries,
                    savedAt = now(),
                    apiBaseUrl = document.serverApiBaseUrl?.takeIf { it.isNotBlank() },
                )
            }
            val entries = withContext(Dispatchers.IO) {
                stateMutex.withLock {
                    // A clear that completed while we were downloading must win: dropping the
                    // result here is what stops old-endpoint data from being written back.
                    if (generation.get() != generationAtStart) throw CacheInvalidatedException()
                    writeCache(fetched.raw, fetched.savedAt, fetched.apiBaseUrl)
                    memory.value = fetched.entries
                    snapshotState.value =
                        ArchiveIndexSnapshot(fetched.entries, fetched.savedAt, fetched.apiBaseUrl)
                    fetched.entries
                }
            }
            errorState.value = false
            Result.success(entries)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (invalidated: CacheInvalidatedException) {
            // The caller that requested this refresh is no longer interested in the result.
            Result.failure(invalidated)
        } catch (outOfMemory: OutOfMemoryError) {
            errorState.value = true
            Result.failure(IOException("Ran out of memory while loading the archive index", outOfMemory))
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
        withContext(Dispatchers.IO) {
            stateMutex.withLock {
                // Bump first so any refresh that is still in flight observes the change when it
                // tries to publish its (now stale) payload.
                generation.incrementAndGet()
                memory.value = emptyList()
                snapshotState.value = null
                errorState.value = false
                cacheFile.delete()
                metaFile.delete()
                quarantineFile.delete()
                sweepTemporaryFiles()
            }
        }
    }

    /** Decodes the cache exactly once. Never throws: a bad file is quarantined and reported as absent. */
    private fun readCacheFromDisk(): ArchiveIndexSnapshot? {
        // Temporary files are leftovers of an interrupted write; none can be valid.
        sweepTemporaryFiles()
        return try {
            if (!cacheFile.isFile) return null
            val size = cacheFile.length()
            if (size <= 0L || size > ARCHIVE_INDEX_MAX_CACHE_BYTES) {
                quarantineCache()
                return null
            }
            val raw = cacheFile.readText()
            if (raw.isBlank()) {
                quarantineCache()
                return null
            }
            val document = cacheJson.decodeFromString(CachedIndexDocument.serializer(), raw)
            val entries = ApiMapper.toDomain(ArchiveIndexDto(entries = document.entries))
                .sortedByDescending { it.date }
            // An empty document is not corrupt, it is simply not a usable cache.
            if (entries.isEmpty()) return null

            val meta = readMeta(size)
            val savedAt = meta?.savedAt
                ?: document.savedAt
                ?: cacheFile.lastModified().takeIf { it > 0L }
                ?: return null
            val apiBaseUrl = meta?.apiBaseUrl?.takeIf { it.isNotBlank() }
                ?: document.legacyApiBaseUrl?.takeIf { it.isNotBlank() }
                ?: document.serverApiBaseUrl?.takeIf { it.isNotBlank() }
            ArchiveIndexSnapshot(entries, savedAt, apiBaseUrl)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (outOfMemory: OutOfMemoryError) {
            // A pathological cache must not crash every launch: drop it and fall back to network.
            quarantineCache()
            null
        } catch (error: Exception) {
            // A corrupt or outdated document is quarantined, never fatal.
            quarantineCache()
            null
        }
    }

    /** Reads the sidecar; a missing or mismatched sidecar simply falls back to the file's mtime. */
    private fun readMeta(cacheSizeBytes: Long): CachedIndexMeta? {
        return try {
            if (!metaFile.isFile) return null
            val size = metaFile.length()
            if (size <= 0L || size > ARCHIVE_INDEX_MAX_META_BYTES) return null
            val meta = cacheJson.decodeFromString(CachedIndexMeta.serializer(), metaFile.readText())
            if (meta.sizeBytes != null && meta.sizeBytes != cacheSizeBytes) return null
            meta
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (outOfMemory: OutOfMemoryError) {
            null
        } catch (error: Exception) {
            null
        }
    }

    /** Writes the raw payload plus the sidecar atomically. Callers must hold [stateMutex]. */
    private fun writeCache(raw: String, savedAt: Long, apiBaseUrl: String?) {
        val directory = cacheFile.parentFile
            ?: throw IOException("No cache directory for ${cacheFile.path}")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create the archive index cache directory ${directory.path}")
        }
        sweepTemporaryFiles()
        if (raw.length > ARCHIVE_INDEX_MAX_CACHE_BYTES) {
            throw IOException("The archive index is too large to cache (${raw.length} characters)")
        }
        val temporary = File.createTempFile(ARCHIVE_INDEX_TEMP_PREFIX, ARCHIVE_INDEX_TEMP_SUFFIX, directory)
        try {
            temporary.writeText(raw)
            val size = temporary.length()
            if (size <= 0L) throw IOException("The archive index payload is empty")
            if (size > ARCHIVE_INDEX_MAX_CACHE_BYTES) {
                throw IOException("The archive index is larger than the cache limit of $ARCHIVE_INDEX_MAX_CACHE_BYTES bytes")
            }
            if (!temporary.renameTo(cacheFile)) {
                temporary.copyTo(cacheFile, overwrite = true)
                temporary.delete()
            }
            writeMeta(savedAt, apiBaseUrl, cacheFile.length())
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /**
     * Writes the sidecar. Failure is deliberately non-fatal: [readCacheFromDisk] falls back to the
     * cache file's modification time when no trustworthy sidecar exists.
     */
    private fun writeMeta(savedAt: Long, apiBaseUrl: String?, sizeBytes: Long) {
        val directory = metaFile.parentFile ?: return
        val temporary = File.createTempFile(ARCHIVE_INDEX_TEMP_PREFIX, ARCHIVE_INDEX_TEMP_SUFFIX, directory)
        try {
            val payload = cacheJson.encodeToString(
                CachedIndexMeta.serializer(),
                CachedIndexMeta(savedAt = savedAt, apiBaseUrl = apiBaseUrl, sizeBytes = sizeBytes),
            )
            temporary.writeText(payload)
            if (!temporary.renameTo(metaFile)) {
                temporary.copyTo(metaFile, overwrite = true)
                temporary.delete()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (outOfMemory: OutOfMemoryError) {
            metaFile.delete()
        } catch (error: Exception) {
            metaFile.delete()
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** Moves a bad cache out of the way so it is not parsed (or re-parsed) on every launch. */
    private fun quarantineCache() {
        try {
            metaFile.delete()
            if (!cacheFile.exists()) return
            if (quarantineFile.exists()) quarantineFile.delete()
            if (!cacheFile.renameTo(quarantineFile)) {
                cacheFile.delete()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (outOfMemory: OutOfMemoryError) {
            // Best effort only; returning without the cache is still the safe outcome.
        } catch (error: Exception) {
            cacheFile.delete()
        }
    }

    /**
     * Removes temporary files left behind by an interrupted write.
     *
     * Every disk mutation (including this sweep) happens under [stateMutex], so a temporary file
     * can never belong to a live writer of this repository.
     */
    private fun sweepTemporaryFiles() {
        val directory = cacheFile.parentFile ?: return
        if (!directory.isDirectory) return
        val files = try {
            directory.listFiles()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (outOfMemory: OutOfMemoryError) {
            null
        } catch (error: Exception) {
            null
        } ?: return
        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            if (name.startsWith(ARCHIVE_INDEX_TEMP_PREFIX) && name.endsWith(ARCHIVE_INDEX_TEMP_SUFFIX)) {
                file.delete()
            }
        }
    }

    private data class FetchedIndex(
        val raw: String,
        val entries: List<ArchiveIndexEntry>,
        val savedAt: Long,
        val apiBaseUrl: String?,
    )

    private class CacheInvalidatedException :
        IOException("The archive index cache was cleared while the refresh was running")

    private companion object {
        const val ARCHIVE_INDEX_PATH = "/archive-index.json"
    }
}
