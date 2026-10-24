package app.mohakash.apod.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context

/**
 * Tells whether Mohakash is the live wallpaper currently selected in the system.
 *
 * The stored [app.mohakash.apod.data.storage.WallpaperMeta] only says which picture the app has
 * prepared. It does not prove that the user actually selected the wallpaper, so UI and background
 * work must ask the system before calling the wallpaper "active" or updating it.
 */
object WallpaperStatus {

    fun isActive(context: Context): Boolean {
        val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
        return info.component == ComponentName(context, MohakashWallpaperService::class.java)
    }
}
