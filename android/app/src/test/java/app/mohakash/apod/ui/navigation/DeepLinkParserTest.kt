package app.mohakash.apod.ui.navigation

import app.mohakash.apod.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {

    private val website = BuildConfig.WEBSITE_BASE_URL.trimEnd('/')

    @Test
    fun `root link opens Today`() {
        assertEquals(DeepLinkTarget.Today, DeepLinkParser.parse("$website/"))
    }

    @Test
    fun `host without a path opens Today`() {
        assertEquals(DeepLinkTarget.Today, DeepLinkParser.parse(website))
    }

    @Test
    fun `archive index opens the feed`() {
        assertEquals(DeepLinkTarget.Archive, DeepLinkParser.parse("$website/archive"))
        assertEquals(DeepLinkTarget.Archive, DeepLinkParser.parse("$website/archive/"))
        // The archive list screen was merged into the feed; the deep link must follow it.
        assertEquals(Routes.TODAY, DeepLinkTarget.Archive.destination)
    }

    @Test
    fun `archive entry opens the matching detail screen`() {
        assertEquals(
            DeepLinkTarget.Detail("2026-10-25"),
            DeepLinkParser.parse("$website/archive/2026-10-25"),
        )
    }

    @Test
    fun `trailing slash, query and fragment do not change the entry`() {
        assertEquals(
            DeepLinkTarget.Detail("2026-10-25"),
            DeepLinkParser.parse("$website/archive/2026-10-25/?utm_source=share#top"),
        )
    }

    @Test
    fun `unknown archive paths fall back to the archive index`() {
        assertEquals(DeepLinkTarget.Archive, DeepLinkParser.parse("$website/archive/entry"))
        assertEquals(DeepLinkTarget.Archive, DeepLinkParser.parse("$website/archive/2026-13-40"))
        assertEquals(DeepLinkTarget.Archive, DeepLinkParser.parse("$website/archive/2026-10-25/extra"))
    }

    @Test
    fun `other website pages are not intercepted`() {
        assertNull(DeepLinkParser.parse("$website/about"))
        assertNull(DeepLinkParser.parse("$website/privacy"))
        assertNull(DeepLinkParser.parse("$website/api"))
        assertNull(DeepLinkParser.parse("$website/archivefoo"))
    }

    @Test
    fun `other hosts and schemes are ignored`() {
        assertNull(DeepLinkParser.parse("https://example.com/archive/2026-10-25"))
        assertNull(DeepLinkParser.parse("http://${website.substringAfter("://")}/archive/2026-10-25"))
        assertNull(DeepLinkParser.parse("mohakash://archive/2026-10-25"))
    }

    @Test
    fun `blank and malformed URLs are ignored`() {
        assertNull(DeepLinkParser.parse(null))
        assertNull(DeepLinkParser.parse(""))
        assertNull(DeepLinkParser.parse("not a url"))
    }
}
