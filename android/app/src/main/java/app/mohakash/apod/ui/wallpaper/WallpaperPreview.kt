package app.mohakash.apod.ui.wallpaper

import java.io.File

/**
 * The picture shown inside a wallpaper frame.
 *
 * A local file wins when it exists: it is the picture that is already installed and therefore
 * works offline. Otherwise the remote URL of the selected entry is shown while the picture is
 * being prepared.
 */
data class WallpaperPreview(
    val localPath: String? = null,
    val remoteUrl: String? = null,
) {

    /** The model handed to Coil — a [File] for local pictures, an URL string otherwise. */
    val model: Any?
        get() = localPath?.let(::File) ?: remoteUrl?.takeIf { it.isNotBlank() }
}
