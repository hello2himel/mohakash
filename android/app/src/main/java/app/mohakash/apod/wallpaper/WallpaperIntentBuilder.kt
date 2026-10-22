package app.mohakash.apod.wallpaper

/**
 * Builds the intents that open the system live-wallpaper preview for this app's wallpaper.
 *
 * Pure Kotlin on purpose so the exact intent shape can be unit tested without Android.
 *
 * Background of the crash we are guarding against: `ACTION_CHANGE_LIVE_WALLPAPER` is only a
 * reliable way in to the stock AOSP picker. Many OEM pickers (Samsung and others) either do not
 * resolve the action at all or choke on the extras we pass, which shows up as the wallpaper
 * settings screen crashing the moment "Preview and set" is tapped. The strategy therefore is:
 *
 * 1. never put any extra on the chooser intent — it must stay exactly
 *    `android.intent.action.MAIN` + `android.service.wallpaper.WallpaperService` category,
 *    because several pickers read unknown extras and crash;
 * 2. try the direct change action first (it lands on our own preview), fall back to the plain
 *    chooser when nothing resolves it;
 * 3. as a last resort open the device-wide wallpaper settings screen.
 */
object WallpaperIntentBuilder {

    /** The package name of the app — the identity of our wallpaper service. */
    data class WallpaperTarget(val packageName: String) {

        init {
            require(packageName.isNotBlank()) { "The package name must not be blank" }
        }

        /** The component string used by [EXTRA_DIRECT_COMPONENT]. */
        val serviceComponent: String
            get() = "$packageName/$SERVICE_CLASS"
    }

    /** Extra used by some devices to jump straight into our own preview activity. */
    const val EXTRA_DIRECT_COMPONENT = "android.intent.extra.COMPONENT_NAME"

    /**
     * The direct intent: asks the system to switch to (and preview) our live wallpaper.
     * Only valid together with [directComponentSet] being true.
     */
    fun directChange(packageName: String): IntentSpec = IntentSpec(
        action = ACTION_CHANGE_LIVE_WALLPAPER,
        componentSet = false,
        extras = mapOf(EXTRA_LIVE_WALLPAPER_COMPONENT to WallpaperTarget(packageName).serviceComponent),
        newTaskFlag = true,
    )

    /**
     * The plain system live-wallpaper chooser: `MAIN` plus the `WallpaperService` category and
     * **no extras at all**. This is the one every stock/AOSP device understands and it never
     * crashes the picker.
     */
    fun plainChooser(): IntentSpec = IntentSpec(
        action = ACTION_MAIN,
        componentSet = false,
        extras = emptyMap(),
        newTaskFlag = true,
        category = CATEGORY_WALLPAPER_SERVICE,
    )

    /**
     * Variant of the chooser that additionally carries the direct-component extra, for the few
     * devices that only accept the preview jump in this form.
     */
    fun chooserWithExtra(packageName: String): IntentSpec = IntentSpec(
        action = ACTION_MAIN,
        componentSet = false,
        extras = mapOf(EXTRA_DIRECT_COMPONENT to WallpaperTarget(packageName).serviceComponent),
        newTaskFlag = true,
        category = CATEGORY_WALLPAPER_SERVICE,
    )

    /** The wallpaper settings of the device — the guaranteed-safe last resort. */
    fun wallpaperSettings(): IntentSpec = IntentSpec(
        action = ACTION_DISPLAY_SETTINGS,
        componentSet = false,
        extras = emptyMap(),
        newTaskFlag = true,
    )

    /**
     * The ordered attempts a caller should make. Each entry is tried in turn until one starts
     * successfully.
     */
    fun attemptsFor(packageName: String, directComponentSet: Boolean): List<IntentSpec> = buildList {
        if (directComponentSet) add(directChange(packageName))
        add(plainChooser())
        if (directComponentSet) add(chooserWithExtra(packageName))
        add(wallpaperSettings())
    }

    /** A description of an intent to launch, kept free of Android types for testability. */
    data class IntentSpec(
        val action: String,
        val componentSet: Boolean,
        val extras: Map<String, String>,
        val newTaskFlag: Boolean,
        val category: String? = null,
    )

    private const val ACTION_MAIN = "android.intent.action.MAIN"
    private const val ACTION_CHANGE_LIVE_WALLPAPER = "android.service.wallpaper.CHANGE_LIVE_WALLPAPER"
    private const val ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS"
    private const val CATEGORY_WALLPAPER_SERVICE = "android.service.wallpaper.WallpaperService"
    private const val EXTRA_LIVE_WALLPAPER_COMPONENT =
        "android.intent.extra.LIVE_WALLPAPER_COMPONENT"
    private const val SERVICE_CLASS = "app.mohakash.apod.wallpaper.MohakashWallpaperService"
}
