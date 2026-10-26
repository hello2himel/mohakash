package app.mohakash.apod.ui.navigation

import app.mohakash.apod.BuildConfig
import java.net.URI
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * A destination that can be opened from a public website link.
 *
 * Only the pages that also exist in the app are represented here. Links to the
 * documentation pages (for example `/about` or `/privacy`) stay in the browser.
 */
sealed interface DeepLinkTarget {

    /** The navigation route that should be opened. */
    val destination: String

    data object Today : DeepLinkTarget {
        override val destination: String get() = Routes.TODAY
    }

    /** `/archive` opens the archive tab. */
    data object Archive : DeepLinkTarget {
        override val destination: String get() = Routes.ARCHIVE
    }

    data class Detail(val date: String) : DeepLinkTarget {
        override val destination: String get() = Routes.detail(date)
    }
}

/**
 * One deep-link hand-off from [MainActivity] to the navigation graph.
 *
 * This is a regular class rather than a data class on purpose: every intent
 * must be a distinct value, even when the same archive URL is opened twice, so
 * Compose's `LaunchedEffect` handles it every time.
 */
class DeepLinkRequest(val target: DeepLinkTarget)

/**
 * Parses the public website URLs that have an in-app destination.
 *
 * Supported:
 *
 * - `https://<website>/` → the feed (latest picture)
 * - `https://<website>/archive` → the archive tab
 * - `https://<website>/archive/YYYY-MM-DD` → Detail
 *
 * All other URLs, hosts and schemes are ignored. The host comes from
 * `BuildConfig.WEBSITE_BASE_URL` (generated from `config/site.json`) so the
 * manifest and the parser cannot drift apart.
 */
object DeepLinkParser {

    private val websiteHost: String? =
        runCatching { URI(BuildConfig.WEBSITE_BASE_URL).host }.getOrNull()

    private val isoDate = Regex("""\d{4}-\d{2}-\d{2}""")

    /** Returns the matching destination, or `null` when the URL is not ours. */
    fun parse(raw: String?): DeepLinkTarget? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (websiteHost == null || !websiteHost.equals(uri.host, ignoreCase = true)) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> DeepLinkTarget.Today
            segments.first() != "archive" -> null
            segments.size == 1 -> DeepLinkTarget.Archive
            segments.size == 2 && isIsoDate(segments[1]) -> DeepLinkTarget.Detail(segments[1])
            else -> DeepLinkTarget.Archive
        }
    }

    /** A strict ISO calendar date (`YYYY-MM-DD`), including a real month and day. */
    private fun isIsoDate(value: String): Boolean {
        if (!isoDate.matches(value)) return false
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }
        return runCatching { calendar.time }.isSuccess
    }
}
