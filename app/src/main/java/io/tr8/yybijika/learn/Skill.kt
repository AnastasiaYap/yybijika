package io.tr8.yybijika.learn

/**
 * The four things you can independently be good at for a single word.
 *
 * Keeping these apart is the whole point of the deck: recognising 热闹 on a page
 * says nothing about being able to produce it, and neither says anything about
 * catching it in speech. A word therefore carries up to four schedules, and the
 * session builder serves whichever one is most overdue.
 */
enum class Skill(val label: String, val labelZh: String) {
    RECOGNITION("Recognition", "认"),
    PRODUCTION("Production", "写"),
    LISTENING("Listening", "听"),
    USAGE("Usage", "用"),
}

/** How an answer went. Mirrors the three review buttons. */
enum class Grade { AGAIN, HARD, GOOD }
