package io.tr8.yybijika.learn

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Spaced repetition, one schedule per (word, skill).
 *
 * The interval ladder and the three-grade review are carried over from yyhsk,
 * where they worked well over a long run. What is new is that the ladder is
 * climbed per skill rather than per word, so a word you can read but not hear
 * keeps showing up as a listening drill while its recognition schedule drifts
 * out to a year.
 */
object Scheduler {

    /** Days until the next review at each box. Box 0 means "seen, not learned". */
    val INTERVALS = intArrayOf(0, 1, 3, 7, 14, 30, 60, 120, 365)

    val MAX_BOX = INTERVALS.size - 1

    /**
     * Reviews cluster if every card scheduled on the same day keeps coming back
     * on the same day forever. A tenth of the interval is enough jitter to break
     * the clumps up without meaningfully changing the schedule.
     */
    const val FUZZ = 0.10

    data class State(
        val box: Int = 0,
        val streak: Int = 0,
        val lapses: Int = 0,
        val lastGrade: Grade? = null,
    )

    /** The box a card moves to, before any fuzz is applied. */
    fun nextBox(state: State, grade: Grade): Int = when (grade) {
        // A lapse drops to half the current box rather than to zero. Restarting
        // a word you have known for months from scratch is punishing and wastes
        // the reviews that got it there.
        Grade.AGAIN -> max(0, state.box / 2)
        Grade.HARD -> state.box
        Grade.GOOD -> min(MAX_BOX, state.box + 1)
    }

    /** Days until this card is due again. */
    fun intervalDays(state: State, grade: Grade, random: Random = Random.Default): Int {
        val box = nextBox(state, grade)
        val base = INTERVALS[box]
        // Hard keeps the box but shortens the wait, so "I got it, barely" is
        // distinct from both "again" and "good".
        val scaled = if (grade == Grade.HARD) base * 0.6 else base.toDouble()
        if (scaled < 1.0) return 0
        val jitter = 1.0 + (random.nextDouble() * 2 - 1) * FUZZ
        return max(1, Math.round(scaled * jitter).toInt())
    }

    /** Apply a grade, returning the new state and how many days until it is due. */
    fun review(
        state: State,
        grade: Grade,
        random: Random = Random.Default,
    ): Pair<State, Int> {
        val next = State(
            box = nextBox(state, grade),
            streak = if (grade == Grade.GOOD) state.streak + 1 else 0,
            lapses = if (grade == Grade.AGAIN) state.lapses + 1 else state.lapses,
            lastGrade = grade,
        )
        return next to intervalDays(state, grade, random)
    }

    /**
     * The interval to show on each button before it is pressed, so the choice is
     * informed. Deliberately unfuzzed: a button that says "7d" and schedules
     * 7 days is more trustworthy than one that says 7 and means 6.4.
     */
    fun previewDays(state: State, grade: Grade): Int {
        val box = nextBox(state, grade)
        val base = INTERVALS[box]
        return if (grade == Grade.HARD) max(1, (base * 0.6).toInt()) else base
    }

    /**
     * Mastery as a 0..1 fraction, for the per-skill bars on a word.
     * Box 8 (a year) is treated as learned rather than as "still climbing".
     */
    fun mastery(box: Int): Float = (box.toFloat() / MAX_BOX).coerceIn(0f, 1f)
}
