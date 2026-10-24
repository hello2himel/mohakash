package app.mohakash.apod.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.mohakashDataStore: DataStore<Preferences> by preferencesDataStore(name = "mohakash_settings")

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
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
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

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
        updateAutomatically = this[Keys.UPDATE_AUTOMATICALLY] ?: true,
        preferHd = this[Keys.PREFER_HD] ?: false,
        wifiOnly = this[Keys.WIFI_ONLY] ?: true,
        wallpaperScaling = WallpaperScaling.fromStorageKey(this[Keys.WALLPAPER_SCALING]),
        hideVideos = this[Keys.HIDE_VIDEOS] ?: true,
        customApiBaseUrl = this[Keys.CUSTOM_API_BASE_URL],
        preferredBaseUrl = this[Keys.PREFERRED_BASE_URL],
        lastSyncAt = this[Keys.LAST_SYNC_AT],
        lastSyncDate = this[Keys.LAST_SYNC_DATE],
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
    }
}
