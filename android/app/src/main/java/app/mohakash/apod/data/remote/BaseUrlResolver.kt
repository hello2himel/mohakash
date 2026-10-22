package app.mohakash.apod.data.remote

import app.mohakash.apod.data.local.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Keeps track of the API base URLs that may serve the public JSON API.
 *
 * The candidate list is baked into `BuildConfig.API_BASE_URL_FALLBACKS` at build time from
 * `config/site.json`. The base URL that answered last is remembered in DataStore and tried first
 * on the next request; when it fails the remaining candidates are tried in order.
 */
class BaseUrlResolver(
    candidates: List<String>,
    private val settingsStore: SettingsStore,
) {
    val candidates: List<String> = candidates.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }

    /** The remembered base URL first, then the remaining candidates in their configured order. */
    suspend fun orderedCandidates(): List<String> {
        val remembered = try {
            settingsStore.settings.first().preferredBaseUrl?.trim()?.trimEnd('/')
        } catch (error: Exception) {
            null
        }
        val normalized = remembered?.takeIf { it.isNotEmpty() && it in candidates }
        return if (normalized == null) {
            candidates
        } else {
            listOf(normalized) + candidates.filter { it != normalized }
        }
    }

    /** Remembers the base URL that answered so it is tried first next time. */
    suspend fun remember(baseUrl: String) {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized in candidates) settingsStore.setPreferredBaseUrl(normalized)
    }
}
