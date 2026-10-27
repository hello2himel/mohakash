package app.mohakash.apod.donation

import java.util.TimeZone

/**
 * App-scoped, local-only engagement tracker for the optional support prompts.
 *
 * Feed navigation and random-button taps are counted here, but only as coarse totals:
 * no entry titles, dates, searches or identifiers are stored. The state is small JSON
 * in app-private storage and survives restarts/reboots.
 */
class DonationTracker(
    private val store: DonationStateStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
) {
    private var state: DonationState = store.read()
    private var promptedThisSession = false
    private val sessionPages = mutableSetOf<String>()

    @Synchronized
    fun recordSession() {
        val timestamp = now()
        val day = DonationPolicy.localDay(timestamp, timeZone())
        val next = if (state.firstSeenAt == null) {
            state.copy(
                firstSeenAt = timestamp,
                lastSeenAt = timestamp,
                activeDays = 1,
                lastActiveDay = day,
            )
        } else if (state.lastActiveDay != day) {
            state.copy(
                lastSeenAt = timestamp,
                activeDays = state.activeDays + 1,
                lastActiveDay = day,
            )
        } else {
            state.copy(lastSeenAt = timestamp)
        }
        if (next != state) {
            state = next
            save()
        }
    }

    @Synchronized
    fun recordPage(date: String) {
        if (!sessionPages.add(date)) return
        state = state.copy(pagesViewed = state.pagesViewed + 1, lastSeenAt = now())
        save()
    }

    @Synchronized
    fun recordRandomUse() {
        state = state.copy(randomUses = state.randomUses + 1, lastSeenAt = now())
        save()
    }

    /** Returns true only once per process and only when the long cooldown is clear. */
    @Synchronized
    fun offerFeedPrompt(): Boolean {
        if (promptedThisSession) return false
        if (!DonationPolicy.canOfferFeedPrompt(state, now())) return false
        promptedThisSession = true
        state = state.copy(
            lastPromptAt = now(),
            feedPrompts = state.feedPrompts + 1,
        )
        save()
        return true
    }

    /** Returns true if a notification was eligible and records its cooldown. */
    @Synchronized
    fun offerNotification(): Boolean {
        if (!DonationPolicy.canSendNotification(state, now())) return false
        state = state.copy(
            lastPromptAt = now(),
            lastNotificationAt = now(),
            notificationsSent = state.notificationsSent + 1,
        )
        save()
        return true
    }

    @Synchronized
    fun dismissPrompt() {
        state = state.copy(lastDismissedAt = now(), lastPromptAt = now())
        save()
    }

    @Synchronized
    fun currentState(): DonationState = state

    private fun save() {
        store.write(state)
    }
}
