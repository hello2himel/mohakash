package app.mohakash.apod.wallpaper

/**
 * Builds the intents that open the system live-wallpaper preview for this app's wallpaper.
 *
 * Pure Kotlin on purpose so the exact intent shape can be unit tested without Android.
 *
 * Two details are load-bearing and were the cause of the wallpaper-picker crash:
 *
 * 1. `EXTRA_LIVE_WALLPAPER_COMPONENT` must carry an **`android.content.ComponentName`**, not a
 *    string. The system wallpaper preview reads the extra with `getParcelableExtra`; a string
 *    makes that read fail and the preview activity crashes.
 * 2. The wallpaper service must be protected with `android.permission.BIND_WALLPAPER` in the
 *    manifest. Without it the system is not allowed to bind to the service, which also surfaces
 *    as the wallpaper screen crashing the moment the preview is opened.
 *
 * The strategy here is deliberately small: try the direct change action (it lands on our own
 * preview), fall back to the plain system chooser (`MAIN` + `WallpaperService` category, no
 * extras), and as a last resort open the device wallpaper settings. [IntentSpec] stays free of
 * Android types; the caller attaches the [android.content.ComponentName].
 */
object WallpaperIntentBuilder {

    /** The package name of the app — the identity of our wallpaper service. */
    data class WallpaperTarget(val packageName: String) {

        init {
            require(packageName.isNotBlank()) { "The package name must not be blank" }
        }

        /** The component string of the wallpaper service. */
        val serviceComponent: String
            get() = "$packageName/$SERVICE_CLASS"
    }

    /** Extra read by the system preview activity; its value must be a `ComponentName`. */
    const val EXTRA_LIVE_WALLPAPER_COMPONENT = "android.intent.extra.LIVE_WALLPAPER_COMPONENT"

    /**
     * The direct intent: asks the system to switch to (and preview) our live wallpaper.
     */
    fun directChange(packageName: String): IntentSpec = IntentSpec(
        action = ACTION_CHANGE_LIVE_WALLPAPER,
        attachWallpaperComponent = true,
        newTaskFlag = true,
    ).also { WallpaperTarget(packageName) }

    /**
     * The plain system live-wallpaper chooser: `MAIN` plus the `WallpaperService` category and
     * **no extras at all**. This is the one every stock/AOSP device understands.
     */
    fun plainChooser(): IntentSpec = IntentSpec(
        action = ACTION_MAIN,
        category = CATEGORY_WALLPAPER_SERVICE,
        attachWallpaperComponent = false,
        newTaskFlag = true,
    )

    /** The wallpaper settings of the device — the guaranteed-safe last resort. */
    fun wallpaperSettings(): IntentSpec = IntentSpec(
        action = ACTION_DISPLAY_SETTINGS,
        attachWallpaperComponent = false,
        newTaskFlag = true,
    )

    /**
     * The ordered attempts a caller should make. Each entry is tried in turn until one starts
     * successfully. Nothing except the component extra is ever attached to them.
     */
    fun attemptsFor(packageName: String, directComponentSet: Boolean): List<IntentSpec> = buildList {
        WallpaperTarget(packageName) // validate the package name early
        if (directComponentSet) add(directChange(packageName))
        add(plainChooser())
        add(wallpaperSettings())
    }

    /** A description of an intent to launch, kept free of Android types for testability. */
    data class IntentSpec(
        val action: String,
        /** Category to add, or `null`. */
        val category: String? = null,
        /** When true the caller must attach [EXTRA_LIVE_WALLPAPER_COMPONENT] as a ComponentName. */
        val attachWallpaperComponent: Boolean = false,
        val newTaskFlag: Boolean = true,
    )

    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_CHANGE_LIVE_WALLPAPER = "android.service.wallpaper.CHANGE_LIVE_WALLPAPER"
    const val ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS"
    const val CATEGORY_WALLPAPER_SERVICE = "android.service.wallpaper.WallpaperService"
    const val SERVICE_CLASS = "app.mohakash.apod.wallpaper.MohakashWallpaperService"
}
