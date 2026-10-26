package app.mohakash.apod.data.storage

import android.content.Context
import android.content.Intent
import app.mohakash.apod.BuildConfig
import app.mohakash.apod.domain.model.ApodEntry

/**
 * Builds and launches the Android share sheet.
 *
 * The share is a plain-text link to the entry on the public website, not the picture itself:
 * `https://<website>/archive/YYYY-MM-DD` opens the same entry in the browser (or in the installed
 * app, thanks to the App Links intent filters) with the picture, the explanation and the credit.
 * Title, date and credit travel with the link so the message still makes sense in chat.
 */
class ShareHelper(private val context: Context) {

    /** Opens the share sheet with the public archive URL of [entry]. */
    fun share(entry: ApodEntry): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareText(entry))
            putExtra(Intent.EXTRA_SUBJECT, entry.title)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    companion object {

        /**
         * The public URL of one entry.
         *
         * It is built from `BuildConfig.WEBSITE_BASE_URL` (generated from `config/site.json`),
         * the same value the App Links intent filters use for their host.
         */
        fun archiveUrl(date: String): String {
            return "${BuildConfig.WEBSITE_BASE_URL.trimEnd('/')}/archive/$date"
        }

        /** The text shared through the sheet: title, date, credit and the archive link. */
        fun buildShareText(entry: ApodEntry): String = buildString {
            append('"').append(entry.title).append('"')
            append(" · ").append(entry.date)
            entry.displayCredit?.let {
                append('\n')
                append("Image: ").append(it)
            }
            append('\n')
            append(archiveUrl(entry.date))
        }
    }
}
