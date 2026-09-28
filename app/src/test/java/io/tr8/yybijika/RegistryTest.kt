package io.tr8.yybijika

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
) : DeckContext {
    override fun distractorGlosses(word: WordBundle, count: Int) =
        List(minOf(distractors, count)) { "gloss$it" }

    override fun distractorHanzi(word: WordBundle, count: Int) =
        List(minOf(distractors, count)) { "词$it" }

    override fun shuffleSeed(word: WordBundle, typeId: String) = 1L

    override val measureWords: List<String> = listOf("条", "张", "本", "件", "位")
}

private fun word(
    hanzi: String = "热闹",
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
    fun `odd one out marks the word that lacks the shared character`() {
        val w = word(relations = family())
        val ex = io.tr8.yybijika.exercise.OddOneOut.generate(w, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.MultipleChoice
        assertEquals(4, ex.choices.size)
        val odd = ex.choices[ex.answerIndex]
        assertTrue("the answer must be the one without 热, got $odd", !odd.contains("热"))
        assertTrue("the other three must all contain 热",
            ex.choices.filter { it != odd }.all { it.contains("热") })
    }

    @Test
    fun `odd one out needs a family of three before it will ask`() {
        val onlyOneRelative = word(
            relations = listOf(Relation("shares", "热情", "rè qíng", "hangat", "热")),
        )
        assertNull(io.tr8.yybijika.exercise.OddOneOut.generate(onlyOneRelative, FakeContext()))
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
            examples = listOf(longExample().copy(gloss = null)),
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

    @Test
    fun `every type has a unique id`() {
        val ids = Registry.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
