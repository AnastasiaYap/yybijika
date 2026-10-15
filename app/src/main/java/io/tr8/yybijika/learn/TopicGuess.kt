package io.tr8.yybijika.learn

/**
 * Filing a word you just added.
 *
 * The deck's own vocabulary is classified when it is built, by rules written
 * over the glosses — see pipeline/topics.py. A word you paste into the app on a
 * Tuesday has missed that, and the two ways to catch it are to ask DeepSeek or
 * to run the same rules here.
 *
 * Both exist, and this one is the floor. The rules travel in content.db rather
 * than being written out a second time in Kotlin, because two copies of a
 * keyword list are two copies that disagree within a month — and because the
 * phone has no key of its own, so "classified only when the network is up and
 * the key is set" would mean most added words never get filed at all.
 */
data class TopicRules(
    val slug: String,
    val words: Set<String>,
    val phrases: List<String>,
    val hanzi: List<String>,
    val include: Set<String>,
    val exclude: Set<String>,
    val exactSense: Boolean,
)

object TopicGuess {

    const val SCORE_INCLUDE = 100
    const val SCORE_PHRASE = 8
    const val SCORE_WORD = 5
    const val SCORE_HANZI = 2

    /** Past three, a category stops being a category and becomes a description. */
    const val MAX_TOPICS = 3

    private val TOKEN = Regex("[^a-z]+")
    private val SPLIT = Regex("[,;/|()]| - ")

    /** Each gloss cut into the separate meanings it lists. */
    fun senses(glosses: List<String>): Set<String> = buildSet {
        glosses.forEach { gloss ->
            SPLIT.split(gloss.lowercase()).forEach { chunk ->
                val clean = TOKEN.split(chunk).filter { it.isNotEmpty() }.joinToString(" ")
                if (clean.isNotEmpty()) {
                    add(clean)
                    // "to put" and "put" are the same claim.
                    if (clean.startsWith("to ")) add(clean.removePrefix("to "))
                    if (clean.startsWith("be ")) add(clean.removePrefix("be "))
                }
            }
        }
    }

    fun score(rules: TopicRules, hanzi: String, blob: String, tokens: Set<String>,
              parts: Set<String>): Int {
        if (hanzi in rules.exclude) return 0
        if (hanzi in rules.include) return SCORE_INCLUDE
        var score = 0
        if (rules.phrases.any { it in blob }) score += SCORE_PHRASE
        val haystack = if (rules.exactSense) parts else tokens
        if (rules.words.any { it in haystack }) score += SCORE_WORD
        if (rules.hanzi.any { it in hanzi }) score += SCORE_HANZI
        return score
    }

    /**
     * The topics this word belongs to, strongest first.
     *
     * [measurePhrases] short-circuits the whole thing: a word whose gloss says
     * it is a counter is about counting, not about what it counts, and 首
     * "measure word for songs" filed under music and study would be wrong twice.
     */
    fun of(
        hanzi: String,
        glosses: List<String>,
        rules: List<TopicRules>,
        measurePhrases: List<String> = emptyList(),
    ): List<String> {
        val blob = glosses.joinToString(" ").lowercase()
        if (measurePhrases.any { it in blob }) {
            return rules.map { it.slug }.filter { it == "quantity" }
        }
        val tokens = TOKEN.split(blob).filter { it.isNotEmpty() }.toSet()
        val parts = senses(glosses)
        return rules
            .mapIndexed { i, r -> Triple(score(r, hanzi, blob, tokens, parts), -i, r.slug) }
            .filter { it.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }
                .thenByDescending { it.second })
            .take(MAX_TOPICS)
            .map { it.third }
    }
}
