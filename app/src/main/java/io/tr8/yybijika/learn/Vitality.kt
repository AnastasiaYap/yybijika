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
        /** Segments of stalk, and so how tall it stands: see [JOINT_LADDER]. */
        val joints: Int,
        /** Leaves: a few the stalk has earned, plus today's work. */
        val leaves: Int,
        /** Words held at [HELD_BOX] or better, across any skill. */
        val held: Int,
        /** Reviews waiting now. */
        val due: Int,
        val daysAway: Int,
        /** Questions answered today — the half of the plant that moves now. */
        val reviewsToday: Int = 0,
        /** Held words that would earn the next joint; null once fully grown. */
        val nextJointAt: Int? = null,
    ) {
        /** Studied today: the plant is at its best. */
        val fresh: Boolean get() = daysAway == 0

        /** Long enough away that the plant visibly wants attention. */
        val thirsty: Boolean get() = daysAway >= 2
    }

    /**
     * Held words that buy each joint after the first.
     *
     * Steeply uneven on purpose. A flat price — one joint per twenty-five words
     * held — meant the first visible growth was several weeks of study away,
     * which is the whole period the plant exists to get somebody through: it
     * showed a finished-looking stalk on day one and then did not move again.
     * Cheap early joints make the first fortnight the most visibly productive
     * time in the plant's life, and the ladder stretches out afterwards so a
     * big deck still has somewhere to grow.
     */
    val JOINT_LADDER = listOf(2, 5, 10, 18, 30, 50, 75, 110, 160, 230, 320)

    /** The tallest the stalk is drawn, so a big deck does not grow off-screen. */
    val MAX_JOINTS = JOINT_LADDER.size + 1

    /**
     * Reviews today that earn each extra leaf.
     *
     * The stalk is a slow, permanent record and cannot answer the question
     * "did what I just did matter?" — a word takes three separate days to be
     * genuinely held. Leaves answer it: the first one arrives on the first
     * question of the day, which means the plant visibly changes inside a
     * single session rather than inside a month.
     */
    val LEAF_LADDER = listOf(1, 6, 15)

    fun joints(held: Int): Int = 1 + JOINT_LADDER.count { held >= it }

    fun nextJointAt(held: Int): Int? = JOINT_LADDER.firstOrNull { held < it }

    /**
     * Leaves the stalk carries on its own, before today.
     *
     * Kept low and tied to height so a plant that has been growing for months
     * never looks bare first thing in the morning, while still leaving the
     * day's own leaves clearly visible on top of it.
     */
    fun baseLeaves(joints: Int): Int = min(3, (joints - 1) / 3)

    fun todayLeaves(reviewsToday: Int): Int = LEAF_LADDER.count { reviewsToday >= it }

    /**
     * @param held words at [HELD_BOX] or better in at least one skill.
     * @param daysAway whole days since the last session; 0 if studied today.
     * @param reviewsToday questions answered today.
     */
    fun of(held: Int, due: Int, daysAway: Int, reviewsToday: Int = 0): State {
        val joints = joints(held)
        return State(
            vigour = vigour(daysAway),
            joints = joints,
            leaves = baseLeaves(joints) + todayLeaves(reviewsToday),
            held = held,
            due = due,
            daysAway = daysAway.coerceAtLeast(0),
            reviewsToday = reviewsToday.coerceAtLeast(0),
            nextJointAt = nextJointAt(held),
        )
    }

    /**
     * The line under the plant: what it is doing and what would move it.
     *
     * Worth stating rather than leaving to the picture. The stalk grows on a
     * ladder, and a learner who cannot see the next rung has no way to tell a
     * plant that is waiting for work from one that has stopped responding —
     * which is exactly how a flat, invisible growth rule reads from outside.
     */
    fun caption(state: State): String {
        // Nothing done and nothing held yet: an invitation reads better than a
        // figure, and "grows again at 2 held" to somebody who has never
        // studied is an instruction without a reason.
        if (state.reviewsToday == 0 && state.held == 0) {
            return "Your bamboo grows as words stick."
        }
        val parts = buildList {
            if (state.reviewsToday > 0) add("${state.reviewsToday} answered today")
            if (state.held > 0) {
                add(if (state.held == 1) "1 word held" else "${state.held} words held")
            }
            state.nextJointAt?.let { add("grows again at $it held") }
        }
        if (parts.isEmpty()) return "Your bamboo grows as words stick."
        return parts.joinToString(" · ") +
            if (state.thirsty) " · it could use a session" else ""
    }

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
