package io.tr8.yybijika.learn

import io.tr8.yybijika.data.CardState
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

    /**
     * Apply a swipe.
     *
     * Unknown drops to the bottom and comes back the same day — the whole value
     * of the gesture is that a word you just failed reappears while the failure
     * is still fresh. Known steps up one box rather than jumping to the end,
     * because one confident glance is weak evidence.
     */
    fun apply(current: CardState, swipe: Swipe, today: Long): CardState = when (swipe) {
        Swipe.UNKNOWN -> current.copy(
            state = CardState.STATE_STRUGGLING,
            box = 0,
            dueAt = today,
            seenCount = current.seenCount + 1,
            touchedAt = System.currentTimeMillis(),
        )

        Swipe.KNOWN -> {
            val box = min(MAX_BOX, current.box + 1)
            current.copy(
                // "Known" is a claim the deck only accepts after it has been
                // made a few times; one swipe moves the card along but does not
                // yet promote it out of learning.
                state = if (box >= 3) CardState.STATE_KNOWN else CardState.STATE_LEARNING,
                box = box,
                dueAt = today + INTERVALS[box],
                seenCount = current.seenCount + 1,
                touchedAt = System.currentTimeMillis(),
            )
        }

        Swipe.RETIRE -> current.copy(
            state = CardState.STATE_RETIRED,
            touchedAt = System.currentTimeMillis(),
        )
    }

    /** Bring a retired card back, at the box it had when it was retired. */
    fun revive(current: CardState, today: Long): CardState = current.copy(
        state = if (current.box >= 3) CardState.STATE_KNOWN else CardState.STATE_LEARNING,
        // Back in rotation immediately: you revived it because you forgot it.
        box = max(0, current.box - 1),
        dueAt = today,
        touchedAt = System.currentTimeMillis(),
    )

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
