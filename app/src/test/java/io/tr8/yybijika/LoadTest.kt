package io.tr8.yybijika

import io.tr8.yybijika.exercise.Exercise
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.Load
import io.tr8.yybijika.learn.SessionItem
import io.tr8.yybijika.learn.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Noticing a bad sitting.
 *
 * The thing being tested is a judgement about a person rather than about a
 * word, so the bar is different from the scheduler's: it has to fire late
 * enough not to interrupt an ordinary session, and the help it offers has to
 * cost the session nothing.
 */
class LoadTest {

    private fun item(
        id: Long,
        box: Int,
        isNew: Boolean = false,
        isRetry: Boolean = false,
    ): SessionItem {
        val word = WordBundle(
            id = id, hanzi = "词$id", hanziTrad = null, pinyin = "cí",
            pinyinVerified = true, isPhrase = false, glosses = listOf("kata"),
        )
        return SessionItem(
            exercise = Exercise.Flashcard(
                typeId = "recall_zh2gloss", skill = Skill.RECOGNITION, word = word,
                front = word.hanzi, back = "kata", showPinyinOnFront = false,
            ),
            skill = Skill.RECOGNITION,
            box = box,
            isNew = isNew,
            isRetry = isRetry,
        )
    }

    private fun ids(items: List<SessionItem>) = items.map { it.exercise.word.id }

    /** Every session worth doing contains failures; two is not a signal. */
    @Test
    fun `two failures are an ordinary session`() {
        assertFalse(Load.struggling(1))
        assertFalse(Load.struggling(2))
        assertTrue(Load.struggling(3))
    }

    @Test
    fun `the easiest remaining question is brought forward`() {
        val queue = listOf(
            item(1L, box = 2), item(2L, box = 0), item(3L, box = 1),
            item(4L, box = 7), item(5L, box = 3),
        )
        val after = Load.ease(queue, at = 1)
        assertEquals("the most solid one is next", 4L, after[2].exercise.word.id)
        assertEquals("nothing is lost", queue.size, after.size)
        assertEquals("and the rest keep their order",
            listOf(1L, 2L, 4L, 3L, 5L), ids(after))
    }

    /**
     * A new word is not a comfortable question — it has never been answered —
     * and nor is a retry of something already missed this sitting.
     */
    @Test
    fun `new words and retries are not offered as relief`() {
        val queue = listOf(
            item(1L, box = 0), item(2L, box = 9, isNew = true),
            item(3L, box = 8, isRetry = true), item(4L, box = 2),
        )
        val after = Load.ease(queue, at = 0)
        assertEquals(4L, after[1].exercise.word.id)
    }

    /**
     * A session of nothing but new words has no comfortable question in it, and
     * promoting another failure would make the run worse rather than better.
     * Saying "no change" is the honest outcome.
     */
    @Test
    fun `a session with nothing solid left is untouched`() {
        val queue = listOf(item(1L, box = 0), item(2L, box = 0, isNew = true))
        assertEquals(ids(queue), ids(Load.ease(queue, at = 0)))
    }

    @Test
    fun `easing is a no-op when the easiest is already next`() {
        val queue = listOf(item(1L, box = 1), item(2L, box = 6), item(3L, box = 2))
        assertEquals(ids(queue), ids(Load.ease(queue, at = 0)))
    }

    /** The queue behind the learner is history and must not be rewritten. */
    @Test
    fun `answered questions are never promoted`() {
        val queue = listOf(item(1L, box = 9), item(2L, box = 0), item(3L, box = 4))
        val after = Load.ease(queue, at = 1)
        assertEquals(listOf(1L, 2L, 3L), ids(after))
    }

    /** A run that starts at the very first question is a different message. */
    @Test
    fun `the wording changes with when it happened`() {
        val early = Load.wording(missStreak = 3, done = 1)
        val late = Load.wording(missStreak = 3, done = 12)
        val bad = Load.wording(missStreak = 5, done = 12)
        assertFalse("the three readings are distinct", early == late)
        assertFalse(late == bad)
        assertTrue(early.isNotBlank() && late.isNotBlank() && bad.isNotBlank())
    }
}
