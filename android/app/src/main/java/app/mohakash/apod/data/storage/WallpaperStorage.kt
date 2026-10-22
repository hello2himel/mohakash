package app.mohakash.apod.data.storage

import app.mohakash.apod.core.ImageUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Metadata of the picture that is currently used as live wallpaper.
 */
@Serializable
data class WallpaperMeta(
    val date: String,
    val title: String,
    val credit: String? = null,
    val sourceUrl: String,
    val fileName: String,
    val updatedAt: Long,
)

/**
 * App-private storage for the live wallpaper picture.
 *
 * Only one picture is kept. Writing is atomic: the image lands in a content addressed file first
 * and the metadata — which names that file — is swapped in afterwards, so the metadata and the
 * picture can never disagree. If anything fails the previous picture stays in place.
 */
class WallpaperStorage(
    filesDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val directory = File(filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val metaFile = File(directory, META_FILE_NAME)

    /** The directory holding the wallpaper picture. */
    val storageDirectory: File get() = directory

    /** The file that currently holds the wallpaper picture, if any. */
    fun currentImageFile(): File? {
        val meta = readMeta() ?: return null
        val file = File(directory, meta.fileName)
        return if (file.isFile && file.length() > 0) file else null
    }

    /** The metadata of the current wallpaper picture, if any. */
    fun readMeta(): WallpaperMeta? {
        if (!metaFile.isFile) return null
        return runCatching { json.decodeFromString<WallpaperMeta>(metaFile.readText()) }.getOrNull()
    }

    /**
     * Stores [source] as the new wallpaper picture.
     *
     * The file is copied into the private directory under a content addressed name and the
     * metadata is swapped atomically afterwards.
     */
    suspend fun commit(source: File, meta: WallpaperMeta): Result<WallpaperMeta> = withContext(Dispatchers.IO) {
        runCatching {
            require(source.isFile && source.length() > 0) { "The downloaded wallpaper file is empty" }
            require(ImageUrlValidator.isValidImageUrl(meta.sourceUrl)) {
                "Refusing to use a non-image URL as wallpaper: ${meta.sourceUrl}"
            }

            val extension = ImageUrlValidator.extensionOf(meta.sourceUrl) ?: "jpg"
            val fileName = "wallpaper-${meta.sourceUrl.hashCode().toUInt().toString(16)}.$extension"
            val target = File(directory, fileName)
            val incoming = File(directory, "incoming-$fileName")

            source.copyTo(incoming, overwrite = true)
            if (!incoming.renameTo(target)) {
                incoming.copyTo(target, overwrite = true)
                incoming.delete()
            }

            val updated = meta.copy(fileName = fileName)
            val metaIncoming = File(directory, "incoming-$META_FILE_NAME")
            metaIncoming.writeText(json.encodeToString(WallpaperMeta.serializer(), updated))
            if (!metaIncoming.renameTo(metaFile)) {
                metaIncoming.copyTo(metaFile, overwrite = true)
                metaIncoming.delete()
            }

            cleanupOrphans(keep = fileName)
            updated
        }
    }

    /** Removes the current picture. Used by tests and by "clear cache". */
    suspend fun clear() = withContext(Dispatchers.IO) {
        metaFile.delete()
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Deletes files that are not referenced by the current metadata. */
    private fun cleanupOrphans(keep: String) {
        directory.listFiles()?.forEach { file ->
            if (file.name != keep && file.name != META_FILE_NAME) file.delete()
        }
    }

    /** The extension of the current wallpaper file, e.g. `jpg`. */
    fun currentExtension(): String? = readMeta()?.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }

    companion object {
        const val DIRECTORY_NAME = "wallpaper"
        const val META_FILE_NAME = "wallpaper-meta.json"

        /** The MIME type of a file extension. */
        fun mimeTypeFor(extension: String?): String = when (extension?.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }
    }
}
