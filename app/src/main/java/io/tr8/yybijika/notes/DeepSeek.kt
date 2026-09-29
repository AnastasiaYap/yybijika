package io.tr8.yybijika.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fills in what a pasted note left out.
 *
 * Only ever asked about words the paste already named — the model supplies
 * readings, meanings and examples, never new vocabulary. That keeps the deck
 * yours: everything in it is something you wrote down.
 *
 * Mirrors pipeline/enrich.py, including the validation, because a suggestion
 * that fails the same checks on the phone would fail them on the desktop too.
 */
class DeepSeek(private val apiKey: String) {

    data class Filled(
        val hanzi: String,
        val pinyin: String?,
        val glossId: String?,
        val glossEn: String?,
        val exampleZh: String?,
        val examplePinyin: String?,
        val exampleGloss: String?,
        val rejected: List<String> = emptyList(),
    )

    class Failure(message: String) : Exception(message)

    /** A passage written to order, before anything has been checked. */
    data class Draft(
        val title: String,
        val titleGloss: String,
        val lines: List<DraftLine>,
        val questions: List<DraftQuestion>,
    )

    data class DraftLine(
        val zh: String,
        val pinyin: String,
        val gloss: String,
        /** The line cut into words. The model knows where they end; we do not. */
        val words: List<String>,
    )

    data class DraftQuestion(val q: String, val choices: List<String>, val answer: Int)

    /**
     * Write a short passage around these words.
     *
     * The words come from what she keeps failing, which is the whole point: ten
     * fixed passages cannot know that 索赔 and 合同 are the two she loses every
     * week, and a story that puts them in one scene is worth more than either
     * word drilled again on its own.
     *
     * The model is asked for the segmentation too. It knows where Chinese words
     * end and the phone does not — the segmenter is a build-time dependency
     * that has no business in an APK — and without it the words in a generated
     * passage could not be tapped.
     *
     * Nothing here is trusted. What comes back is checked against the deck
     * before it is kept, and what cannot be checked is counted and shown.
     */
    suspend fun writePassage(
        words: List<String>,
        vocabulary: List<String>,
        language: String,
    ): Draft = withContext(Dispatchers.IO) {
        val asked = JSONObject().apply {
            put("must_use", JSONArray(words))
            put("gloss_language", language)
            // A sample rather than the whole deck: 1,173 words would dominate
            // the request, and the instruction is what does the work anyway.
            put("learner_vocabulary_sample", JSONArray(vocabulary.take(150)))
        }
        val reply = request(PASSAGE_PROMPT + asked.toString(2))
        val json = JSONObject(extractJson(reply))

        val lines = json.getJSONArray("lines").let { arr ->
            (0 until arr.length()).map { i ->
                val line = arr.getJSONObject(i)
                val pieces = line.optJSONArray("words")
                DraftLine(
                    zh = line.getString("zh"),
                    pinyin = line.optString("pinyin"),
                    gloss = line.optString("gloss"),
                    words = (0 until (pieces?.length() ?: 0))
                        .map { pieces!!.getString(it) },
                )
            }
        }
        if (lines.isEmpty()) throw Failure("the reply had no lines")

        val questions = json.optJSONArray("questions").let { arr ->
            (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                val q = arr!!.getJSONObject(i)
                val choices = q.optJSONArray("choices") ?: return@mapNotNull null
                DraftQuestion(
                    q = q.getString("q"),
                    choices = (0 until choices.length()).map { choices.getString(it) },
                    answer = q.optInt("answer", 0),
                )
            }
        }

        Draft(
            title = json.optString("title").ifBlank { words.firstOrNull().orEmpty() },
            titleGloss = json.optString("title_gloss"),
            lines = lines,
            questions = questions,
        )
    }

    /**
     * Annotate lines the learner has pasted: reading, meaning, word boundaries.
     *
     * Annotation only. The model is given the text and asked to explain it; it
     * is never asked to supply, recall or continue a song, and the app has no
     * way to fetch one. What comes back is a reading of words that were already
     * on the screen — which is what a dictionary does, and the reason this is a
     * study tool rather than a way of getting hold of lyrics.
     *
     * Song language is its own problem: lines are short, inverted for the tune,
     * and full of 啊 and 呀 padding out a beat. The prompt says so, because a
     * model translating them as ordinary prose produces confident nonsense.
     */
    suspend fun annotate(
        lines: List<String>,
        language: String,
    ): List<DraftLine> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()
        val asked = JSONObject().apply {
            put("lines", JSONArray(lines))
            put("gloss_language", language)
        }
        val reply = request(ANNOTATE_PROMPT + asked.toString(2))
        val out = JSONObject(extractJson(reply)).getJSONArray("lines")

        (0 until out.length()).map { i ->
            val line = out.getJSONObject(i)
            val pieces = line.optJSONArray("words")
            DraftLine(
                // The original line wins over whatever came back: the reader
                // must see what she pasted, not the model's tidied version of it.
                zh = lines.getOrElse(i) { line.optString("zh") },
                pinyin = line.optString("pinyin"),
                gloss = line.optString("gloss"),
                words = (0 until (pieces?.length() ?: 0)).map { pieces!!.getString(it) },
            )
        }
    }

    /** Models sometimes wrap JSON in prose or a fence; take the object. */
    private fun extractJson(reply: String): String {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw Failure("no JSON in the reply")
        return reply.substring(start, end + 1)
    }

    suspend fun fill(drafts: List<NoteParser.Draft>): List<Filled> =
        withContext(Dispatchers.IO) {
            if (drafts.isEmpty()) return@withContext emptyList()

            val asked = JSONArray()
            drafts.forEach { d ->
                asked.put(JSONObject().apply {
                    put("hanzi", d.hanzi)
                    put("needs", JSONArray(d.missing))
                    d.pinyin?.let { put("pinyin", it) }
                    if (d.glosses.isNotEmpty()) put("meaning", d.glosses.first())
                })
            }

            val reply = request(PROMPT_PREFIX + asked.toString(2) + PROMPT_SUFFIX)
            parse(reply, drafts)
        }

    /**
     * Mark one written sentence.
     *
     * Deliberately narrow: the model is told the word, its meaning, its usage
     * note and the sentence, and asked whether the word is used correctly *in
     * that sense*. It is not asked to rate the writing, suggest better
     * vocabulary, or be encouraging — a model given room to be helpful returns
     * a paragraph of praise, and a paragraph of praise teaches nothing.
     *
     * One correction and one reason. Anything it cannot fit in those is
     * something a learner at this stage would not act on anyway.
     */
    suspend fun check(
        sentence: String,
        word: String,
        pinyin: String,
        meaning: String,
        usageNote: String?,
        language: String,
    ): Marked = withContext(Dispatchers.IO) {
        val asked = JSONObject().apply {
            put("word", word)
            put("pinyin", pinyin)
            put("meaning", meaning)
            usageNote?.let { put("usage_note", it) }
            put("sentence", sentence)
            put("reply_language", language)
        }
        val reply = request(CHECK_PROMPT + asked.toString(2))
        val json = JSONObject(extractJson(reply))
        Marked(
            correct = json.optBoolean("correct", false),
            corrected = json.optString("corrected").takeIf { it.isNotBlank() },
            note = json.optString("note").takeIf { it.isNotBlank() },
        )
    }

    data class Marked(
        val correct: Boolean,
        val corrected: String?,
        val note: String?,
    )

    private fun request(prompt: String): String {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", SYSTEM)
                })
                put(JSONObject().apply {
                    put("role", "user"); put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("response_format", JSONObject().put("type", "json_object"))
        }.toString()

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            BufferedWriter(OutputStreamWriter(connection.outputStream)).use { it.write(body) }
            if (connection.responseCode == 401) {
                throw Failure("DeepSeek rejected the key. Check it in Settings.")
            }
            if (connection.responseCode !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw Failure("DeepSeek returned ${connection.responseCode}. ${detail.orEmpty().take(140)}")
            }
            val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return payload.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("Could not reach DeepSeek: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(raw: String, drafts: List<NoteParser.Draft>): List<Filled> {
        val byHanzi = drafts.associateBy { it.hanzi }
        val json = runCatching { JSONObject(raw) }.getOrElse {
            throw Failure("DeepSeek sent something that was not JSON")
        }
        val items = json.optJSONArray("items") ?: throw Failure("DeepSeek sent no items")

        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val hanzi = item.optString("hanzi")
                // Never accept a word that was not asked about: the deck is the
                // learner's own vocabulary, not the model's suggestions.
                val draft = byHanzi[hanzi] ?: continue

                val rejected = mutableListOf<String>()

                val pinyin = item.optString("pinyin").takeIf { it.isNotBlank() }
                    ?.let { candidate ->
                        if (Validate.pinyinMatches(hanzi, candidate)) candidate
                        else { rejected += "pinyin had the wrong syllable count"; null }
                    } ?: draft.pinyin

                val example = item.optString("example").takeIf { it.isNotBlank() }
                    ?.let { candidate ->
                        if (Validate.exampleUses(hanzi, candidate)) candidate
                        else { rejected += "example did not use the word"; null }
                    }

                add(
                    Filled(
                        hanzi = hanzi,
                        pinyin = pinyin,
                        glossId = item.optString("id").takeIf { it.isNotBlank() }
                            ?: draft.glosses.firstOrNull(),
                        glossEn = item.optString("en").takeIf { it.isNotBlank() },
                        exampleZh = example,
                        examplePinyin = item.optString("example_pinyin").takeIf { it.isNotBlank() },
                        exampleGloss = item.optString("example_id").takeIf { it.isNotBlank() },
                        rejected = rejected,
                    )
                )
            }
        }
    }

    private companion object {
        const val PASSAGE_PROMPT = """Write a short Chinese reading passage for
a learner. Reply with JSON only:

{"title": "...", "title_gloss": "...",
 "lines": [{"zh": "...", "pinyin": "...", "gloss": "...",
            "words": ["...", "..."]}],
 "questions": [{"q": "...", "choices": ["...","...","...","..."], "answer": 0}]}

Rules:
- 12 to 18 lines. One sentence per line. It must be a story with something
  happening in it, not a list of sentences that use the words.
- Every word in "must_use" has to appear, and to matter to the story.
- Use only common vocabulary (HSK 1-3) plus the words in "must_use" and the
  sample given. Do not reach for a rarer word when a common one will do.
- "words" is that same line cut at word boundaries, punctuation included as its
  own entries. Every character of "zh" must appear in "words", in order.
- "pinyin" is the line with tone marks, spaced by syllable.
- "gloss" is the line translated into gloss_language.
- 4 questions about what the passage said, in Chinese, each with 4 choices.
  "answer" is the index of the correct one.

Here is the request:
"""

        const val ANNOTATE_PROMPT = """Annotate these Chinese lines for a
learner. They are lines the learner already has in front of her; explain them,
do not replace them. Reply with JSON only:

{"lines": [{"pinyin": "...", "gloss": "...", "words": ["...", "..."]}]}

Rules:
- One entry per input line, in the same order, same count. An empty input line
  gets an entry with empty strings.
- "pinyin" is that line with tone marks, spaced by syllable.
- "gloss" is what the line means, in gloss_language. These are song lines:
  word order may be inverted for the tune, and 啊 呀 哦 may be there only to
  fill a beat. Translate what it means, not word by word, and do not invent a
  meaning for a line that is only padding — say so plainly instead.
- "words" is the line cut at word boundaries, punctuation as its own entries.
  Every character of the line must appear in "words", in order.

Here are the lines:
"""

        const val CHECK_PROMPT = """You are marking one sentence written by a
learner of Chinese. Reply with JSON only:

{"correct": true|false, "corrected": "...", "note": "..."}

Rules:
- "correct" is false only if the sentence is ungrammatical, or the target word
  is used in a sense it does not have. Awkward-but-correct is correct.
- "corrected" is the smallest possible fix, or "" if the sentence is fine.
- "note" is ONE sentence saying what was wrong and why, written in the
  reply_language given below. "" if the sentence is fine.
- Do not praise, do not suggest better vocabulary, do not add alternatives.

Here is the sentence:
"""

        const val API_URL = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-chat"

        const val SYSTEM =
            "You help maintain a personal Mandarin flashcard deck built from a " +
                "learner's own notes. The learner is Indonesian and studies in " +
                "Indonesian and English. Answer only with JSON. Never invent a word " +
                "that was not asked about. Prefer plain everyday wording."

        const val PROMPT_PREFIX =
            "For each word below, supply only what its \"needs\" list asks for. " +
                "Keep meanings under ten words. An example sentence must contain the " +
                "word itself and be 5 to 20 characters long.\n\n"

        const val PROMPT_SUFFIX =
            "\n\nReply as {\"items\":[{\"hanzi\":\"...\",\"pinyin\":\"tone marks, " +
                "space separated, one syllable per character\",\"id\":\"Indonesian " +
                "meaning\",\"en\":\"English meaning\",\"example\":\"例句\"," +
                "\"example_pinyin\":\"...\",\"example_id\":\"Indonesian translation\"}]}"
    }
}

/** The same gates pipeline/validate.py applies, so the phone is no more trusting. */
object Validate {

    private val HAN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]")
    private const val TONES = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüńň"

    fun pinyinMatches(hanzi: String, pinyin: String): Boolean {
        val syllables = pinyin.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val characters = HAN.findAll(hanzi).count()
        if (syllables.size != characters) return false
        if (characters > 1 && pinyin.none { it in TONES }) return false
        return syllables.all { it.all { c -> c.isLetter() || c in TONES || c.isDigit() } }
    }

    fun exampleUses(hanzi: String, sentence: String): Boolean {
        if (!sentence.contains(hanzi)) return false
        val characters = HAN.findAll(sentence).count()
        if (characters < 4 || characters > 30) return false
        return !Regex("[A-Za-z]{3,}").containsMatchIn(sentence)
    }
}
