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
