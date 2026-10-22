package app.mohakash.apod.data.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The atomic swap of the wallpaper picture and its metadata.
 */
class WallpaperStorageTest {

    @Test
    fun `commit stores the picture and its metadata without leaving temp files`() = runTest {
        val root = Files.createTempDirectory("mohakash-wallpaper").toFile()
        try {
            val storage = WallpaperStorage(root)
            val source = root.resolve("source.jpg").apply { writeBytes(ByteArray(32) { 1 }) }

            val result = storage.commit(
                source,
                WallpaperMeta(
                    date = "2026-10-23",
                    title = "A New Picture",
                    sourceUrl = "https://example.org/new.jpg",
                    fileName = "",
                    updatedAt = 1L,
                ),
            )

            assertTrue(result.isSuccess)
            val file = storage.currentImageFile()
            assertNotNull(file)
            assertTrue(file!!.isFile)
            assertTrue(file.length() > 0)
            assertEquals("https://example.org/new.jpg", storage.readMeta()?.sourceUrl)
            assertTrue(
                storage.storageDirectory.listFiles().orEmpty().none { it.name.startsWith("incoming-") },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a failed commit keeps the previous picture`() = runTest {
        val root = Files.createTempDirectory("mohakash-wallpaper").toFile()
        try {
            val storage = WallpaperStorage(root)
            val first = root.resolve("first.jpg").apply { writeBytes(ByteArray(16) { 1 }) }
            storage.commit(
                first,
                WallpaperMeta(
                    date = "2026-10-22",
                    title = "First",
                    sourceUrl = "https://example.org/first.jpg",
                    fileName = "",
                    updatedAt = 1L,
                ),
            )
            val previous = storage.currentImageFile()
            assertNotNull(previous)

            val second = root.resolve("second.jpg").apply { writeBytes(ByteArray(16) { 2 }) }
            val result = storage.commit(
                second,
                WallpaperMeta(
                    date = "2026-10-23",
                    title = "Second",
                    sourceUrl = "https://example.org/article.html",
                    fileName = "",
                    updatedAt = 2L,
                ),
            )

            assertTrue(result.isFailure)
            assertEquals(previous, storage.currentImageFile())
            assertEquals("First", storage.readMeta()?.title)
        } finally {
            root.deleteRecursively()
        }
    }
}
