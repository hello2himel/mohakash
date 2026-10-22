package app.mohakash.apod.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the manifest contract that makes the system live-wallpaper preview able to bind to
 * [MohakashWallpaperService].
 *
 * A wallpaper service must be protected by `android.permission.BIND_WALLPAPER`; declaring a
 * different (or non-existent) permission makes the wallpaper screen crash when the preview is
 * opened. This test reads the source manifest so a regression is caught before a build.
 */
class WallpaperManifestTest {

    private fun manifest(): String = findManifest().readText()

    private fun findManifest(): File {
        var directory: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(directory, "src/main/AndroidManifest.xml")
            if (candidate.isFile) return candidate
            directory = directory?.parentFile
        }
        error("Could not find src/main/AndroidManifest.xml from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `wallpaper service requires BIND_WALLPAPER`() {
        val manifest = manifest()
        assertTrue(manifest.contains("android:permission=\"android.permission.BIND_WALLPAPER\""))
        assertFalse(manifest.contains("android:permission=\"android.permission.WALLPAPER\""))
    }

    @Test
    fun `wallpaper service is exported and advertises the wallpaper intent`() {
        val manifest = manifest()
        assertTrue(manifest.contains("android:name=\".wallpaper.MohakashWallpaperService\""))
        assertTrue(manifest.contains("android.service.wallpaper.WallpaperService"))
        assertTrue(manifest.contains("android:exported=\"true\""))
    }

    @Test
    fun `wallpaper metadata resource exists`() {
        val manifest = manifest()
        assertTrue(manifest.contains("android:resource=\"@xml/wallpaper\""))
        assertTrue(findXml().readText().contains("android:thumbnail"))
    }

    private fun findXml(): File {
        var directory: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(directory, "src/main/res/xml/wallpaper.xml")
            if (candidate.isFile) return candidate
            directory = directory?.parentFile
        }
        error("Could not find src/main/res/xml/wallpaper.xml")
    }
}
