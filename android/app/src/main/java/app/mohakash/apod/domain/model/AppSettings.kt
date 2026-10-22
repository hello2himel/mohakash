package app.mohakash.apod.domain.model

/**
 * The complete set of user preferences, persisted in DataStore.
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val updateAutomatically: Boolean = true,
    val preferHd: Boolean = false,
    val wifiOnly: Boolean = true,
    val wallpaperScaling: WallpaperScaling = WallpaperScaling.DEFAULT,
    /** Videos are hidden unless the user turns them on. */
    val hideVideos: Boolean = true,
    /** The API base URL that answered last time; tried first on the next request. */
    val preferredBaseUrl: String? = null,
    /** Epoch millis of the last successful metadata sync. */
    val lastSyncAt: Long? = null,
    /** The APOD date of the last successful metadata sync. */
    val lastSyncDate: String? = null,
)
