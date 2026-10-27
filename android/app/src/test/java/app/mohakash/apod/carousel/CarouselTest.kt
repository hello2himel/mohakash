package app.mohakash.apod.carousel

import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.testing.FakeArchiveIndexRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

class CarouselTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun index(
        date: String,
        title: String = "Title $date",
        mediaType: MediaType = MediaType.IMAGE,
        imageUrl: String? = "https://example.org/$date.jpg",
    ) = ArchiveIndexEntry(
        date = date,
        title = title,
        mediaType = mediaType,
        thumbnailUrl = null,
        imageUrl = imageUrl,
        apodUrl = null,
        copyright = "Credit $date",
        detailPath = null,
    )

    @Test
    fun `month-day includes leap day and respects the timezone`() {
        val calendar = Calendar.getInstance(utc).apply {
            clear()
            set(2024, Calendar.FEBRUARY, 29, 12, 0, 0)
        }
        assertEquals("02-29", CarouselMath.monthDay(calendar.timeInMillis, utc))
        val next = Calendar.getInstance(utc).apply {
            clear()
            set(2024, Calendar.MARCH, 1, 0, 1, 0)
        }
        assertEquals("03-01", CarouselMath.monthDay(next.timeInMillis, utc))
    }

    @Test
    fun `month-day uses ASCII digits even in a Bengali locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("bn"))
            val calendar = Calendar.getInstance(utc).apply {
                clear()
                set(2026, Calendar.OCTOBER, 28, 12, 0, 0)
            }
            assertEquals("10-28", CarouselMath.monthDay(calendar.timeInMillis, utc))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `interval is exactly 24 hours divided by the valid count`() {
        assertEquals(CarouselMath.DAY_MILLIS / 6, CarouselMath.intervalMillis(6))
        assertEquals(CarouselMath.DAY_MILLIS / 24, CarouselMath.intervalMillis(24))
        assertEquals(CarouselMath.DAY_MILLIS / 48, CarouselMath.intervalMillis(48))
        assertNull(CarouselMath.intervalMillis(0))
    }

    @Test
    fun `candidates are image-only, date-matched and de-duplicated`() {
        val entries = listOf(
            index("2026-01-01"),
            index("2025-01-01"),
            index("2024-01-01", mediaType = MediaType.VIDEO),
            index("2023-01-01", imageUrl = "https://example.org/page.html"),
            index("2022-01-02"),
            index("2021-01-01"),
            index("2021-01-01"),
        )
        val candidates = CarouselMath.candidates(entries, "01-01")
        assertEquals(listOf("2026-01-01", "2025-01-01", "2021-01-01"), candidates.map { it.date })
    }

    @Test
    fun `thumbnail-only index entries are upscaled for wallpaper use`() {
        val entry = ArchiveIndexEntry(
            date = "2024-10-27",
            title = "Thumbnail only",
            mediaType = MediaType.IMAGE,
            thumbnailUrl = "https://example.org/photo.jpg?w=480",
            imageUrl = null,
            apodUrl = null,
            copyright = null,
            detailPath = null,
        )
        assertEquals(
            "https://example.org/photo.jpg?w=1600",
            CarouselMath.candidates(listOf(entry), "10-27").single().imageUrl,
        )
    }

    @Test
    fun `next avoids the immediately previous image when alternatives exist`() {
        val items = listOf(index("2026-01-01"), index("2025-01-01")).map {
            CarouselItem(it.date, it.title, it.imageUrl!!, it.displayCredit)
        }
        repeat(20) {
            assertNotEquals("2026-01-01", CarouselMath.next(items, "2026-01-01", Random(it))!!.date)
        }
        assertEquals("2026-01-01", CarouselMath.next(listOf(items.first()), "2026-01-01")!!.date)
    }

    @Test
    fun `discovery keeps only validated photographs and exposes the interval count`() = runTest {
        val dir = Files.createTempDirectory("carousel-test").toFile()
        try {
            val entries = listOf(
                index("2026-01-01"),
                index("2025-01-01"),
                index("2024-01-01"),
                index("2023-01-01"),
            )
            val repository = FakeArchiveIndexRepository(initial = entries)
            val validator = FakeValidator(
                valid = setOf(
                    "https://example.org/2026-01-01.jpg",
                    "https://example.org/2024-01-01.jpg",
                ),
            )
            val discovery = CarouselDiscovery(
                archiveIndexRepository = repository,
                validator = validator,
                collectionStore = CarouselCollectionStore(File(dir, "collection.json")),
                now = { 1_000_000L },
                timeZone = { utc },
            )
            val collection = discovery.load(forceRefresh = true)!!
            assertEquals("01-01", collection.monthDay)
            assertEquals(listOf("2026-01-01", "2024-01-01"), collection.items.map { it.date })
            assertEquals(CarouselMath.DAY_MILLIS / 2, CarouselMath.intervalMillis(collection.items.size))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an empty date collection is safe and has no interval`() = runTest {
        val dir = Files.createTempDirectory("carousel-empty").toFile()
        try {
            val discovery = CarouselDiscovery(
                archiveIndexRepository = FakeArchiveIndexRepository(
                    initial = listOf(index("2026-02-28")),
                ),
                validator = FakeValidator(),
                collectionStore = CarouselCollectionStore(File(dir, "collection.json")),
                now = { 1_000_000L },
                timeZone = { utc },
            )
            val collection = discovery.load(forceRefresh = true)!!
            assertTrue(collection.items.isEmpty())
            assertNull(CarouselMath.intervalMillis(collection.items.size))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `temporary validation failures keep previously cached pictures`() = runTest {
        val dir = Files.createTempDirectory("carousel-test").toFile()
        try {
            val entries = listOf(index("2026-01-01"), index("2025-01-01"))
            val repository = FakeArchiveIndexRepository(initial = entries)
            val store = CarouselCollectionStore(File(dir, "collection.json"))
            store.write(
                CarouselCollection(
                    monthDay = "01-01",
                    validatedAt = 1_000_000L,
                    items = listOf(
                        CarouselItem("2026-01-01", "Cached", "https://example.org/2026-01-01.jpg"),
                    ),
                ),
            )
            val discovery = CarouselDiscovery(
                archiveIndexRepository = repository,
                validator = FakeValidator(temporary = entries.mapNotNull { it.imageUrl }.toSet()),
                collectionStore = store,
                now = { 1_000_000L },
                timeZone = { utc },
            )
            val collection = discovery.load(forceRefresh = true)!!
            assertEquals(listOf("2026-01-01"), collection.items.map { it.date })
            assertTrue(collection.hadTemporaryFailures)
        } finally {
            dir.deleteRecursively()
        }
    }

    private class FakeValidator(
        private val valid: Set<String> = emptySet(),
        private val temporary: Set<String> = emptySet(),
    ) : CarouselImageValidator {
        override suspend fun validate(url: String): CarouselValidation = when {
            url in valid -> CarouselValidation.Valid
            url in temporary -> CarouselValidation.TemporaryFailure
            else -> CarouselValidation.Invalid
        }
    }
}
