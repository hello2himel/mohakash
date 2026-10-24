package app.mohakash.apod.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The custom API endpoint must be a clean HTTPS base URL. */
class ApiEndpointTest {

    @Test
    fun `a plain https base url is accepted`() {
        assertEquals("https://example.org/api/v1", ApiEndpoint.normalize("https://example.org/api/v1"))
    }

    @Test
    fun `whitespace and trailing slashes are removed`() {
        assertEquals("https://example.org/api/v1", ApiEndpoint.normalize("  https://example.org/api/v1///  "))
    }

    @Test
    fun `a pasted endpoint file becomes its base url`() {
        assertEquals("https://example.org/api/v1", ApiEndpoint.normalize("https://example.org/api/v1/latest.json"))
        assertEquals(
            "https://example.org/api/v1",
            ApiEndpoint.normalize("https://example.org/api/v1/archive-index.json/"),
        )
    }

    @Test
    fun `ports and nested paths are kept`() {
        assertEquals("https://example.org:8443/self-hosted/apod", ApiEndpoint.normalize("https://example.org:8443/self-hosted/apod/"))
    }

    @Test
    fun `cleartext http is rejected`() {
        assertNull(ApiEndpoint.normalize("http://example.org/api/v1"))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(ApiEndpoint.normalize(""))
        assertNull(ApiEndpoint.normalize("   "))
        assertNull(ApiEndpoint.normalize("/latest.json"))
    }

    @Test
    fun `query strings and fragments are rejected`() {
        assertNull(ApiEndpoint.normalize("https://example.org/api?key=1"))
        assertNull(ApiEndpoint.normalize("https://example.org/api#top"))
    }

    @Test
    fun `malformed urls are rejected`() {
        assertNull(ApiEndpoint.normalize("example.org/api"))
        assertNull(ApiEndpoint.normalize("https://"))
        assertNull(ApiEndpoint.normalize("https://example.org/api/../v1"))
        assertNull(ApiEndpoint.normalize("https://user:pass@example.org/api"))
        assertNull(ApiEndpoint.normalize("https://example.org:70000/api"))
        assertNull(ApiEndpoint.normalize("https://example.org:0/api"))
    }
}
