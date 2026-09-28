package io.tr8.yybijika

import io.tr8.yybijika.notes.NoteParser
import io.tr8.yybijika.notes.Validate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser has to read the shorthand that is actually in the notes, so every
 * case here is a line lifted from them rather than an invented one.
 */
class NoteParserTest {

    private fun parse(text: String) = runBlocking { NoteParser.parse(text) }

    @Test
    fun `word with a plain gloss`() {
        val drafts = parse("努力berusaha keras")
        assertEquals(1, drafts.size)
        assertEquals("努力", drafts[0].hanzi)
        assertEquals("berusaha keras", drafts[0].glosses.first())
    }

    @Test
    fun `parenthetical is a usage note, not a meaning`() {
        val draft = parse("热闹rame (positif）meriah").single()
        assertEquals("热闹", draft.hanzi)
        assertTrue("positif should be a note", draft.usageNotes.contains("positif"))
        assertTrue("rame should survive", draft.glosses.any { it.contains("rame") })
        assertFalse("the note must not become a meaning",
            draft.glosses.any { it == "positif" })
    }

    @Test
    fun `equals links to another word rather than glossing`() {
        val draft = parse("旅行tour=旅游").single()
        assertEquals("旅行", draft.hanzi)
        assertTrue(draft.related.contains("旅游"))
        assertTrue(draft.glosses.any { it.contains("tour") })
    }

    @Test
    fun `colon introduces inline examples`() {
        val draft = parse("接menerima：接电话, 接到电话").single()
        assertEquals("接", draft.hanzi)
        assertEquals("menerima", draft.glosses.first())
        assertEquals(listOf("接电话", "接到电话"), draft.examples)
    }

    @Test
    fun `angle brackets mark an antonym`() {
        val draft = parse("全职><兼职").single()
        assertEquals("全职", draft.hanzi)
        assertTrue(draft.related.contains("兼职"))
    }

    @Test
    fun `a bare word reports everything as missing`() {
        val draft = parse("努力").single()
        assertEquals(listOf("pinyin", "meaning", "example"), draft.missing)
    }

    @Test
    fun `a sentence attaches to the word above it`() {
        val drafts = parse("热闹rame\n这里很热闹。")
        assertEquals("a sentence must not become its own card", 1, drafts.size)
        assertEquals(listOf("这里很热闹。"), drafts[0].examples)
    }

    @Test
    fun `a blank line ends the group`() {
        val drafts = parse("热闹rame\n\n这里很热闹。")
        // With nothing above it the sentence has no owner, so it is read as a
        // headword rather than silently attached to an unrelated word.
        assertEquals(2, drafts.size)
    }

    @Test
    fun `hand written pinyin is kept`() {
        val draft = parse("热闹 rè nào ramai").single()
        assertEquals("rè nào", draft.pinyin)
        assertFalse("pinyin should not linger in the meaning",
            draft.glosses.any { it.contains("rè") })
    }

    @Test
    fun `several lines parse independently`() {
        val drafts = parse(
            """
            散步Jalan Jalan
            湖lake
            早起bangun pagi
            """.trimIndent()
        )
        assertEquals(3, drafts.size)
        assertEquals(listOf("散步", "湖", "早起"), drafts.map { it.hanzi })
    }

    @Test
    fun `latin only lines never become cards`() {
        val drafts = parse("grammar\n热闹rame")
        assertEquals(1, drafts.size)
        assertEquals("热闹", drafts[0].hanzi)
    }
}

class ValidateTest {

    @Test
    fun `pinyin must have one syllable per character`() {
        assertTrue(Validate.pinyinMatches("热闹", "rè nào"))
        assertFalse(Validate.pinyinMatches("热闹", "rè"))
        assertFalse(Validate.pinyinMatches("热闹", "rè nào le"))
    }

    @Test
    fun `toneless pinyin for a multi-character word is rejected`() {
        assertFalse(Validate.pinyinMatches("热闹", "re nao"))
        // A single character with no tone is a plausible neutral tone, so it
        // passes; the check exists to catch a model dropping tones wholesale.
        assertTrue(Validate.pinyinMatches("的", "de"))
    }

    @Test
    fun `an example has to contain its word`() {
        assertTrue(Validate.exampleUses("热闹", "这里很热闹。"))
        assertFalse(Validate.exampleUses("热闹", "这里很安静。"))
    }

    @Test
    fun `an example that is just the word teaches nothing`() {
        assertFalse(Validate.exampleUses("热闹", "热闹"))
    }

    @Test
    fun `an example padded with latin text is rejected`() {
        assertFalse(Validate.exampleUses("热闹", "This place is 热闹 today"))
    }

    @Test
    fun `an overlong example is rejected`() {
        assertFalse(Validate.exampleUses("热闹", "热闹".repeat(20)))
    }
}
