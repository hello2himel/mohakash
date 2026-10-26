package app.mohakash.apod.data.local.prefs

import app.mohakash.apod.domain.model.AppSettings
import app.mohakash.apod.domain.model.WallpaperScaling
import kotlinx.coroutines.flow.Flow

/**
 * The user preferences.
 *
 * The interface keeps the rest of the app independent of DataStore, so the behaviour can be
 * tested with a plain in-memory implementation.
 */
interface SettingsStore {

    /** The current settings; emits on every change. */
    val settings: Flow<AppSettings>

    /** The current settings. */
    suspend fun current(): AppSettings

    /** Marks the onboarding as done (or not). */
    suspend fun setOnboardingCompleted(value: Boolean)

    /** Switches the automatic wallpaper update on or off. */
    suspend fun setUpdateAutomatically(value: Boolean)

    /** Switches the HD preference on or off. */
    suspend fun setPreferHd(value: Boolean)

    /** Switches the Wi-Fi only download on or off. */
    suspend fun setWifiOnly(value: Boolean)

    /** Hides video entries from the archive and the wallpaper. */
    suspend fun setHideVideos(value: Boolean)

    /** Changes the scaling mode of the live wallpaper. */
    suspend fun setWallpaperScaling(scaling: WallpaperScaling)

    /** Sets a custom API base URL, or clears it to use the built-in candidates. */
    suspend fun setCustomApiBaseUrl(baseUrl: String?)

    /** Remembers the API base URL that answered last. */
    suspend fun setPreferredBaseUrl(baseUrl: String?)

    /** Records the time of the last successful synchronisation. */
    suspend fun setLastSync(atMillis: Long, date: String?)

    /** Remembers that the first-run feed swipe cue has been shown. */
    suspend fun setFeedSwipeHintSeen(value: Boolean)
}
