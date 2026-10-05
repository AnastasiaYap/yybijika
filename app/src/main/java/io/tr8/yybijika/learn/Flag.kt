package io.tr8.yybijika.learn

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Telling the app a card is wrong.
 *
 * The deck is one person's notes, typed over months, and some of it is simply
 * mistaken: a gloss that does not fit, a reading that is off, an example nobody
 * would say. Everything else in the app assumes the card is right and the
 * learner is being tested — so meeting a bad card has meant being marked against
 * it, and then meeting it again on schedule, learning the error a little better
 * each time.
 *
 * A flag is the one place the arrow points the other way. It costs one tap, it
 * never grades the word, and it keeps the card out of sessions until the note
 * behind it is fixed — because the alternative is drilling something known to be
 * wrong.
 */
enum class FlagReason(val id: String, val label: String) {
    MEANING("meaning", "The meaning is wrong"),
    PINYIN("pinyin", "The pinyin is wrong"),
    EXAMPLE("example", "The example is wrong"),
    QUESTION("question", "The question doesn't make sense"),
    OTHER("other", "Something else");

    companion object {
        fun of(id: String): FlagReason = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** One flagged card, as the screens and the report see it. */
data class FlaggedCard(
    val id: Long,
    val wordId: Long,
    val hanzi: String,
    val pinyin: String,
    val reason: FlagReason,
    /** What the card said at the time, kept verbatim. */
    val shown: String?,
    val note: String?,
    val at: Long,
)

object Flags {

    private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

    /**
     * The whole list as text, ready to paste.
     *
     * The fix does not happen in the app — it happens in notes.txt, or in a
     * question to DeepSeek — so the list has to leave the phone in one piece.
     * Plain lines rather than JSON: this is read by a person first.
     */
    fun report(cards: List<FlaggedCard>, zone: ZoneId = ZoneId.systemDefault()): String {
        if (cards.isEmpty()) return "Nothing flagged."
        val header = if (cards.size == 1) "1 card flagged" else "${cards.size} cards flagged"
        return buildString {
            appendLine(header)
            cards.forEach { card ->
                appendLine()
                appendLine("${card.hanzi}  ${card.pinyin} — ${card.reason.label.lowercase()}")
                card.shown?.takeIf { it.isNotBlank() }?.let { appendLine("  card said: $it") }
                card.note?.takeIf { it.isNotBlank() }?.let { appendLine("  you said: $it") }
                appendLine("  " + DAY.format(Instant.ofEpochMilli(card.at).atZone(zone)))
            }
        }.trimEnd()
    }
}
