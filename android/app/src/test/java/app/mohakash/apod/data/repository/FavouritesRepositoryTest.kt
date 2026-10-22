package app.mohakash.apod.data.repository

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.testing.FakeFavouriteDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving pictures — the DAO logic behind the heart button.
 */
class FavouritesRepositoryTest {

    private fun entry(date: String = "2026-10-22") = ApodEntry(
        date = date,
        title = "The First Green Star",
        explanation = "Some explanation.",
        mediaType = MediaType.IMAGE,
        imageUrl = "https://example.org/photo.jpg?w=1600",
        hdImageUrl = "https://example.org/photo.jpg?w=2400",
        thumbnailUrl = "https://example.org/photo.jpg?w=480",
        apodUrl = "https://example.org/page",
        copyright = "Mark A. Garlick",
        credit = "Mark A. Garlick",
        altText = "A star",
        source = "NASA APOD",
        sourceId = "1497603",
        sourceMediaType = "image",
        imageStatus = ImageStatus.OK,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )

    @Test
    fun `an entry can be saved and removed again`() = runTest {
        val dao = FakeFavouriteDao()
        val repository = FavouritesRepository(dao) { 1_000L }

        assertTrue(repository.toggle(entry()))

        assertEquals(1, dao.count())
        assertTrue(repository.isFavourite("2026-10-22"))

        assertFalse(repository.toggle(entry()))

        assertEquals(0, dao.count())
        assertFalse(repository.isFavourite("2026-10-22"))
    }

    @Test
    fun `a saved entry keeps the whole metadata so it works offline`() = runTest {
        val dao = FakeFavouriteDao()
        val repository = FavouritesRepository(dao) { 1_000L }

        repository.toggle(entry())

        val saved = repository.observeAll().first().single()
        assertEquals("2026-10-22", saved.date)
        assertEquals("The First Green Star", saved.title)
        assertEquals("Some explanation.", saved.explanation)
        assertEquals(MediaType.IMAGE, saved.mediaType)
        assertEquals(ImageStatus.OK, saved.imageStatus)
        assertEquals("https://example.org/photo.jpg?w=1600", saved.imageUrl)
        assertEquals("https://example.org/photo.jpg?w=2400", saved.hdImageUrl)
        assertEquals("https://example.org/photo.jpg?w=480", saved.thumbnailUrl)
        assertEquals("https://example.org/page", saved.apodUrl)
        assertEquals("Mark A. Garlick", saved.displayCredit)
    }

    @Test
    fun `the saved list is ordered by the time it was saved`() = runTest {
        val dao = FakeFavouriteDao()
        var clock = 1_000L
        val repository = FavouritesRepository(dao) { clock }

        repository.toggle(entry("2026-10-21"))
        clock = 2_000L
        repository.toggle(entry("2026-10-22"))

        val dates = repository.observeAll().first().map { it.date }
        assertEquals(listOf("2026-10-22", "2026-10-21"), dates)
    }

    @Test
    fun `toggling an entry adds and removes it`() = runTest {
        val dao = FakeFavouriteDao()
        val repository = FavouritesRepository(dao) { 1_000L }

        repository.toggle(entry())
        assertTrue(repository.isFavourite("2026-10-22"))
        assertEquals(1, dao.count())

        repository.toggle(entry())
        assertFalse(repository.isFavourite("2026-10-22"))
        assertEquals(0, dao.count())

        repository.toggle(entry())
        assertTrue(repository.isFavourite("2026-10-22"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `the favourite state of one entry can be observed`() = runTest {
        val dao = FakeFavouriteDao()
        val repository = FavouritesRepository(dao) { 1_000L }

        assertNull(repository.getByDate("2026-10-22"))

        repository.toggle(entry())

        assertNotNull(repository.getByDate("2026-10-22"))
        assertEquals(1, repository.observeCount().first())
    }
}
