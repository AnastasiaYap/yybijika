package io.tr8.yybijika

import io.tr8.yybijika.exercise.Exercise
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.FlagReason
import io.tr8.yybijika.learn.FlaggedCard
import io.tr8.yybijika.learn.Flags
import io.tr8.yybijika.learn.SessionBuilder
import io.tr8.yybijika.learn.SessionItem
import io.tr8.yybijika.learn.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Saying a card is wrong.
 *
 * Two things have to hold for the flag to be worth pressing: the word has to
 * actually leave the sitting it was reported in, and the list has to come off
 * the phone in a form that can be acted on — the correction happens in the
 * notes, not in the app.
 */
class FlagTest {

    private fun item(id: Long, skill: Skill = Skill.RECOGNITION): SessionItem {
        val word = WordBundle(
            id = id, hanzi = "词$id", hanziTrad = null, pinyin = "cí",
            pinyinVerified = true, isPhrase = false, glosses = listOf("kata"),
        )
        return SessionItem(
            exercise = Exercise.Flashcard(
                typeId = "recall_zh2gloss", skill = skill, word = word,
                front = word.hanzi, back = "kata", showPinyinOnFront = false,
            ),
            skill = skill,
            box = 2,
            isNew = false,
        )
    }

    private fun ids(items: List<SessionItem>) = items.map { it.exercise.word.id }

    // ----------------------------------------------------------------------
    // The word leaves the session
    // ----------------------------------------------------------------------

    /**
     * A word can be in one sitting several times over — a retry it earned
     * earlier, or a second skill that came due the same day. Reporting the card
     * has to clear all of them, or the doubted question comes straight back.
     */
    @Test
    fun `reporting a card clears the rest of its appearances`() {
        val queue = listOf(
            item(1L), item(2L), item(3L, Skill.LISTENING),
            item(3L), item(4L), item(3L, Skill.PRODUCTION),
        )
        val after = SessionBuilder.dropFrom(queue, at = 2, wordId = 3L)
        assertEquals("only the two later copies went", 4, after.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(after))
    }

    /**
     * Everything already answered stays answered. Rewriting the queue under the
     * index would renumber the sitting and change which card is on screen.
     */
    @Test
    fun `answered questions are left alone`() {
        val queue = listOf(item(5L), item(6L), item(5L), item(7L))
        val after = SessionBuilder.dropFrom(queue, at = 2, wordId = 5L)
        assertEquals("the one on screen and the one behind it both stay",
            listOf(5L, 6L, 5L, 7L), ids(after))
    }

    @Test
    fun `other words are untouched`() {
        val queue = (1L..6L).map { item(it) }
        val after = SessionBuilder.dropFrom(queue, at = 0, wordId = 99L)
        assertEquals(queue.size, after.size)
    }

    // ----------------------------------------------------------------------
    // The list leaves the phone
    // ----------------------------------------------------------------------

    private fun flag(
        hanzi: String,
        reason: FlagReason,
        shown: String? = null,
        note: String? = null,
    ) = FlaggedCard(
        id = 1, wordId = 1, hanzi = hanzi, pinyin = "guàng jiē",
        reason = reason, shown = shown, note = note,
        // 5 October 2026, 09:00 UTC.
        at = 1791277200000L,
    )

    @Test
    fun `the report names the word, the fault and what the card said`() {
        val text = Flags.report(
            listOf(
                flag("逛街", FlagReason.MEANING,
                    shown = "逛街 → jalan-jalan belanja",
                    note = "it is window shopping")
            ),
            ZoneId.of("Asia/Jakarta"),
        )
        assertTrue("the word is there", text.contains("逛街"))
        assertTrue("the fault is named", text.contains("the meaning is wrong"))
        assertTrue("what was on the card is kept", text.contains("jalan-jalan belanja"))
        assertTrue("so is what she said about it", text.contains("window shopping"))
    }

    /** A note is optional, and an absent one must not leave a dangling label. */
    @Test
    fun `a flag with no note produces no empty line`() {
        val text = Flags.report(listOf(flag("逛街", FlagReason.PINYIN)))
        assertFalse(text.contains("you said:"))
        assertFalse(text.contains("card said:"))
    }

    @Test
    fun `an empty list says so rather than producing a header for nothing`() {
        assertEquals("Nothing flagged.", Flags.report(emptyList()))
    }

    @Test
    fun `the count is singular for one`() {
        assertTrue(Flags.report(listOf(flag("词", FlagReason.OTHER))).startsWith("1 card flagged"))
    }

    /**
     * Reasons are stored as strings so the table outlives any renaming of the
     * enum, which means an unknown one has to land somewhere rather than throw
     * on a screen built out of the learner's own history.
     */
    @Test
    fun `an unrecognised reason reads as something else`() {
        assertEquals(FlagReason.OTHER, FlagReason.of("tone-sandhi"))
        assertEquals(FlagReason.MEANING, FlagReason.of("meaning"))
    }
}
