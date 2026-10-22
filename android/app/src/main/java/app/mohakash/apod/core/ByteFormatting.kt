package app.mohakash.apod.core

import java.util.Locale

/**
 * Formats a number of bytes for the cache size.
 */
object ByteFormatting {

    private val UNITS = arrayOf("B", "KB", "MB", "GB")

    /** Formats [bytes] as e.g. `12.4 MB`. */
    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < UNITS.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${UNITS[unitIndex]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, UNITS[unitIndex])
        }
    }
}
