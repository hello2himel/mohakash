package app.mohakash.apod.wallpaper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process wide signal that tells the live wallpaper "the picture changed, draw again".
 *
 * The wallpaper engine observes the [revision] flow; the sync worker bumps it after a new
 * picture was stored. No polling and no network traffic is involved.
 */
object WallpaperSignal {

    private val _revision = MutableStateFlow(0L)

    /** Increases every time the current wallpaper picture changes. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Notifies the engine that a new picture is available. */
    fun notifyChanged() {
        _revision.value = _revision.value + 1
    }
}
