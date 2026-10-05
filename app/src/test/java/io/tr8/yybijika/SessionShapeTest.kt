package io.tr8.yybijika

import io.tr8.yybijika.exercise.Exercise
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.SessionBuilder
import io.tr8.yybijika.learn.SessionItem
import io.tr8.yybijika.learn.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of a sitting, and what happens to the things you get wrong.
 *
 * Both of these are about the session as an experience rather than as a list of
 * questions: whether a failure is ever actually re-tested, and whether the
 * sitting is one somebody will come back to tomorrow.
 */
class SessionShapeTest {

    private fun item(id: Long, box: Int, isNew: Boolean = false): SessionItem {
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
        )
    }

    private fun ids(items: List<SessionItem>) = items.map { it.exercise.word.id }

    // ----------------------------------------------------------------------
    // Failures come back
    // ----------------------------------------------------------------------

    /**
     * The hole this fills: the session was a fixed list, so a word you got
     * wrong was shown its answer and never asked again. Seeing a correction is
     * not producing it.
     */
    @Test
    fun `a failed item is asked again later in the same session`() {
        val queue = (1L..8L).map { item(it, box = 2) }
        val after = SessionBuilder.requeue(queue, at = 0, item = queue[0])
        assertEquals("the session grew by the retry", 9, after.size)
        assertTrue("the failed word returns", ids(after).count { it == 1L } == 2)
    }

    /** Far enough that the answer is out of mind, not a re-read of the screen. */
    @Test
    fun `the retry is not the very next question`() {
        val queue = (1L..8L).map { item(it, box = 2) }
        val after = SessionBuilder.requeue(queue, at = 0, item = queue[0])
        assertTrue("asked again immediately tests the last three seconds",
            ids(after).drop(1).indexOf(1L) >= 2)
    }

    @Test
    fun `the retry is marked so the learner knows why it is back`() {
        val queue = (1L..8L).map { item(it, box = 2) }
        val after = SessionBuilder.requeue(queue, at = 0, item = queue[0])
        assertTrue(after.filter { it.isRetry }.size == 1)
        assertFalse("the original is untouched", after[0].isRetry)
    }

    /** A failure on the last question must still come back, not fall off the end. */
    @Test
    fun `a failure near the end is still re-asked`() {
        val queue = (1L..4L).map { item(it, box = 2) }
        val after = SessionBuilder.requeue(queue, at = 3, item = queue[3])
        assertEquals(5, after.size)
        assertEquals("it lands at the end", 4L, ids(after).last())
    }

    // ----------------------------------------------------------------------
    // Shape
    // ----------------------------------------------------------------------

    /**
     * Most-overdue-first opens a bad fortnight with five things you have
     * already failed, which is the least likely way to get anyone through a
     * session.
     */
    @Test
    fun `the session opens and closes on something solid`() {
        val items = listOf(
            item(1, box = 0), item(2, box = 0), item(3, box = 1),
            item(4, box = 6), item(5, box = 5), item(6, box = 0),
        )
        val shaped = SessionBuilder.shape(items)
        assertEquals("every item is still there", items.size, shaped.size)
        assertEquals("opens on the most solid", 4L, ids(shaped).first())
        assertEquals("ends on the next most solid", 5L, ids(shaped).last())
    }

    @Test
    fun `the hard ones end up in the middle`() {
        val items = listOf(
            item(1, box = 0), item(2, box = 0), item(3, box = 6),
            item(4, box = 5), item(5, box = 0), item(6, box = 1),
        )
        val middle = ids(SessionBuilder.shape(items)).drop(1).dropLast(1)
        assertTrue("the weakest should sit in the middle", middle.containsAll(listOf(1L, 2L, 5L)))
    }

    /** A word met for the first time has never been answered, so it is not solid. */
    @Test
    fun `a brand new word is never used as the opener`() {
        val items = listOf(
            item(1, box = 9, isNew = true), item(2, box = 3), item(3, box = 2),
            item(4, box = 1), item(5, box = 0), item(6, box = 0),
        )
        val shaped = SessionBuilder.shape(items)
        assertFalse("a new word is not a confidence opener", shaped.first().isNew)
    }

    @Test
    fun `nothing is lost or duplicated by shaping`() {
        val items = (1L..9L).map { item(it, box = (it % 4).toInt()) }
        val shaped = SessionBuilder.shape(items)
        assertEquals(ids(items).sorted(), ids(shaped).sorted())
    }

    @Test
    fun `a very short session is left alone`() {
        val items = (1L..3L).map { item(it, box = 1) }
        assertEquals(ids(items), ids(SessionBuilder.shape(items)))
    }
}
