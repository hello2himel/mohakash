package app.mohakash.apod.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only real image assets may be downloaded — an article URL is never an image.
 */
class ImageUrlValidatorTest {

    @Test
    fun `the real api urls are valid`() {
        assertTrue(
            ImageUrlValidator.isValidImageUrl(
                "https://assets.science.nasa.gov/dynamicimage/assets/science/cds/apod/apod/2026/october/green_wd.jpg?w=1600",
            ),
        )
        assertTrue(ImageUrlValidator.isValidImageUrl("https://example.org/photo.png"))
        assertTrue(ImageUrlValidator.isValidImageUrl("https://example.org/photo.jpeg"))
        assertTrue(ImageUrlValidator.isValidImageUrl("https://example.org/photo.webp"))
    }

    @Test
    fun `an article url is not an image`() {
        assertFalse(
            ImageUrlValidator.isValidImageUrl(
                "https://science.nasa.gov/image-article/apod-2026-october-22-the-first-green-star/",
            ),
        )
        assertFalse(ImageUrlValidator.isValidImageUrl("https://example.org/page.html"))
        assertFalse(ImageUrlValidator.isValidImageUrl("https://example.org/data.json"))
    }

    @Test
    fun `cleartext and relative urls are rejected`() {
        assertFalse(ImageUrlValidator.isValidImageUrl("http://example.org/photo.jpg"))
        assertFalse(ImageUrlValidator.isValidImageUrl("/photo.jpg"))
        assertFalse(ImageUrlValidator.isValidImageUrl("photo.jpg"))
        assertFalse(ImageUrlValidator.isValidImageUrl(null))
        assertFalse(ImageUrlValidator.isValidImageUrl("  "))
    }

    @Test
    fun `the extension is read from the path and not from the query`() {
        assertEquals("jpg", ImageUrlValidator.extensionOf("https://example.org/photo.jpg?w=1600"))
        assertEquals("png", ImageUrlValidator.extensionOf("https://example.org/a/b/photo.PNG"))
        assertNull(ImageUrlValidator.extensionOf("https://example.org/photo"))
        assertNull(ImageUrlValidator.extensionOf(null))
    }

    @Test
    fun `a url without an extension is rejected`() {
        // The API always serves pictures with a file extension, so anything else is a page.
        assertFalse(ImageUrlValidator.isValidImageUrl("https://example.org/dynamicimage/photo"))
        assertFalse(ImageUrlValidator.isValidImageUrl("https://science.nasa.gov/image-article/apod-2026/"))
    }

    @Test
    fun `page urls are detected`() {
        assertTrue(ImageUrlValidator.looksLikePageUrl("https://example.org/index.html"))
        assertTrue(ImageUrlValidator.looksLikePageUrl("https://example.org/index.php"))
        assertFalse(ImageUrlValidator.looksLikePageUrl("https://example.org/photo.jpg"))
        assertFalse(ImageUrlValidator.looksLikePageUrl("https://example.org/photo"))
    }
}
