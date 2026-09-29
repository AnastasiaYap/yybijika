package io.tr8.yybijika

import io.tr8.yybijika.data.Selectors
import io.tr8.yybijika.exercise.Registry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * The coverage counts, checked against the deck they describe.
 *
 * The Quiz screen offers a question type with a number beside it, and that
 * number comes from SQL while the question itself comes from the registry
 * evaluating Requirements on a loaded word. The two are written in different
 * languages against the same rules, so nothing but a test can stop them
 * drifting — and the failure mode is quiet: a type shows 900 available, you pick
 * it, and the quiz is empty.
 *
 * Reads the real content.db out of the app's assets over JDBC, which is why
 * [Selectors] carries no Android imports.
 */
class ContentDbSqlTest {

    private val deck = File("src/main/assets/content.db")

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${deck.absolutePath}").use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use(read) }
        }

    private fun count(sql: String): Int =
        query("SELECT COUNT(*) FROM ($sql)") { if (it.next()) it.getInt(1) else 0 }

    @Test
    fun `every exercise type has a selector`() {
        assumeTrue("content.db has not been built", deck.exists())
        val registered = Registry.all.map { it.id }.toSet()
        val selected = Selectors.byType.keys
        assertEquals(
            "a type with no selector is invisible on the Quiz screen; " +
                "a selector with no type is dead SQL",
            registered, selected,
        )
    }

    @Test
    fun `every selector runs and finds words`() {
        assumeTrue("content.db has not been built", deck.exists())
        for ((id, sql) in Selectors.byType) {
            val n = count(sql)
            assertTrue("$id selects no words from the shipped deck", n > 0)
            assertTrue("$id selects more rows than the deck has words", n <= 1400)
        }
    }

    /**
     * The selectors return word ids, and a quiz loads bundles by those ids. A
     * selector that returned a relation id or a duplicate would silently build
     * a shorter quiz than asked for.
     */
    @Test
    fun `selectors return distinct ids that exist in the word table`() {
        assumeTrue("content.db has not been built", deck.exists())
        for ((id, sql) in Selectors.byType) {
            val orphaned = count(
                "SELECT word_id FROM ($sql) s WHERE s.word_id NOT IN (SELECT id FROM word)"
            )
            assertEquals("$id selects ids that are not words", 0, orphaned)

            val total = count(sql)
            val distinct = count("SELECT DISTINCT word_id FROM ($sql)")
            assertEquals("$id returns the same word more than once", total, distinct)
        }
    }

    /**
     * Every link the reader offers has to have something behind it.
     *
     * The failure this replaces: tapping a word in a passage opened a blank
     * screen, because the reader made every word a link and only some of them
     * are in the deck. The fix is that a piece knows whether it is openable, and
     * this is the check that the two sets are actually different — if every
     * segment were a headword the fix would be untested and the next passage
     * would bring the bug back.
     */
    @Test
    fun `passages contain words the deck has no card for`() {
        assumeTrue("content.db has not been built", deck.exists())
        // Rebuilt the way the app does it: segments split on NUL, looked up.
        val headwords = query("SELECT hanzi FROM word") { rs ->
            buildSet<String> { while (rs.next()) add(rs.getString(1)) }
        }
        val pieces = query(
            "SELECT segments FROM passage_token WHERE segments IS NOT NULL"
        ) { rs ->
            buildList<String> {
                while (rs.next()) {
                    addAll(rs.getString(1).split('\u0000').filter { it.isNotBlank() })
                }
            }
        }
        assertTrue("no passage has been segmented", pieces.isNotEmpty())

        val han = pieces.filter { p -> p.any { it.code in 0x4E00..0x9FFF } }
        val openable = han.count { it in headwords }
        assertTrue("some passage words must be openable", openable > 0)
        assertTrue(
            "if every word were in the deck, the openable check would be dead " +
                "code and the blank screen would return with the next passage",
            openable < han.size,
        )
    }

    /**
     * Relations point at a hanzi, not always at a headword: the measure-word
     * pairs name 间 and 座, which the deck teaches inside words and has no
     * entry for. Those links are shown but must not open.
     */
    @Test
    fun `some relations point outside the deck`() {
        assumeTrue("content.db has not been built", deck.exists())
        val dangling = count(
            """SELECT r.rowid AS word_id FROM relation r
               WHERE NOT EXISTS (SELECT 1 FROM word w WHERE w.hanzi = r.related_hanzi)"""
        )
        assertTrue(
            "relations to non-headwords are expected — the reader has to know " +
                "which they are rather than assume there are none",
            dangling > 0,
        )
    }

    /**
     * The deck used to give every word exactly two examples and every exercise
     * the first of them. Borrowed contexts are how that was fixed without
     * writing two thousand more sentences, so the check is that they are
     * actually there — and that a borrowed sentence really does contain its
     * word, since a context that does not is worse than none.
     */
    @Test
    fun `words carry more than one context and every context contains its word`() {
        assumeTrue("content.db has not been built", deck.exists())
        val thin = count(
            """SELECT word_id FROM example GROUP BY word_id HAVING COUNT(*) < 2"""
        )
        assertEquals("every word needs at least two contexts", 0, thin)

        val borrowed = count("SELECT id AS word_id FROM example WHERE source = 'borrowed'")
        assertTrue("borrowed contexts should exist", borrowed > 300)

        val wrong = count(
            """SELECT e.id AS word_id FROM example e JOIN word w ON w.id = e.word_id
               WHERE e.source = 'borrowed' AND instr(e.zh, w.hanzi) = 0"""
        )
        assertEquals("a borrowed context must contain the word it was borrowed for",
            0, wrong)
    }

    /**
     * A sentence borrowed for a grammar word teaches nothing — every sentence
     * contains 不 — so those are excluded, and this is the check that the
     * exclusion held.
     */
    @Test
    fun `no context is borrowed for a grammar word`() {
        assumeTrue("content.db has not been built", deck.exists())
        val grammar = count(
            """SELECT e.id AS word_id FROM example e
               JOIN word w ON w.id = e.word_id
               JOIN character ch ON ch.hanzi = w.hanzi
               WHERE e.source = 'borrowed' AND ch.is_function = 1"""
        )
        assertEquals(0, grammar)
    }

    /**
     * The baseline ships so the phone can judge a passage written on it the
     * same way the build judges a hand-written one. Without it, "is this word
     * unfamiliar" has no answer on the device: the notes record what was new,
     * so 今天 is in neither the deck nor the notes and is new to nobody.
     */
    @Test
    fun `the assumed vocabulary ships with the deck`() {
        assumeTrue("content.db has not been built", deck.exists())
        val n = count("SELECT hanzi AS word_id FROM assumed_known")
        assertTrue("HSK 1-3 should be about 1,300 words, got $n", n in 1000..2000)

        // The two sets are meant to overlap, not to be the same thing: the
        // baseline is what she knows, the deck is what she wrote down.
        val both = count(
            """SELECT a.hanzi AS word_id FROM assumed_known a
               JOIN word w ON w.hanzi = a.hanzi"""
        )
        assertTrue("the baseline and the deck must overlap", both > 100)
        assertTrue("the baseline must also hold words the deck does not", both < n)
    }

    /**
     * The character layer is the point of the character questions, so a deck
     * that shipped without meanings would leave both types generating nothing
     * while the Quiz screen still counted them as available.
     */
    @Test
    fun `every recurring character carries a meaning in both languages`() {
        assumeTrue("content.db has not been built", deck.exists())
        val unglossed = count(
            """SELECT hanzi AS word_id FROM character
               WHERE word_count >= 2
                 AND (gloss IS NULL OR gloss = '' OR gloss_en IS NULL OR gloss_en = '')"""
        )
        assertEquals("a character in two or more words must say what it means",
            0, unglossed)
    }

    @Test
    fun `every word is linked to its own characters in order`() {
        assumeTrue("content.db has not been built", deck.exists())
        // A word whose character rows are missing, duplicated or out of order
        // would build the wrong answer in word_building without failing.
        val mismatched = count(
            """SELECT w.id AS word_id FROM word w
               WHERE w.char_count <> (SELECT COUNT(*) FROM word_character wc
                                       WHERE wc.word_id = w.id)"""
        )
        assertEquals("every character of every word must be linked", 0, mismatched)

        val misordered = count(
            """SELECT word_id FROM word_character
               GROUP BY word_id
               HAVING MIN(position) <> 0 OR MAX(position) <> COUNT(*) - 1"""
        )
        assertEquals("positions must run 0..n-1 with no gaps", 0, misordered)
    }

    /**
     * The reading section claims to be built from the learner's own notes. That
     * is a number the build computes, so it can be checked rather than trusted.
     */
    @Test
    fun `every passage practises words from the deck`() {
        assumeTrue("content.db has not been built", deck.exists())
        val thin = count(
            "SELECT id AS word_id FROM passage WHERE deck_words < 10"
        )
        assertEquals("a passage using fewer than ten of her words is not " +
            "reading practice built from her notes", 0, thin)

        val unlinked = count(
            """SELECT p.id AS word_id FROM passage p
               WHERE p.deck_words <> (SELECT COUNT(*) FROM passage_word pw
                                       WHERE pw.passage_id = p.id)"""
        )
        assertEquals("the count and the linked words must agree", 0, unlinked)
    }

    /**
     * A long passage with four questions is a reading exercise with a token
     * quiz bolted on. The new ones carry both kinds.
     */
    @Test
    fun `long passages carry comprehension and vocabulary questions`() {
        assumeTrue("content.db has not been built", deck.exists())
        val missing = count(
            """SELECT id AS word_id FROM passage p WHERE p.char_count > 300
                 AND ((SELECT COUNT(*) FROM passage_question q
                        WHERE q.passage_id = p.id AND q.kind = 'comprehension') < 5
                   OR (SELECT COUNT(*) FROM passage_question q
                        WHERE q.passage_id = p.id AND q.kind = 'vocabulary') < 3)"""
        )
        assertEquals("a long passage needs both kinds of question", 0, missing)
    }

    @Test
    fun `passage lines are segmented and translated`() {
        assumeTrue("content.db has not been built", deck.exists())
        // Only the long passages: the six short ones predate both features and
        // the reader falls back for them deliberately.
        val bad = count(
            """SELECT t.rowid AS word_id FROM passage_token t
               JOIN passage p ON p.id = t.passage_id
               WHERE p.char_count > 300
                 AND (t.segments IS NULL OR t.segments = ''
                   OR t.gloss IS NULL OR t.gloss = ''
                   OR t.gloss_en IS NULL OR t.gloss_en = '')"""
        )
        assertEquals("every line of a long passage needs words and both " +
            "translations", 0, bad)
    }

    /**
     * Word tiles are the whole reason the pipeline runs a segmenter, so a deck
     * shipped without them would quietly turn every tile exercise back into a
     * character jigsaw rather than fail.
     */
    @Test
    fun `sentences are segmented into words`() {
        assumeTrue("content.db has not been built", deck.exists())
        val unsegmented = count(
            "SELECT id AS word_id FROM example WHERE segments IS NULL OR segments = ''"
        )
        assertEquals("every example must carry its word tiles", 0, unsegmented)

        // If the segmenter had silently fallen back to characters, tiles would
        // outnumber the sentence's own characters one for one.
        val perWord = query(
            "SELECT AVG(CAST(segment_count AS REAL) / token_count) FROM example " +
                "WHERE token_count > 0"
        ) { if (it.next()) it.getDouble(1) else 1.0 }
        assertTrue("tiles look like characters, not words (ratio $perWord)",
            perWord < 0.85)
    }
}
