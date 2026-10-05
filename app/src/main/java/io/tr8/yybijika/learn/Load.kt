package io.tr8.yybijika.learn

/**
 * Noticing that a sitting has gone wrong.
 *
 * Everything else in the app decides what to study from the schedule, which
 * knows what you did on previous days and nothing at all about this minute. So
 * a session is twenty questions whether you are fresh or finishing it on a bus
 * at midnight, and three failures in a row — which is a real signal that the
 * load is above what this particular sitting can carry — changes nothing.
 *
 * The humane response to that signal is not to push through. It is to say so,
 * offer the exit, and if the learner stays, make the next question an easier
 * one. Cognitive load that high stops producing learning well before it stops
 * producing effort, and the session a learner abandons in frustration costs
 * tomorrow's session too.
 */
object Load {

    /**
     * Failures in a row before the app says something.
     *
     * Two is ordinary — any session worth doing contains failures, and a deck
     * that flinches at the second one would interrupt constantly. Three in a
     * row is the point where it is no longer plausibly the words.
     */
    const val WOBBLE = 3

    fun struggling(missStreak: Int): Boolean = missStreak >= WOBBLE

    /**
     * Put something winnable next.
     *
     * The most solid item left — highest box, never new, not a retry of
     * something already missed — is moved to just after the current position.
     * Nothing is dropped: the rest of the queue keeps its order behind it, so
     * easing the next question costs the session no content, only sequence.
     *
     * Returns the queue unchanged when there is nothing solid left to bring
     * forward, which is the honest outcome — a session made entirely of new
     * words has no comfortable question to offer, and inventing one by
     * promoting another failure would make it worse.
     */
    fun ease(queue: List<SessionItem>, at: Int): List<SessionItem> {
        val rest = queue.drop(at + 1)
        val pick = rest
            .filter { !it.isNew && !it.isRetry }
            .maxByOrNull { it.box }
            ?: return queue
        if (pick.box < 1) return queue
        val index = queue.indexOfFirst { it === pick }
        if (index <= at + 1) return queue
        return queue.toMutableList().apply {
            removeAt(index)
            add(at + 1, pick)
        }
    }

    /**
     * What to say.
     *
     * Named after what actually happened rather than dressed up: somebody who
     * has just missed three in a row knows it, and a cheerful line would read
     * as the app not having noticed.
     */
    fun wording(missStreak: Int, done: Int): String = when {
        done <= 3 -> "Three in a row, right at the start. These might be the " +
            "wrong words for today rather than the wrong day for words."
        missStreak >= 5 -> "Five in a row now. This is the point where more " +
            "questions stop teaching you anything."
        else -> "Three in a row. That is usually tiredness rather than the words."
    }
}
