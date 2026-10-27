package app.mohakash.apod.donation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

class DonationPolicyTest {
    private val now = 1_000_000_000_000L
    private val day = DonationPolicy.DAY_MILLIS

    private fun engaged(
        activeDays: Int = DonationPolicy.MIN_ACTIVE_DAYS,
        pages: Int = DonationPolicy.MIN_PAGES_VIEWED,
        random: Int = DonationPolicy.MIN_RANDOM_USES,
        ageDays: Int = DonationPolicy.MIN_FIRST_SEEN_DAYS,
        lastPrompt: Long? = null,
        lastDismissed: Long? = null,
    ) = DonationState(
        firstSeenAt = now - ageDays * day,
        activeDays = activeDays,
        pagesViewed = pages,
        randomUses = random,
        lastPromptAt = lastPrompt,
        lastDismissedAt = lastDismissed,
    )

    @Test
    fun `new installs are never offered a prompt`() {
        assertFalse(DonationPolicy.canOfferFeedPrompt(DonationState(), now))
        assertFalse(DonationPolicy.canSendNotification(DonationState(), now))
    }

    @Test
    fun `sustained use becomes eligible`() {
        assertTrue(DonationPolicy.canOfferFeedPrompt(engaged(), now))
        assertTrue(DonationPolicy.canSendNotification(engaged(), now))
    }

    @Test
    fun `every engagement signal is required`() {
        assertFalse(DonationPolicy.canOfferFeedPrompt(engaged(activeDays = 2), now))
        assertFalse(DonationPolicy.canOfferFeedPrompt(engaged(pages = 7), now))
        assertFalse(DonationPolicy.canOfferFeedPrompt(engaged(random = 2), now))
        assertFalse(DonationPolicy.canOfferFeedPrompt(engaged(ageDays = 2), now))
    }

    @Test
    fun `notification cooldown is separate from the feed card`() {
        val recentNotification = now - 2 * day
        assertFalse(
            DonationPolicy.canSendNotification(
                engaged().copy(lastNotificationAt = recentNotification),
                now,
            ),
        )
        assertTrue(
            DonationPolicy.canOfferFeedPrompt(
                engaged().copy(lastNotificationAt = recentNotification),
                now,
            ),
        )
    }

    @Test
    fun `long cooldowns block both channels`() {
        val recent = now - 2 * day
        assertFalse(DonationPolicy.canOfferFeedPrompt(engaged(lastPrompt = recent), now))
        assertFalse(DonationPolicy.canSendNotification(engaged(lastPrompt = recent), now))
        assertFalse(DonationPolicy.canSendNotification(engaged(lastDismissed = recent), now))
    }

    @Test
    fun `tracker records engagement and enforces one prompt per session`() {
        val dir = Files.createTempDirectory("donation-test").toFile()
        try {
            val store = DonationStateStore(File(dir, "state.json"), Json { ignoreUnknownKeys = true })
            val tracker = DonationTracker(store, now = { now }, timeZone = { TimeZone.getTimeZone("UTC") })
            tracker.recordSession()
            repeat(DonationPolicy.MIN_PAGES_VIEWED) { tracker.recordPage("2026-01-$it") }
            repeat(DonationPolicy.MIN_RANDOM_USES) { tracker.recordRandomUse() }
            assertFalse(tracker.offerFeedPrompt())

            // Age the install without waiting three real days.
            store.write(tracker.currentState().copy(firstSeenAt = now - 4 * day, activeDays = 3))
            val aged = DonationTracker(store, now = { now }, timeZone = { TimeZone.getTimeZone("UTC") })
            assertTrue(aged.offerFeedPrompt())
            assertFalse(aged.offerFeedPrompt())
            assertTrue(aged.currentState().feedPrompts == 1)
        } finally {
            dir.deleteRecursively()
        }
    }
}
