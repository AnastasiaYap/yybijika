package io.tr8.yybijika.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads pasted note lines the same way the Python pipeline reads the notes file.
 *
 * The point is that you can paste in exactly the shorthand you already write —
 * `热闹rame (positif）meriah`, `旅行tour=旅游`, `接menerima：接电话` — and get the
 * same structure the shipped deck was built from, rather than having to fill in
 * a form.
 *
 * Whatever is missing after parsing is what the enrichment step is asked for.
 */
object NoteParser {

    private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]")
    private val HAN_RUN = Regex("^([\\u3400-\\u4DBF\\u4E00-\\u9FFF]+)")
    private val LATIN = Regex("[A-Za-z]")
    private val PAREN = Regex("[（(]([^）)]*)[）)]")
    private val ANTONYM = Regex("><\\s*([\\u3400-\\u4DBF\\u4E00-\\u9FFF]{1,8})")
    private val EQUALS = Regex("=\\s*([\\u3400-\\u4DBF\\u4E00-\\u9FFF]{1,8})")
    private val SENT_PUNCT = Regex("[。？！]")
    private val GRAMMAR_MARKER = Regex("[我你他她们的了是在不很也都呢吗吧把被给就还]")
    private const val TONE_MARKS = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüńň"

    data class Draft(
        val hanzi: String,
        val glosses: MutableList<String> = mutableListOf(),
        val usageNotes: MutableList<String> = mutableListOf(),
        val related: MutableList<String> = mutableListOf(),
        val examples: MutableList<String> = mutableListOf(),
        var pinyin: String? = null,
        val sourceLine: String = "",
    ) {
        val needsGloss: Boolean get() = glosses.isEmpty()
        val needsPinyin: Boolean get() = pinyin.isNullOrBlank()
        val needsExample: Boolean get() = examples.isEmpty()

        val missing: List<String>
            get() = buildList {
                if (needsPinyin) add("pinyin")
                if (needsGloss) add("meaning")
                if (needsExample) add("example")
            }
    }

    suspend fun parse(text: String): List<Draft> = withContext(Dispatchers.Default) {
        val drafts = mutableListOf<Draft>()
        var current: Draft? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                current = null
                continue
            }
            if (!HAN.containsMatchIn(line)) {
                // A latin-only line annotates whatever came before it.
                current?.usageNotes?.add(line)
                continue
            }

            // A sentence belongs to the word above it, not beside it.
            if (isSentence(line) && current != null) {
                current.examples.add(line)
                continue
            }

            parseEntry(line)?.let {
                drafts.add(it)
                current = it
            }
        }
        drafts
    }

    private fun isSentence(line: String): Boolean {
        if (LATIN.containsMatchIn(line)) return false
        val hanziCount = HAN.findAll(line).count()

        // Sentence-final punctuation settles it on its own: 这里很热闹。is five
        // characters, and a length threshold high enough to exclude four-character
        // idioms would have thrown it away.
        if (SENT_PUNCT.containsMatchIn(line)) return hanziCount >= 3

        // Without punctuation, length plus a function word is the only signal,
        // and the bar has to stay above chengyu length.
        return hanziCount >= 6 && GRAMMAR_MARKER.containsMatchIn(line)
    }

    private fun parseEntry(line: String): Draft? {
        val head = HAN_RUN.find(line)?.groupValues?.get(1) ?: return null
        val draft = Draft(hanzi = head, sourceLine = line)
        var rest = line.substring(head.length).trim()

        ANTONYM.findAll(rest).forEach { draft.related.add(it.groupValues[1]) }
        rest = ANTONYM.replace(rest, " ")

        PAREN.findAll(rest).forEach { m ->
            m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let(draft.usageNotes::add)
        }
        rest = PAREN.replace(rest, " ")

        // A colon introduces examples written inline.
        val colon = rest.indexOfFirst { it == '：' || it == ':' }
        if (colon >= 0) {
            rest.substring(colon + 1)
                .split(',', '，', '、', '/')
                .map { it.trim() }
                .filter { it.isNotEmpty() && HAN.containsMatchIn(it) && !LATIN.containsMatchIn(it) }
                .forEach(draft.examples::add)
            rest = rest.substring(0, colon)
        }

        EQUALS.findAll(rest).forEach { draft.related.add(it.groupValues[1]) }
        rest = EQUALS.replace(rest, " ")

        // Pinyin written by hand, if any.
        //
        // Every syllable has to carry a tone mark for the run to count. Without
        // that rule the match runs straight on into the meaning — "热闹 rè nào
        // ramai" would be read as three syllables of pinyin and no gloss at all.
        // The cost is that a hand-written neutral tone ends the run early, which
        // is the safer way round: a missed syllable is recoverable, a swallowed
        // meaning is not.
        val toneRun = Regex("[A-Za-z$TONE_MARKS]+(?:\\s+[A-Za-z$TONE_MARKS]+)*")
            .findAll(rest)
            .map { it.value.trim() }
            .map { candidate ->
                candidate.split(Regex("\\s+"))
                    .takeWhile { token -> token.any { it in TONE_MARKS } }
                    .joinToString(" ")
            }
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length }

        if (toneRun != null) {
            draft.pinyin = toneRun
            rest = rest.replaceFirst(toneRun, " ")
        }

        rest = HAN.replace(rest, " ")
        rest.split('/', ',', ';', '，', '、')
            .map { it.trim().trim('.', '·', '-', '=', ':', '：') }
            .filter { it.length >= 2 }
            .forEach(draft.glosses::add)

        return draft
    }
}
