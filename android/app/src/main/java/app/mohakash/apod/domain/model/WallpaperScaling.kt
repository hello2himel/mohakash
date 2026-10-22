package app.mohakash.apod.domain.model

/**
 * How the live wallpaper paints the picture on the device screen.
 *
 * * [FILL] – centre crop, the picture fills the whole screen, edges are cut away.
 * * [FIT]  – letterbox, the whole picture is visible, the remaining area keeps the app background.
 */
enum class WallpaperScaling(val storageKey: String, val label: String) {
    FILL("fill", "Fill"),
    FIT("fit", "Fit");

    companion object {
        val DEFAULT = FILL

        fun fromStorageKey(key: String?): WallpaperScaling =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
