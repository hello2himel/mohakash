package app.mohakash.apod.carousel

import app.mohakash.apod.core.ImageUrlValidator
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Pure carousel rules: local-date matching, the dynamic interval and random next-image selection.
 * Keeping this free of Android/WorkManager types makes the calendar edge cases testable.
 */
object CarouselMath {

    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    /** `MM-dd` for the current local date. */
    fun monthDay(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epochMillis }
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format(
            Locale.ROOT,
            "%02d-%02d",
            month,
            day,
        )
    }

    /**
     * `24 hours / N`, exactly as requested. Returns `null` for an empty collection so callers
     * never divide by zero.
     */
    fun intervalMillis(validImageCount: Int): Long? {
        if (validImageCount <= 0) return null
        return DAY_MILLIS / validImageCount
    }

    /** Historical candidates for [monthDay], newest year first. */
    fun candidates(index: List<ArchiveIndexEntry>, monthDay: String): List<CarouselItem> =
        index.asSequence()
            .mapNotNull { entry ->
                if (entry.date.length != 10 || entry.date.substring(5) != monthDay ||
                    !entry.mediaType.isImage
                ) {
                    return@mapNotNull null
                }
                val imageUrl = entry.imageUrl
                    ?.takeIf(ImageUrlValidator::isValidImageUrl)
                    ?: entry.thumbnailUrl?.let(::upscaleThumbnail)
                imageUrl?.takeIf(ImageUrlValidator::isValidImageUrl)?.let { url ->
                    CarouselItem(
                        date = entry.date,
                        title = entry.title.ifBlank { entry.date },
                        imageUrl = url,
                        credit = entry.displayCredit,
                        apodUrl = entry.apodUrl,
                    )
                }
            }
            .distinctBy { it.date }
            .toList()

    /**
     * The archive index carries the 480 px thumbnail to stay small. NASA's CDN exposes larger
     * renditions by changing the width query, so the carousel can ask for a wallpaper-sized
     * image without downloading a per-year document or shipping a second backend.
     */
    internal fun upscaleThumbnail(url: String): String {
        val regex = Regex("([?&]w=)480(?=&|$)")
        return url.replace(regex, "$1" + 1600)
    }

    /** A random item, excluding the immediately previous date whenever an alternative exists. */
    fun next(
        items: List<CarouselItem>,
        previousDate: String?,
        random: Random = Random.Default,
    ): CarouselItem? {
        if (items.isEmpty()) return null
        val alternatives = if (items.size > 1 && previousDate != null) {
            items.filterNot { it.date == previousDate }
        } else {
            items
        }
        val pool = alternatives.ifEmpty { items }
        return pool[random.nextInt(pool.size)]
    }
}
