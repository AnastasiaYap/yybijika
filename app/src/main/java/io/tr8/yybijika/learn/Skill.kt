package io.tr8.yybijika.learn

/**
 * The five things you can independently be good at for a single word.
 *
 * Keeping these apart is the whole point of the deck: recognising 热闹 on a page
 * says nothing about being able to produce it, and neither says anything about
 * catching it in speech. A word therefore carries up to five schedules, and the
 * session builder serves whichever one is most overdue.
 *
 * [COMPOSITION] is deliberately not filed under [PRODUCTION]. Picking 索赔 out
 * of four options and writing a sentence you meant with it are different
 * abilities, and folding them together is exactly the conflation the per-skill
 * schedules exist to prevent — a learner who aced the multiple choice would
 * look fluent while never having written a word.
 */
enum class Skill(val label: String, val labelZh: String) {
    RECOGNITION("Recognition", "认"),
    PRODUCTION("Production", "写"),
    LISTENING("Listening", "听"),
    USAGE("Usage", "用"),
    COMPOSITION("Writing", "作"),
}

/** How an answer went. Mirrors the three review buttons. */
enum class Grade { AGAIN, HARD, GOOD }
