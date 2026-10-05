package io.tr8.yybijika

import io.tr8.yybijika.remind.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When the app is allowed to interrupt somebody.
 *
 * Every one of these guards the same thing: a reminder that arrives when there
 * is nothing to do teaches the reader to ignore reminders, and then the useful
 * one is lost with the rest.
 */
class ReminderTest {

    @Test
    fun `it fires when work is waiting and the day is untouched`() {
        val d = Reminder.decide(due = 12, studiedToday = false, lastRemindedDay = null, today = 100)
        assertTrue(d.why, d.show)
    }

    @Test
    fun `nothing due means nothing said`() {
        val d = Reminder.decide(due = 0, studiedToday = false, lastRemindedDay = null, today = 100)
        assertFalse(d.show)
    }

    /** Having already done the work, being told to do it is pure noise. */
    @Test
    fun `a session today cancels the reminder`() {
        val d = Reminder.decide(due = 9, studiedToday = true, lastRemindedDay = null, today = 100)
        assertFalse(d.show)
    }

    @Test
    fun `it never fires twice in one day`() {
        val d = Reminder.decide(due = 9, studiedToday = false, lastRemindedDay = 100, today = 100)
        assertFalse(d.show)
        val tomorrow = Reminder.decide(due = 9, studiedToday = false, lastRemindedDay = 100, today = 101)
        assertTrue(tomorrow.show)
    }

    /**
     * The bug this exists for: a deck that has never been studied shows its
     * plant as fresh, because a wilting plant is an odd welcome. Read as "they
     * studied today", that suppresses the reminder for ever — for exactly the
     * person who has not started yet and most needs the nudge.
     */
    @Test
    fun `someone who has never studied still gets reminded`() {
        val d = Reminder.decide(
            due = 14,
            studiedToday = false,   // asked directly, never inferred from the plant
            lastRemindedDay = null,
            today = 100,
        )
        assertTrue(d.why, d.show)
    }

    // ----------------------------------------------------------------------
    // Timing
    // ----------------------------------------------------------------------

    @Test
    fun `a time still ahead today is used today`() {
        val now = LocalDateTime.of(2026, 10, 5, 9, 0)
        val next = Reminder.nextOccurrence(LocalTime.of(20, 0), now)
        assertEquals(LocalDateTime.of(2026, 10, 5, 20, 0), next)
    }

    @Test
    fun `a time already past today moves to tomorrow`() {
        val now = LocalDateTime.of(2026, 10, 5, 21, 30)
        val next = Reminder.nextOccurrence(LocalTime.of(20, 0), now)
        assertEquals(LocalDateTime.of(2026, 10, 6, 20, 0), next)
    }

    /**
     * The drift bug this prevents: rescheduling "24 hours from now" each time
     * walks the reminder later and later until it arrives in the small hours.
     */
    @Test
    fun `repeated scheduling keeps the same time of day`() {
        var now = LocalDateTime.of(2026, 10, 5, 7, 0)
        val at = LocalTime.of(20, 0)
        repeat(30) {
            val next = Reminder.nextOccurrence(at, now)
            assertEquals("drifted to ${next.toLocalTime()}", at, next.toLocalTime())
            // Pretend it fired a few minutes late, as a real alarm would.
            now = next.plusMinutes(4)
        }
    }

    @Test
    fun `the wording never gives an instruction`() {
        val (title, body) = Reminder.wording(due = 12, daysAway = 0)
        assertTrue(title.contains("12"))
        listOf(title, body).forEach {
            assertFalse("found a command in '$it'",
                it.contains("Study now", true) || it.contains("Don't forget", true))
        }
    }

    @Test
    fun `one review is not called 1 reviews`() {
        assertEquals("1 review waiting", Reminder.wording(due = 1, daysAway = 0).first)
    }
}
