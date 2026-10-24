package app.mohakash.apod.core

import java.net.URI

/**
 * Validation and normalisation for a user-supplied API base URL.
 *
 * The app can only talk to HTTPS endpoints because `usesCleartextTraffic` is disabled in the
 * manifest. A user may paste a full endpoint such as `…/latest.json`; that is accepted and
 * reduced to its base URL so the same field works for both forms.
 */
object ApiEndpoint {

    /** Returns the normalised base URL, or null when [input] is blank or invalid. */
    fun normalize(input: String): String? {
        var value = input.trim().trimEnd('/')
        if (value.isEmpty()) return null

        // Accept a pasted endpoint file and turn it back into the base URL it belongs to.
        value = value
            .removeSuffix("/latest.json")
            .removeSuffix("/archive-index.json")
            .trimEnd('/')
        if (value.isEmpty()) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (uri.path?.split('/')?.any { it == ".." } == true) return null

        return value
    }
}
