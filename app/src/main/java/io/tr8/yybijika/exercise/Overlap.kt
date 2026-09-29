package io.tr8.yybijika.exercise

/**
 * Whether two glosses say enough of the same thing to be the same answer.
 *
 * 逛街 is "cuci mata, lihat-lihat di pertokoan" and 旅行 is "bepergian,
 * jalan-jalan". Offered together as two of four choices they are a trick rather
 * than a question: both are plausible, one is marked wrong, and the learner
 * takes away that they were wrong about a word they had right.
 *
 * A distractor has to be distinguishable, not merely different.
 */
object Overlap {

    /**
     * Words too ordinary to make two glosses mean the same thing.
     *
     * Both languages in one set: a gloss list mixes them, and "to" colliding
     * with an Indonesian word is not a risk worth a second set for.
     */
    private val IGNORED = setOf(
        // Indonesian
        "di", "ke", "dari", "yang", "dan", "atau", "untuk", "pada", "dengan",
        "itu", "ini", "ada", "adalah", "tidak", "bukan", "juga", "saja", "akan",
        "sudah", "belum", "lagi", "oleh", "para", "se", "suatu", "dalam", "atas",
        // English
        "a", "an", "the", "to", "of", "in", "on", "for", "be", "is", "are",
        "at", "by", "with", "from", "or", "and", "as", "it", "its", "that",
        "this", "not", "do", "does", "someone", "something",
    )

    /** Content words of a gloss, lowercased, with punctuation dropped. */
    fun tokens(gloss: String): Set<String> =
        gloss.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length > 2 && it !in IGNORED }
            .toSet()

    /**
     * True when the two glosses share a content word.
     *
     * One shared word is the threshold rather than a proportion: "jalan-jalan"
     * appearing in both is already enough to make either answer defensible, and
     * a ratio would let a long gloss hide the collision.
     */
    fun collide(a: String, b: String): Boolean {
        val left = tokens(a)
        if (left.isEmpty()) return false
        return tokens(b).any { it in left }
    }
}
