package app.mohakash.apod.data.repository

import app.mohakash.apod.testing.FakeApodService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * The index is the only way the feed knows the order of 11k dates, so its cache has to be robust:
 * it survives restarts and offline use, it is written atomically, and a corrupt copy is simply
 * ignored and refetched instead of crashing the feed.
 */
class ArchiveIndexRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private lateinit var directory: File
    private lateinit var cacheFile: File

    private var clock: Long = 1_000L

    private fun indexJson(
        vararg entries: Pair<String, String>,
        apiBaseUrl: String = "https://example.org/api/v1",
    ): String {
        val rows = entries.joinToString(",") { (date, title) ->
            """{"date":"$date","title":"$title","media_type":"image","thumbnail_url":"https://example.org/$date.jpg"}"""
        }
        return """{"schema_version":"1.0.0","count":${entries.size},"api_base_url":"$apiBaseUrl","entries":[$rows]}"""
    }

    private fun repository(api: FakeApodService) = FileArchiveIndexRepository(
        api = api,
        json = json,
        cacheFile = cacheFile,
        now = { clock },
    )

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("mohakash-index-test").toFile()
        cacheFile = File(directory, "feed/archive-index.json")
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `refresh writes the wrapper and the next instance reads it without the network`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Older", "2026-10-23" to "Newest"))
        val first = repository(api)

        val result = first.refresh()

        assertTrue(result.isSuccess)
        assertTrue(cacheFile.isFile)
        val text = cacheFile.readText()
        assertTrue(text.contains("\"savedAt\":1000"))
        assertTrue(text.contains("\"apiBaseUrl\":\"https://example.org/api/v1\""))

        // A new process: no network is allowed to run, the disk copy is enough.
        val offlineApi = FakeApodService(error = IOException("offline"))
        val second = repository(offlineApi)
        val cached = second.loadCached()

        assertNotNull(cached)
        assertEquals(listOf("2026-10-23", "2026-10-22"), cached!!.map { it.date })
        assertTrue(offlineApi.requestedPaths.isEmpty())
        assertEquals(1000L, second.snapshot.value?.savedAt)
        assertEquals("https://example.org/api/v1", second.snapshot.value?.apiBaseUrl)
    }

    @Test
    fun `refresh replaces the cache and updates the timestamp`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Old"))
        val repository = repository(api)
        repository.refresh()

        clock = 2_000L
        api.archiveIndexJson = indexJson("2026-10-22" to "Old", "2026-10-23" to "New")
        val refreshed = repository.refresh()

        assertEquals(2, refreshed.getOrThrow().size)
        assertEquals(2000L, repository.snapshot.value?.savedAt)
        assertEquals(listOf("2026-10-23", "2026-10-22"), repository.loadCached()!!.map { it.date })
        // A failed refresh must never leave a temporary file behind.
        assertEquals(
            emptyList<String>(),
            directory.walkTopDown().filter { it.isFile && it.name.startsWith("archive-index-") }.toList()
                .map { it.name },
        )
    }

    @Test
    fun `a failed refresh keeps the old cache`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Cached"))
        val repository = repository(api)
        repository.refresh()
        val before = repository.snapshot.value

        api.error = IOException("offline")
        clock = 5_000L
        val failed = repository.refresh()

        assertTrue(failed.isFailure)
        assertEquals(before?.entries, repository.loadCached())
        assertEquals(before?.savedAt, repository.snapshot.value?.savedAt)
        assertTrue(cacheFile.isFile)
        assertTrue(repository.hasError.value)
    }

    @Test
    fun `a corrupt cache file is ignored and refetched`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("this is not json {")
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Fresh"))
        val repository = repository(api)

        assertNull(repository.loadCached())

        val refreshed = repository.refresh()
        assertEquals("Fresh", refreshed.getOrThrow().single().title)
        assertEquals(1, repository.loadCached()!!.size)
    }

    @Test
    fun `an empty entries array is treated as no cache`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("""{"savedAt":123,"entries":[]}""")
        val repository = repository(FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Fresh")))

        assertNull(repository.loadCached())
    }

    @Test
    fun `a wrapper without a savedAt uses the file timestamp`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("""{"entries":[{"date":"2026-10-22","title":"No timestamp"}]}""")

        val repository = repository(FakeApodService())
        val cached = repository.loadCached()

        assertNotNull(cached)
        assertEquals("No timestamp", cached!!.single().title)
        assertNotNull(repository.snapshot.value)
        assertTrue((repository.snapshot.value?.savedAt ?: 0L) > 0L)
    }

    @Test
    fun `clear removes the file and the memory copy`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Cached"))
        val repository = repository(api)
        repository.refresh()
        assertTrue(cacheFile.isFile)

        repository.clear()

        assertFalse(cacheFile.isFile)
        assertNull(repository.loadCached())
        assertTrue(repository.entries.value.isEmpty())
        assertNull(repository.snapshot.value)
        assertFalse(repository.hasCachedIndex())
    }

    @Test
    fun `an empty index response is a failure and does not overwrite the cache`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Cached"))
        val repository = repository(api)
        repository.refresh()

        api.archiveIndexJson = """{"entries":[]}"""
        val failed = repository.refresh()

        assertTrue(failed.isFailure)
        assertEquals(1, repository.loadCached()!!.size)
    }

    @Test
    fun `ensureLoaded respects the offline flag and uses the network only when allowed`() = runTest {
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Fresh"))
        val repository = repository(api)

        assertNull(repository.ensureLoaded(allowNetwork = false))
        assertTrue(api.requestedPaths.isEmpty())

        val loaded = repository.ensureLoaded(allowNetwork = true)
        assertNotNull(loaded)
        assertEquals(1, loaded!!.size)
        assertEquals(listOf("/archive-index.json"), api.requestedPaths)
        // The second call is served from memory.
        assertEquals(1, repository.ensureLoaded()!!.size)
        assertEquals(1, api.requestedPaths.size)
    }

    @Test
    fun `a failed network load leaves the repository empty without throwing`() = runTest {
        val api = FakeApodService(error = IOException("offline"))
        val repository = repository(api)

        assertNull(repository.ensureLoaded(allowNetwork = true))
        assertTrue(repository.entries.value.isEmpty())
        assertNull(repository.snapshot.value)
    }

    @Test
    fun `hasCachedIndex reports the disk copy even before it is parsed`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("""{"savedAt":1,"entries":[{"date":"2026-10-22","title":"x"}]}""")
        assertTrue(repository(FakeApodService()).hasCachedIndex())
    }
}
