package io.tr8.yybijika.learn

/**
 * What is going wrong, and where.
 *
 * The app has recorded every answer since the first day and read almost none of
 * it back. Level, points and a streak say how much has been done; none of them
 * says what is not working, which is the only thing a learner can act on.
 *
 * Three levels, in rising order of what they can tell you that a lapse count
 * cannot:
 *
 *  * [SkillGap] — five schedules per word exist so a listening weakness cannot
 *    hide behind a reading strength, and putting them side by side is the whole
 *    reason they were kept apart.
 *  * [TroubleWord] — the words being lost, with what was answered instead.
 *  * [TroubleCharacter] — whether a run of failures shares a character. If
 *    医学院, 商学院 and 工学院 all go wrong, the question is whether 院 is the
 *    problem, and nothing but the word-to-character graph can answer it.
 */
data class Diagnosis(
    val skills: List<SkillGap>,
    val words: List<TroubleWord>,
    val characters: List<TroubleCharacter>,
    val reviewsCounted: Int,
) {
    /** Too little history to say anything honest. */
    val thin: Boolean get() = reviewsCounted < MIN_REVIEWS

    companion object {
        /**
         * Below this, every pattern is noise.
         *
         * A diagnosis drawn from nine reviews would name whichever word
         * happened to go wrong twice, and the learner would have no way to know
         * it was chance. Saying "not yet" is the honest output.
         */
        const val MIN_REVIEWS = 30
    }
}

data class SkillGap(
    val skill: Skill,
    val words: Int,
    val meanBox: Double,
    val lapses: Int,
) {
    /** Roughly how far along, as a share of the ladder. */
    val progress: Double get() = (meanBox / (Scheduler.INTERVALS.size - 1)).coerceIn(0.0, 1.0)
}

data class TroubleWord(
    val wordId: Long,
    val hanzi: String,
    val pinyin: String,
    val gloss: String,
    val lapses: Int,
    /** What was answered instead, commonest first. Empty until it happens again. */
    val answeredInstead: List<String>,
    /** Words the deck says this one is genuinely lost against. */
    val confusableWith: List<String>,
    val skill: Skill?,
)

/**
 * A character whose words go wrong more often than the deck's words do.
 *
 * [lift] is how much worse: 2.0 means the words containing it are lost twice as
 * often as average. Below about 1.5 there is nothing to see, and a character
 * appearing in two words cannot show a pattern at all — both are filtered out
 * before this is built, because a diagnosis that names something every week
 * stops being read.
 */
data class TroubleCharacter(
    val hanzi: String,
    val pinyin: String,
    val gloss: String?,
    val lift: Double,
    val words: List<String>,
)


/**
 * Finding the character behind a run of failures.
 *
 * Pure, and separated from the database for that reason: it is the one piece of
 * this screen that makes a statistical claim, and a claim that fires on noise
 * would send the learner off to study a character that is fine.
 */
object CharacterLift {

    data class Found(val hanzi: String, val lift: Double, val wordIds: List<Long>)

    /**
     * @param rate lapses per review, for every word that has been reviewed.
     * @param members which characters each word is made of.
     * @param minWords how many reviewed words a character needs before a
     *   pattern in them means anything. Two words that both went badly is a
     *   coincidence with a name.
     * @param minLift how much worse than the learner's own average it has to
     *   be. Measured as a lift rather than an absolute rate, or somebody
     *   failing a third of everything would be told every character is a
     *   problem.
     */
    fun find(
        rate: Map<Long, Double>,
        members: Map<Long, List<String>>,
        minWords: Int = 3,
        minLift: Double = 1.5,
    ): List<Found> {
        if (rate.isEmpty()) return emptyList()
        val average = rate.values.average()
        if (average <= 0.0) return emptyList()

        val byCharacter = mutableMapOf<String, MutableList<Long>>()
        for ((wordId, chars) in members) {
            if (wordId !in rate) continue
            for (ch in chars.distinct()) {
                byCharacter.getOrPut(ch) { mutableListOf() }.add(wordId)
            }
        }

        return byCharacter
            .filter { it.value.size >= minWords }
            .mapNotNull { (ch, ids) ->
                val lift = ids.map { rate.getValue(it) }.average() / average
                if (lift < minLift) null else Found(ch, lift, ids)
            }
            .sortedByDescending { it.lift }
    }
}
