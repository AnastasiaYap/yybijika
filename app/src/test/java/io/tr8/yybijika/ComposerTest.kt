package io.tr8.yybijika

import io.tr8.yybijika.learn.Composer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of the writing feature that works with no network.
 *
 * It cannot say whether a sentence is idiomatic — nothing on the device can —
 * so what matters is that it is right about the things it does claim, and
 * silent about everything else. A checker that fires on correct sentences
 * teaches the writer to ignore it, and after that the real corrections go
 * unread too.
 */
class ComposerTest {

    private fun check(sentence: String, mustUse: List<String> = listOf("合同"),
                      examples: List<String> = emptyList()) =
        Composer.check(sentence, mustUse, examples)

    @Test
    fun `a sentence without the target word does not count`() {
        val c = check("我今天很忙。")
        assertFalse(c.usable)
        assertTrue(c.findings.any { it.fatal && it.message.contains("合同") })
    }

    @Test
    fun `a few characters are not a sentence`() {
        val c = check("合同。")
        assertFalse(c.usable)
    }

    /**
     * The failure this exists for: the example is on screen a moment earlier,
     * and copying it back produces a perfect answer that exercises nothing.
     */
    @Test
    fun `the example copied back is rejected`() {
        val example = "我们昨天签了合同。"
        val c = check(example, examples = listOf(example))
        assertFalse(c.usable)
        assertTrue(c.findings.any { it.message.contains("copied") })

        // The same words genuinely rearranged is a different sentence.
        val own = check("昨天我跟客户签了那个合同。", examples = listOf(example))
        assertTrue(own.usable)
    }

    @Test
    fun `a reasonable sentence passes cleanly`() {
        val c = check("我明天要跟他们签合同。")
        assertTrue(c.findings.joinToString(), c.clean)
    }

    @Test
    fun `missing final punctuation is mentioned but not fatal`() {
        val c = check("我明天要跟他们签合同")
        assertTrue(c.usable)
        assertTrue(c.findings.any { !it.fatal })
    }

    // ----------------------------------------------------------------------
    // The traps
    // ----------------------------------------------------------------------

    @Test
    fun `two before a measure word is caught`() {
        val c = check("我签了二个合同。")
        assertTrue(c.findings.any { it.message.contains("两") })
        // …and 二 used as a plain number is left alone.
        assertFalse(check("第二个合同很长。").findings.any { it.message.contains("两") })
    }

    @Test
    fun `a duration after gang is caught`() {
        assertTrue(check("我刚三天签了合同。").findings.any { it.message.contains("刚") })
        // 刚 as "just now" is correct and must not fire.
        assertFalse(check("我刚签了合同。").findings.any { it.message.contains("刚") })
    }

    @Test
    fun `mei with le is caught`() {
        assertTrue(check("我没签合同了。").findings.any { it.message.contains("没") })
        assertFalse(check("我没签合同。").findings.any { it.message.contains("没") })
        // 了 elsewhere in a longer sentence with 没 in a different clause is
        // common and correct, so the rule stays inside one clause.
        assertFalse(
            check("我没去，他签合同了。").findings.any { it.message.contains("没") }
        )
    }

    @Test
    fun `characters outside the deck are reported but not fatal`() {
        val c = Composer.check(
            "我签了合同鑫。", listOf("合同"), emptyList(), known = "我签了合同。".toSet(),
        )
        assertTrue(c.usable)
        assertTrue(c.findings.any { it.message.contains("鑫") })
    }

    @Test
    fun `no vocabulary supplied means no strange-character warning`() {
        val c = check("我签了合同鑫。")
        assertFalse(c.findings.any { it.message.contains("鑫") })
    }
}
