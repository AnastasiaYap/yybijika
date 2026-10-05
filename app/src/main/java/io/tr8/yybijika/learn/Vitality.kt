package io.tr8.yybijika.learn

import kotlin.math.min
import kotlin.math.pow

/**
 * How the plant on the home screen is doing.
 *
 * The point of it is retention, and retention has a known failure mode: a pet
 * that can die turns a fortnight away into a dead pet, and the thing meant to
 * pull somebody back becomes the reason they uninstall. So nothing here can
 * reach zero, and recovery is deliberately faster than decay — coming back is
 * always rewarded and never punished.
 *
 * The other decision is what feeds it. A plant watered by *opening the app* is
 * a loyalty token and can be satisfied without learning anything. This one
 * grows from the deck's own mastery ladder, so the picture on the home screen
 * is a true statement about the deck rather than a sticker for turning up.
 */
object Vitality {

    /** Words at this box or beyond count as genuinely held, not merely met. */
    const val HELD_BOX = 3

    /**
     * Days of absence before the plant is as faded as it will ever get.
     *
     * Long enough that a busy week does not undo a month, short enough that the
     * change is visible within a couple of days — an indicator that only moves
     * after a fortnight is not an indicator, it is decoration.
     */
    const val FADE_DAYS = 6

    /**
     * How faded it can get. Chosen so the plant always reads as alive and
     * waiting rather than as a reproach.
     */
    const val FLOOR = 0.3

    data class State(
        /** 0..1. Freshness, from how long since the last session. */
        val vigour: Double,
        /** Segments of stalk: one per [WORDS_PER_JOINT] words genuinely held. */
        val joints: Int,
        /** Words held at [HELD_BOX] or better, across any skill. */
        val held: Int,
        /** Reviews waiting now. */
        val due: Int,
        val daysAway: Int,
    ) {
        /** Studied today: the plant is at its best. */
        val fresh: Boolean get() = daysAway == 0

        /** Long enough away that the plant visibly wants attention. */
        val thirsty: Boolean get() = daysAway >= 2
    }

    /** How many held words each new joint of bamboo costs. */
    const val WORDS_PER_JOINT = 25

    /** The tallest the stalk is drawn, so a big deck does not grow off-screen. */
    const val MAX_JOINTS = 12

    /**
     * @param held words at [HELD_BOX] or better in at least one skill.
     * @param daysAway whole days since the last session; 0 if studied today.
     */
    fun of(held: Int, due: Int, daysAway: Int): State = State(
        vigour = vigour(daysAway),
        joints = min(MAX_JOINTS, 1 + held / WORDS_PER_JOINT),
        held = held,
        due = due,
        daysAway = daysAway.coerceAtLeast(0),
    )

    /**
     * Freshness from absence.
     *
     * Eased rather than linear so the first day away barely shows — missing a
     * single day is normal life, not a lapse worth marking — and the slide
     * becomes obvious around the third or fourth, which is where a habit
     * actually starts to break.
     */
    fun vigour(daysAway: Int): Double {
        if (daysAway <= 0) return 1.0
        if (daysAway >= FADE_DAYS) return FLOOR
        // 1.3 rather than anything rounder: it is the shape that keeps the
        // first day away above 0.93 — missing one day is ordinary life — while
        // bringing the third clearly below 0.75, which is where a habit really
        // starts to come apart. The curve is the design decision; the exponent
        // is just what satisfies it.
        val t = daysAway.toDouble() / FADE_DAYS
        return 1.0 - (1.0 - FLOOR) * t.pow(1.3)
    }
}
