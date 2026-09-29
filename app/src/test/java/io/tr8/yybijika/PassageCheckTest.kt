package io.tr8.yybijika

import io.tr8.yybijika.learn.PassageCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A passage written on demand has not been read by anyone before it arrives.
 *
 * These are the checks that stand between a model's reply and the reading
 * screen. They matter more than the usual test because the input is not a fixed
 * file in the repository — it is different every time, and it is the one place
 * in the app where content appears that nobody wrote.
 */
class PassageCheckTest {

    private fun line(zh: String, pieces: List<String>) =
        PassageCheck.Line(zh, "pinyin", "gloss", pieces)

    private val known = setOf("合同", "签", "公司", "经理")

    @Test
    fun `a passage using its target words is kept`() {
        val checked = PassageCheck.check(
            lines = listOf(
                line("我们签了合同。", listOf("我们", "签", "了", "合同", "。")),
                line("经理很高兴。", listOf("经理", "很", "高兴", "。")),
            ),
            targets = listOf("合同", "经理"),
            known = known,
        )
        assertEquals(2, checked.lines.size)
    }

    @Test
    fun `a passage that left out a target word is rejected`() {
        val e = assertThrows(PassageCheck.Rejected::class.java) {
            PassageCheck.check(
                lines = listOf(line("我们签了合同。", listOf("我们", "签", "了", "合同", "。"))),
                targets = listOf("合同", "索赔"),
                known = known,
            )
        }
        assertTrue(e.message!!.contains("索赔"))
    }

    @Test
    fun `an empty passage is rejected`() {
        assertThrows(PassageCheck.Rejected::class.java) {
            PassageCheck.check(emptyList(), listOf("合同"), known)
        }
    }

    /**
     * The failure this guards is quiet and nasty: a segmentation that does not
     * rebuild its line still renders, but every tap after the bad split opens
     * the wrong entry. Better to lose the tapping on that line than to have it
     * lie.
     */
    @Test
    fun `a segmentation that does not rebuild the line falls back to the whole line`() {
        val checked = PassageCheck.check(
            lines = listOf(line("我们签了合同。", listOf("我们", "签", "合同"))),
            targets = listOf("合同"),
            known = known,
        )
        assertEquals(listOf("我们签了合同。"), checked.lines[0].pieces)
    }

    @Test
    fun `a correct segmentation is kept as it is`() {
        val pieces = listOf("我们", "签", "了", "合同", "。")
        val checked = PassageCheck.check(
            lines = listOf(line("我们签了合同。", pieces)),
            targets = listOf("合同"),
            known = known,
        )
        assertEquals(pieces, checked.lines[0].pieces)
    }

    @Test
    fun `words outside the deck and the baseline are counted`() {
        val checked = PassageCheck.check(
            lines = listOf(
                line("公司索赔了。", listOf("公司", "索赔", "了", "。")),
                line("经理辞职了。", listOf("经理", "辞职", "了", "。")),
            ),
            targets = listOf("公司"),
            known = known,
        )
        // 索赔 and 辞职 are in neither set; 了 and 。 are too short to count.
        assertEquals(2, checked.unknownWords)
    }

    /**
     * A song is not written to practise anything, so it has no target words to
     * have left out — the check must not reject it for that.
     */
    @Test
    fun `text with no target words is accepted`() {
        val checked = PassageCheck.check(
            lines = listOf(line("经理签了合同。", listOf("经理", "签", "了", "合同", "。"))),
            targets = emptyList(),
            known = known,
        )
        assertEquals(1, checked.lines.size)
    }

    /**
     * Song lines are short and repeat. A repeated line is not an error and must
     * come through as its own entry, or the reading stops lining up with the
     * words on the page.
     */
    @Test
    fun `repeated lines are each kept`() {
        val one = line("我们签了合同。", listOf("我们", "签", "了", "合同", "。"))
        val checked = PassageCheck.check(listOf(one, one, one), emptyList(), known)
        assertEquals(3, checked.lines.size)
    }

    @Test
    fun `a passage entirely within the vocabulary counts nothing unknown`() {
        val checked = PassageCheck.check(
            lines = listOf(line("经理签了合同。", listOf("经理", "签", "了", "合同", "。"))),
            targets = listOf("合同"),
            known = known,
        )
        assertEquals(0, checked.unknownWords)
    }
}
