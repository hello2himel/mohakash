package app.mohakash.apod.domain.model

/**
 * The complete set of user preferences, persisted in DataStore.
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val updateAutomatically: Boolean = true,
    val preferHd: Boolean = false,
    /**
     * Restrict picture downloads to Wi-Fi/ethernet.
     *
     * Off by default: the daily wallpaper update must work on mobile data too, otherwise a user
     * who never opens the app on Wi-Fi would never receive a new picture.
     */
    val wifiOnly: Boolean = false,
    val wallpaperScaling: WallpaperScaling = WallpaperScaling.DEFAULT,
    /** Videos are hidden unless the user turns them on. */
    val hideVideos: Boolean = true,
    /** A user-supplied API base URL. When set, it replaces the built-in candidates. */
    val customApiBaseUrl: String? = null,
    /** The API base URL that answered last time; tried first on the next request. */
    val preferredBaseUrl: String? = null,
    /** Epoch millis of the last successful metadata sync. */
    val lastSyncAt: Long? = null,
    /** The APOD date of the last successful metadata sync. */
    val lastSyncDate: String? = null,
    /** True once the first-run "swipe up" cue on the feed has been shown. */
    val feedSwipeHintSeen: Boolean = false,
    /** Rotate the live wallpaper through historical APODs from today's month/day. */
    val carouselMode: Boolean = false,
    /** Notify only when the regular latest-APOD sync installs a new picture. */
    val notificationsEnabled: Boolean = true,
    /** Optional, infrequent reminders about supporting the app. */
    val donationRemindersEnabled: Boolean = true,
)
