package io.tr8.yybijika.exercise

import io.tr8.yybijika.learn.Skill

/**
 * A word with everything the deck knows about it, as the registry sees it.
 *
 * Assembled once per session rather than queried per exercise, so generating a
 * question never touches the database.
 */
data class WordBundle(
    val id: Long,
    val hanzi: String,
    val hanziTrad: String?,
    val pinyin: String,
    val pinyinVerified: Boolean,
    val isPhrase: Boolean,
    val glosses: List<String>,
    val usageNotes: List<String> = emptyList(),
    val examples: List<ExampleSentence> = emptyList(),
    val tags: List<String> = emptyList(),
    val relations: List<Relation> = emptyList(),
) {
    val primaryGloss: String? get() = glosses.firstOrNull()

    /**
     * Words this one is genuinely lost against — not every link, only the kinds
     * where picking the wrong one produces a real mistake.
     */
    val confusables: List<Relation>
        get() = relations.filter {
            it.kind in setOf("near-homophone", "homophone", "synonym", "reversed")
        }

    /** Examples usable for a cloze: the word has to actually appear in them. */
    val clozeExamples: List<ExampleSentence>
        get() = examples.filter { it.containsTarget }

    /** Sentences long enough to be worth rebuilding from tiles. */
    val buildableExamples: List<ExampleSentence>
        get() = clozeExamples.filter { it.segmentCount >= 4 }

    /** Sentences that carry a translation, so they can be asked for in reverse. */
    val translatableExamples: List<ExampleSentence>
        get() = examples.filter { it.segmentCount >= 4 && !it.gloss.isNullOrBlank() }

    /**
     * The measure word this noun takes, when the notes recorded one.
     *
     * Only when this word is the noun. The link is stored from both ends, so
     * 条 also carries a measure relation to 裤子 — and asking "which measure word
     * does 条 take" is nonsense.
     */
    val measures: List<Relation>
        get() = relations.filter {
            it.kind == "measure" && it.note?.startsWith("一" + it.hanzi) == true
        }

    /** Hand-written same/opposite links — the ones that teach a distinction. */
    val semanticLinks: List<Relation>
        get() = relations.filter { it.kind == "synonym" || it.kind == "antonym" }

    /** Other words built from a character this one contains. */
    val sharesCharacterWith: List<Relation>
        get() = relations.filter { it.kind == "shares" && !it.note.isNullOrBlank() }
}

/**
 * A link to another word in the deck.
 *
 * [note] is what separates the two — the part that makes the link teach
 * something rather than merely observe that two words look alike.
 */
data class Relation(
    val kind: String,
    val hanzi: String,
    val pinyin: String?,
    val gloss: String?,
    val note: String?,
)

data class ExampleSentence(
    val zh: String,
    val pinyin: String?,
    val gloss: String?,        // Indonesian
    val glossEn: String?,      // English
    val containsTarget: Boolean,
    val tokenCount: Int,
    /**
     * The sentence cut at word boundaries, by pipeline/segment.py.
     *
     * Tiles are words rather than characters because a character jigsaw is not
     * a language question: 他 想 提 高 自 己 has one plausible order for someone
     * who recognises none of it, where 他 / 想 / 提高 / 自己 asks something real.
     */
    val segments: List<String> = emptyList(),
) {
    val segmentCount: Int get() = segments.size
}

/**
 * What a word must have before a given exercise can be built from it.
 *
 * Declaring requirements rather than checking them inside each generator is what
 * lets the app compute coverage ahead of time — and lets the enrichment pipeline
 * read the same set backwards to find what is worth filling in next.
 */
enum class Requirement {
    GLOSS,
    VERIFIED_PINYIN,
    CLOZE_EXAMPLE,
    BUILDABLE_SENTENCE,
    DISTRACTORS_3,

    /** At least one word this is genuinely confusable with. */
    CONFUSABLE,

    /** A recorded measure word, with this word as the noun. */
    MEASURE,

    /** A hand-written synonym or antonym link. */
    SEMANTIC_LINK,

    /** Two or more other words built from a character this one contains. */
    SHARED_CHARACTER,

    /** An example long enough to rebuild, carrying a translation to work from. */
    TRANSLATABLE,

    /**
     * A Chinese voice is installed on this device.
     *
     * Unlike the others this is a property of the phone rather than the word,
     * but it has to live in the same set: a generator that checked it privately
     * could return null while coverage still claimed the exercise was available.
     */
    AUDIO,
}

/** A generated question, ready for the UI to render. */
sealed interface Exercise {
    val typeId: String
    val skill: Skill
    val word: WordBundle

    /** Shown while grading, so a wrong answer still teaches something. */
    val explanation: String?

    data class Flashcard(
        override val typeId: String,
        override val skill: Skill,
        override val word: WordBundle,
        val front: String,
        val back: String,
        val showPinyinOnFront: Boolean,
        override val explanation: String? = null,
    ) : Exercise

    data class MultipleChoice(
        override val typeId: String,
        override val skill: Skill,
        override val word: WordBundle,
        val prompt: String,
        val promptPinyin: String?,
        val choices: List<String>,
        val answerIndex: Int,
        val speakPrompt: Boolean = false,
        override val explanation: String? = null,
    ) : Exercise

    data class Cloze(
        override val typeId: String,
        override val skill: Skill,
        override val word: WordBundle,
        val sentenceBefore: String,
        val sentenceAfter: String,
        val answer: String,
        val choices: List<String>,
        val answerIndex: Int,
        val gloss: String?,
        override val explanation: String? = null,
    ) : Exercise

    data class TileBuilder(
        override val typeId: String,
        override val skill: Skill,
        override val word: WordBundle,
        val prompt: String,
        val tiles: List<String>,
        val solution: List<String>,
        /** Dictation: the sentence is played, and [prompt] is the instruction. */
        val speak: String? = null,
        override val explanation: String? = null,
    ) : Exercise

    data class Typing(
        override val typeId: String,
        override val skill: Skill,
        override val word: WordBundle,
        val prompt: String,
        val promptPinyin: String?,
        val answer: String,
        override val explanation: String? = null,
    ) : Exercise
}

/**
 * The contract every exercise implements.
 *
 * Adding a new way to drill a word means adding one object here and one
 * composable to render its [Exercise] shape. Nothing else in the app changes,
 * and coverage picks it up automatically.
 */
interface ExerciseType {
    val id: String
    val skill: Skill
    val label: String
    val requires: Set<Requirement>

    /**
     * Build a question, or return null if this word cannot support one.
     *
     * Implementations must return null exactly when [requires] is unmet — a test
     * asserts this, because coverage is computed from [requires] alone and would
     * otherwise be able to lie.
     */
    fun generate(word: WordBundle, ctx: DeckContext): Exercise?
}

/** Everything a generator needs that is not the word itself. */
interface DeckContext {
    /** Plausible wrong answers: same topic and similar length where possible. */
    fun distractorGlosses(word: WordBundle, count: Int): List<String>

    fun distractorHanzi(word: WordBundle, count: Int): List<String>

    /**
     * Every measure word the deck knows, for measure-word distractors.
     *
     * A property of the deck rather than of the word: the wrong answers to
     * "一 __ 裤子" have to be other real measure words, or the question answers
     * itself.
     */
    val measureWords: List<String>

    /** True when the device actually has a Chinese voice installed. */
    val ttsAvailable: Boolean

    fun shuffleSeed(word: WordBundle, typeId: String): Long
}
