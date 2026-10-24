package app.mohakash.apod.data.repository

import app.mohakash.apod.data.local.db.dao.CacheDocumentDao
import app.mohakash.apod.data.local.db.dao.CachedEntryDao
import app.mohakash.apod.data.local.db.entity.CacheDocumentEntity
import app.mohakash.apod.data.local.db.entity.CachedEntryEntity
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.remote.ApiMapper
import app.mohakash.apod.data.remote.ApodService
import app.mohakash.apod.data.remote.dto.ApodRecordDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.data.remote.dto.ArchiveYearDto
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import kotlinx.serialization.json.Json

/**
 * A record together with the place it came from.
 */
data class EntrySnapshot(
    val entry: ApodEntry,
    val fromCache: Boolean,
    val syncedAt: Long? = null,
)

/**
 * The single entry point for APOD metadata.
 *
 * Network responses are cached verbatim so the app can render Today and the archive while
 * offline. Nothing in here touches images — that is the job of [ImageRepository] and the
 * wallpaper storage.
 */
class ApodRepository(
    private val api: ApodService,
    private val cacheDocumentDao: CacheDocumentDao,
    private val cachedEntryDao: CachedEntryDao,
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) : LatestEntrySource {

    /** Fetches `latest.json`, caches it and records the successful sync. */
    override suspend fun fetchLatest(): ApodEntry {
        val raw = api.getString("/latest.json")
        val entry = ApiMapper.toDomain(json.decodeFromString<ApodRecordDto>(raw))
        cacheDocumentDao.put(
            CacheDocumentEntity(
                key = CacheDocumentEntity.KEY_LATEST,
                json = raw,
                updatedAt = now(),
            ),
        )
        cachedEntryDao.upsertAll(listOf(CachedEntryEntity.from(entry, now())))
        settingsStore.setLastSync(now(), entry.date)
        return entry
    }

    /** Reads the cached `latest.json` document, if any. */
    suspend fun cachedLatest(): ApodEntry? {
        val raw = readDocument(CacheDocumentEntity.KEY_LATEST) ?: return null
        return runCatching { ApiMapper.toDomain(json.decodeFromString<ApodRecordDto>(raw)) }
            .getOrNull()
    }

    /** Fetches `archive-index.json`, caches it and returns the entries. */
    suspend fun fetchArchiveIndex(): List<ArchiveIndexEntry> {
        val raw = api.getString("/archive-index.json")
        val entries = ApiMapper.toDomain(json.decodeFromString<ArchiveIndexDto>(raw))
        cacheDocumentDao.put(
            CacheDocumentEntity(
                key = CacheDocumentEntity.KEY_ARCHIVE_INDEX,
                json = raw,
                updatedAt = now(),
            ),
        )
        return entries
    }

    /** Reads the cached `archive-index.json` document, if any. */
    suspend fun cachedArchiveIndex(): List<ArchiveIndexEntry> {
        val raw = readDocument(CacheDocumentEntity.KEY_ARCHIVE_INDEX) ?: return emptyList()
        return runCatching { ApiMapper.toDomain(json.decodeFromString<ArchiveIndexDto>(raw)) }
            .getOrDefault(emptyList())
    }

    /**
     * Reads a cached JSON document in slices.
     *
     * `archive-index.json` is several megabytes, which is more than a single SQLite
     * CursorWindow can hold; reading it as one row throws [android.database.sqlite.SQLiteBlobTooBigException].
     */
    private suspend fun readDocument(key: String): String? {
        val total = cacheDocumentDao.length(key) ?: return null
        if (total == 0) return ""
        val builder = StringBuilder(total)
        var start = 1
        while (start <= total) {
            val chunk = cacheDocumentDao.chunk(key, start, DOCUMENT_CHUNK_CHARS) ?: return null
            builder.append(chunk)
            start += DOCUMENT_CHUNK_CHARS
        }
        return builder.toString()
    }

    private companion object {
        /** 256k characters is at most 1 MiB of UTF-8, safely below the CursorWindow limit. */
        const val DOCUMENT_CHUNK_CHARS = 256_000
    }

    /** Fetches a full year file and caches every record it contains. */
    suspend fun fetchArchiveYear(year: Int): List<ApodEntry> {
        val raw = api.getString("/archive/$year.json")
        val dto = json.decodeFromString<ArchiveYearDto>(raw)
        val records = dto.records.map { ApiMapper.toDomain(it) }
        val timestamp = now()
        cachedEntryDao.upsertAll(records.map { CachedEntryEntity.from(it, timestamp) })
        return records
    }

    /** Reads the cached records of a year, newest first. */
    suspend fun cachedArchiveYear(year: Int): List<ApodEntry> =
        cachedEntryDao.getByYearPrefix("$year-")
            .map { it.toDomain() }

    /** True when the records of the year are already cached. */
    suspend fun hasCachedYear(year: Int): Boolean =
        cachedEntryDao.hasYearPrefix("$year-")

    /**
     * Looks a record up in the local cache.
     *
     * The cached `latest.json` record wins because it is the most recently fetched one.
     */
    suspend fun findCachedEntry(date: String): ApodEntry? {
        if (date.isBlank()) return null
        cachedEntryDao.getByDate(date)?.let { return it.toDomain() }
        return null
    }

    /**
     * Fetches one entry by date.
     *
     * Only the year file of the requested year is fetched — the whole archive is never
     * downloaded. When the network is unavailable the cached records of that year are used.
     */
    suspend fun fetchEntry(date: String): ApodEntry? {
        if (date.isBlank()) return null
        val year = date.take(4).toIntOrNull() ?: return null
        val records = runCatching { fetchArchiveYear(year) }.getOrElse { cachedArchiveYear(year) }
        return records.firstOrNull { it.date == date }
    }
}
