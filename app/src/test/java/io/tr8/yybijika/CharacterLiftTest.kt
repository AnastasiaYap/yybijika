package io.tr8.yybijika

import io.tr8.yybijika.learn.CharacterLift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding the character behind a run of failures.
 *
 * The claim this makes is a statistical one, and a wrong statistical claim is
 * worse than silence: it sends the learner off to study a character that is
 * perfectly fine, and it costs the screen its credibility for the one week it
 * has something real to say.
 */
class CharacterLiftTest {

    /**
     * 院 words go badly; 学 words mostly do not.
     *
     * 学 deliberately appears in both groups. If it only ever appeared beside
     * 院 the two would be indistinguishable — identical lift, arbitrary order —
     * and the test would be asserting a coin toss. Separating them is what makes
     * "which of these two characters is the problem" a real question.
     */
    private val members = mapOf(
        1L to listOf("医", "学", "院"),
        2L to listOf("商", "学", "院"),
        3L to listOf("工", "学", "院"),
        4L to listOf("学", "生"),
        5L to listOf("学", "期"),
        6L to listOf("大", "学"),
        7L to listOf("散", "步"),
        8L to listOf("旅", "行"),
        9L to listOf("经", "理"),
        10L to listOf("合", "同"),
    )

    @Test
    fun `the character shared by the failures is found`() {
        val rate = mapOf(
            1L to 0.8, 2L to 0.9, 3L to 0.7,            // the 院 words
            4L to 0.1, 5L to 0.0, 6L to 0.1,            // 学 without 院: fine
            7L to 0.1, 8L to 0.0, 9L to 0.1, 10L to 0.0,
        )
        val found = CharacterLift.find(rate, members)
        assertTrue("expected 院 among $found", found.any { it.hanzi == "院" })
        assertEquals("院 should be the strongest signal", "院", found.first().hanzi)
        assertTrue(found.first().lift > 1.5)
    }

    /**
     * The one that matters most. If everything is going equally badly there is
     * no character behind it, and saying otherwise would be inventing a cause.
     */
    @Test
    fun `nothing is named when the failures are spread evenly`() {
        val rate = (1L..10L).associateWith { 0.5 }
        assertTrue(CharacterLift.find(rate, members).isEmpty())
    }

    @Test
    fun `nothing is named when nothing is failing`() {
        val rate = (1L..10L).associateWith { 0.0 }
        assertTrue(CharacterLift.find(rate, members).isEmpty())
    }

    /**
     * Two words that both went badly is a coincidence with a name on it. A
     * character needs enough words behind it to show a pattern at all.
     */
    @Test
    fun `a character in too few words is not named`() {
        val rate = mapOf(1L to 0.9, 2L to 0.9, 7L to 0.0, 8L to 0.0, 9L to 0.0)
        val found = CharacterLift.find(rate, members)
        assertTrue("院 is only in two reviewed words here, got $found",
            found.none { it.hanzi == "院" })
    }

    /**
     * The measure is relative to this learner. Someone losing most of
     * everything must not be told that every character is a problem.
     */
    @Test
    fun `a high overall failure rate does not make everything a problem`() {
        val rate = mapOf(
            1L to 0.85, 2L to 0.85, 3L to 0.85,
            4L to 0.8, 5L to 0.8, 6L to 0.8,
            7L to 0.8, 8L to 0.8, 9L to 0.8, 10L to 0.8,
        )
        assertTrue(CharacterLift.find(rate, members).isEmpty())
    }

    @Test
    fun `words never reviewed are ignored rather than counted as perfect`() {
        // Only the 院 words have ever been seen; the rest have no entry at all.
        val rate = mapOf(1L to 0.9, 2L to 0.9, 3L to 0.9)
        // Every reviewed word then contains both 学 and 院 equally, so neither
        // stands out — the unreviewed words must not be counted as successes.
        assertTrue(CharacterLift.find(rate, members).isEmpty())
    }

    @Test
    fun `an empty history says nothing`() {
        assertTrue(CharacterLift.find(emptyMap(), members).isEmpty())
    }
}
