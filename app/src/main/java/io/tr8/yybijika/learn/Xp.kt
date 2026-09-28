package io.tr8.yybijika.learn

import kotlin.math.sqrt

/**
 * Points, combo and levels.
 *
 * XP is strictly decorative. It reads the same answers the scheduler reads but
 * never feeds anything back into it — nothing here may influence when a card is
 * next due. That separation is deliberate: the moment points affect scheduling,
 * the app starts rewarding easy reviews, and the deck quietly rots while the
 * number goes up.
 */
object Xp {

    private const val BASE = 10

    /** Harder skills are worth more, because they are harder. */
    private fun skillWeight(skill: Skill): Int = when (skill) {
        Skill.RECOGNITION -> 1
        Skill.LISTENING -> 2
        Skill.PRODUCTION -> 2
        Skill.USAGE -> 3
    }

    /**
     * Points for one answer.
     *
     * A wrong answer scores nothing but is not punished — losing points for
     * getting something wrong discourages exactly the reviews that teach most.
     * The combo multiplier is capped so a long streak cannot dwarf the rest of
     * a session's work.
     */
    fun award(grade: Grade, skill: Skill, combo: Int): Int {
        if (grade == Grade.AGAIN) return 0
        val gradeFactor = if (grade == Grade.HARD) 0.5 else 1.0
        val multiplier = 1.0 + (combo.coerceAtMost(10) * 0.1)
        return (BASE * skillWeight(skill) * gradeFactor * multiplier).toInt()
    }

    /** A combo counts consecutive non-lapse answers within one session. */
    fun nextCombo(current: Int, grade: Grade): Int =
        if (grade == Grade.AGAIN) 0 else current + 1

    /**
     * Levels come at a widening interval so early ones arrive quickly and later
     * ones stay meaningful: level n needs 100 * n^2 points in total.
     */
    fun levelFor(totalXp: Int): Int =
        if (totalXp < 100) 1 else (sqrt(totalXp / 100.0).toInt() + 1)

    fun xpForLevel(level: Int): Int = if (level <= 1) 0 else 100 * (level - 1) * (level - 1)

    /** Progress through the current level, 0..1, for the header bar. */
    fun levelProgress(totalXp: Int): Float {
        val level = levelFor(totalXp)
        val floor = xpForLevel(level)
        val ceiling = xpForLevel(level + 1)
        if (ceiling <= floor) return 1f
        return ((totalXp - floor).toFloat() / (ceiling - floor)).coerceIn(0f, 1f)
    }
}
