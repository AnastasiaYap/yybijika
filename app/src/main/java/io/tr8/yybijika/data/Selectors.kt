package io.tr8.yybijika.data

/**
 * One query per exercise type, selecting the words that can produce it.
 *
 * Counting and sampling read the same statement, so a type can never be offered
 * on the Quiz screen with a count it cannot then fill.
 *
 * Kept free of Android imports on purpose: each predicate mirrors that type's
 * Requirement set in [io.tr8.yybijika.exercise.Registry], which is a duplication
 * SQL cannot avoid — the registry evaluates requirements one loaded word at a
 * time, and the Quiz screen needs the totals before anything is loaded. Because
 * this file is plain Kotlin, ContentDbSqlTest can run every statement against
 * the shipped deck and check the two agree, instead of them being kept in step
 * by hand.
 */
object Selectors {

    /** Words with at least one meaning — the floor for almost every type. */
    private const val GLOSSED =
        "SELECT word_id FROM word_capability WHERE sense_count > 0"

    /** Glossed with a reading we trust, which listening drills grade against. */
    private const val TRUSTED =
        "SELECT word_id FROM word_capability WHERE sense_count > 0 " +
            "AND pinyin_verified = 1"

    /** Words with a character that recurs and has a meaning written for it. */
    private const val TAUGHT_CHARACTER =
        "SELECT DISTINCT wc.word_id FROM word_character wc " +
            "JOIN character ch ON ch.hanzi = wc.hanzi " +
            "JOIN word_capability c ON c.word_id = wc.word_id AND c.sense_count > 0 " +
            "WHERE ch.word_count >= 2 AND ch.gloss IS NOT NULL"

    /** Short enough to type: a set phrase is a memory test, not production. */
    private const val TYPEABLE =
        "SELECT c.word_id FROM word_capability c JOIN word w ON w.id = c.word_id " +
            "WHERE c.sense_count > 0 AND c.pinyin_verified = 1 AND w.is_phrase = 0"

    val byType: Map<String, String> = mapOf(
        "recall_zh2gloss" to GLOSSED,
        "mcq_meaning" to GLOSSED,
        "recall_gloss2zh" to GLOSSED,
        "semantic_choice" to
            """SELECT DISTINCT r.word_id FROM relation r
               JOIN word_capability c ON c.word_id = r.word_id AND c.sense_count > 0
               WHERE r.kind IN ('synonym','antonym')""",
        // Three words sharing one character, so the family needs two besides
        // this one before an odd fourth can be added. DISTINCT because a word
        // made of several shared characters qualifies once, not once per
        // character — without it 1,173 words reported 1,192 available.
        "odd_one_out" to
            """SELECT DISTINCT word_id FROM (
                 SELECT word_id FROM relation
                 WHERE kind = 'shares' AND note IS NOT NULL
                 GROUP BY word_id, note HAVING COUNT(*) >= 2
               )""",

        // A word has a character question when one of its characters recurs
        // elsewhere and someone wrote a meaning for it. A character used in a
        // single word explains nothing, so it does not count.
        "character_meaning" to TAUGHT_CHARACTER,

        "type_hanzi" to TYPEABLE,
        "pinyin_to_hanzi" to TYPEABLE,
        "build_sentence" to "SELECT word_id FROM word_capability WHERE builder_count > 0",
        "translate_sentence" to
            "SELECT word_id FROM word_capability WHERE translatable_count > 0",
        // Two to four characters: one is not a word to build, and a set phrase
        // is a spelling test rather than a question about word formation.
        "word_building" to
            """SELECT DISTINCT wc.word_id FROM word_character wc
               JOIN character ch ON ch.hanzi = wc.hanzi
               JOIN word w ON w.id = wc.word_id
               JOIN word_capability c ON c.word_id = w.id AND c.sense_count > 0
               WHERE ch.word_count >= 2 AND ch.gloss IS NOT NULL
                 AND w.char_count BETWEEN 2 AND 4""",

        "listen_choose" to TRUSTED,
        // Tone drills need a contrast to ask about: an all-neutral reading, or
        // one longer than three syllables, is excluded in the generator and so
        // has to be excluded here too.
        "tone_id" to
            """SELECT c.word_id FROM word_capability c JOIN word w ON w.id = c.word_id
               WHERE c.sense_count > 0 AND c.pinyin_verified = 1
                 AND w.char_count BETWEEN 1 AND 3""",
        "dictation" to "SELECT word_id FROM word_capability WHERE builder_count > 0",

        "cloze_example" to "SELECT word_id FROM word_capability WHERE cloze_count > 0",
        "tell_apart" to
            """SELECT DISTINCT r.word_id FROM relation r
               JOIN example e ON e.word_id = r.word_id AND e.contains_target = 1
               WHERE r.kind IN ('near-homophone','homophone','synonym','reversed')""",
        // Only where this word is the noun: the link is stored from both ends,
        // and "which measure word does 条 take" is not a question.
        // DISTINCT because a noun may take more than one measure word and still
        // be one question: 小说 is 一篇小说 or 一部小说 depending on its length.
        "measure_word" to
            """SELECT DISTINCT word_id FROM relation
               WHERE kind = 'measure' AND note LIKE '一' || related_hanzi || '%'""",
    )
}
