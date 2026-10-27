package app.mohakash.apod.history

import app.mohakash.apod.carousel.CarouselItem
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HistoryRepositoryTest {

    private fun entry(date: String, title: String = "Entry $date") = ApodEntry(
        date = date,
        title = title,
        explanation = "explanation",
        mediaType = MediaType.IMAGE,
        imageUrl = "https://example.org/$date.jpg",
        hdImageUrl = null,
        thumbnailUrl = "https://example.org/$date-thumb.jpg",
        apodUrl = "https://apod.nasa.gov/apod/ap$date.html",
        copyright = "Credit",
        credit = null,
        altText = "alt",
        source = "NASA APOD",
        sourceId = null,
        sourceMediaType = "image",
        imageStatus = ImageStatus.OK,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )

    @Test
    fun `random and carousel history are separate and moved to the top`() {
        val dir = Files.createTempDirectory("history-test").toFile()
        try {
            val store = HistoryStore(File(dir, "history.json"), Json { ignoreUnknownKeys = true })
            var clock = 1_000L
            val repository = HistoryRepository(store) { clock++ }

            repository.recordRandom(entry("2026-10-26"))
            repository.recordCarousel(CarouselItem("2025-10-26", "Carousel", "https://example.org/c.jpg"))
            repository.recordRandom(entry("2026-10-25"))

            val first = repository.items.value
            assertEquals(3, first.size)
            assertEquals(HistorySource.RANDOM, first[0].source)
            assertEquals("2026-10-25", first[0].date)
            assertEquals(HistorySource.CAROUSEL, first[1].source)

            // The same date/source moves to the top instead of duplicating.
            repository.recordRandom(entry("2026-10-26"))
            val second = repository.items.value
            assertEquals(3, second.size)
            assertEquals("2026-10-26", second[0].date)
            assertEquals(HistorySource.RANDOM, second[0].source)

            // A fresh repository reads the persisted list.
            val reopened = HistoryRepository(store) { clock++ }
            assertEquals(second.map { it.date to it.source }, reopened.items.value.map { it.date to it.source })
        } finally {
            dir.deleteRecursively()
        }
    }
}
