package io.tr8.yybijika.learn

/**
 * What can be said about a written sentence without asking anyone.
 *
 * This is the half of the feature that works on a plane. It cannot tell you
 * whether your sentence is idiomatic — nothing on the device can — but it can
 * catch the failures that make the exercise pointless, and those turn out to be
 * most of them: the target word missing, four characters passed off as a
 * sentence, or the example sentence copied back verbatim.
 *
 * Everything here is a claim that can be checked mechanically. Anything
 * requiring judgement is left for [io.tr8.yybijika.notes.DeepSeek], and when
 * that is unavailable the sentence is simply kept for the writer to judge
 * herself later — which is still worth more than not writing it.
 */
object Composer {

    /** What the checks found. [fatal] means the sentence does not count yet. */
    data class Finding(val message: String, val fatal: Boolean)

    data class Critique(
        val findings: List<Finding>,
        val corrected: String? = null,
        val note: String? = null,
        val source: String = "offline",
    ) {
        val usable: Boolean get() = findings.none { it.fatal }
        val clean: Boolean get() = findings.isEmpty()
    }

    private val SENTENCE_END = setOf('。', '！', '？', '.', '!', '?')

    private fun hanCount(text: String) = text.count { it.code in 0x4E00..0x9FFF }

    /**
     * Check a sentence against the words it had to use.
     *
     * [examples] are the word's own example sentences, used only to notice that
     * one of them has been copied back: producing the sentence you were just
     * shown exercises your eyes, not your Chinese.
     */
    fun check(
        sentence: String,
        mustUse: List<String>,
        examples: List<String> = emptyList(),
        known: Set<Char> = emptySet(),
    ): Critique {
        val text = sentence.trim()
        val findings = mutableListOf<Finding>()

        if (hanCount(text) < 5) {
            findings += Finding(
                "That is too short to be a sentence — aim for at least five characters.",
                fatal = true,
            )
        }

        mustUse.filterNot { text.contains(it) }.forEach {
            findings += Finding("Your sentence does not use 「$it」.", fatal = true)
        }

        val bare = text.filter { it.code in 0x4E00..0x9FFF }
        if (examples.any { ex -> ex.filter { it.code in 0x4E00..0x9FFF } == bare }) {
            findings += Finding(
                "That is the example sentence copied back. Write one of your own — " +
                    "the point is to say something you meant.",
                fatal = true,
            )
        }

        if (text.isNotEmpty() && text.last() !in SENTENCE_END) {
            findings += Finding("Finish it with 。— a habit worth having.", fatal = false)
        }

        // Characters outside anything the deck or HSK 1-3 contains are usually a
        // typo or a stray paste, and flagging them costs nothing. Only reported
        // when the caller supplied a vocabulary to check against.
        if (known.isNotEmpty()) {
            val strange = bare.filterNot { it in known }.toSortedSet()
            if (strange.isNotEmpty()) {
                findings += Finding(
                    "These are not in your deck: ${strange.joinToString("")}. " +
                        "Check them, or add them to your notes.",
                    fatal = false,
                )
            }
        }

        findings += traps(text)
        return Critique(findings)
    }

    /**
     * Mistakes common enough, and regular enough, to catch by pattern.
     *
     * Kept short on purpose. A rule that fires on correct sentences is worse
     * than no rule: it teaches the writer to distrust the feedback, and after
     * that the real corrections get ignored too.
     */
    private fun traps(text: String): List<Finding> {
        val out = mutableListOf<Finding>()
        val measures = "个件条张只本杯瓶位份台辆间座顿次场"

        // 二 counts; 两 quantifies. 二个人 is the classic first-year slip —
        // but 第二个 is an ordinal and 十二个 is a number ending in two, and
        // both are correct. The rule therefore fires only where 二 is standing
        // alone as the quantity.
        val ordinalOrDigit = "第一二三四五六七八九十百千万"
        text.forEachIndexed { i, ch ->
            if (ch == '二' && i + 1 < text.length && text[i + 1] in measures &&
                (i == 0 || text[i - 1] !in ordinalOrDigit)
            ) {
                out += Finding(
                    "Before a measure word it is 两, not 二 — 两个, not 二个.", false,
                )
            }
        }

        // 刚 is "just now", a point in time; it cannot take a duration.
        Regex("刚[一二两三四五六七八九十几].{0,2}?[天年月周]").find(text)?.let {
            out += Finding(
                "刚 marks a moment, so it cannot take a length of time. " +
                    "For a duration use 刚才 or rewrite with 了.",
                false,
            )
        }

        // 没 already negates completion, so 了 does not follow it.
        Regex("没[有]?[^，。！？]{0,6}了").find(text)?.let {
            out += Finding("没 and 了 do not go together — 没去, not 没去了.", false)
        }

        if (text.contains("很是")) {
            out += Finding("很 modifies adjectives, not 是. Drop one of them.", false)
        }
        return out
    }
}
