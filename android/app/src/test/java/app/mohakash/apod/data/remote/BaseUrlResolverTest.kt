package app.mohakash.apod.data.remote

import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.testing.FakeSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A custom endpoint replaces the built-in candidates; otherwise the remembered one is first. */
class BaseUrlResolverTest {

    private val defaults = listOf("https://one.example/api/v1", "https://two.example/api/v1")

    @Test
    fun `a custom endpoint replaces the defaults`() = runTest {
        val settings = FakeSettingsStore(
            AppSettings(customApiBaseUrl = "https://custom.example/api/v1/"),
        )
        val resolver = BaseUrlResolver(defaults, settings)

        assertEquals(listOf("https://custom.example/api/v1"), resolver.orderedCandidates())
    }

    @Test
    fun `the remembered default endpoint is tried first`() = runTest {
        val settings = FakeSettingsStore(
            AppSettings(preferredBaseUrl = "https://two.example/api/v1"),
        )
        val resolver = BaseUrlResolver(defaults, settings)

        assertEquals(
            listOf("https://two.example/api/v1", "https://one.example/api/v1"),
            resolver.orderedCandidates(),
        )
    }

    @Test
    fun `an unknown remembered endpoint is ignored`() = runTest {
        val settings = FakeSettingsStore(
            AppSettings(preferredBaseUrl = "https://old.example/api/v1"),
        )
        val resolver = BaseUrlResolver(defaults, settings)

        assertEquals(defaults, resolver.orderedCandidates())
    }

    @Test
    fun `only a built-in endpoint is remembered`() = runTest {
        val settings = FakeSettingsStore()
        val resolver = BaseUrlResolver(defaults, settings)

        resolver.remember("https://custom.example/api/v1")
        assertEquals(null, settings.current().preferredBaseUrl)

        resolver.remember("https://two.example/api/v1/")
        assertEquals("https://two.example/api/v1", settings.current().preferredBaseUrl)
    }

    @Test
    fun `the first built-in endpoint is the default`() {
        val resolver = BaseUrlResolver(defaults, FakeSettingsStore())

        assertEquals("https://one.example/api/v1", resolver.defaultBaseUrl)
    }
}
