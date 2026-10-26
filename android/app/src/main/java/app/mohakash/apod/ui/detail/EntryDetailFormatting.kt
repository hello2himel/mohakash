package app.mohakash.apod.ui.detail

import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pure formatting of an APOD entry for the detail viewer.
 *
 * The composables only map these values onto resources/rows; the wording of the header, the
 * order of the metadata rows and the conditions for the notes stay testable.
 */
object EntryDetailFormatting {

    /** The label of the media type pill and the *Media type* metadata row. */
    fun mediaLabel(mediaType: MediaType): EntryMediaLabel = when (mediaType) {
        MediaType.IMAGE -> EntryMediaLabel.IMAGE
        MediaType.VIDEO -> EntryMediaLabel.VIDEO
        MediaType.OTHER -> EntryMediaLabel.INTERACTIVE
    }

    /**
     * Formats an APOD date (`yyyy-MM-dd`) as a human date (`26 October 2026`).
     *
     * The date is parsed in UTC so it never shifts by a day on a device west of Greenwich. When
     * the input cannot be parsed (unexpected payload) the raw value is returned unchanged — the
     * header never shows an empty line.
     */
    fun formatDisplayDate(
        date: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val raw = date.trim()
        if (raw.isEmpty()) return ""
        val parser = SimpleDateFormat(API_DATE_PATTERN, Locale.US).apply {
            timeZone = UTC
            isLenient = false
        }
        val parsed = runCatching { parser.parse(raw) }.getOrNull() ?: return raw
        val formatter = SimpleDateFormat(DISPLAY_DATE_PATTERN, locale).apply { timeZone = UTC }
        return formatter.format(parsed)
    }

    /** Formats an ISO-8601 `updatedAt` timestamp as a human date; `null` when there is none. */
    fun formatUpdated(
        updatedAt: String?,
        locale: Locale = Locale.getDefault(),
    ): String? {
        val day = TimeFormatting.formatIsoDate(updatedAt) ?: return null
        return formatDisplayDate(day, locale).takeIf { it.isNotBlank() }
    }

    /** The header meta line: the human date followed by the credit when the API provides one. */
    fun headerLine(
        entry: ApodEntry,
        locale: Locale = Locale.getDefault(),
    ): String {
        val date = formatDisplayDate(entry.date, locale)
        val credit = entry.displayCredit?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            date.isEmpty() -> credit.orEmpty()
            credit == null -> date
            else -> "$date · Credit: $credit"
        }
    }

    /**
     * The metadata rows of the entry, in reading order:
     * date, media type, credit (always, *Not provided* when absent), updated and source when the
     * API has them. Rows without a value are omitted instead of rendering an empty line.
     */
    fun metadataRows(
        entry: ApodEntry,
        locale: Locale = Locale.getDefault(),
    ): List<EntryMetadataRow> = buildList {
        entry.date.trim().takeIf { it.isNotEmpty() }?.let {
            add(EntryMetadataRow(EntryMetadataKey.DATE, EntryMetadataValue.Text(it)))
        }
        add(EntryMetadataRow(EntryMetadataKey.MEDIA_TYPE, EntryMetadataValue.Media(entry.mediaType)))
        val credit = entry.displayCredit?.trim()?.takeIf { it.isNotEmpty() }
        add(
            EntryMetadataRow(
                EntryMetadataKey.CREDIT,
                credit?.let { EntryMetadataValue.Text(it) } ?: EntryMetadataValue.NotProvided,
            ),
        )
        formatUpdated(entry.updatedAt, locale)?.let {
            add(EntryMetadataRow(EntryMetadataKey.UPDATED, EntryMetadataValue.Text(it)))
        }
        entry.source?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(EntryMetadataRow(EntryMetadataKey.SOURCE, EntryMetadataValue.Text(it)))
        }
    }

    /** The note shown when the publisher substituted a placeholder or has no image at all. */
    fun imageStatusNote(imageStatus: ImageStatus): EntryImageNote? = when (imageStatus) {
        ImageStatus.OK -> null
        ImageStatus.PLACEHOLDER -> EntryImageNote.PLACEHOLDER
        ImageStatus.MISSING -> EntryImageNote.MISSING
    }

    /** The offline note is only relevant when a cached record is shown after a failed fetch. */
    fun offlineNoteVisible(isShowingCachedCopy: Boolean, entry: ApodEntry?): Boolean =
        isShowingCachedCopy && entry != null

    /** The note about videos is shown for every entry that cannot be played inside the app. */
    fun videoNoteVisible(entry: ApodEntry?): Boolean = entry?.mediaType?.isPlayable == true

    /** The explanation paragraphs, split on blank lines by the domain model. */
    fun explanationParagraphs(entry: ApodEntry): List<String> = entry.paragraphs

    private const val API_DATE_PATTERN = "yyyy-MM-dd"
    private const val DISPLAY_DATE_PATTERN = "d MMMM yyyy"

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
}

/** The displayable label of a media type. */
enum class EntryMediaLabel { IMAGE, VIDEO, INTERACTIVE }

/** One metadata row of the *Details* section. */
data class EntryMetadataRow(val key: EntryMetadataKey, val value: EntryMetadataValue)

/** The label of a metadata row; the UI maps it to a string resource. */
enum class EntryMetadataKey { DATE, MEDIA_TYPE, CREDIT, UPDATED, SOURCE }

/** The value of a metadata row. */
sealed interface EntryMetadataValue {
    /** Plain text, e.g. the ISO date or the credit. */
    data class Text(val text: String) : EntryMetadataValue

    /** A media type, rendered through [EntryDetailFormatting.mediaLabel]. */
    data class Media(val mediaType: MediaType) : EntryMetadataValue

    /** The entry has no value for this row (only used by the credit row). */
    data object NotProvided : EntryMetadataValue
}

/** The image-status note shown under the metadata. */
enum class EntryImageNote { PLACEHOLDER, MISSING }
