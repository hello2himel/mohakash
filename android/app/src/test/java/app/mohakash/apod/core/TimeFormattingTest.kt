package app.mohakash.apod.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Freshness of the cached metadata and the "Synced X ago" caption.
 */
class TimeFormattingTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `a missing sync time is always stale`() {
        assertTrue(TimeFormatting.isStale(null, now))
    }

    @Test
    fun `a recent sync is not stale`() {
        assertFalse(TimeFormatting.isStale(now - 60_000L, now))
        assertFalse(TimeFormatting.isStale(now - TimeFormatting.STALE_AFTER_MILLIS + 1, now))
    }

    @Test
    fun `an old sync is stale`() {
        assertTrue(TimeFormatting.isStale(now - TimeFormatting.STALE_AFTER_MILLIS - 1, now))
    }

    @Test
    fun `a clock that jumped backwards does not trigger a refresh`() {
        assertFalse(TimeFormatting.isStale(now + 60_000L, now))
    }

    @Test
    fun `the sync caption counts minutes hours and days`() {
        assertEquals("Never synced", TimeFormatting.formatSyncedAgo(null, now))
        assertEquals("Just now", TimeFormatting.formatSyncedAgo(now - 30_000L, now))
        assertEquals("5 min ago", TimeFormatting.formatSyncedAgo(now - 5 * 60_000L, now))
        assertEquals("3 h ago", TimeFormatting.formatSyncedAgo(now - 3 * 3_600_000L, now))
        assertEquals("2 d ago", TimeFormatting.formatSyncedAgo(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `an old sync shows the date`() {
        val old = now - 60L * 86_400_000L
        val expected = TimeFormatting.formatDate(old)
        assertEquals(expected, TimeFormatting.formatSyncedAgo(old, now))
    }

    @Test
    fun `iso timestamps are parsed`() {
        val millis = TimeFormatting.parseIsoMillis("2026-10-22T12:00:00Z")
        assertEquals("2026-10-22", TimeFormatting.formatDate(millis!!))
        assertEquals(millis, TimeFormatting.parseIsoMillis("2026-10-22T12:00:00.000Z"))
        assertEquals(null, TimeFormatting.parseIsoMillis("not a date"))
        assertEquals(null, TimeFormatting.parseIsoMillis(null))
    }

    @Test
    fun `api dates are parsed as utc midnight`() {
        val millis = TimeFormatting.parseApiDate("2026-10-22")
        assertEquals("2026-10-22", TimeFormatting.formatDate(millis!!))
    }
}
