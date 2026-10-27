package app.mohakash.apod.work

import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.repository.LatestEntrySource
import app.mohakash.apod.data.storage.PictureDownloader
import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.data.storage.WallpaperPictureStore
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.testing.FakeLatestEntrySource
import app.mohakash.apod.testing.FakePictureDownloader
import app.mohakash.apod.testing.FakeSettingsStore
import app.mohakash.apod.testing.FakeWallpaperPictureStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The behaviour of the background synchronisation.
 */
class ApodSyncEngineTest {

    private fun entry(
        date: String = "2026-10-22",
        mediaType: MediaType = MediaType.IMAGE,
        imageStatus: ImageStatus = ImageStatus.OK,
        imageUrl: String? = "https://example.org/photo.jpg?w=1600",
        hdImageUrl: String? = "https://example.org/photo.jpg?w=2400",
        thumbnailUrl: String? = "https://example.org/photo.jpg?w=480",
    ) = ApodEntry(
        date = date,
        title = "The First Green Star",
        explanation = "Some explanation.",
        mediaType = mediaType,
        imageUrl = imageUrl,
        hdImageUrl = hdImageUrl,
        thumbnailUrl = thumbnailUrl,
        apodUrl = "https://example.org/page",
        copyright = "Mark A. Garlick",
        credit = null,
        altText = null,
        source = "NASA APOD",
        sourceId = null,
        sourceMediaType = null,
        imageStatus = imageStatus,
        updatedAt = null,
        lastSuccessfulSync = null,
        latestAvailableDate = null,
        recordCount = null,
        sourceStatus = null,
    )

    private fun engine(
        source: LatestEntrySource,
        settings: SettingsStore = FakeSettingsStore(),
        store: WallpaperPictureStore = FakeWallpaperPictureStore(),
        downloader: PictureDownloader = FakePictureDownloader(),
    ) = ApodSyncEngine(source, settings, store, downloader) { 5_000L }

    @Test
    fun `a new entry is downloaded and installed`() = runTest {
        val source = FakeLatestEntrySource(entry())
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()

        val outcome = engine(source, store = store, downloader = downloader).sync()

        assertTrue(outcome is SyncOutcome.Updated)
        assertEquals(true, source.lastForceNetwork)
        assertEquals(listOf("https://example.org/photo.jpg?w=1600"), downloader.downloadedUrls)
        assertEquals("2026-10-22", store.meta?.date)
        assertEquals("https://example.org/photo.jpg?w=1600", store.meta?.sourceUrl)
    }

    @Test
    fun `a known latest entry can be installed without fetching metadata again`() = runTest {
        val source = FakeLatestEntrySource(entry())
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()

        val outcome = engine(source, store = store, downloader = downloader).syncEntry(entry())

        assertTrue(outcome is SyncOutcome.Updated)
        assertEquals(0, source.callCount)
        assertEquals("2026-10-22", store.meta?.date)
    }

    @Test
    fun `notifications are sent only when the new-picture setting is enabled`() = runTest {
        var notified = 0
        val source = FakeLatestEntrySource(entry())
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        val app = app.mohakash.apod.domain.model.AppSettings(notificationsEnabled = true)
        val engine = ApodSyncEngine(
            source,
            FakeSettingsStore(app),
            store,
            downloader,
            notifyWallpaperChanged = { notified++ },
        ) { 5_000L }

        engine.sync()
        assertEquals(1, notified)

        val secondStore = FakeWallpaperPictureStore()
        var secondNotified = 0
        val quiet = ApodSyncEngine(
            source,
            FakeSettingsStore(app.copy(notificationsEnabled = false)),
            secondStore,
            downloader,
            notifyWallpaperChanged = { secondNotified++ },
        ) { 5_000L }
        quiet.sync()
        assertEquals(0, secondNotified)
    }

    @Test
    fun `the hd picture is used when it is preferred`() = runTest {
        val downloader = FakePictureDownloader()
        val settings = FakeSettingsStore(app.mohakash.apod.domain.model.AppSettings(preferHd = true))

        engine(
            FakeLatestEntrySource(entry()),
            settings = settings,
            downloader = downloader,
        ).sync()

        assertEquals(listOf("https://example.org/photo.jpg?w=2400"), downloader.downloadedUrls)
    }

    @Test
    fun `nothing is downloaded when neither the date nor the url changed`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        store.meta = WallpaperMeta(
            date = "2026-10-22",
            title = "The First Green Star",
            credit = "Mark A. Garlick",
            sourceUrl = "https://example.org/photo.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader).sync()

        assertTrue(outcome is SyncOutcome.Unchanged)
        assertTrue(downloader.downloadedUrls.isEmpty())
        assertEquals(0, store.commitCount)
    }

    @Test
    fun `a new date triggers a new download`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        store.meta = WallpaperMeta(
            date = "2026-10-21",
            title = "The First Green Star",
            credit = null,
            sourceUrl = "https://example.org/photo.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader).sync()

        assertTrue(outcome is SyncOutcome.Updated)
        assertEquals(1, downloader.downloadedUrls.size)
    }

    @Test
    fun `a new url for the same date triggers a new download`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        store.meta = WallpaperMeta(
            date = "2026-10-22",
            title = "The First Green Star",
            credit = null,
            sourceUrl = "https://example.org/other.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader).sync()

        assertTrue(outcome is SyncOutcome.Updated)
        assertEquals(1, downloader.downloadedUrls.size)
    }

    @Test
    fun `a video entry without a poster keeps the previous wallpaper`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        val previous = WallpaperMeta(
            date = "2026-10-21",
            title = "Older",
            credit = null,
            sourceUrl = "https://example.org/old.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )
        store.meta = previous

        val outcome = engine(
            FakeLatestEntrySource(entry(mediaType = MediaType.VIDEO, thumbnailUrl = null)),
            store = store,
            downloader = downloader,
        ).sync()

        assertTrue(outcome is SyncOutcome.WallpaperUnavailable)
        assertTrue(downloader.downloadedUrls.isEmpty())
        assertEquals(previous, store.meta)
    }

    @Test
    fun `a placeholder picture keeps the previous wallpaper`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        val previous = WallpaperMeta(
            date = "2026-10-21",
            title = "Older",
            credit = null,
            sourceUrl = "https://example.org/old.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )
        store.meta = previous

        val outcome = engine(
            FakeLatestEntrySource(entry(imageStatus = ImageStatus.PLACEHOLDER)),
            store = store,
            downloader = downloader,
        ).sync()

        assertTrue(outcome is SyncOutcome.WallpaperUnavailable)
        assertTrue(downloader.downloadedUrls.isEmpty())
        assertEquals(previous, store.meta)
    }

    @Test
    fun `a failed download keeps the last picture`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader().apply { failWith = IOException("offline") }
        val previous = WallpaperMeta(
            date = "2026-10-21",
            title = "Older",
            credit = null,
            sourceUrl = "https://example.org/old.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )
        store.meta = previous

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader).sync()

        assertTrue(outcome is SyncOutcome.Failed)
        assertEquals(previous, store.meta)
        assertEquals(0, store.commitCount)
    }

    @Test
    fun `a failed request keeps the last picture`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()
        val previous = WallpaperMeta(
            date = "2026-10-21",
            title = "Older",
            credit = null,
            sourceUrl = "https://example.org/old.jpg?w=1600",
            fileName = "current.jpg",
            updatedAt = 1_000L,
        )
        store.meta = previous

        val outcome = engine(
            FakeLatestEntrySource(error = IOException("offline")),
            store = store,
            downloader = downloader,
        ).sync()

        assertTrue(outcome is SyncOutcome.Failed)
        assertEquals(previous, store.meta)
        assertTrue(downloader.downloadedUrls.isEmpty())
    }

    @Test
    fun `a failed commit keeps the last picture`() = runTest {
        val store = FakeWallpaperPictureStore().apply { failCommit = true }
        val downloader = FakePictureDownloader()

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader).sync()

        assertTrue(outcome is SyncOutcome.Failed)
        assertNull(store.meta)
    }

    @Test
    fun `the wallpaper is not touched when the automatic update is switched off`() = runTest {
        val settings = FakeSettingsStore(app.mohakash.apod.domain.model.AppSettings(updateAutomatically = false))
        val downloader = FakePictureDownloader()

        val outcome = engine(
            FakeLatestEntrySource(entry()),
            settings = settings,
            downloader = downloader,
        ).sync(updateWallpaper = false)

        assertTrue(outcome is SyncOutcome.Unchanged)
        assertTrue(downloader.downloadedUrls.isEmpty())
    }

    @Test
    fun `no download happens while the device is offline`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader)
            .sync(networkAvailable = false)

        assertTrue(outcome is SyncOutcome.WallpaperUnavailable)
        assertTrue(downloader.downloadedUrls.isEmpty())
    }

    @Test
    fun `a single entry can be installed as wallpaper`() = runTest {
        val store = FakeWallpaperPictureStore()
        val downloader = FakePictureDownloader()

        val outcome = engine(FakeLatestEntrySource(entry()), store = store, downloader = downloader)
            .install(entry(), preferHd = false)

        assertEquals(InstallOutcome.INSTALLED, outcome)
        assertNotNull(store.meta)
        assertEquals("2026-10-22", store.meta?.date)
    }

    @Test
    fun `a video entry cannot be installed as wallpaper`() = runTest {
        val store = FakeWallpaperPictureStore()

        val outcome = engine(FakeLatestEntrySource(entry()), store = store)
            .install(entry(mediaType = MediaType.VIDEO, thumbnailUrl = null), preferHd = false)

        assertEquals(InstallOutcome.NOT_USABLE, outcome)
        assertNull(store.meta)
    }
}
