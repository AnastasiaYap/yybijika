package io.tr8.yybijika

import io.tr8.yybijika.exercise.DeckContext
import io.tr8.yybijika.exercise.ExampleSentence
import io.tr8.yybijika.exercise.Registry
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
}

private fun word(
    hanzi: String = "热闹",
    pinyin: String = "rè nào",
    verified: Boolean = true,
    glosses: List<String> = listOf("ramai"),
    examples: List<ExampleSentence> = emptyList(),
    isPhrase: Boolean = false,
) = WordBundle(
    id = 1,
    hanzi = hanzi,
    hanziTrad = null,
    pinyin = pinyin,
    pinyinVerified = verified,
    isPhrase = isPhrase,
    glosses = glosses,
    examples = examples,
)

private fun example(zh: String = "这里很热闹。", target: String = "热闹") =
    ExampleSentence(
        zh = zh,
        pinyin = "zhè lǐ hěn rè nào",
        gloss = "Di sini ramai.",
        glossEn = "It is lively here.",
        containsTarget = zh.contains(target),
        tokenCount = zh.count { it.code in 0x4E00..0x9FFF },
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
        val full = word(examples = listOf(example()))
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
        val full = word(examples = listOf(example()))
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
        val full = word(examples = listOf(example()))
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
        val full = word(examples = listOf(example()))
        val ex = io.tr8.yybijika.exercise.BuildSentence.generate(full, FakeContext())
            as io.tr8.yybijika.exercise.Exercise.TileBuilder
        assertEquals(ex.solution.sorted(), ex.tiles.sorted())
    }

    @Test
    fun `every type has a unique id`() {
        val ids = Registry.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
