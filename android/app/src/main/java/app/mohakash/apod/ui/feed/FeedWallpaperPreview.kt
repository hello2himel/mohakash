package app.mohakash.apod.ui.feed

import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formatting helpers of the feed, moved out of the old Today screen.
 *
 * They are plain functions so the wallpaper sheet preview can be unit tested without a ViewModel
 * or a database: the tests from the Today screen are kept with the same semantics.
 */

private val displayDateFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
}

/**
 * The human date of the feed header: `26 October 2026`.
 *
 * Falls back to the raw ISO date when it cannot be parsed, so a malformed record never shows an
 * empty line.
 */
internal fun feedDisplayDate(date: String?): String {
    val raw = date?.takeIf { it.isNotBlank() } ?: return ""
    val millis = app.mohakash.apod.core.TimeFormatting.parseApiDate(raw) ?: return raw
    return displayDateFormat.get()!!.format(Date(millis))
}

/** The date and credit line — the credit is omitted when the API has none. */
internal fun feedCreditLine(date: String?, credit: String?): String {
    val cleanDate = feedDisplayDate(date)
    val cleanCredit = credit?.takeIf { it.isNotBlank() }
    return when {
        cleanCredit.isNullOrBlank() -> cleanDate
        cleanDate.isBlank() -> cleanCredit
        else -> "$cleanDate · $cleanCredit"
    }
}

/** The caption of the wallpaper sheet: `date · title` (ISO date, as in the previous design). */
internal fun feedWallpaperCaption(entry: ApodEntry?, wallpaperMeta: WallpaperMeta?): String {
    val date = (entry?.date ?: wallpaperMeta?.date)?.takeIf { it.isNotBlank() }.orEmpty()
    val title = (entry?.title ?: wallpaperMeta?.title)?.takeIf { it.isNotBlank() }.orEmpty()
    return when {
        date.isBlank() -> title
        title.isBlank() -> date
        else -> "$date · $title"
    }
}

/**
 * The picture the wallpaper sheet should show.
 *
 * It is the entry on screen, not the previously installed picture. The local file is reused only
 * when it already belongs to that entry, so the sheet keeps working offline after it was set.
 */
internal fun feedWallpaperPreview(
    entry: ApodEntry?,
    wallpaperMeta: WallpaperMeta?,
    wallpaperPreviewPath: String?,
): WallpaperPreview {
    if (entry == null) return WallpaperPreview(localPath = wallpaperPreviewPath)
    val installedUrl = wallpaperMeta?.sourceUrl
    val installedIsEntry = wallpaperPreviewPath != null &&
        wallpaperMeta?.date == entry.date &&
        installedUrl != null &&
        installedUrl in setOfNotNull(entry.imageUrl, entry.hdImageUrl, entry.thumbnailUrl)
    if (installedIsEntry) {
        return WallpaperPreview(localPath = wallpaperPreviewPath)
    }

    val remote = if (entry.mediaType.isImage) {
        entry.displayImageUrl
    } else {
        entry.thumbnailUrl ?: entry.displayImageUrl
    }
    return WallpaperPreview(
        localPath = wallpaperPreviewPath.takeIf { remote == null },
        remoteUrl = remote,
    )
}
