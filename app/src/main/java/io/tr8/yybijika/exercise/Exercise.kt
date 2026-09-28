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
) {
    val primaryGloss: String? get() = glosses.firstOrNull()

    /** Examples usable for a cloze: the word has to actually appear in them. */
    val clozeExamples: List<ExampleSentence>
        get() = examples.filter { it.containsTarget }
}

data class ExampleSentence(
    val zh: String,
    val pinyin: String?,
    val gloss: String?,
    val containsTarget: Boolean,
    val tokenCount: Int,
)

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

    /** True when the device actually has a Chinese voice installed. */
    val ttsAvailable: Boolean

    fun shuffleSeed(word: WordBundle, typeId: String): Long
}
