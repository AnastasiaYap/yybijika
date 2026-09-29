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

        return items
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
        return items
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
