package app.mohakash.apod.donation

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.TimeZone

/**
 * Local-only engagement state for the optional support prompts.
 *
 * Nothing here is transmitted anywhere. It is intentionally stored outside the main
 * settings so a profile reset or backup restore cannot accidentally turn a one-off
 * support ask into an infinite reminder.
 */
@Serializable
data class DonationState(
    val firstSeenAt: Long? = null,
    val lastSeenAt: Long? = null,
    val activeDays: Int = 0,
    val lastActiveDay: String? = null,
    val pagesViewed: Int = 0,
    val randomUses: Int = 0,
    val lastPromptAt: Long? = null,
    val lastDismissedAt: Long? = null,
    val lastNotificationAt: Long? = null,
    val feedPrompts: Int = 0,
    val notificationsSent: Int = 0,
)

/**
 * Central thresholds for both the in-feed card and the notification reminder.
 *
 * Keep every number here so the product can tune the pause without hunting through
 * Compose, ViewModels and workers.
 */
object DonationPolicy {
    const val DAY_MILLIS: Long = 86_400_000L

    /** Do not ask before the user has come back on several separate days. */
    const val MIN_ACTIVE_DAYS: Int = 3

    /** Enough browsing that the app is clearly part of the routine. */
    const val MIN_PAGES_VIEWED: Int = 8

    /** Enough random exploration that a support ask is contextual. */
    const val MIN_RANDOM_USES: Int = 3

    /** Minimum age of the install before any ask. */
    const val MIN_FIRST_SEEN_DAYS: Int = 3

    /** A very long shared pause between any feed card and any notification. */
    const val REQUEST_COOLDOWN_MILLIS: Long = 30L * DAY_MILLIS

    /** Dismissing one prompt should be meaningful across sessions. */
    const val DISMISS_COOLDOWN_MILLIS: Long = 30L * DAY_MILLIS

    /** Notifications are rarer than the in-app card. */
    const val NOTIFICATION_COOLDOWN_MILLIS: Long = 30L * DAY_MILLIS

    /** At most one card per process/session, in addition to the persisted cooldown. */
    const val MAX_FEED_PROMPTS_PER_SESSION: Int = 1

    fun localDay(epochMillis: Long, timeZone: TimeZone): String {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = epochMillis }
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun canOfferFeedPrompt(state: DonationState, now: Long): Boolean {
        if (!hasEngagement(state, now)) return false
        if (state.lastPromptAt != null && now - state.lastPromptAt < REQUEST_COOLDOWN_MILLIS) return false
        if (state.lastDismissedAt != null && now - state.lastDismissedAt < DISMISS_COOLDOWN_MILLIS) return false
        return true
    }

    fun canSendNotification(state: DonationState, now: Long): Boolean {
        if (!hasEngagement(state, now)) return false
        if (state.lastPromptAt != null && now - state.lastPromptAt < REQUEST_COOLDOWN_MILLIS) return false
        if (state.lastDismissedAt != null && now - state.lastDismissedAt < DISMISS_COOLDOWN_MILLIS) return false
        if (state.lastNotificationAt != null &&
            now - state.lastNotificationAt < NOTIFICATION_COOLDOWN_MILLIS
        ) {
            return false
        }
        return true
    }

    private fun hasEngagement(state: DonationState, now: Long): Boolean {
        val firstSeen = state.firstSeenAt ?: return false
        if (state.activeDays < MIN_ACTIVE_DAYS) return false
        if (state.pagesViewed < MIN_PAGES_VIEWED) return false
        if (state.randomUses < MIN_RANDOM_USES) return false
        return now - firstSeen >= MIN_FIRST_SEEN_DAYS * DAY_MILLIS
    }
}
