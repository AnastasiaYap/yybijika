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

    // ----------------------------------------------------------------------
    // Growth you can actually see
    // ----------------------------------------------------------------------

    /**
     * The fault this replaces: one joint per twenty-five held words meant a
     * new deck drew a finished-looking plant on day one and then did not move
     * for weeks — the exact weeks the plant exists to get somebody through.
     */
    @Test
    fun `the first joints are cheap`() {
        assertEquals("a new deck is a sprout", 1, Vitality.joints(0))
        assertEquals(2, Vitality.joints(2))
        assertEquals(3, Vitality.joints(5))
        assertTrue("a fortnight of study shows", Vitality.joints(10) >= 4)
    }

    /** And expensive later, so a big deck still has somewhere to grow. */
    @Test
    fun `growth slows as the deck gets large`() {
        assertTrue(Vitality.joints(60) < Vitality.MAX_JOINTS)
        assertEquals(Vitality.MAX_JOINTS, Vitality.joints(5_000))
        assertEquals("fully grown has no next rung", null, Vitality.nextJointAt(5_000))
        assertEquals(2, Vitality.nextJointAt(0))
    }

    @Test
    fun `the stalk never shrinks as more words are held`() {
        val series = (0..400).map { Vitality.joints(it) }
        series.zipWithNext().forEach { (a, b) ->
            assertTrue("joints went down at a larger deck", b >= a)
        }
    }

    /**
     * The half of the plant that answers "did what I just did matter?". A word
     * needs three separate days to be genuinely held, so the stalk cannot.
     */
    @Test
    fun `the first question of the day puts out a leaf`() {
        val before = Vitality.of(held = 0, due = 5, daysAway = 0, reviewsToday = 0)
        val after = Vitality.of(held = 0, due = 4, daysAway = 0, reviewsToday = 1)
        assertTrue("a session has to change the picture", after.leaves > before.leaves)
    }

    @Test
    fun `more work today means more leaves`() {
        val leaves = listOf(0, 1, 6, 15, 40).map {
            Vitality.of(held = 3, due = 0, daysAway = 0, reviewsToday = it).leaves
        }
        assertEquals("they are capped", leaves.max(), leaves.last())
        leaves.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    /** A plant months old must not look bare before the day's first question. */
    @Test
    fun `an established stalk keeps leaves overnight`() {
        val morning = Vitality.of(held = 120, due = 9, daysAway = 1, reviewsToday = 0)
        assertTrue(morning.leaves > 0)
    }

    @Test
    fun `the caption says what would move it`() {
        val fresh = Vitality.of(held = 0, due = 0, daysAway = 0, reviewsToday = 0)
        assertEquals("Your bamboo grows as words stick.", Vitality.caption(fresh))

        val working = Vitality.caption(
            Vitality.of(held = 1, due = 0, daysAway = 0, reviewsToday = 4)
        )
        assertTrue(working.contains("4 answered today"))
        assertTrue("singular, not '1 words'", working.contains("1 word held"))
        assertTrue("the next rung is named", working.contains("grows again at 2"))
    }
}
