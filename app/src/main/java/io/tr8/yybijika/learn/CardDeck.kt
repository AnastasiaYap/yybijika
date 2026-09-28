package io.tr8.yybijika.learn

import io.tr8.yybijika.data.CardState
import io.tr8.yybijika.data.CharacterState
import kotlin.math.max
import kotlin.math.min

/**
 * What each swipe does to a card.
 *
 * Deliberately simpler than the four-skill scheduler in [Scheduler]. A swipe is
 * a snap judgement made while leafing through the deck, not a graded answer, so
 * it moves the card one step rather than computing a fitted interval. The point
 * is that a hundred cards can be sorted in a couple of minutes.
 */
object CardDeck {

    /** Days until a card comes back, by box. Shorter than the review ladder:
     *  this is for grooming the deck, not for long-term retention. */
    val INTERVALS = intArrayOf(0, 1, 2, 4, 8, 16, 30, 60)

    val MAX_BOX = INTERVALS.size - 1

    enum class Swipe { UNKNOWN, KNOWN, RETIRE }

    /** Where a swipe leaves a card: its new state, box and due day. */
    data class Step(val state: String, val box: Int, val dueAt: Long)

    /**
     * The ladder itself, written once.
     *
     * Words and characters are groomed identically and stored separately, so
     * this takes the three fields that matter and returns the three that change,
     * rather than being written twice against two row types that would then be
     * free to drift apart.
     */
    fun step(state: String, box: Int, swipe: Swipe, today: Long): Step = when (swipe) {
        // Unknown drops to the bottom and comes back the same day — the whole
        // value of the gesture is that a word you just failed reappears while
        // the failure is still fresh.
        Swipe.UNKNOWN -> Step(CardState.STATE_STRUGGLING, 0, today)

        Swipe.KNOWN -> {
            val next = min(MAX_BOX, box + 1)
            Step(
                // "Known" is a claim the deck only accepts after it has been
                // made a few times; one swipe moves the card along but does not
                // yet promote it out of learning.
                if (next >= 3) CardState.STATE_KNOWN else CardState.STATE_LEARNING,
                next,
                today + INTERVALS[next],
            )
        }

        Swipe.RETIRE -> Step(CardState.STATE_RETIRED, box, 0)
    }

    /** Bringing a retired card back, as state, box and due day. */
    fun revival(box: Int, today: Long): Step = Step(
        if (box >= 3) CardState.STATE_KNOWN else CardState.STATE_LEARNING,
        // Back in rotation immediately, one box down: you revived it because
        // you had forgotten it.
        max(0, box - 1),
        today,
    )

    /**
     * Apply a swipe.
     *
     * Unknown drops to the bottom and comes back the same day — the whole value
     * of the gesture is that a word you just failed reappears while the failure
     * is still fresh. Known steps up one box rather than jumping to the end,
     * because one confident glance is weak evidence.
     */
    fun apply(current: CardState, swipe: Swipe, today: Long): CardState {
        val next = step(current.state, current.box, swipe, today)
        // Retiring preserves the due day rather than zeroing it, so a revived
        // card remembers roughly where it was in the rotation.
        return current.copy(
            state = next.state,
            box = next.box,
            dueAt = if (swipe == Swipe.RETIRE) current.dueAt else next.dueAt,
            seenCount = current.seenCount + if (swipe == Swipe.RETIRE) 0 else 1,
            touchedAt = System.currentTimeMillis(),
        )
    }

    fun apply(current: CharacterState, swipe: Swipe, today: Long): CharacterState {
        val next = step(current.state, current.box, swipe, today)
        return current.copy(
            state = next.state,
            box = next.box,
            dueAt = if (swipe == Swipe.RETIRE) current.dueAt else next.dueAt,
            seenCount = current.seenCount + if (swipe == Swipe.RETIRE) 0 else 1,
            touchedAt = System.currentTimeMillis(),
        )
    }

    /** Bring a retired card back, one box below where it was retired. */
    fun revive(current: CardState, today: Long): CardState {
        val next = revival(current.box, today)
        return current.copy(
            state = next.state,
            box = next.box,
            dueAt = next.dueAt,
            touchedAt = System.currentTimeMillis(),
        )
    }

    fun revive(current: CharacterState, today: Long): CharacterState {
        val next = revival(current.box, today)
        return current.copy(
            state = next.state,
            box = next.box,
            dueAt = next.dueAt,
            touchedAt = System.currentTimeMillis(),
        )
    }

    /** How long until this card is seen again, for the hint under each gesture. */
    fun previewDays(current: CardState, swipe: Swipe): Int = when (swipe) {
        Swipe.UNKNOWN -> 0
        Swipe.KNOWN -> INTERVALS[min(MAX_BOX, current.box + 1)]
        Swipe.RETIRE -> -1
    }
}

/** Which slice of the deck the Cards screen is showing. */
enum class CardFilter(val label: String) {
    DUE("Due"),
    ALL("All"),
    STRUGGLING("Struggling"),
    LEARNING("Learning"),
    KNOWN("Known"),
    RETIRED("Retired"),
}
