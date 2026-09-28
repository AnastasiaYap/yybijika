package io.tr8.yybijika.exercise

/**
 * Reading tones off a pinyin string.
 *
 * Tone is the part of Mandarin an Indonesian or English speaker has no prior
 * hook for, and it is the part every other exercise lets you skip: you can pick
 * the right meaning, type the right characters and rebuild the right sentence
 * while hearing 买 and 卖 as the same word. So it gets a drill of its own, and
 * that drill needs the tone numbers, which the deck stores only as marks.
 */
object Tones {

    /** Vowel letters carrying each tone mark, indexed by tone number. */
    private val MARKS = mapOf(
        'ā' to 1, 'ē' to 1, 'ī' to 1, 'ō' to 1, 'ū' to 1, 'ǖ' to 1,
        'á' to 2, 'é' to 2, 'í' to 2, 'ó' to 2, 'ú' to 2, 'ǘ' to 2,
        'ǎ' to 3, 'ě' to 3, 'ǐ' to 3, 'ǒ' to 3, 'ǔ' to 3, 'ǚ' to 3,
        'à' to 4, 'è' to 4, 'ì' to 4, 'ò' to 4, 'ù' to 4, 'ǜ' to 4,
    )

    private val STRIP = mapOf(
        'ā' to 'a', 'á' to 'a', 'ǎ' to 'a', 'à' to 'a',
        'ē' to 'e', 'é' to 'e', 'ě' to 'e', 'è' to 'e',
        'ī' to 'i', 'í' to 'i', 'ǐ' to 'i', 'ì' to 'i',
        'ō' to 'o', 'ó' to 'o', 'ǒ' to 'o', 'ò' to 'o',
        'ū' to 'u', 'ú' to 'u', 'ǔ' to 'u', 'ù' to 'u',
        'ǖ' to 'ü', 'ǘ' to 'ü', 'ǚ' to 'ü', 'ǜ' to 'ü',
    )

    /** Tone number per syllable; 0 for a neutral syllable such as 子 in 裤子. */
    fun pattern(pinyin: String): List<Int> =
        pinyin.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { syllable ->
            syllable.firstNotNullOfOrNull { MARKS[it] } ?: 0
        }

    /** The same reading with its marks removed, so the prompt gives nothing away. */
    fun toneless(pinyin: String): String =
        pinyin.map { STRIP[it] ?: it }.joinToString("")

    /** How a pattern is written on a button: "2-1", or "3-neutral" as "3-0". */
    fun label(pattern: List<Int>): String = pattern.joinToString("–")

    /**
     * Wrong tone patterns of the same length.
     *
     * Built by changing one syllable at a time rather than by drawing random
     * patterns: the mistake being tested is hearing 3 where 2 was said, and a
     * distractor that differs everywhere is rejected without listening.
     */
    fun neighbours(pattern: List<Int>, count: Int): List<List<Int>> {
        val out = LinkedHashSet<List<Int>>()
        for (position in pattern.indices) {
            for (tone in 1..4) {
                if (tone == pattern[position]) continue
                out += pattern.toMutableList().also { it[position] = tone }
                if (out.size >= count) return out.toList()
            }
        }
        return out.toList()
    }
}
