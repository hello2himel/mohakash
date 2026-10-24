package app.mohakash.apod.testing

import app.mohakash.apod.data.local.db.dao.CacheDocumentDao
import app.mohakash.apod.data.local.db.dao.CachedEntryDao
import app.mohakash.apod.data.local.db.dao.FavouriteDao
import app.mohakash.apod.data.local.db.entity.CacheDocumentEntity
import app.mohakash.apod.data.local.db.entity.CachedEntryEntity
import app.mohakash.apod.data.local.db.entity.FavouriteEntity
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.remote.ApodService
import app.mohakash.apod.data.repository.LatestEntrySource
import app.mohakash.apod.data.storage.PictureDownloader
import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.data.storage.WallpaperPictureStore
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.WallpaperScaling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

/** An in-memory [SettingsStore]. */
class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    override suspend fun setOnboardingCompleted(value: Boolean) = state.update { it.copy(onboardingCompleted = value) }

    override suspend fun setUpdateAutomatically(value: Boolean) = state.update { it.copy(updateAutomatically = value) }

    override suspend fun setPreferHd(value: Boolean) = state.update { it.copy(preferHd = value) }

    override suspend fun setWifiOnly(value: Boolean) = state.update { it.copy(wifiOnly = value) }

    override suspend fun setHideVideos(value: Boolean) = state.update { it.copy(hideVideos = value) }

    override suspend fun setWallpaperScaling(scaling: WallpaperScaling) =
        state.update { it.copy(wallpaperScaling = scaling) }

    override suspend fun setCustomApiBaseUrl(baseUrl: String?) =
        state.update { it.copy(customApiBaseUrl = baseUrl) }

    override suspend fun setPreferredBaseUrl(baseUrl: String?) = state.update { it.copy(preferredBaseUrl = baseUrl) }

    override suspend fun setLastSync(atMillis: Long, date: String?) =
        state.update { it.copy(lastSyncAt = atMillis, lastSyncDate = date) }
}

/** An in-memory [FavouriteDao] that behaves like the Room implementation. */
class FakeFavouriteDao : FavouriteDao {

    private val state = MutableStateFlow<List<FavouriteEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavouriteEntity>> = state.map { list ->
        list.sortedByDescending { it.savedAt }
    }

    override fun observeByDate(date: String): Flow<FavouriteEntity?> =
        state.map { list -> list.firstOrNull { it.date == date } }

    override suspend fun getByDate(date: String): FavouriteEntity? =
        state.value.firstOrNull { it.date == date }

    override fun observeCount(): Flow<Int> = state.map { it.size }

    override suspend fun count(): Int = state.value.size

    override suspend fun isFavourite(date: String): Boolean = state.value.any { it.date == date }

    override suspend fun upsert(entity: FavouriteEntity) {
        state.update { list -> (list.filterNot { it.date == entity.date } + entity).sortedByDescending { it.savedAt } }
    }

    override suspend fun deleteByDate(date: String) {
        state.update { list -> list.filterNot { it.date == date } }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/** An in-memory [CacheDocumentDao]. */
class FakeCacheDocumentDao : CacheDocumentDao {

    private val documents = mutableMapOf<String, CacheDocumentEntity>()

    override suspend fun get(key: String): CacheDocumentEntity? = documents[key]

    override suspend fun length(key: String): Int? = documents[key]?.json?.length

    override suspend fun chunk(key: String, start: Int, length: Int): String? {
        val json = documents[key]?.json ?: return null
        if (start < 1 || start > json.length) return ""
        val end = minOf(start - 1 + length, json.length)
        return json.substring(start - 1, end)
    }

    override suspend fun put(entity: CacheDocumentEntity) {
        documents[entity.key] = entity
    }

    override suspend fun delete(key: String) {
        documents.remove(key)
    }

    override suspend fun clear() {
        documents.clear()
    }
}

/** An in-memory [CachedEntryDao]. */
class FakeCachedEntryDao : CachedEntryDao {

    private val entries = mutableMapOf<String, CachedEntryEntity>()

    override suspend fun getByDate(date: String): CachedEntryEntity? = entries[date]

    override suspend fun getByDates(dates: List<String>): List<CachedEntryEntity> =
        dates.mapNotNull { entries[it] }

    override suspend fun getByYearPrefix(yearPrefix: String): List<CachedEntryEntity> =
        entries.values.filter { it.date.startsWith(yearPrefix) }.sortedByDescending { it.date }

    override suspend fun hasYearPrefix(yearPrefix: String): Boolean =
        entries.keys.any { it.startsWith(yearPrefix) }

    override suspend fun upsertAll(entities: List<CachedEntryEntity>) {
        entities.forEach { entries[it.date] = it }
    }

    override suspend fun clear() {
        entries.clear()
    }
}

/** A [LatestEntrySource] that returns a fixed entry or fails. */
class FakeLatestEntrySource(
    private val entry: ApodEntry? = null,
    private val error: Throwable? = null,
) : LatestEntrySource {

    var callCount: Int = 0
        private set

    override suspend fun fetchLatest(): ApodEntry {
        callCount++
        error?.let { throw it }
        return entry ?: throw IOException("No entry configured")
    }
}

/** An [ApodService] that can be switched between success and failure. */
class FakeApodService(
    var latestJson: String? = null,
    var archiveIndexJson: String? = null,
    var archiveYearJson: String? = null,
    var error: Throwable? = null,
) : ApodService {

    val requestedPaths = mutableListOf<String>()

    override suspend fun latest(): ApodEntry = throw UnsupportedOperationException()

    override suspend fun archiveIndex(): List<ArchiveIndexEntry> = throw UnsupportedOperationException()

    override suspend fun getString(path: String): String {
        requestedPaths += path
        error?.let { throw it }
        return when {
            path == "/latest.json" -> latestJson ?: throw IOException("no latest")
            path == "/archive-index.json" -> archiveIndexJson ?: throw IOException("no index")
            else -> archiveYearJson ?: throw IOException("no year")
        }
    }
}

/**
 * An in-memory [WallpaperPictureStore].
 *
 * It mimics the atomic behaviour of the real store: the picture is only replaced when the commit
 * succeeds.
 */
class FakeWallpaperPictureStore(
    override val storageDirectory: File = File(System.getProperty("java.io.tmpdir"), "mohakash-test"),
) : WallpaperPictureStore {

    var meta: WallpaperMeta? = null
    var commitCount: Int = 0
        private set
    var failCommit: Boolean = false

    override fun readMeta(): WallpaperMeta? = meta

    override suspend fun commit(source: File, meta: WallpaperMeta): Result<WallpaperMeta> {
        if (failCommit) return Result.failure(IOException("commit failed"))
        commitCount++
        val stored = meta.copy(fileName = "current.jpg")
        this.meta = stored
        return Result.success(stored)
    }
}

/**
 * A [PictureDownloader] that records every download and can be told to fail.
 */
class FakePictureDownloader(
    private val content: ByteArray = ByteArray(64) { 1 },
) : PictureDownloader {

    val downloadedUrls = mutableListOf<String>()
    var failWith: Throwable? = null

    override suspend fun download(url: String, target: File): Result<File> {
        downloadedUrls += url
        failWith?.let {
            target.delete()
            return Result.failure(it)
        }
        target.parentFile?.mkdirs()
        target.writeBytes(content)
        return Result.success(target)
    }
}
