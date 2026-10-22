package app.mohakash.apod.data.storage

import java.io.File

/**
 * Downloads a single picture into a file.
 */
interface PictureDownloader {

    /**
     * Downloads [url] into [target].
     *
     * The target file must not exist or must be replaced; a failed download has to remove the
     * incomplete file.
     */
    suspend fun download(url: String, target: File): Result<File>
}
