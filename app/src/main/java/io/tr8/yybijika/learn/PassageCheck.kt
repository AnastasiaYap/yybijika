package io.tr8.yybijika.learn

/**
 * What to believe of a passage that was written on demand.
 *
 * A shipped passage was read by someone before it was published; one written
 * thirty seconds ago was not. Everything the model returns is therefore a claim
 * to be checked rather than content to be stored, and the checks here are the
 * ones that decide whether a passage is usable at all:
 *
 *  * it has to contain the words it was asked to practise, or it is a passage
 *    about nothing in particular;
 *  * each line's segmentation has to reassemble into that line, or the words in
 *    it cannot be tapped — a tap would open the wrong entry, or none.
 *
 * What cannot be decided is counted instead. A word in neither the deck nor
 * HSK 1-3 is not wrong, but the reader deserves to know how many of them she is
 * about to meet.
 */
object PassageCheck {

    data class Line(val zh: String, val pinyin: String, val gloss: String,
                    val pieces: List<String>)

    data class Checked(val lines: List<Line>, val unknownWords: Int)

    class Rejected(message: String) : Exception(message)

    private fun isHan(c: Char) = c.code in 0x4E00..0x9FFF

    /**
     * @param known everything the reader can be assumed to read: the deck plus
     *   the shipped HSK 1-3 baseline.
     */
    fun check(
        lines: List<Line>,
        targets: List<String>,
        known: Set<String>,
    ): Checked {
        if (lines.isEmpty()) throw Rejected("The passage came back empty.")

        val body = lines.joinToString("") { it.zh }
        val missing = targets.filterNot { body.contains(it) }
        if (missing.isNotEmpty()) {
            throw Rejected(
                "It left out ${missing.joinToString("、")} — asking again usually fixes it."
            )
        }

        var unknown = 0
        val out = lines.map { line ->
            // A segmentation that does not rebuild its own line is unusable, so
            // the line falls back to being one unsplittable piece rather than
            // being dropped: it can still be read, just not tapped.
            val pieces = if (line.pieces.joinToString("") == line.zh) line.pieces
            else listOf(line.zh)

            unknown += pieces.count { piece ->
                piece.length > 1 && piece.any(::isHan) && piece !in known
            }
            line.copy(pieces = pieces)
        }
        return Checked(out, unknown)
    }
}
