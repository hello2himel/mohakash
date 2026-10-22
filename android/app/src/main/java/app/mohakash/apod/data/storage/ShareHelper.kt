package app.mohakash.apod.data.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.mohakash.apod.domain.model.ApodEntry
import java.io.File

/**
 * Builds and launches the Android share sheet.
 *
 * The credit always travels with the share: title, date and the creator are part of the shared
 * text, next to the link to the original APOD page.
 */
class ShareHelper(private val context: Context) {

    /** The text that is shared next to the picture. */
    fun buildShareText(entry: ApodEntry): String = buildString {
        append('"').append(entry.title).append('"')
        append(" · ").append(entry.date)
        entry.displayCredit?.let {
            append('\n')
            append("Image: ").append(it)
        }
        entry.apodUrl?.let {
            append('\n')
            append(it)
        }
    }

    /**
     * Opens the share sheet.
     *
     * @param imageFile the picture to share; when it is `null` only the text is shared.
     */
    fun share(entry: ApodEntry, imageFile: File?): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (imageFile != null) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareText(entry))
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
            if (imageFile != null) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile,
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }
}
