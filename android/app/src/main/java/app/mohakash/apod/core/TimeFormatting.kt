package app.mohakash.apod.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Time helpers shared by the UI, the sync worker and the settings screen.
 */
object TimeFormatting {

    /** Cached metadata older than this is considered stale and refreshed on the next app start. */
    const val STALE_AFTER_MILLIS: Long = 6L * 60L * 60L * 1000L

    /** True when the cached metadata should be refreshed. */
    fun isStale(lastSyncAt: Long?, now: Long): Boolean {
        if (lastSyncAt == null) return true
        val age = now - lastSyncAt
        // A clock that jumped backwards must not trigger an endless refresh loop.
        if (age < 0) return false
        return age > STALE_AFTER_MILLIS
    }

    /**
     * Formats the "Synced X ago" caption.
     *
     * * `null` → "Never synced"
     * * under a minute → "Just now"
     * * minutes, hours, days
     * * older than a month → an absolute date
     */
    fun formatSyncedAgo(lastSyncAt: Long?, now: Long): String {
        if (lastSyncAt == null) return "Never synced"
        val delta = now - lastSyncAt
        if (delta < 0) return "Just now"
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            delta < minute -> "Just now"
            delta < hour -> "${delta / minute} min ago"
            delta < day -> "${delta / hour} h ago"
            delta < 30 * day -> "${delta / day} d ago"
            else -> formatDate(lastSyncAt)
        }
    }

    /** Formats an epoch millis value as `yyyy-MM-dd`. */
    fun formatDate(epochMillis: Long): String = isoDateFormat.get()!!.format(Date(epochMillis))

    /** Formats an ISO-8601 timestamp (`2026-10-22T12:17:44Z`) as `yyyy-MM-dd`. */
    fun formatIsoDate(isoTimestamp: String?): String? {
        val millis = parseIsoMillis(isoTimestamp) ?: return null
        return formatDate(millis)
    }

    /** Parses an ISO-8601 timestamp into epoch millis. Returns `null` when it cannot be parsed. */
    fun parseIsoMillis(isoTimestamp: String?): Long? {
        val raw = isoTimestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (pattern in ISO_PATTERNS) {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.isLenient = false
            val parsed = try {
                format.parse(raw)
            } catch (error: Exception) {
                null
            }
            if (parsed != null) return parsed.time
        }
        return null
    }

    /** Parses a `yyyy-MM-dd` APOD date into epoch millis (UTC midnight). */
    fun parseApiDate(date: String?): Long? {
        val raw = date?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        format.isLenient = false
        return try {
            format.parse(raw)?.time
        } catch (error: Exception) {
            null
        }
    }

    private val ISO_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd",
    )

    private val isoDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
}
