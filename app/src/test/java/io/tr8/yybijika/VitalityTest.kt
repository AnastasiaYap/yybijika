package io.tr8.yybijika

import io.tr8.yybijika.learn.Vitality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plant that gets somebody to open the app.
 *
 * Most of these guard one property: it cannot die. A mechanic that can reach a
 * failure state turns a fortnight away into a dead pet, and the feature meant
 * to bring somebody back becomes the reason they delete the app.
 */
class VitalityTest {

    @Test
    fun `studying today is full vigour`() {
        assertEquals(1.0, Vitality.vigour(0), 0.001)
        assertTrue(Vitality.of(held = 50, due = 3, daysAway = 0).fresh)
    }

    @Test
    fun `it fades with absence but never dies`() {
        val week = Vitality.vigour(7)
        val month = Vitality.vigour(30)
        val year = Vitality.vigour(365)
        assertEquals("a floor, not zero", Vitality.FLOOR, week, 0.001)
        assertEquals("no worse after a month than a week", week, month, 0.001)
        assertEquals("nor after a year", week, year, 0.001)
        assertTrue("always alive", year > 0.0)
    }

    @Test
    fun `fading is monotonic`() {
        val series = (0..10).map { Vitality.vigour(it) }
        series.zipWithNext().forEach { (a, b) ->
            assertTrue("vigour went up while away: $series", b <= a + 1e-9)
        }
    }

    /**
     * One missed day is ordinary life. The slide should be barely visible then
     * and obvious by the third, which is where a habit actually breaks.
     */
    @Test
    fun `one day away barely shows and three days clearly do`() {
        assertTrue("a single day should cost little", Vitality.vigour(1) > 0.85)
        assertTrue("three days should be plain", Vitality.vigour(3) < 0.75)
    }

    /**
     * The asymmetry that makes returning safe: however long you were gone, one
     * session puts it back to full. Nothing is held against you.
     */
    @Test
    fun `one session restores it completely however long the absence`() {
        val afterAges = Vitality.of(held = 40, due = 99, daysAway = 400)
        val afterReturning = Vitality.of(held = 40, due = 99, daysAway = 0)
        assertEquals(1.0, afterReturning.vigour, 0.001)
        assertTrue(afterReturning.vigour > afterAges.vigour)
    }

    // ----------------------------------------------------------------------
    // Growth
    // ----------------------------------------------------------------------

    /**
     * Growth comes from words genuinely held, not from sessions attended — a
     * plant fed by opening the app can be satisfied by opening the app.
     */
    @Test
    fun `the stalk grows with words held`() {
        val new = Vitality.of(held = 0, due = 0, daysAway = 0)
        val some = Vitality.of(held = 60, due = 0, daysAway = 0)
        val many = Vitality.of(held = 300, due = 0, daysAway = 0)
        assertTrue(new.joints < some.joints)
        assertTrue(some.joints < many.joints)
    }

    @Test
    fun `an empty deck still has a plant to look at`() {
        val none = Vitality.of(held = 0, due = 0, daysAway = 0)
        assertTrue("there must be something on the screen", none.joints >= 1)
    }

    @Test
    fun `growth is capped so a large deck stays on screen`() {
        val huge = Vitality.of(held = 100_000, due = 0, daysAway = 0)
        assertEquals(Vitality.MAX_JOINTS, huge.joints)
    }

    /** Absence fades the plant; it must never shrink what was learned. */
    @Test
    fun `being away does not undo growth`() {
        val here = Vitality.of(held = 200, due = 0, daysAway = 0)
        val gone = Vitality.of(held = 200, due = 0, daysAway = 90)
        assertEquals(here.joints, gone.joints)
        assertEquals(here.held, gone.held)
    }

    @Test
    fun `a negative day count is treated as today`() {
        val state = Vitality.of(held = 10, due = 1, daysAway = -3)
        assertEquals(1.0, state.vigour, 0.001)
        assertEquals(0, state.daysAway)
    }
}
