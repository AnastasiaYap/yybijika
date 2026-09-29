package io.tr8.yybijika.exercise

import io.tr8.yybijika.exercise.Requirement.AUDIO
import io.tr8.yybijika.exercise.Requirement.BUILDABLE_SENTENCE
import io.tr8.yybijika.exercise.Requirement.GLOSS
import io.tr8.yybijika.exercise.Requirement.MEASURE
import io.tr8.yybijika.exercise.Requirement.SEMANTIC_LINK
import io.tr8.yybijika.exercise.Requirement.TAUGHT_CHARACTER
import io.tr8.yybijika.exercise.Requirement.TRANSLATABLE
import io.tr8.yybijika.exercise.Requirement.VERIFIED_PINYIN
import io.tr8.yybijika.learn.Prompts
import io.tr8.yybijika.learn.Skill
import kotlin.random.Random

/**
 * The second wave of question types.
 *
 * All seven read content the deck already holds — tone marks, the 44 measure
 * pairs, the 101 hand-written semantic links, the 5,130 shared-character links
 * and the 2,379 segmented sentences. None of them needs new vocabulary, which
 * is the point: a smaller deck has to be asked about in more ways, not fewer.
 */

// --------------------------------------------------------------------------
// Listening
// --------------------------------------------------------------------------

/**
 * Hear the word, name its tones.
 *
 * The reading is shown without its marks, so the syllables are given and only
 * the tones are in question. Every other listening exercise lets you answer from
 * the consonants alone; this one cannot be.
 */
object ToneIdentify : ExerciseType {
    override val id = "tone_id"
    override val skill = Skill.LISTENING
    override val label = "Which tones?"
    override val requires = setOf(GLOSS, VERIFIED_PINYIN, AUDIO)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        if (!ctx.ttsAvailable || !word.pinyinVerified) return null
        if (word.primaryGloss == null) return null
        val pattern = Tones.pattern(word.pinyin)
        // A neutral-only or empty reading has no tone contrast to ask about.
        if (pattern.isEmpty() || pattern.all { it == 0 }) return null
        // Four syllables of tones is a memory test, not an ear test.
        if (pattern.size > 3) return null

        val wrong = Tones.neighbours(pattern, 3)
        if (wrong.size < 3) return null
        val answer = Tones.label(pattern)
        val choices = (wrong.map { Tones.label(it) } + answer)
            .shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.MultipleChoice(
            typeId = id,
            skill = skill,
            word = word,
            prompt = word.hanzi,
            promptPinyin = Tones.toneless(word.pinyin),
            choices = choices,
            answerIndex = choices.indexOf(answer),
            speakPrompt = true,
            explanation = "${word.hanzi} · ${word.pinyin} · ${word.primaryGloss}",
        )
    }
}

/**
 * Hear a whole sentence, rebuild it.
 *
 * Word recognition in isolation is not listening. In a sentence the syllables
 * run together, the tones bend, and the only way through is to hold the whole
 * thing in your head — which is what this asks for, with the tiles as the
 * concession to doing it on a phone.
 */
object Dictation : ExerciseType {
    override val id = "dictation"
    override val skill = Skill.LISTENING
    override val label = "Write what you hear"
    override val requires = setOf(BUILDABLE_SENTENCE, AUDIO)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        if (!ctx.ttsAvailable) return null
        val sentence = word.buildableExamples.firstOrNull() ?: return null
        val solution = sentence.segments
        if (solution.size < 4) return null
        return Exercise.TileBuilder(
            typeId = id,
            skill = skill,
            word = word,
            prompt = "Listen, then put the sentence back together.",
            tiles = solution.shuffled(Random(ctx.shuffleSeed(word, id))),
            solution = solution,
            speak = sentence.zh,
            explanation = listOfNotNull(sentence.pinyin, sentence.preferredGloss)
                .joinToString("  ·  "),
        )
    }
}

// --------------------------------------------------------------------------
// Usage
// --------------------------------------------------------------------------

/**
 * 一 __ 裤子.
 *
 * Measure words are the classic thing a learner postpones and then never
 * acquires, because nothing in a vocabulary list forces the question. The notes
 * recorded 44 of them, each as a phrase; this turns each one back into a choice.
 */
object MeasureWord : ExerciseType {
    override val id = "measure_word"
    override val skill = Skill.USAGE
    override val label = "Which measure word?"
    override val requires = setOf(MEASURE)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val measure = word.measures.firstOrNull() ?: return null
        // Every measure word this noun accepts is excluded from the wrong
        // answers, not just the one being asked for. 小说 takes both 篇 and 部,
        // and offering the other as a distractor marks a right answer wrong.
        val alsoValid = word.measures.map { it.hanzi }.toSet()
        val others = ctx.measureWords
            .filter { it !in alsoValid }
            .shuffled(Random(ctx.shuffleSeed(word, id)))
            .take(3)
        if (others.size < 3) return null
        val choices = (others + measure.hanzi)
            .shuffled(Random(ctx.shuffleSeed(word, id) + 1))
        val alternatives = word.measures.drop(1)
        return Exercise.Cloze(
            typeId = id,
            skill = skill,
            word = word,
            sentenceBefore = "一",
            sentenceAfter = word.hanzi,
            answer = measure.hanzi,
            choices = choices,
            answerIndex = choices.indexOf(measure.hanzi),
            gloss = word.primaryGloss,
            explanation = buildString {
                append(measure.note ?: "一${measure.hanzi}${word.hanzi}")
                append(" · ${measure.hanzi} ${measure.pinyin.orEmpty()}")
                if (alternatives.isNotEmpty()) {
                    append("  ·  also ")
                    append(alternatives.joinToString("、") {
                        it.note ?: "一${it.hanzi}${word.hanzi}"
                    })
                }
            },
        )
    }
}

// --------------------------------------------------------------------------
// Recognition
// --------------------------------------------------------------------------

/**
 * Same or opposite.
 *
 * Meaning is a position among other meanings, not a translation. A learner who
 * can gloss 干净 but cannot reach for 脏 has half a word, and the notes already
 * say which pairs go together.
 */
object SemanticChoice : ExerciseType {
    override val id = "semantic_choice"
    override val skill = Skill.RECOGNITION
    override val label = "Same or opposite"
    override val requires = setOf(GLOSS, SEMANTIC_LINK)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        if (word.primaryGloss == null) return null
        val link = word.semanticLinks.firstOrNull() ?: return null
        val distractors = ctx.distractorHanzi(word, 3).filter { it != link.hanzi }
        if (distractors.size < 3) return null
        val choices = (distractors.take(3) + link.hanzi)
            .shuffled(Random(ctx.shuffleSeed(word, id)))
        val ask = if (link.kind == "antonym") "the opposite of" else "another word for"
        return Exercise.MultipleChoice(
            typeId = id,
            skill = skill,
            word = word,
            prompt = "Which is $ask ${word.hanzi}?",
            promptPinyin = word.pinyin,
            choices = choices,
            answerIndex = choices.indexOf(link.hanzi),
            explanation = "${word.hanzi} ${word.pinyin} — ${word.primaryGloss}  ·  " +
                "${link.hanzi} ${link.pinyin.orEmpty()} — ${link.gloss.orEmpty()}",
        )
    }
}

// --------------------------------------------------------------------------
// Production
// --------------------------------------------------------------------------

/**
 * Read the reading, write the characters.
 *
 * The complement of [TypeHanzi], which starts from the meaning. Starting from
 * the sound is the harder half and the one that matters when someone says a
 * word you have only ever read.
 */
object PinyinToHanzi : ExerciseType {
    override val id = "pinyin_to_hanzi"
    override val skill = Skill.PRODUCTION
    override val label = "Pinyin to characters"
    override val requires = setOf(GLOSS, VERIFIED_PINYIN)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val gloss = word.primaryGloss ?: return null
        if (!word.pinyinVerified) return null
        if (word.isPhrase) return null
        return Exercise.Typing(
            typeId = id,
            skill = skill,
            word = word,
            prompt = word.pinyin,
            // The meaning sits under the reading as a disambiguator rather than
            // as the question: many readings spell more than one word.
            promptPinyin = gloss,
            answer = word.hanzi,
            explanation = "${word.hanzi} · ${word.pinyin} · $gloss",
        )
    }
}

/**
 * Indonesian in, Chinese out, with two tiles that do not belong.
 *
 * [BuildSentence] shows the same sentence's own pieces and only asks for their
 * order. Here the pieces are contested: two plausible extra words are mixed in,
 * so finishing the sentence means deciding what it does not contain as well as
 * what it does.
 */
object TranslateSentence : ExerciseType {
    override val id = "translate_sentence"
    override val skill = Skill.PRODUCTION
    override val label = "Say this in Chinese"
    override val requires = setOf(TRANSLATABLE)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val sentence = word.translatableExamples.firstOrNull() ?: return null
        val solution = sentence.segments
        if (solution.size < 4) return null
        val gloss = sentence.preferredGloss ?: return null

        val decoys = ctx.distractorHanzi(word, 6)
            .filter { it !in solution }
            .take(2)
        val random = Random(ctx.shuffleSeed(word, id))
        return Exercise.TileBuilder(
            typeId = id,
            skill = skill,
            word = word,
            prompt = gloss,
            tiles = (solution + decoys).shuffled(random),
            solution = solution,
            explanation = listOfNotNull(sentence.zh, sentence.pinyin)
                .joinToString("  ·  "),
        )
    }
}

// --------------------------------------------------------------------------
// Characters
// --------------------------------------------------------------------------

/**
 * What is this character doing in this word?
 *
 * The question the deck was built to be able to ask. 医院, 学院, 商学院 and 工学院
 * are four vocabulary items and one idea; a learner who has only ever met them
 * as whole words has memorised the idea four times and still cannot read 法院.
 * Asking what 院 contributes is what turns the fourth one into a guess worth
 * making.
 *
 * The word is named in the prompt, so this is never a bare character quiz — the
 * character is being explained by the word it is standing in.
 */
object CharacterMeaning : ExerciseType {
    override val id = "character_meaning"
    override val skill = Skill.RECOGNITION
    override val label = "What does this character mean?"
    override val requires = setOf(GLOSS, TAUGHT_CHARACTER)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        if (word.primaryGloss == null) return null
        // A one-character word is its own character, so the question becomes
        // "in 内, what does 内 contribute?" — which answers itself and teaches
        // nothing but a moment of irritation.
        if (word.characters.size < 2) return null
        // 这不挺好吗 is a turn of phrase, not a word built from parts. Picking a
        // character out of it and asking what it contributes mistakes a sentence
        // for a compound.
        if (word.isPhrase) return null
        // Among the characters that are vocabulary rather than grammar, the one
        // that recurs the most: it pays for itself across the most other words.
        val part = word.taughtCharacters.maxByOrNull { it.wordCount } ?: return null
        val answer = part.preferredGloss ?: return null
        val distractors = ctx.distractorCharacterGlosses(part.hanzi, 8)
            .filter { it != answer }
            .filterNot { Overlap.collide(answer, it) }
            .distinct()
        if (distractors.size < 3) return null
        val choices = (distractors.take(3) + answer)
            .shuffled(Random(ctx.shuffleSeed(word, id)))
        return Exercise.MultipleChoice(
            typeId = id,
            skill = skill,
            word = word,
            prompt = "In ${word.hanzi}, what does ${part.hanzi} contribute?",
            promptPinyin = "${word.hanzi} ${word.pinyin} — ${word.primaryGloss}",
            choices = choices,
            answerIndex = choices.indexOf(answer),
            explanation = "${part.hanzi} ${part.pinyin} — $answer" +
                "  ·  in ${part.wordCount} words in your deck",
        )
    }
}

/**
 * Build the word from its characters.
 *
 * The reverse of [CharacterMeaning], and the harder direction: recognising that
 * 院 means an institution is one thing, reaching for it when you want to say
 * "faculty" is another. The wrong tiles are other real characters from the deck,
 * so the word cannot be assembled by elimination.
 */
object WordBuilding : ExerciseType {
    override val id = "word_building"
    override val skill = Skill.PRODUCTION
    override val label = "Build the word"
    override val requires = setOf(GLOSS, TAUGHT_CHARACTER)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val gloss = word.primaryGloss ?: return null
        if (word.taughtCharacters.isEmpty()) return null
        val solution = word.characters.map { it.hanzi }
        // One character is not a word to build, and a set phrase is a spelling
        // test rather than a question about how words are put together.
        if (solution.size < 2 || solution.size > 4) return null

        val decoys = ctx.distractorHanzi(word, 12)
            .flatMap { other -> other.map(Char::toString) }
            .filter { it !in solution }
            .distinct()
            .take(3)
        if (decoys.size < 3) return null

        return Exercise.TileBuilder(
            typeId = id,
            skill = skill,
            word = word,
            prompt = gloss,
            tiles = (solution + decoys).shuffled(Random(ctx.shuffleSeed(word, id))),
            solution = solution,
            explanation = word.taughtCharacters.joinToString("  ·  ") {
                "${it.hanzi} ${it.pinyin} — ${it.preferredGloss}"
            },
        )
    }
}

// --------------------------------------------------------------------------
// Writing
// --------------------------------------------------------------------------

/**
 * Say something you mean, using this word.
 *
 * The gap this fills: after a year of the rest of the app you can recognise
 * 1,173 words and have never written one sentence. Recognition and production
 * do not transfer to each other on their own — reaching for 索赔 when you need
 * it is a separate ability from spotting it on a page, and it is built only by
 * being made to reach.
 *
 * Offered only for words already met. Writing with a word introduced ten
 * seconds ago is guessing, and the session builder never introduces a new word
 * as anything but recognition, so this takes care of itself.
 */
object WriteSentence : ExerciseType {
    override val id = "write_sentence"
    override val skill = Skill.COMPOSITION
    override val label = "Write a sentence"
    override val requires = setOf(GLOSS)

    override fun generate(word: WordBundle, ctx: DeckContext): Exercise? {
        val gloss = word.primaryGloss ?: return null
        // A set phrase is already a whole sentence; asking for one around it
        // produces padding rather than composition.
        if (word.isPhrase) return null

        return Exercise.Compose(
            typeId = id,
            skill = skill,
            word = word,
            situation = Prompts.forTags(word.tags, ctx.shuffleSeed(word, id)),
            instruction = Prompts.instruction(listOf(word.hanzi)),
            mustUse = listOf(word.hanzi),
            hint = buildString {
                append("${word.hanzi} ${word.pinyin} — $gloss")
                word.usageNotes.firstOrNull()?.let { append("\n").append(it) }
            },
            examples = word.examples.map { it.zh },
            explanation = word.usageNotes.firstOrNull(),
        )
    }
}
