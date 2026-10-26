package app.mohakash.apod.data.repository

import app.mohakash.apod.data.remote.ApodService
import app.mohakash.apod.testing.FakeApodService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * The index is the only way the feed knows the order of 11k dates, so its cache has to be robust:
 * it survives restarts and offline use, it is written atomically, and a corrupt, oversized or
 * unreadable copy is quarantined and refetched instead of crashing the feed.
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
    fun `refresh stores the raw payload and a sidecar without re-serialising it`() = runTest {
        val payload = indexJson("2026-10-22" to "Older", "2026-10-23" to "Newest")
        val api = FakeApodService(archiveIndexJson = payload)
        val first = repository(api)

        val result = first.refresh()

        assertTrue(result.isSuccess)
        assertTrue(cacheFile.isFile)
        // The cache file is the server's document, byte for byte: no wrapper, no re-encoding.
        assertEquals(payload, cacheFile.readText())
        assertFalse(cacheFile.readText().contains("\"savedAt\""))

        val meta = first.metaFile.readText()
        assertTrue(meta, meta.length < 1024)
        assertTrue(meta, meta.contains("\"savedAt\":1000"))
        assertTrue(meta, meta.contains("\"apiBaseUrl\":\"https://example.org/api/v1\""))
        assertTrue(meta, meta.contains("\"sizeBytes\":${payload.toByteArray().size}"))
        assertEquals(ApodService.DEFAULT_MAX_RESPONSE_BYTES, api.requestedMaxBytes.single())

        // A new process: no network is allowed to run, the disk copy plus the sidecar are enough.
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
        val firstPayload = indexJson("2026-10-22" to "Old")
        val api = FakeApodService(archiveIndexJson = firstPayload)
        val repository = repository(api)
        repository.refresh()

        clock = 2_000L
        val secondPayload = indexJson("2026-10-22" to "Old", "2026-10-23" to "New")
        api.archiveIndexJson = secondPayload
        val refreshed = repository.refresh()

        assertEquals(2, refreshed.getOrThrow().size)
        assertEquals(2_000L, repository.snapshot.value?.savedAt)
        assertEquals(secondPayload, cacheFile.readText())
        assertTrue(repository.metaFile.readText().contains("\"savedAt\":2000"))
        assertEquals(listOf("2026-10-23", "2026-10-22"), repository.loadCached()!!.map { it.date })
        // A refresh must never leave a temporary file behind.
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
    fun `a corrupt cache file is quarantined and refetched`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("this is not json {")
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Fresh"))
        val repository = repository(api)

        assertNull(repository.loadCached())
        assertFalse(cacheFile.exists())
        assertTrue(repository.quarantineFile.isFile)

        val refreshed = repository.refresh()
        assertEquals("Fresh", refreshed.getOrThrow().single().title)
        assertEquals(1, repository.loadCached()!!.size)
        assertTrue(cacheFile.isFile)
    }

    @Test
    fun `an oversized cache file is quarantined before it is read`() = runTest {
        cacheFile.parentFile?.mkdirs()
        // A sparse file of the reported size: no 16 MiB buffer is allocated for the test.
        RandomAccessFile(cacheFile, "rw").use { it.setLength(ARCHIVE_INDEX_MAX_CACHE_BYTES + 1L) }
        val repository = repository(FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Fresh")))

        assertFalse(repository.hasCachedIndex())
        assertNull(repository.loadCached())
        assertFalse(cacheFile.exists())
        assertTrue(repository.quarantineFile.isFile)
        assertEquals(ARCHIVE_INDEX_MAX_CACHE_BYTES + 1L, repository.quarantineFile.length())

        // The next refresh recovers instead of inheriting the oversized file.
        assertTrue(repository.refresh().isSuccess)
        assertEquals("Fresh", repository.loadCached()!!.single().title)
    }

    @Test
    fun `an out of memory error while reading the cache is contained and the file quarantined`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("""{"savedAt":1,"entries":[{"date":"2026-10-22","title":"Explodes"}]}""")
        // Simulates a pathological file/device where even measuring the cache throws OOM.
        val exploding = object : File(cacheFile.path) {
            override fun length(): Long = throw OutOfMemoryError("simulated")
        }
        val repository = FileArchiveIndexRepository(
            api = FakeApodService(error = IOException("offline")),
            json = json,
            cacheFile = exploding,
            now = { clock },
        )

        assertNull(repository.loadCached())
        assertFalse(cacheFile.exists())
        assertTrue(repository.quarantineFile.isFile)
    }

    @Test
    fun `leftover temporary files are swept when the cache is read`() = runTest {
        cacheFile.parentFile?.mkdirs()
        val temporary = File(cacheFile.parentFile, "archive-index-orphan.tmp").apply { writeText("{}") }
        val unrelated = File(cacheFile.parentFile, "keep-me.tmp").apply { writeText("{}") }
        val repository = repository(FakeApodService())

        assertNull(repository.loadCached())
        assertFalse(temporary.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `a legacy wrapper cache is still readable and its sidecar values are honoured`() = runTest {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(
            """{"savedAt":123,"apiBaseUrl":"https://legacy.example/api/v1","entries":[{"date":"2026-10-22","title":"Legacy"}]}""",
        )
        val repository = repository(FakeApodService())

        val cached = repository.loadCached()

        assertNotNull(cached)
        assertEquals("Legacy", cached!!.single().title)
        assertEquals(123L, repository.snapshot.value?.savedAt)
        assertEquals("https://legacy.example/api/v1", repository.snapshot.value?.apiBaseUrl)
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
        assertTrue(repository.metaFile.isFile)

        repository.clear()

        assertFalse(cacheFile.isFile)
        assertFalse(repository.metaFile.isFile)
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
    fun `clear during an in-flight refresh wins and no old endpoint data is written`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = FakeApodService(archiveIndexJson = indexJson("2026-10-22" to "Old endpoint")).apply {
            archiveIndexStarted = started
            archiveIndexGate = release
        }
        val repository = repository(api)

        val refresh = async(Dispatchers.Default) { repository.refresh() }

        withTimeout(10_000) { started.await() }
        repository.clear()
        release.complete(Unit)

        val result = refresh.await()
        assertTrue(result.isFailure)
        assertFalse(repository.hasError.value)
        assertFalse(cacheFile.exists())
        assertFalse(repository.metaFile.exists())
        assertNull(repository.loadCached())
        assertTrue(repository.entries.value.isEmpty())
        assertNull(repository.snapshot.value)

        // A refresh that starts *after* the clear is allowed to write again.
        api.archiveIndexStarted = null
        api.archiveIndexGate = null
        api.archiveIndexJson = indexJson("2026-10-24" to "New endpoint")
        val afterClear = withTimeout(10_000) { repository.refresh() }
        assertTrue(afterClear.isSuccess)
        assertEquals("New endpoint", repository.loadCached()!!.single().title)
        assertTrue(cacheFile.isFile)
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
