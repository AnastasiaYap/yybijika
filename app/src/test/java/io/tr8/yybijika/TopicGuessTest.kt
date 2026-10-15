package io.tr8.yybijika

import io.tr8.yybijika.learn.TopicGuess
import io.tr8.yybijika.learn.TopicRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * The classifier that runs on the phone, against the one that built the deck.
 *
 * A word added on a Tuesday is filed by the same rules as the 1,173 words that
 * shipped — not by a second set written in Kotlin that looks similar. The rules
 * travel in content.db, and the only thing that can prove the two agree is
 * running the device code over the deck and comparing it with what the pipeline
 * decided.
 */
class TopicGuessTest {

    private val deck = File("src/personal/assets/content.db")

    private fun connect() =
        DriverManager.getConnection("jdbc:sqlite:${deck.absolutePath}")

    private fun rules(): Pair<List<TopicRules>, List<String>> = connect().use { conn ->
        val exact = mutableListOf<Pair<String, Boolean>>()
        conn.createStatement().executeQuery(
            "SELECT slug, exact_sense FROM topic ORDER BY ordinal"
        ).use { rs -> while (rs.next()) exact += rs.getString(1) to (rs.getInt(2) == 1) }

        val kinds = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        conn.createStatement().executeQuery("SELECT slug, kind, value FROM topic_rule")
            .use { rs ->
                while (rs.next()) {
                    kinds.getOrPut(rs.getString(1)) { mutableMapOf() }
                        .getOrPut(rs.getString(2)) { mutableListOf() }
                        .add(rs.getString(3))
                }
            }
        val built = exact.map { (slug, exactSense) ->
            val k = kinds[slug].orEmpty()
            TopicRules(
                slug = slug,
                words = k["word"].orEmpty().toSet(),
                phrases = k["phrase"].orEmpty(),
                hanzi = k["hanzi"].orEmpty(),
                include = k["include"].orEmpty().toSet(),
                exclude = k["exclude"].orEmpty().toSet(),
                exactSense = exactSense,
            )
        }
        val measures = kinds.values.flatMap { it["measure"].orEmpty() }
        built to measures
    }

    /**
     * The whole point of shipping the rules rather than rewriting them: run the
     * device classifier over every word the pipeline classified, and get the
     * same answer.
     */
    @Test
    fun `the phone files words exactly as the pipeline did`() {
        assumeTrue("no personal deck — see the README", deck.exists())
        val (rules, measures) = rules()

        var checked = 0
        val disagreements = mutableListOf<String>()
        connect().use { conn ->
            conn.createStatement().executeQuery(
                """SELECT w.id, w.hanzi,
                          (SELECT GROUP_CONCAT(s.gloss, '|') FROM sense s
                           WHERE s.word_id = w.id ORDER BY s.ordinal) AS glosses,
                          (SELECT GROUP_CONCAT(wt.slug, ',') FROM
                             (SELECT slug FROM word_topic WHERE word_id = w.id
                              ORDER BY rank) wt) AS topics
                   FROM word w"""
            ).use { rs ->
                while (rs.next()) {
                    val hanzi = rs.getString(2)
                    val glosses = rs.getString(3)?.split("|").orEmpty()
                    if (glosses.isEmpty()) continue
                    val expected = rs.getString(4)?.split(",").orEmpty()
                    val actual = TopicGuess.of(hanzi, glosses, rules, measures)
                    checked++
                    if (actual != expected) {
                        disagreements += "$hanzi: pipeline=$expected phone=$actual"
                    }
                }
            }
        }
        assertTrue("nothing to check — the deck has no glosses?", checked > 500)
        assertEquals(
            "the two classifiers have drifted:\n" + disagreements.take(10).joinToString("\n"),
            emptyList<String>(),
            disagreements,
        )
    }

    // ----------------------------------------------------------------------
    // The rules themselves
    // ----------------------------------------------------------------------

    private val work = TopicRules(
        slug = "work", words = setOf("office", "kantor"), phrases = listOf("tempat kerja"),
        hanzi = listOf("工"), include = setOf("打工"), exclude = setOf("工具"),
        exactSense = false,
    )
    private val size = TopicRules(
        slug = "description", words = setOf("tall", "tinggi"), phrases = emptyList(),
        hanzi = emptyList(), include = emptySet(), exclude = emptySet(),
        exactSense = true,
    )

    @Test
    fun `a keyword in the gloss files the word`() {
        assertEquals(listOf("work"), TopicGuess.of("办公室", listOf("kantor"), listOf(work)))
    }

    @Test
    fun `an exclusion wins over every other signal`() {
        assertEquals(emptyList<String>(), TopicGuess.of("工具", listOf("kantor"), listOf(work)))
    }

    /** The describing words: the adjective has to be the whole meaning. */
    @Test
    fun `an exact-sense topic ignores adjectives buried in a phrase`() {
        assertEquals(
            listOf("description"),
            TopicGuess.of("高", listOf("tinggi", "tall, high"), listOf(size)),
        )
        assertEquals(
            emptyList<String>(),
            TopicGuess.of("大厦", listOf("gedung tinggi", "tower block"), listOf(size)),
        )
    }

    /** A counter is about counting, not about what it counts. */
    @Test
    fun `a measure word is filed as one and nothing else`() {
        val quantity = TopicRules("quantity", emptySet(), emptyList(), emptyList(),
            emptySet(), emptySet(), false)
        assertEquals(
            listOf("quantity"),
            TopicGuess.of(
                "首",
                listOf("satuan untuk lagu dan puisi", "measure word for songs"),
                listOf(work, quantity),
                measurePhrases = listOf("measure word", "satuan untuk"),
            ),
        )
    }

    @Test
    fun `a word about nothing in particular is filed under nothing`() {
        assertEquals(
            emptyList<String>(),
            TopicGuess.of("却", listOf("tetapi, malah", "yet, however"), listOf(work, size)),
        )
    }
}
