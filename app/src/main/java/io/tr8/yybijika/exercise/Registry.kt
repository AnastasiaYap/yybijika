package io.tr8.yybijika.exercise

import io.tr8.yybijika.exercise.Requirement.AUDIO
import io.tr8.yybijika.exercise.Requirement.BUILDABLE_SENTENCE
import io.tr8.yybijika.exercise.Requirement.CLOZE_EXAMPLE
import io.tr8.yybijika.exercise.Requirement.CONFUSABLE
import io.tr8.yybijika.exercise.Requirement.DISTRACTORS_3
import io.tr8.yybijika.exercise.Requirement.GLOSS
import io.tr8.yybijika.exercise.Requirement.MEASURE
import io.tr8.yybijika.exercise.Requirement.SEMANTIC_LINK
import io.tr8.yybijika.exercise.Requirement.SHARED_CHARACTER
import io.tr8.yybijika.exercise.Requirement.TAUGHT_CHARACTER
import io.tr8.yybijika.exercise.Requirement.TRANSLATABLE
import io.tr8.yybijika.exercise.Requirement.VERIFIED_PINYIN
import io.tr8.yybijika.learn.Skill
import kotlin.random.Random

/**
 * Every way the app knows how to drill a word.
 *
 * This list is the only place an exercise is declared. The session builder, the
 * coverage report and the enrichment backlog all read it, so a type added here
 * is immediately schedulable, counted, and able to pull its own missing content
 * into the queue.
 */
object Registry {

    val all: List<ExerciseType> = listOf(
        RecallZhToGloss,
        McqMeaning,
        RecallGlossToZh,
        ListenChoose,
        ClozeExample,
        BuildSentence,
        TypeHanzi,
        TellApart,
        ToneIdentify,
        Dictation,
        MeasureWord,
        SemanticChoice,
        OddOneOut,
        PinyinToHanzi,
        TranslateSentence,
        CharacterMeaning,
        WordBuilding,
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): ExerciseType? = byId[id]

    fun forSkill(skill: Skill): List<ExerciseType> = all.filter { it.skill == skill }

    /** Which requirements a word satisfies, evaluated once per word per session. */
    fun satisfied(word: WordBundle, ctx: DeckContext): Set<Requirement> = buildSet {
        if (word.glosses.isNotEmpty()) add(GLOSS)
        if (word.pinyinVerified) add(VERIFIED_PINYIN)
        if (word.clozeExamples.isNotEmpty()) add(CLOZE_EXAMPLE)
        if (word.buildableExamples.isNotEmpty()) add(BUILDABLE_SENTENCE)
        if (word.translatableExamples.isNotEmpty()) add(TRANSLATABLE)
        if (ctx.distractorGlosses(word, 3).size >= 3) add(DISTRACTORS_3)
        if (ctx.ttsAvailable) add(AUDIO)
        if (word.confusables.isNotEmpty() && word.clozeExamples.isNotEmpty()) add(CONFUSABLE)
        if (word.measures.isNotEmpty()) add(MEASURE)
        if (word.semanticLinks.isNotEmpty()) add(SEMANTIC_LINK)
        if (word.taughtCharacters.isNotEmpty()) add(TAUGHT_CHARACTER)
        // Three of a kind plus one outsider, so a family of two is not enough.
        if (word.sharesCharacterWith.groupBy { it.note }.any { it.value.size >= 2 }) {
            add(SHARED_CHARACTER)
        }
    }

    /** Exercise types this word can actually produce right now. */
    fun available(word: WordBundle, ctx: DeckContext): List<ExerciseType> {
        val have = satisfied(word, ctx)
        return all.filter { have.containsAll(it.requires) }
    }
}

// --------------------------------------------------------------------------
// Recognition
// --------------------------------------------------------------------------

object RecallZhToGloss : ExerciseType {
    override val id = "recall_zh2gloss"
    override val skill = Skill.RECOGNITION
    override val label = "Read it"
    override val requires = setOf(GLOSS)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        if (word.primaryGloss == null) return null
        return Exercise.Flashcard(
            typeId = id,
            skill = skill,
            word = word,
            front = word.hanzi,
            back = word.glosses.joinToString(" · "),
            showPinyinOnFront = false,
            // Only a usage note earns the footer. Repeating the gloss that is
            // already on the card teaches nothing and makes the note look like
            // it might be something else.
            explanation = word.usageNotes.firstOrNull(),
        )
    }
}

object McqMeaning : ExerciseType {
    override val id = "mcq_meaning"
    override val skill = Skill.RECOGNITION
    override val label = "Pick the meaning"
    override val requires = setOf(GLOSS, DISTRACTORS_3)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val answer = word.primaryGloss ?: return null
        val distractors = ctx.distractorGlosses(word, 3)
        if (distractors.size < 3) return null
        val choices = (distractors + answer).shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.MultipleChoice(
            typeId = id,
            skill = skill,
            word = word,
            prompt = word.hanzi,
            promptPinyin = word.pinyin,
            choices = choices,
            answerIndex = choices.indexOf(answer),
            explanation = word.usageNotes.firstOrNull(),
        )
    }
}

// --------------------------------------------------------------------------
// Production
// --------------------------------------------------------------------------

object RecallGlossToZh : ExerciseType {
    override val id = "recall_gloss2zh"
    override val skill = Skill.PRODUCTION
    override val label = "Say it in Chinese"
    override val requires = setOf(GLOSS, DISTRACTORS_3)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val gloss = word.primaryGloss ?: return null
        val distractors = ctx.distractorHanzi(word, 3)
        if (distractors.size < 3) return null
        val choices = (distractors + word.hanzi).shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.MultipleChoice(
            typeId = id,
            skill = skill,
            word = word,
            prompt = gloss,
            promptPinyin = null,
            choices = choices,
            answerIndex = choices.indexOf(word.hanzi),
            explanation = "${word.hanzi} · ${word.pinyin}",
        )
    }
}

object TypeHanzi : ExerciseType {
    override val id = "type_hanzi"
    override val skill = Skill.PRODUCTION
    override val label = "Type it"
    // Typing is graded against the pinyin as well as the characters, so a
    // reading we do not trust would mark correct answers wrong.
    override val requires = setOf(GLOSS, VERIFIED_PINYIN)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val gloss = word.primaryGloss ?: return null
        if (!word.pinyinVerified) return null
        // Long set phrases are a memory test rather than a production test.
        if (word.isPhrase) return null
        return Exercise.Typing(
            typeId = id,
            skill = skill,
            word = word,
            prompt = gloss,
            promptPinyin = word.pinyin,
            answer = word.hanzi,
            explanation = "${word.hanzi} · ${word.pinyin}",
        )
    }
}

object BuildSentence : ExerciseType {
    override val id = "build_sentence"
    override val skill = Skill.PRODUCTION
    override val label = "Build the sentence"
    override val requires = setOf(BUILDABLE_SENTENCE)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val sentence = word.buildableExamples.firstOrNull() ?: return null
        // Word tiles, not characters. Cutting 他想提高自己的水平 into nine single
        // characters asks nothing about Chinese; cutting it into 他 / 想 / 提高 /
        // 自己 / 的 / 水平 asks where the words are, which is the skill.
        val solution = sentence.segments
        if (solution.size < 4) return null
        return Exercise.TileBuilder(
            typeId = id,
            skill = skill,
            word = word,
            prompt = sentence.gloss ?: word.primaryGloss.orEmpty(),
            tiles = solution.shuffled(Random(ctx.shuffleSeed(word, id))),
            solution = solution,
            explanation = sentence.pinyin,
        )
    }
}

// --------------------------------------------------------------------------
// Listening
// --------------------------------------------------------------------------

object ListenChoose : ExerciseType {
    override val id = "listen_choose"
    override val skill = Skill.LISTENING
    override val label = "What did you hear?"
    override val requires = setOf(GLOSS, VERIFIED_PINYIN, DISTRACTORS_3, AUDIO)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        // Without a Chinese voice the question would be silent, so it is not
        // offered at all rather than shown broken.
        if (!ctx.ttsAvailable || !word.pinyinVerified) return null
        if (word.primaryGloss == null) return null
        val distractors = ctx.distractorHanzi(word, 3)
        if (distractors.size < 3) return null
        val choices = (distractors + word.hanzi).shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.MultipleChoice(
            typeId = id,
            skill = skill,
            word = word,
            prompt = word.hanzi,          // spoken, not shown, until answered
            promptPinyin = word.pinyin,
            choices = choices,
            answerIndex = choices.indexOf(word.hanzi),
            speakPrompt = true,
            explanation = "${word.hanzi} · ${word.pinyin} · ${word.primaryGloss}",
        )
    }
}

/**
 * Two words that are genuinely easy to mix up, and one sentence that only one of
 * them fits.
 *
 * This is the exercise the deck was missing. Every other type drills a word on
 * its own, which is exactly the condition under which 认为 and 以为 both feel
 * right. Confusion lives between words, so it has to be tested between them: the
 * distractor is not a random other word but the specific one this word is lost
 * against — its near-homophone, its near-synonym, or itself reversed.
 */
object TellApart : ExerciseType {
    override val id = "tell_apart"
    override val skill = Skill.USAGE
    override val label = "Which one fits?"
    override val requires = setOf(GLOSS, CONFUSABLE)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val sentence = word.clozeExamples.firstOrNull() ?: return null
        val at = sentence.zh.indexOf(word.hanzi)
        if (at < 0) return null
        val rival = word.confusables.firstOrNull() ?: return null

        val choices = listOf(word.hanzi, rival.hanzi)
            .shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.Cloze(
            typeId = id,
            skill = skill,
            word = word,
            sentenceBefore = sentence.zh.substring(0, at),
            sentenceAfter = sentence.zh.substring(at + word.hanzi.length),
            answer = word.hanzi,
            choices = choices,
            answerIndex = choices.indexOf(word.hanzi),
            gloss = sentence.gloss,
            // The note is the whole point: getting it right by luck teaches
            // nothing, so the reason is shown either way.
            explanation = rival.note
                ?: "${word.hanzi} ${word.pinyin} — ${word.primaryGloss}  ·  " +
                   "${rival.hanzi} ${rival.pinyin.orEmpty()} — ${rival.gloss.orEmpty()}",
        )
    }
}

// --------------------------------------------------------------------------
// Usage
// --------------------------------------------------------------------------

object ClozeExample : ExerciseType {
    override val id = "cloze_example"
    override val skill = Skill.USAGE
    override val label = "Fill the gap"
    override val requires = setOf(CLOZE_EXAMPLE, DISTRACTORS_3)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val sentence = word.clozeExamples.firstOrNull() ?: return null
        val at = sentence.zh.indexOf(word.hanzi)
        if (at < 0) return null
        val distractors = ctx.distractorHanzi(word, 3)
        if (distractors.size < 3) return null
        val choices = (distractors + word.hanzi).shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.Cloze(
            typeId = id,
            skill = skill,
            word = word,
            sentenceBefore = sentence.zh.substring(0, at),
            sentenceAfter = sentence.zh.substring(at + word.hanzi.length),
            answer = word.hanzi,
            choices = choices,
            answerIndex = choices.indexOf(word.hanzi),
            gloss = sentence.gloss,
            explanation = sentence.pinyin,
        )
    }
}
