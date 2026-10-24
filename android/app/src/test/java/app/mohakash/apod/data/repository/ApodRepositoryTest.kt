package app.mohakash.apod.data.repository

import app.mohakash.apod.data.local.db.entity.CacheDocumentEntity
import app.mohakash.apod.testing.FakeCacheDocumentDao
import app.mohakash.apod.testing.FakeCachedEntryDao
import app.mohakash.apod.testing.FakeApodService
import app.mohakash.apod.testing.FakeSettingsStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The caching behaviour of the repository — in particular the recovery after a failed request.
 */
class ApodRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val latestJson = """
        {
          "schema_version": "1.0.0",
          "date": "2026-10-22",
          "title": "The First Green Star",
          "explanation": "Text",
          "media_type": "image",
          "image_url": "https://example.org/photo.jpg?w=1600",
          "hd_image_url": "https://example.org/photo.jpg?w=2400",
          "thumbnail_url": "https://example.org/photo.jpg?w=480",
          "apod_url": "https://example.org/page",
          "copyright": "Mark A. Garlick",
          "image_status": "ok",
          "updated_at": "2026-10-22T12:17:44Z"
        }
    """.trimIndent()

    private val indexJson = """
        {
          "schema_version": "1.0.0",
          "count": 1,
          "entries": [
            {
              "date": "2026-10-22",
              "title": "The First Green Star",
              "media_type": "image",
              "thumbnail_url": "https://example.org/photo.jpg?w=480",
              "image_url": "https://example.org/photo.jpg?w=1600",
              "apod_url": "https://example.org/page",
              "copyright": "Mark A. Garlick"
            }
          ]
        }
    """.trimIndent()

    private fun repository(
        api: FakeApodService,
        documents: FakeCacheDocumentDao = FakeCacheDocumentDao(),
        entries: FakeCachedEntryDao = FakeCachedEntryDao(),
        settings: FakeSettingsStore = FakeSettingsStore(),
    ) = ApodRepository(api, documents, entries, settings, json) { 1_000L }

    @Test
    fun `a successful fetch caches the document and the record`() = runTest {
        val api = FakeApodService(latestJson = latestJson)
        val documents = FakeCacheDocumentDao()
        val entries = FakeCachedEntryDao()
        val settings = FakeSettingsStore()
        val repository = repository(api, documents, entries, settings)

        val entry = repository.fetchLatest()

        assertEquals("2026-10-22", entry.date)
        assertNotNull(documents.get(CacheDocumentEntity.KEY_LATEST))
        assertNotNull(entries.getByDate("2026-10-22"))
        assertEquals(1_000L, settings.current().lastSyncAt)
        assertEquals("2026-10-22", settings.current().lastSyncDate)
    }

    @Test
    fun `the cached copy is returned when the network fails`() = runTest {
        val api = FakeApodService(latestJson = latestJson)
        val documents = FakeCacheDocumentDao()
        val repository = repository(api, documents)

        repository.fetchLatest()

        api.error = IOException("offline")
        val cached = repository.cachedLatest()

        assertNotNull(cached)
        assertEquals("2026-10-22", cached?.date)
        assertEquals("The First Green Star", cached?.title)
    }

    @Test
    fun `a failed fetch does not destroy the cached copy`() = runTest {
        val api = FakeApodService(latestJson = latestJson)
        val documents = FakeCacheDocumentDao()
        val repository = repository(api, documents)

        repository.fetchLatest()
        api.error = IOException("offline")

        val failed = runCatching { repository.fetchLatest() }
        assertTrue(failed.isFailure)

        assertNotNull(repository.cachedLatest())
    }

    @Test
    fun `without a cache a failed fetch returns nothing`() = runTest {
        val api = FakeApodService(error = IOException("offline"))
        val repository = repository(api)

        assertNull(repository.cachedLatest())
    }

    @Test
    fun `the archive index is fetched every time and never stored`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson)
        val documents = FakeCacheDocumentDao()
        val repository = repository(api, documents)

        assertEquals(1, repository.fetchArchiveIndex().size)
        assertEquals(1, repository.fetchArchiveIndex().size)

        assertEquals(listOf("/archive-index.json", "/archive-index.json"), api.requestedPaths)
        assertNull(documents.get(CacheDocumentEntity.KEY_ARCHIVE_INDEX))
    }

    @Test
    fun `a previously cached archive index is deleted`() = runTest {
        val documents = FakeCacheDocumentDao()
        documents.put(
            CacheDocumentEntity(
                key = CacheDocumentEntity.KEY_ARCHIVE_INDEX,
                json = """{"entries":[]}""",
                updatedAt = 1L,
            ),
        )
        val repository = repository(FakeApodService(archiveIndexJson = indexJson), documents)

        repository.fetchArchiveIndex()

        assertNull(documents.get(CacheDocumentEntity.KEY_ARCHIVE_INDEX))
    }

    @Test
    fun `a single year is fetched and cached`() = runTest {
        val api = FakeApodService(archiveYearJson = """{"year":2026,"records":[$latestJson]}""")
        val entries = FakeCachedEntryDao()
        val repository = repository(api, entries = entries)

        val records = repository.fetchArchiveYear(2026)

        assertEquals(1, records.size)
        assertEquals(listOf("/archive/2026.json"), api.requestedPaths)
        assertNotNull(entries.getByDate("2026-10-22"))
    }

    @Test
    fun `an entry can be found in the local cache`() = runTest {
        val api = FakeApodService(latestJson = latestJson)
        val entries = FakeCachedEntryDao()
        val repository = repository(api, entries = entries)

        repository.fetchLatest()

        val found = repository.findCachedEntry("2026-10-22")

        assertNotNull(found)
        assertEquals("The First Green Star", found?.title)
        assertNull(repository.findCachedEntry("2020-01-01"))
    }
}
