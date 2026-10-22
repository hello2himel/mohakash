package app.mohakash.apod.data.repository

import app.mohakash.apod.data.local.db.dao.CacheDocumentDao
import app.mohakash.apod.data.local.db.dao.CachedEntryDao
import app.mohakash.apod.data.local.db.entity.CacheDocumentEntity
import app.mohakash.apod.data.local.db.entity.CachedEntryEntity
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.remote.ApodApi
import app.mohakash.apod.data.remote.ApiMapper
import app.mohakash.apod.data.remote.dto.ApodRecordDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
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
    private val api: ApodApi,
    private val cacheDocumentDao: CacheDocumentDao,
    private val cachedEntryDao: CachedEntryDao,
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Fetches `latest.json`, caches it and records the successful sync. */
    suspend fun fetchLatest(): ApodEntry {
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
        val document = cacheDocumentDao.get(CacheDocumentEntity.KEY_LATEST) ?: return null
        return runCatching { ApiMapper.toDomain(json.decodeFromString<ApodRecordDto>(document.json)) }
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
        val document = cacheDocumentDao.get(CacheDocumentEntity.KEY_ARCHIVE_INDEX) ?: return emptyList()
        return runCatching { ApiMapper.toDomain(json.decodeFromString<ArchiveIndexDto>(document.json)) }
            .getOrDefault(emptyList())
    }

    /** Fetches a full year file and caches every record it contains. */
    suspend fun fetchArchiveYear(year: Int): List<ApodEntry> {
        val records = api.archiveYear(year)
        val timestamp = now()
        cachedEntryDao.upsertAll(records.map { CachedEntryEntity.from(it, timestamp) })
        return records
    }

    /** Reads the cached records of a year, newest first. */
    suspend fun cachedArchiveYear(year: Int): List<ApodEntry> =
        cachedEntryDao.getAll()
            .filter { it.date.startsWith("$year-") }
            .sortedByDescending { it.date }
            .map { it.toDomain() }

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
