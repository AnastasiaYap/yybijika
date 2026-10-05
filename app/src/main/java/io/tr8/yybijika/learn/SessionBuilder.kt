package io.tr8.yybijika.learn

import io.tr8.yybijika.exercise.DeckContext
import io.tr8.yybijika.exercise.Exercise
import io.tr8.yybijika.exercise.ExerciseType
import io.tr8.yybijika.exercise.Registry
import io.tr8.yybijika.exercise.WordBundle
import kotlin.random.Random

/** One card in a session: a word, the skill being tested, and the question. */
data class SessionItem(
    val exercise: Exercise,
    val skill: Skill,
    val box: Int,
    val isNew: Boolean,
    /**
     * True when this item is back because it was failed earlier in the session.
     *
     * Worth marking in the UI: meeting the same word twice looks like a bug
     * unless the app says why, and "you missed this one" is also the point.
     */
    val isRetry: Boolean = false,
)

/**
 * Assembles a study session.
 *
 * The ordering rule is: due cards first, most overdue first, then new words to
 * fill the session out. Within that, the exercise served is whichever of the
 * available types matches the skill that came due — so the session is driven by
 * the schedule rather than by what happens to be easy to generate.
 */
object SessionBuilder {

    data class Due(val wordId: Long, val skill: Skill, val box: Int, val dueAt: Long)

    /**
     * How many new words to introduce when there is room left in a session.
     * Capped because every new word immediately creates up to four schedules,
     * and a big intake today becomes an unmanageable review day next week.
     */
    const val MAX_NEW_PER_SESSION = 10

    /**
     * Build a session.
     *
     * [onlyType] restricts every question to one exercise type, which is what a
     * quiz of a single kind needs. Left null, the type is chosen per card from
     * whichever skill came due — the mixed behaviour.
     */
    fun build(
        size: Int,
        due: List<Due>,
        unseen: List<Long>,
        bundles: Map<Long, WordBundle>,
        ctx: DeckContext,
        random: Random = Random.Default,
        onlyType: String? = null,
    ): List<SessionItem> {
        if (onlyType != null) return single(size, due, unseen, bundles, ctx, onlyType, random)
        val items = mutableListOf<SessionItem>()

        for (card in due.sortedWith(compareBy({ it.dueAt }, { it.box }))) {
            if (items.size >= size) break
            val word = bundles[card.wordId] ?: continue
            val exercise = pick(word, card.skill, ctx, random) ?: continue
            items += SessionItem(exercise, card.skill, card.box, isNew = false)
        }

        var introduced = 0
        for (wordId in unseen) {
            if (items.size >= size || introduced >= MAX_NEW_PER_SESSION) break
            val word = bundles[wordId] ?: continue
            // A new word is always met as recognition first. Being asked to
            // produce or hear a word you have never seen is not a test, it is a
            // guess, and it pollutes the schedule with a guaranteed lapse.
            val exercise = pick(word, Skill.RECOGNITION, ctx, random) ?: continue
            items += SessionItem(exercise, Skill.RECOGNITION, box = 0, isNew = true)
            introduced++
        }

        return shape(items)
    }

    /**
     * A quiz of exactly one question type.
     *
     * Due words first so the quiz still serves the schedule, then anything else
     * that can produce this question — a single-type quiz should not run dry
     * just because nothing of that kind happens to be due today.
     */
    private fun single(
        size: Int,
        due: List<Due>,
        unseen: List<Long>,
        bundles: Map<Long, WordBundle>,
        ctx: DeckContext,
        typeId: String,
        random: Random,
    ): List<SessionItem> {
        val type = Registry.byId(typeId) ?: return emptyList()
        val items = mutableListOf<SessionItem>()
        val used = mutableSetOf<Long>()

        fun offer(wordId: Long, box: Int, isNew: Boolean) {
            if (items.size >= size || wordId in used) return
            val word = bundles[wordId] ?: return
            val exercise = type.generate(word, ctx) ?: return
            used += wordId
            items += SessionItem(exercise, type.skill, box, isNew)
        }

        due.sortedBy { it.dueAt }.forEach { offer(it.wordId, it.box, false) }
        // You write with words you have. Composing a sentence around a word met
        // ten seconds ago is guessing, and the feedback lands on a word with no
        // memory to attach it to — so new words are skipped for writing and
        // offered for everything else.
        if (type.skill != Skill.COMPOSITION) {
            unseen.forEach { offer(it, 0, true) }
        }
        bundles.keys.shuffled(random).forEach { offer(it, 0, false) }
        return shape(items)
    }

    /**
     * How many questions to put between a failure and its second attempt.
     *
     * Far enough that the answer has left working memory — asking again
     * immediately tests the last three seconds, not the word — and close enough
     * that it still happens inside the session, while the correction is fresh.
     */
    const val RETRY_LAG = 4

    /**
     * Put a failed item back into the queue, a few questions further on.
     *
     * The gap this closes: the session used to be a fixed list, so a word you
     * got wrong was shown its answer and then never asked again. Reading a
     * correction and producing it are different acts, and only the second one
     * leaves anything behind.
     */
    fun requeue(
        queue: List<SessionItem>,
        at: Int,
        item: SessionItem,
        lag: Int = RETRY_LAG,
    ): List<SessionItem> {
        val retry = item.copy(isRetry = true)
        // Clamped to the end so a failure near the finish still comes back
        // rather than falling off the edge of the session.
        val target = (at + lag).coerceIn(0, queue.size)
        return queue.toMutableList().apply { add(target, retry) }
    }

    /**
     * Take a word out of the rest of the queue.
     *
     * Used when a card is reported as wrong: the question on screen is being
     * walked away from, and any copy of the same word waiting further down —
     * a retry, or a second skill — would ask the doubted question again inside
     * the same session, which is the thing the flag was pressed to stop.
     *
     * Everything up to and including [at] is left alone. Those questions have
     * already been answered, and rewriting history under the index would make
     * the current card change identity mid-tap.
     */
    fun dropFrom(queue: List<SessionItem>, at: Int, wordId: Long): List<SessionItem> =
        queue.filterIndexed { i, item ->
            i <= at || item.exercise.word.id != wordId
        }

    /**
     * Give the session a beginning, a middle and an end.
     *
     * Strictly most-overdue-first means a bad fortnight opens with five things
     * you have already failed, which is the least likely way to get somebody
     * through a session. People remember the first and last items of a sequence
     * best, and a session that ends on a failure is remembered as a bad one
     * whatever happened in the middle — so it opens on something solid, puts
     * the hard work where it will not be the lasting impression, and ends on
     * something winnable.
     *
     * The schedule still decides *what* is in the session. This only decides
     * the order.
     */
    fun shape(items: List<SessionItem>): List<SessionItem> {
        if (items.size < 5) return items
        // New words are not "solid" whatever their box says — they have never
        // been answered — so they are never chosen to open or close on.
        val solid = items.filter { !it.isNew }.sortedByDescending { it.box }
        if (solid.size < 3) return items

        val opener = solid.take(1)
        val closer = solid.drop(1).take(1)
        val bookends = (opener + closer).toSet()
        val middle = items.filterNot { it in bookends }

        return opener + middle + closer
    }

    /**
     * Choose how to test one skill on one word.
     *
     * Falls back to any available type only when nothing matches the requested
     * skill, and returns null rather than testing the wrong skill silently —
     * crediting a listening review for a reading answer would corrupt exactly
     * the signal the per-skill schedules exist to keep clean.
     */
    fun pick(
        word: WordBundle,
        skill: Skill,
        ctx: DeckContext,
        random: Random = Random.Default,
    ): Exercise? {
        val candidates = Registry.available(word, ctx).filter { it.skill == skill }
        if (candidates.isEmpty()) return null
        return rotate(candidates, word, random).firstNotNullOfOrNull {
            it.generate(word, ctx)
        }
    }

    /**
     * Vary which type a word gets, so the same word is not always met the same
     * way. Seeded per word so a session rebuilt after a rotation is stable.
     */
    private fun rotate(
        types: List<ExerciseType>,
        word: WordBundle,
        random: Random,
    ): List<ExerciseType> = types.shuffled(random)

}
