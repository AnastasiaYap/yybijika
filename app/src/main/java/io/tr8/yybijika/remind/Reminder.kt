package io.tr8.yybijika.remind

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When to nudge, and whether to nudge at all.
 *
 * Separated from Android so the decisions can be tested, because they are the
 * part that is easy to get wrong in a way nobody notices until the reminder has
 * already trained somebody to ignore it.
 *
 * Two rules do most of the work:
 *
 *  * **Nothing due, nothing said.** A reminder that arrives when there is no
 *    work waiting teaches the reader that reminders are noise, and the next one
 *    — the one that mattered — gets swiped away with it.
 *  * **Not twice in a day.** Once is a nudge; a second is nagging, and nagging
 *    turns something you chose to do into something you owe.
 */
object Reminder {

    /** Default: evening, when a short session is realistic for most people. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(20, 0)

    data class Decision(val show: Boolean, val why: String)

    /**
     * @param due how many reviews are waiting.
     * @param studiedToday whether a session has already happened today.
     * @param lastRemindedDay epoch day of the last reminder, or null.
     * @param today epoch day now.
     */
    fun decide(
        due: Int,
        studiedToday: Boolean,
        lastRemindedDay: Long?,
        today: Long,
    ): Decision = when {
        due <= 0 -> Decision(false, "nothing is due")
        // Already done the work: a reminder now is a reminder of nothing, and
        // the next one gets believed a little less.
        studiedToday -> Decision(false, "already studied today")
        lastRemindedDay == today -> Decision(false, "already reminded today")
        else -> Decision(true, "$due due")
    }

    /**
     * The next occurrence of [at], today if it is still ahead and tomorrow
     * otherwise.
     *
     * Computed rather than assumed: scheduling "in 24 hours" from whenever the
     * app last ran walks the reminder around the clock until it arrives at
     * three in the morning.
     */
    fun nextOccurrence(
        at: LocalTime,
        now: LocalDateTime,
    ): LocalDateTime {
        val todayAt = LocalDateTime.of(now.toLocalDate(), at)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    fun nextOccurrenceMillis(
        at: LocalTime,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = nextOccurrence(at, now).atZone(zone).toInstant().toEpochMilli()

    /** What the notification says. Plain, and never an instruction. */
    fun wording(due: Int, daysAway: Int): Pair<String, String> {
        val title = if (due == 1) "1 review waiting" else "$due reviews waiting"
        val body = when {
            // The plant is the gentlest possible form of "it has been a while",
            // and it is a statement rather than a reproach.
            daysAway >= 3 -> "Your bamboo is fading. A few minutes brings it back."
            daysAway == 0 -> "A few minutes keeps everything on schedule."
            else -> "Picking up where you left off takes a couple of minutes."
        }
        return title to body
    }

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).toEpochDay()
}
