package app.mohakash.apod.data.local.prefs

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.domain.model.WallpaperScaling
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.mohakashDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mohakash_settings",
    // A corrupt preferences file is replaced with defaults instead of failing every read.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Persists the user preferences in a DataStore (Preferences).
 *
 * The store also remembers which API base URL answered last so the app does not have to probe
 * every candidate on every start.
 */
class PreferencesSettingsStore(context: Context) : SettingsStore {

    private val dataStore = context.applicationContext.mohakashDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            when (throwable) {
                // Belt and braces: the handler above should have replaced the file already, but a
                // read must never take the app down if replacement itself fails.
                is CorruptionException -> emit(emptyPreferences())
                is IOException -> emit(emptyPreferences())
                else -> throw throwable
            }
        }
        .map { prefs -> prefs.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setOnboardingCompleted(value: Boolean) = edit { it[Keys.ONBOARDING_COMPLETED] = value }

    override suspend fun setUpdateAutomatically(value: Boolean) = edit { it[Keys.UPDATE_AUTOMATICALLY] = value }

    override suspend fun setPreferHd(value: Boolean) = edit { it[Keys.PREFER_HD] = value }

    override suspend fun setWifiOnly(value: Boolean) = edit { it[Keys.WIFI_ONLY] = value }

    override suspend fun setHideVideos(value: Boolean) = edit { it[Keys.HIDE_VIDEOS] = value }

    override suspend fun setWallpaperScaling(scaling: WallpaperScaling) =
        edit { it[Keys.WALLPAPER_SCALING] = scaling.storageKey }

    override suspend fun setCustomApiBaseUrl(baseUrl: String?) = edit { prefs ->
        if (baseUrl.isNullOrBlank()) prefs.remove(Keys.CUSTOM_API_BASE_URL) else prefs[Keys.CUSTOM_API_BASE_URL] = baseUrl
    }

    override suspend fun setPreferredBaseUrl(baseUrl: String?) = edit { prefs ->
        if (baseUrl.isNullOrBlank()) prefs.remove(Keys.PREFERRED_BASE_URL) else prefs[Keys.PREFERRED_BASE_URL] = baseUrl
    }

    override suspend fun setLastSync(atMillis: Long, date: String?) = edit { prefs ->
        prefs[Keys.LAST_SYNC_AT] = atMillis
        if (date.isNullOrBlank()) prefs.remove(Keys.LAST_SYNC_DATE) else prefs[Keys.LAST_SYNC_DATE] = date
    }

    override suspend fun setFeedSwipeHintSeen(value: Boolean) = edit { it[Keys.FEED_SWIPE_HINT_SEEN] = value }

    override suspend fun setCarouselMode(enabled: Boolean) = edit { it[Keys.CAROUSEL_MODE] = enabled }

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

    override suspend fun setDonationRemindersEnabled(enabled: Boolean) =
        edit { it[Keys.DONATION_REMINDERS_ENABLED] = enabled }

    /**
     * Applies an edit, swallowing storage failures.
     *
     * Preferences are a convenience: failing to persist one (a full disk, a corrupt file, a
     * read-only device) must never crash an event handler running in `viewModelScope`. The
     * in-memory flow keeps the last successfully written values, so the UI stays consistent.
     */
    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (corruption: CorruptionException) {
            // The corruption handler resets the store on the next read; drop this write.
        } catch (error: IOException) {
            // Disk full / I/O error: keep the previous value rather than crashing the caller.
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
        updateAutomatically = this[Keys.UPDATE_AUTOMATICALLY] ?: true,
        preferHd = this[Keys.PREFER_HD] ?: false,
        wifiOnly = this[Keys.WIFI_ONLY] ?: false,
        wallpaperScaling = WallpaperScaling.fromStorageKey(this[Keys.WALLPAPER_SCALING]),
        hideVideos = this[Keys.HIDE_VIDEOS] ?: true,
        customApiBaseUrl = this[Keys.CUSTOM_API_BASE_URL],
        preferredBaseUrl = this[Keys.PREFERRED_BASE_URL],
        lastSyncAt = this[Keys.LAST_SYNC_AT],
        lastSyncDate = this[Keys.LAST_SYNC_DATE],
        feedSwipeHintSeen = this[Keys.FEED_SWIPE_HINT_SEEN] ?: false,
        carouselMode = this[Keys.CAROUSEL_MODE] ?: false,
        notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: true,
        donationRemindersEnabled = this[Keys.DONATION_REMINDERS_ENABLED] ?: true,
    )

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val UPDATE_AUTOMATICALLY = booleanPreferencesKey("update_automatically")
        val PREFER_HD = booleanPreferencesKey("prefer_hd")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val HIDE_VIDEOS = booleanPreferencesKey("hide_videos")
        val WALLPAPER_SCALING = stringPreferencesKey("wallpaper_scaling")
        val CUSTOM_API_BASE_URL = stringPreferencesKey("custom_api_base_url")
        val PREFERRED_BASE_URL = stringPreferencesKey("preferred_base_url")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_DATE = stringPreferencesKey("last_sync_date")
        val FEED_SWIPE_HINT_SEEN = booleanPreferencesKey("feed_swipe_hint_seen")
        val CAROUSEL_MODE = booleanPreferencesKey("carousel_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DONATION_REMINDERS_ENABLED = booleanPreferencesKey("donation_reminders_enabled")
    }
}
