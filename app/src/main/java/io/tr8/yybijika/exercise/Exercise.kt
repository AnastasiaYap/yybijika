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
    /**
     * The characters this word is built from, with what each contributes.
     *
     * Empty for a word you added yourself, which has no character data behind
     * it — the two character exercises simply do not offer those words.
     */
    val characters: List<CharacterPart> = emptyList(),
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

    /**
     * One of several, chosen by how often the word has come round.
     *
     * The salt keeps two exercise types from landing on the same sentence in the
     * same session: meeting 合同 in the same clause twice running teaches the
     * clause. Returns null for an empty list so callers keep their own reason
     * for declining.
     */
    fun <T> rotate(options: List<T>, variant: Int, salt: Int): T? =
        if (options.isEmpty()) null
        else options[((variant + salt) % options.size + options.size) % options.size]

    /** Examples usable for a cloze: the word has to actually appear in them. */
    val clozeExamples: List<ExampleSentence>
        get() = examples.filter { it.containsTarget }

    /** Sentences long enough to be worth rebuilding from tiles. */
    val buildableExamples: List<ExampleSentence>
        get() = clozeExamples.filter { it.segmentCount >= 4 }

    /** Sentences that carry a translation, so they can be asked for in reverse. */
    val translatableExamples: List<ExampleSentence>
        get() = examples.filter { it.segmentCount >= 4 && !it.preferredGloss.isNullOrBlank() }

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

    /**
     * Characters worth asking about.
     *
     * They recur, someone wrote a meaning, and they are not grammar. 不 is in
     * seventeen words of this deck and the answer is always "not", which makes
     * it the character a "commonest wins" rule reaches for and the last one
     * worth a question.
     */
    val taughtCharacters: List<CharacterPart>
        get() = characters.filter {
            it.wordCount >= 2 && !it.preferredGloss.isNullOrBlank() && !it.isFunction
        }
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
    /**
     * Whether the deck has a card for the other side.
     *
     * Not every link points at a headword: the measure-word pairs name 间 and
     * 座, which are answers rather than vocabulary. A link with nothing behind
     * it is shown but not offered as one.
     */
    val inDeck: Boolean = true,
)

/**
 * One character of a word, as the exercise generators see it.
 *
 * A flattened copy of the deck's character row rather than a reference to it, so
 * generating a question stays a pure function of the bundle it was handed.
 */
data class CharacterPart(
    val hanzi: String,
    val pinyin: String,
    val gloss: String?,
    val glossEn: String?,
    val wordCount: Int,
    /** Grammar rather than vocabulary — see pipeline/characters.py. */
    val isFunction: Boolean = false,
    /** The meaning in the language questions are being asked in. */
    val preferredGloss: String? = null,
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
    /**
     * The translation in the language questions are being asked in.
     *
     * Resolved when the deck is read rather than chosen by each generator, so
     * an exercise never has to know what the learner picked.
     */
    val preferredGloss: String? = null,
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

    /** An example long enough to rebuild, carrying a translation to work from. */
    TRANSLATABLE,

    /**
     * At least one character in this word recurs elsewhere and has a meaning
     * written for it, so the word can be asked about through its parts.
     */
    TAUGHT_CHARACTER,

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

    /**
     * Write a sentence of your own.
     *
     * The only exercise in the app where nothing is supplied. Every other one
     * hands over the tiles, the options or the target and asks which — this one
     * asks what you would actually say, which is the thing none of the others
     * ever finds out.
     */
    data class Compose(
        override val typeId: String,
        override val skill: Skill,
        override val word: WordBundle,
        /** The scene, so the sentence has a point rather than just a word in it. */
        val situation: String,
        val instruction: String,
        val mustUse: List<String>,
        /** The meaning and any usage note, available on request rather than shown. */
        val hint: String,
        /** The word's own examples — used to notice one being copied back. */
        val examples: List<String>,
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

    /**
     * Meanings of other characters, for the character questions.
     *
     * Drawn from the character table rather than from word glosses: "a large
     * institution" and "to be lively" are the same kind of answer, where a word
     * meaning mixed in among character meanings gives itself away by its shape.
     */
    fun distractorCharacterGlosses(exclude: String, count: Int): List<String>

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

    /**
     * How many times this word has been reviewed.
     *
     * Used to rotate which example a question is built from. Without it, five
     * different exercises all take the first sentence and keep taking it, so a
     * word arrives welded to one context — and after enough repetitions the
     * review is about remembering that sentence rather than knowing the word.
     */
    fun variant(word: WordBundle): Int
}
