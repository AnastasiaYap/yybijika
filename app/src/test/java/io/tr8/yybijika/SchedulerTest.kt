package io.tr8.yybijika

import io.tr8.yybijika.learn.Grade
import io.tr8.yybijika.learn.Scheduler
import io.tr8.yybijika.learn.Skill
import io.tr8.yybijika.learn.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SchedulerTest {

    /** A fixed seed keeps the interval fuzz out of the assertions. */
    private fun fixed() = Random(42)

    @Test
    fun `climbs to the top box after eight good answers`() {
        var state = Scheduler.State()
        repeat(8) { state = Scheduler.review(state, Grade.GOOD, fixed()).first }
        assertEquals(Scheduler.MAX_BOX, state.box)
        assertEquals(8, state.streak)
    }

    @Test
    fun `top box cannot be exceeded`() {
        var state = Scheduler.State()
        repeat(20) { state = Scheduler.review(state, Grade.GOOD, fixed()).first }
        assertEquals(Scheduler.MAX_BOX, state.box)
        assertEquals(365, Scheduler.INTERVALS[state.box])
    }

    @Test
    fun `again halves the box rather than resetting it`() {
        val state = Scheduler.State(box = 6, streak = 6)
        val (next, _) = Scheduler.review(state, Grade.AGAIN, fixed())
        assertEquals(3, next.box)
        assertEquals(0, next.streak)
        assertEquals(1, next.lapses)
    }

    @Test
    fun `hard keeps the box but shortens the wait`() {
        val state = Scheduler.State(box = 5)   // 30 days
        val (next, days) = Scheduler.review(state, Grade.HARD, fixed())
        assertEquals(5, next.box)
        assertTrue("hard should be shorter than 30d, was $days", days < 30)
        assertTrue("hard should still be a real wait, was $days", days > 10)
    }

    @Test
    fun `fuzz keeps intervals within ten percent`() {
        val state = Scheduler.State(box = 5)   // 30 days
        repeat(200) {
            val days = Scheduler.intervalDays(state, Grade.GOOD, Random.Default)
            // Good moves to box 6, which is 60 days.
            assertTrue("interval $days outside fuzz band", days in 54..66)
        }
    }

    @Test
    fun `a new card is due immediately`() {
        assertEquals(0, Scheduler.intervalDays(Scheduler.State(), Grade.AGAIN, fixed()))
    }

    @Test
    fun `button previews are not fuzzed`() {
        val state = Scheduler.State(box = 3)
        repeat(20) {
            assertEquals(14, Scheduler.previewDays(state, Grade.GOOD))
        }
    }

    @Test
    fun `mastery runs from nothing to fully learned`() {
        assertEquals(0f, Scheduler.mastery(0), 0.001f)
        assertEquals(1f, Scheduler.mastery(Scheduler.MAX_BOX), 0.001f)
    }

    @Test
    fun `xp never influences scheduling`() {
        // The scheduler's inputs are the state and the grade. If XP could reach
        // it, two identical reviews with different point totals would diverge.
        val state = Scheduler.State(box = 4)
        val a = Scheduler.review(state, Grade.GOOD, Random(7))
        Xp.award(Grade.GOOD, Skill.USAGE, combo = 9)
        val b = Scheduler.review(state, Grade.GOOD, Random(7))
        assertEquals(a, b)
    }
}

class XpTest {

    @Test
    fun `a lapse scores nothing but is not punished`() {
        assertEquals(0, Xp.award(Grade.AGAIN, Skill.USAGE, combo = 5))
    }

    @Test
    fun `harder skills are worth more`() {
        val recognition = Xp.award(Grade.GOOD, Skill.RECOGNITION, combo = 0)
        val usage = Xp.award(Grade.GOOD, Skill.USAGE, combo = 0)
        assertTrue("usage ($usage) should beat recognition ($recognition)",
            usage > recognition)
    }

    @Test
    fun `combo multiplier is capped`() {
        val atTen = Xp.award(Grade.GOOD, Skill.RECOGNITION, combo = 10)
        val atThousand = Xp.award(Grade.GOOD, Skill.RECOGNITION, combo = 1000)
        assertEquals(atTen, atThousand)
    }

    @Test
    fun `combo resets on a lapse`() {
        assertEquals(0, Xp.nextCombo(9, Grade.AGAIN))
        assertEquals(10, Xp.nextCombo(9, Grade.GOOD))
        assertEquals(10, Xp.nextCombo(9, Grade.HARD))
    }

    @Test
    fun `levels widen as they go`() {
        assertEquals(1, Xp.levelFor(0))
        assertEquals(1, Xp.levelFor(99))
        assertEquals(2, Xp.levelFor(100))
        assertEquals(3, Xp.levelFor(400))
        assertEquals(4, Xp.levelFor(900))
        // Each level costs more than the one before it.
        val first = Xp.xpForLevel(3) - Xp.xpForLevel(2)
        val later = Xp.xpForLevel(9) - Xp.xpForLevel(8)
        assertTrue("later levels should cost more", later > first)
    }

    @Test
    fun `level progress stays within bounds`() {
        for (xp in listOf(0, 1, 99, 100, 101, 399, 400, 10_000)) {
            val p = Xp.levelProgress(xp)
            assertTrue("progress $p out of range at $xp", p in 0f..1f)
        }
    }
}
