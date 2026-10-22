package app.mohakash.apod.data.storage

import android.content.ContentValues
import android.content.Context
import android.annotation.SuppressLint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Saves a picture into the device's *Pictures* collection.
 *
 * On API 29+ the [MediaStore] is used, which needs no runtime permission at all. Older releases
 * fall back to the public Pictures directory, which does need `WRITE_EXTERNAL_STORAGE` — that
 * permission is declared with `maxSdkVersion="28"` only.
 */
class GallerySaver(private val context: Context) {

    /**
     * Copies [source] into the gallery.
     *
     * @return the URI of the stored picture.
     */
    suspend fun save(fileName: String, source: File): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (!source.isFile || source.length() == 0L) {
                throw IOException("The image file is missing or empty")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(fileName, source)
            } else {
                saveLegacy(fileName, source)
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun saveWithMediaStore(fileName: String, source: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, WallpaperStorage.mimeTypeFor(fileName.substringAfterLast('.', "")))
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Mohakash")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the insert")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Could not open the MediaStore output stream")
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveLegacy(fileName: String, source: File): Uri {
        @Suppress("DEPRECATION")
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val targetDir = File(pictures, "Mohakash")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Could not create ${targetDir.absolutePath}")
        }
        val target = File(targetDir, fileName)
        source.copyTo(target, overwrite = true)
        var scanned: Uri? = null
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(WallpaperStorage.mimeTypeFor(fileName.substringAfterLast('.', ""))),
        ) { _, uri -> scanned = uri }
        return scanned ?: Uri.fromFile(target)
    }
}
