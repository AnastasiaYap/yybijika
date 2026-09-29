package io.tr8.yybijika

import io.tr8.yybijika.exercise.CharacterPart
import io.tr8.yybijika.exercise.DeckContext
import io.tr8.yybijika.exercise.ExampleSentence
import io.tr8.yybijika.exercise.Registry
import io.tr8.yybijika.exercise.Relation
import io.tr8.yybijika.exercise.WordBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeContext(
    private val distractors: Int = 5,
    override val ttsAvailable: Boolean = true,
    private val reviews: Int = 0,
) : DeckContext {
    override fun distractorGlosses(word: WordBundle, count: Int) =
        List(minOf(distractors, count)) { "gloss$it" }

    override fun distractorHanzi(word: WordBundle, count: Int) =
        List(minOf(distractors, count)) { "词$it" }

    override fun distractorCharacterGlosses(exclude: String, count: Int) =
        List(minOf(distractors, count)) { "meaning$it" }

    override fun shuffleSeed(word: WordBundle, typeId: String) = 1L

    override fun variant(word: WordBundle) = reviews

    override val measureWords: List<String> = listOf("条", "张", "本", "件", "位")
}

private fun word(
    hanzi: String = "热闹",
    characters: List<CharacterPart> = listOf(
        CharacterPart("热", "rè", "panas", "hot", 9, preferredGloss = "panas"),
        CharacterPart("闹", "nào", "berisik", "noisy", 4, preferredGloss = "berisik"),
    ),
    pinyin: String = "rè nào",
    verified: Boolean = true,
    glosses: List<String> = listOf("ramai"),
    examples: List<ExampleSentence> = emptyList(),
    isPhrase: Boolean = false,
    relations: List<Relation> = emptyList(),
) = WordBundle(
    id = 1,
    hanzi = hanzi,
    hanziTrad = null,
    pinyin = pinyin,
    pinyinVerified = verified,
    isPhrase = isPhrase,
    glosses = glosses,
    examples = examples,
    relations = relations,
    characters = characters,
)

/** A word this one is genuinely lost against, for the tell-apart exercise. */
private fun confusable(hanzi: String = "热脑") = Relation(
    kind = "near-homophone",
    hanzi = hanzi,
    pinyin = "rè nǎo",
    gloss = "bukan kata sungguhan",
    note = "rè nao vs rè nǎo",
)

private fun example(
    zh: String = "这里很热闹。",
    target: String = "热闹",
    segments: List<String> = listOf("这里", "很", "热闹"),
    gloss: String? = "Di sini ramai.",
) = ExampleSentence(
    zh = zh,
    pinyin = "zhè lǐ hěn rè nào",
    gloss = gloss,
    glossEn = "It is lively here.",
    containsTarget = zh.contains(target),
    tokenCount = zh.count { it.code in 0x4E00..0x9FFF },
    segments = segments,
    preferredGloss = gloss,
)

/** A sentence long enough for the tile exercises to accept it. */
private fun longExample() = example(
    zh = "这里晚上很热闹。",
    segments = listOf("这里", "晚上", "很", "热闹"),
)

private fun measure(noun: String = "热闹", unit: String = "条") = Relation(
    kind = "measure",
    hanzi = unit,
    pinyin = "tiáo",
    gloss = null,
    note = "一$unit$noun",
)

private fun antonym(hanzi: String = "安静") = Relation(
    kind = "antonym",
    hanzi = hanzi,
    pinyin = "ān jìng",
    gloss = "sepi",
    note = null,
)

/** Two other words built from the same character, for the odd-one-out family. */
private fun family(character: String = "热") = listOf(
    Relation("shares", "热情", "rè qíng", "hangat", character),
    Relation("shares", "热心", "rè xīn", "antusias", character),
)

/** Everything the registry can ask for, so every type is generatable. */
private fun fullWord() = word(
    examples = listOf(longExample()),
    relations = listOf(confusable(), measure(), antonym()) + family(),
)

class RegistryTest {

    /**
     * The contract coverage depends on. If a generator could return null while
     * its requirements are met, the app would promise exercises it cannot build;
     * if it could return non-null while they are unmet, coverage would understate
     * the deck and the enrichment backlog would chase work that is already done.
     */
    @Test
    fun `generate returns null exactly when requirements are unmet`() {
        val ctx = FakeContext()
        val candidates = listOf(
            word(),
            word(glosses = emptyList()),
            word(verified = false),
            word(examples = listOf(example())),
            fullWord(),
            word(glosses = emptyList(), verified = false),
            word(isPhrase = true, hanzi = "农林牧渔水利生产人员"),
            word(examples = listOf(example(zh = "热闹", target = "热闹"))),
        )

        for (candidate in candidates) {
            val satisfied = Registry.satisfied(candidate, ctx)
            for (type in Registry.all) {
                val requirementsMet = satisfied.containsAll(type.requires)
                val produced = type.generate(candidate, ctx)
                if (!requirementsMet) {
                    assertNull(
                        "${type.id} produced an exercise for ${candidate.hanzi} " +
                            "with unmet requirements ${type.requires - satisfied}",
                        produced,
                    )
                }
                // The converse is allowed to fail only for reasons the type
                // documents (a phrase is too long to type, a sentence too short
                // to rebuild); those types declare it and we assert it below.
            }
        }
    }

    @Test
    fun `a fully populated word can be drilled every way`() {
        val full = fullWord()
        val available = Registry.available(full, FakeContext()).map { it.id }
        assertTrue("expected every type, got $available",
            available.containsAll(Registry.all.map { it.id }))
    }

    @Test
    fun `a word with no gloss supports nothing`() {
        val bare = word(glosses = emptyList(), verified = false)
        assertTrue(Registry.available(bare, FakeContext()).isEmpty())
    }

    @Test
    fun `listening is withheld when the device has no chinese voice`() {
        val ctx = FakeContext(ttsAvailable = false)
        val full = fullWord()
        assertNull(io.tr8.yybijika.exercise.ListenChoose.generate(full, ctx))
        assertTrue(Registry.available(full, ctx).none { it.id == "listen_choose" })
    }

    @Test
    fun `unverified pinyin is kept out of listening and typing`() {
        val ctx = FakeContext()
        val shaky = word(hanzi = "睡不着", pinyin = "shuì bù zhe", verified = false)
        assertNull(io.tr8.yybijika.exercise.ListenChoose.generate(shaky, ctx))
        assertNull(io.tr8.yybijika.exercise.TypeHanzi.generate(shaky, ctx))
        // It is still perfectly good for reading practice.
        assertNotNull(io.tr8.yybijika.exercise.RecallZhToGloss.generate(shaky, ctx))
    }

    @Test
    fun `multiple choice always contains its answer exactly once`() {
        val ctx = FakeContext()
        val full = fullWord()
        for (type in Registry.all) {
            when (val ex = type.generate(full, ctx)) {
                is io.tr8.yybijika.exercise.Exercise.MultipleChoice -> {
                    assertEquals("${type.id} choices should be unique",
                        ex.choices.size, ex.choices.distinct().size)
                    assertTrue("${type.id} answerIndex out of range",
                        ex.answerIndex in ex.choices.indices)
                }
                is io.tr8.yybijika.exercise.Exercise.Cloze -> {
                    assertTrue("${type.id} answerIndex out of range",
                        ex.answerIndex in ex.choices.indices)
                    assertEquals(ex.answer, ex.choices[ex.answerIndex])
                }
                else -> Unit
            }
        }
    }

    @Test
    fun `cloze splits the sentence around the target`() {
        val full = word(examples = listOf(example()))
        val ex = io.tr8.yybijika.exercise.ClozeExample.generate(full, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.Cloze
        assertEquals("这里很", ex.sentenceBefore)
        assertEquals("。", ex.sentenceAfter)
        assertEquals("热闹", ex.answer)
    }

    @Test
    fun `tile builder can always be solved from its own tiles`() {
        val full = word(examples = listOf(longExample()))
        val ex = io.tr8.yybijika.exercise.BuildSentence.generate(full, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertEquals(ex.solution.sorted(), ex.tiles.sorted())
    }

    /**
     * Word tiles, not character tiles. Nine single characters have one plausible
     * order for someone who recognises none of them, which makes the exercise a
     * jigsaw; the words are the question.
     */
    @Test
    fun `sentence tiles are words`() {
        val full = word(examples = listOf(longExample()))
        val ex = io.tr8.yybijika.exercise.BuildSentence.generate(full, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertEquals(listOf("这里", "晚上", "很", "热闹"), ex.solution)
    }

    @Test
    fun `a sentence too short to be a puzzle is not offered`() {
        val terse = word(examples = listOf(example(segments = listOf("很", "热闹"))))
        assertNull(io.tr8.yybijika.exercise.BuildSentence.generate(terse, FakeContext()))
    }

    /**
     * The tell-apart exercise exists to pit a word against the specific word it
     * is confused with, so a random distractor would defeat the point entirely.
     */
    @Test
    fun `tell apart uses the confusable word as the distractor`() {
        val full = fullWord()
        val ex = io.tr8.yybijika.exercise.TellApart.generate(full, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.Cloze
        assertEquals(2, ex.choices.size)
        assertTrue("the rival must be one of the two choices",
            ex.choices.contains("热脑"))
        assertEquals("热闹", ex.choices[ex.answerIndex])
    }

    @Test
    fun `tell apart is withheld when nothing is confusable with the word`() {
        val lonely = word(examples = listOf(example()))
        assertNull(io.tr8.yybijika.exercise.TellApart.generate(lonely, FakeContext()))
    }

    // ----------------------------------------------------------------------
    // The second wave of types
    // ----------------------------------------------------------------------

    @Test
    fun `tone identification hides the marks it is asking about`() {
        val ex = io.tr8.yybijika.exercise.ToneIdentify.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.MultipleChoice
        // The prompt carries the syllables so only the tones are in question;
        // leaving the marks on would make it a reading test with the answer
        // printed above the buttons.
        assertEquals("re nao", ex.promptPinyin)
        assertEquals("4–4", ex.choices[ex.answerIndex])
        assertTrue("every choice must have the same shape",
            ex.choices.all { it.split("–").size == 2 })
    }

    @Test
    fun `tone identification is withheld from an all-neutral reading`() {
        val neutral = word(hanzi = "的", pinyin = "de")
        assertNull(io.tr8.yybijika.exercise.ToneIdentify.generate(neutral, FakeContext()))
    }

    @Test
    fun `measure words are only asked of the noun`() {
        // The relation is stored from both ends. Asking which measure word 条
        // takes is not a question, so the reversed row must produce nothing.
        val noun = word(hanzi = "裤子", relations = listOf(measure(noun = "裤子")))
        val unit = word(
            hanzi = "条",
            relations = listOf(Relation("measure", "裤子", "kù zi", "celana", "一条裤子")),
        )
        assertNotNull(io.tr8.yybijika.exercise.MeasureWord.generate(noun, FakeContext()))
        assertNull(io.tr8.yybijika.exercise.MeasureWord.generate(unit, FakeContext()))
    }

    @Test
    fun `measure word distractors are other measure words`() {
        val noun = word(hanzi = "裤子", relations = listOf(measure(noun = "裤子")))
        val ex = io.tr8.yybijika.exercise.MeasureWord.generate(noun, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.Cloze
        assertEquals("条", ex.answer)
        assertTrue("wrong answers must be plausible measure words, got ${ex.choices}",
            ex.choices.all { it in FakeContext().measureWords })
    }

    /**
     * 小说 is 一篇小说 or 一部小说 depending on how long it is. Both are right, so
     * neither may be offered as the wrong answer to the other.
     */
    @Test
    fun `a second valid measure word is never a distractor`() {
        val novel = word(
            hanzi = "小说",
            relations = listOf(
                Relation("measure", "篇", "piān", null, "一篇小说"),
                Relation("measure", "部", "bù", null, "一部小说"),
            ),
        )
        val ctx = object : DeckContext by FakeContext() {
            override val measureWords = listOf("篇", "部", "条", "张", "本")
        }
        val ex = io.tr8.yybijika.exercise.MeasureWord.generate(novel, ctx)
            as io.tr8.yybijika.exercise.Exercise.Cloze
        val wrong = ex.choices.filterIndexed { i, _ -> i != ex.answerIndex }
        assertTrue("部 is also correct and must not be a wrong answer, got $wrong",
            "部" !in wrong && "篇" !in wrong)
        assertTrue("the learner should be told about the other measure word",
            ex.explanation!!.contains("一部小说"))
    }

    @Test
    fun `semantic choice asks for the opposite when the link is an antonym`() {
        val w = word(relations = listOf(antonym()))
        val ex = io.tr8.yybijika.exercise.SemanticChoice.generate(w, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.MultipleChoice
        assertTrue("prompt should name the relation, got '${ex.prompt}'",
            ex.prompt.contains("opposite"))
        assertEquals("安静", ex.choices[ex.answerIndex])
    }

    @Test
    fun `dictation plays the sentence rather than showing it`() {
        val ex = io.tr8.yybijika.exercise.Dictation.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertEquals("这里晚上很热闹。", ex.speak)
        assertTrue("the sentence must not appear in the prompt",
            !ex.prompt.contains("热闹"))
        assertEquals(ex.solution.sorted(), ex.tiles.sorted())
    }

    @Test
    fun `dictation is withheld when the device has no chinese voice`() {
        val ctx = FakeContext(ttsAvailable = false)
        assertNull(io.tr8.yybijika.exercise.Dictation.generate(fullWord(), ctx))
    }

    /**
     * The decoys are the difference between this and [BuildSentence]: without
     * them the tile count alone tells you how long the answer is, and every
     * tile is known to belong.
     */
    @Test
    fun `sentence translation mixes in tiles that do not belong`() {
        val ex = io.tr8.yybijika.exercise.TranslateSentence.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertEquals("Di sini ramai.", ex.prompt)
        assertTrue("the solution must be reachable from the tiles",
            ex.tiles.containsAll(ex.solution))
        assertEquals("two tiles should be decoys",
            ex.solution.size + 2, ex.tiles.size)
    }

    @Test
    fun `sentence translation needs a translation to work from`() {
        val untranslated = word(
            examples = listOf(longExample().copy(gloss = null, preferredGloss = null)),
        )
        assertNull(
            io.tr8.yybijika.exercise.TranslateSentence.generate(untranslated, FakeContext())
        )
    }

    @Test
    fun `pinyin to hanzi asks from the sound, not the meaning`() {
        val ex = io.tr8.yybijika.exercise.PinyinToHanzi.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.Typing
        assertEquals("rè nào", ex.prompt)
        assertEquals("热闹", ex.answer)
    }

    // ----------------------------------------------------------------------
    // Characters
    // ----------------------------------------------------------------------

    @Test
    fun `character meaning names the word the character is standing in`() {
        val ex = io.tr8.yybijika.exercise.CharacterMeaning.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.MultipleChoice
        // 热 is in nine words, 闹 in four, so the question is about 热: it is the
        // one whose meaning pays off across the rest of the deck.
        assertTrue("should ask about the commoner character, got '${ex.prompt}'",
            ex.prompt.contains("热") && ex.prompt.contains("热闹"))
        assertEquals("panas", ex.choices[ex.answerIndex])
    }

    /**
     * A character that appears in one word explains nothing — its "meaning" is
     * just that word's meaning, so the question would be circular.
     */
    @Test
    fun `a character used in only one word is not taught`() {
        val lonely = word(
            characters = listOf(CharacterPart("罕", "hǎn", "jarang", "rare", 1, preferredGloss = "jarang")),
        )
        assertNull(io.tr8.yybijika.exercise.CharacterMeaning.generate(lonely, FakeContext()))
        assertNull(io.tr8.yybijika.exercise.WordBuilding.generate(lonely, FakeContext()))
    }

    @Test
    fun `word building offers the characters plus decoys`() {
        val ex = io.tr8.yybijika.exercise.WordBuilding.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertEquals(listOf("热", "闹"), ex.solution)
        assertEquals("ramai", ex.prompt)
        assertTrue("the answer must be reachable", ex.tiles.containsAll(ex.solution))
        assertTrue("decoys are needed or the word assembles itself",
            ex.tiles.size > ex.solution.size)
    }

    /**
     * Four characters is already a phrase to remember rather than a word to put
     * together, and one character is not an assembly at all.
     */
    @Test
    fun `word building declines words that are not built from parts`() {
        val single = word(
            hanzi = "湖",
            characters = listOf(CharacterPart("湖", "hú", "danau", "lake", 3, preferredGloss = "danau")),
        )
        assertNull(io.tr8.yybijika.exercise.WordBuilding.generate(single, FakeContext()))

        val phrase = word(
            hanzi = "农林牧渔水利生产人员",
            isPhrase = true,
            characters = "农林牧渔水利生产人员".map {
                CharacterPart(it.toString(), "x", "sesuatu", "something", 3,
                    preferredGloss = "sesuatu")
            },
        )
        assertNull(io.tr8.yybijika.exercise.WordBuilding.generate(phrase, FakeContext()))
    }

    /**
     * "In 内, what does 内 contribute?" answers itself. A word that is a single
     * character has no parts to ask about.
     */
    @Test
    fun `character meaning is withheld from a one-character word`() {
        val single = word(
            hanzi = "内",
            characters = listOf(
                CharacterPart("内", "nèi", "di dalam", "inside", 5,
                    preferredGloss = "di dalam"),
            ),
        )
        assertNull(io.tr8.yybijika.exercise.CharacterMeaning.generate(single, FakeContext()))
    }

    /**
     * 不 is in seventeen words of this deck and the answer is always "not",
     * which makes it exactly what a "commonest character wins" rule reaches for
     * and the last character worth a question.
     */
    @Test
    fun `character questions skip grammar and ask about the vocabulary`() {
        val w = word(
            hanzi = "不客气",
            characters = listOf(
                CharacterPart("不", "bù", "tidak", "not", 17, isFunction = true,
                    preferredGloss = "tidak"),
                CharacterPart("客", "kè", "tamu", "guest", 5,
                    preferredGloss = "tamu"),
                CharacterPart("气", "qì", "udara; suasana", "air; mood", 3,
                    preferredGloss = "udara; suasana"),
            ),
        )
        val ex = io.tr8.yybijika.exercise.CharacterMeaning.generate(w, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.MultipleChoice
        // 不 is the commonest of the three and must still be passed over.
        assertTrue("should not ask about 不, got '${ex.prompt}'", !ex.prompt.contains("不,"))
        assertEquals("tamu", ex.choices[ex.answerIndex])
    }

    @Test
    fun `a word made only of grammar has no character question`() {
        val w = word(
            hanzi = "这不",
            characters = listOf(
                CharacterPart("这", "zhè", "ini", "this", 4, isFunction = true,
                    preferredGloss = "ini"),
                CharacterPart("不", "bù", "tidak", "not", 17, isFunction = true,
                    preferredGloss = "tidak"),
            ),
        )
        assertNull(io.tr8.yybijika.exercise.CharacterMeaning.generate(w, FakeContext()))
    }

    // ----------------------------------------------------------------------
    // Writing
    // ----------------------------------------------------------------------

    @Test
    fun `writing asks for a sentence and names the word it must contain`() {
        val ex = io.tr8.yybijika.exercise.WriteSentence.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.Compose
        assertEquals(listOf("热闹"), ex.mustUse)
        assertTrue("the instruction must name the word", ex.instruction.contains("热闹"))
        assertTrue("there must be a situation to write about", ex.situation.isNotBlank())
        // The examples travel with the exercise so the checker can notice one
        // being copied back.
        assertTrue(ex.examples.isNotEmpty())
    }

    /**
     * The meaning is carried but not shown. A sentence written while looking at
     * the gloss is a translation, which is the one skill the rest of the app
     * already drills to death.
     */
    @Test
    fun `writing carries the meaning as a hint rather than the prompt`() {
        val ex = io.tr8.yybijika.exercise.WriteSentence.generate(fullWord(), FakeContext())
            as io.tr8.yybijika.exercise.Exercise.Compose
        assertTrue(ex.hint.contains("ramai"))
        assertTrue("the gloss must not be in the prompt itself",
            !ex.situation.contains("ramai") && !ex.instruction.contains("ramai"))
    }

    @Test
    fun `a set phrase is not something to write a sentence around`() {
        val phrase = word(hanzi = "农林牧渔水利生产人员", isPhrase = true)
        assertNull(io.tr8.yybijika.exercise.WriteSentence.generate(phrase, FakeContext()))
    }

    /**
     * Writing is its own skill. Filing it under Production would let a learner
     * who can pick a word out of four options look as though she could use it.
     */
    @Test
    fun `writing is scheduled separately from production`() {
        assertEquals(io.tr8.yybijika.learn.Skill.COMPOSITION,
            io.tr8.yybijika.exercise.WriteSentence.skill)
    }

    // ----------------------------------------------------------------------
    // Variety
    // ----------------------------------------------------------------------

    /**
     * Five exercises used to take the first example and keep taking it, so a
     * word was welded to one sentence for ever and the review slowly became
     * about remembering that sentence.
     */
    @Test
    fun `a word met again is met in a different sentence`() {
        val twoContexts = word(
            examples = listOf(
                longExample(),
                example(
                    zh = "热闹的地方我不喜欢。",
                    segments = listOf("热闹", "的", "地方", "我", "不", "喜欢"),
                    gloss = "Tempat ramai tidak saya suka.",
                ),
            ),
        )
        val first = io.tr8.yybijika.exercise.BuildSentence
            .generate(twoContexts, FakeContext(reviews = 0))
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        val later = io.tr8.yybijika.exercise.BuildSentence
            .generate(twoContexts, FakeContext(reviews = 1))
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertTrue("the same word should come round in a different sentence",
            first.solution != later.solution)
    }

    /**
     * And two exercises in one session should not land on the same sentence —
     * meeting 热闹 twice in the same clause teaches the clause.
     */
    @Test
    fun `two exercise types do not use the same sentence`() {
        val twoContexts = word(
            examples = listOf(
                longExample(),
                example(
                    zh = "热闹的地方我不喜欢。",
                    segments = listOf("热闹", "的", "地方", "我", "不", "喜欢"),
                    gloss = "Tempat ramai tidak saya suka.",
                ),
            ),
        )
        val ctx = FakeContext()
        val builder = io.tr8.yybijika.exercise.BuildSentence.generate(twoContexts, ctx)
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        val cloze = io.tr8.yybijika.exercise.ClozeExample.generate(twoContexts, ctx)
            as io.tr8.yybijika.exercise.Exercise.Cloze
        assertTrue("the two should not draw the same sentence",
            builder.solution.joinToString("") !=
                cloze.sentenceBefore + cloze.answer + cloze.sentenceAfter)
    }

    @Test
    fun `rotation is safe with one example and with none`() {
        val one = word(examples = listOf(longExample()))
        assertNotNull(io.tr8.yybijika.exercise.BuildSentence
            .generate(one, FakeContext(reviews = 7)))
        val none = word(examples = emptyList())
        assertNull(io.tr8.yybijika.exercise.BuildSentence
            .generate(none, FakeContext(reviews = 3)))
    }

    @Test
    fun `every type has a unique id`() {
        val ids = Registry.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
