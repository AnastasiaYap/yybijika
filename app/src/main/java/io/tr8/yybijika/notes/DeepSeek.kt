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
