package io.tr8.yybijika.data

import android.content.Context
import io.tr8.yybijika.exercise.DeckContext
import io.tr8.yybijika.exercise.Exercise
import io.tr8.yybijika.learn.Composer
import io.tr8.yybijika.learn.CharacterLift
import io.tr8.yybijika.learn.Diagnosis
import io.tr8.yybijika.learn.SkillGap
import io.tr8.yybijika.learn.TroubleCharacter
import io.tr8.yybijika.learn.TroubleWord
import io.tr8.yybijika.learn.PassageCheck
import io.tr8.yybijika.notes.DeepSeek
import io.tr8.yybijika.exercise.ExampleSentence
import io.tr8.yybijika.exercise.Overlap
import io.tr8.yybijika.exercise.Registry
import io.tr8.yybijika.exercise.Requirement
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.CardDeck
import io.tr8.yybijika.learn.CardFilter
import io.tr8.yybijika.learn.Grade
import io.tr8.yybijika.learn.Scheduler
import io.tr8.yybijika.learn.SessionBuilder
import io.tr8.yybijika.learn.SessionItem
import io.tr8.yybijika.learn.Skill
import io.tr8.yybijika.learn.Xp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * The one place the deck, the schedule and the registry meet.
 *
 * Everything above this (the view models, the screens) deals in sessions and
 * answers; everything below deals in rows.
 */
private const val SONG = "song"

/** One request has to hold a whole song, or the second half loses its context. */
const val MAX_SONG_LINES = 60

class Repo(
    private val content: ContentDb,
    private val progress: ProgressDb,
    private var voiceInstalled: Boolean = false,
    private var audioEnabled: Boolean = true,
) {

    private val dao = progress.dao()

    /** Distractor pools are reused across a session; rebuilding them per question
     *  would mean a random query per card for no gain in variety. */
    private val distractorCache = java.util.concurrent.ConcurrentHashMap<Long, List<WordBundle>>()

    /**
     * How often each word has been reviewed, refreshed when a session is built.
     *
     * Drives which example a question is built from, so the same word arrives
     * in a different sentence each time it comes round.
     */
    @Volatile
    private var rotations: Map<Long, Int> = emptyMap()

    /**
     * Whether a question may rely on sound.
     *
     * Two conditions, deliberately collapsed into one: the phone has a Chinese
     * voice, and the learner has not muted the app. They are different reasons
     * for the same consequence — a listening question would be unanswerable —
     * so every generator can keep asking one question instead of two.
     */
    private val canSpeak: Boolean get() = voiceInstalled && audioEnabled

    fun setTtsAvailable(available: Boolean) {
        voiceInstalled = available
    }

    fun setAudioEnabled(enabled: Boolean) {
        audioEnabled = enabled
    }

    /**
     * Ask the questions in this language from now on.
     *
     * Also clears the distractor cache: the wrong answers held there are glosses
     * in the old language, and a question mixing the two would be a giveaway.
     */
    fun setGlossLanguage(language: GlossLanguage) {
        content.language = language
        distractorCache.clear()
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    val deckContext: DeckContext = object : DeckContext {
        override val ttsAvailable: Boolean get() = canSpeak

        // Read once and held: 44 rows that never change between releases, and
        // every measure-word question asks for the same list.
        override val measureWords: List<String> by lazy { content.measureWords() }

        override fun distractorGlosses(word: WordBundle, count: Int): List<String> {
            val answer = word.primaryGloss
            return pool(word)
                .mapNotNull { it.primaryGloss }
                .filter { it != answer }
                // A wrong answer that shares a content word with the right one
                // is not a wrong answer, it is a trap: 逛街 "cuci mata,
                // lihat-lihat" against 旅行 "bepergian, jalan-jalan" used to
                // appear together, and both read as correct.
                .filterNot { answer != null && Overlap.collide(answer, it) }
                .distinct()
                .take(count)
        }

        override fun distractorHanzi(word: WordBundle, count: Int): List<String> =
            pool(word)
                .map { it.hanzi }
                .filter { it != word.hanzi }
                .distinct()
                .take(count)

        override fun distractorCharacterGlosses(exclude: String, count: Int): List<String> =
            content.characterDistractors(exclude, count * 4)
                .mapNotNull { it.gloss }
                .distinct()
                .take(count)

        override fun shuffleSeed(word: WordBundle, typeId: String): Long =
            word.id * 31 + typeId.hashCode()

        override fun variant(word: WordBundle): Int = rotations[word.id] ?: 0
    }

    private fun pool(word: WordBundle): List<WordBundle> =
        distractorCache.getOrPut(word.id) {
            content.distractorPool(word.id, word.hanzi.length)
        }

    /**
     * A word you pasted in, shaped exactly like one from the shipped deck.
     *
     * The negative id is what keeps the two apart without either side needing to
     * know the other exists: content.db ids count up from 1, so nothing can
     * collide, and every exercise, schedule and XP row treats them identically.
     */
    private fun UserWord.toBundle() = WordBundle(
        id = -id,
        hanzi = hanzi,
        hanziTrad = null,
        pinyin = pinyin,
        pinyinVerified = pinyinVerified,
        isPhrase = hanzi.length >= 5,
        glosses = listOfNotNull(glossId, glossEn),
        usageNotes = listOfNotNull(notes),
        examples = listOfNotNull(
            exampleZh?.let {
                ExampleSentence(
                    zh = it,
                    pinyin = examplePinyin,
                    gloss = exampleGloss,
                    glossEn = null,
                    containsTarget = it.contains(hanzi),
                    tokenCount = it.count { c -> c.code in 0x4E00..0x9FFF },
                    // No segmenter on the phone — the dictionary it needs is a
                    // build-time thing. A word you added yourself falls back to
                    // character tiles, which is worse but not broken.
                    segments = it.filter { c -> c.code in 0x4E00..0x9FFF }
                        .map(Char::toString),
                )
            }
        ),
        tags = listOf("added"),
        relations = emptyList(),
    )

    /**
     * Mark a written sentence, and keep it either way.
     *
     * Two layers, and the order matters. The offline checks run first and can
     * settle the matter on their own: a sentence missing its target word or
     * copied from the example does not need a model to say so, and spending an
     * API call to be told is a waste of hers.
     *
     * Only if it survives that does the sentence go out for real marking, and
     * only if there is a key and a network. When there is not, the sentence is
     * stored unmarked rather than refused — writing it was most of the value,
     * and the marking can catch up later.
     */
    suspend fun mark(
        exercise: Exercise.Compose,
        sentence: String,
        apiKey: String,
        language: GlossLanguage,
    ): Composer.Critique = withContext(Dispatchers.IO) {
        val offline = Composer.check(
            sentence = sentence,
            mustUse = exercise.mustUse,
            examples = exercise.examples,
            known = knownCharacters(),
        )

        var critique = offline
        if (offline.usable && apiKey.isNotBlank()) {
            critique = runCatching {
                val marked = DeepSeek(apiKey).check(
                    sentence = sentence,
                    word = exercise.word.hanzi,
                    pinyin = exercise.word.pinyin,
                    meaning = exercise.word.primaryGloss.orEmpty(),
                    usageNote = exercise.word.usageNotes.firstOrNull(),
                    language = if (language == GlossLanguage.ENGLISH) "English"
                    else "Indonesian",
                )
                offline.copy(
                    findings = offline.findings + listOfNotNull(
                        marked.note?.let { Composer.Finding(it, fatal = false) }
                    ),
                    corrected = marked.corrected,
                    note = marked.note,
                    source = "deepseek",
                )
            }.getOrElse {
                // No network, a bad key, a model having a bad day: the sentence
                // is still hers and still saved. Saying so is better than a
                // silent pass that looks like approval.
                offline.copy(
                    findings = offline.findings + Composer.Finding(
                        "Could not reach DeepSeek, so this is unmarked for now. " +
                            "It is saved under Writing.",
                        fatal = false,
                    )
                )
            }
        }

        dao.putComposition(
            Composition(
                wordId = exercise.word.id,
                hanzi = exercise.word.hanzi,
                prompt = exercise.situation,
                text = sentence.trim(),
                source = critique.source,
                correct = if (critique.source == "deepseek") critique.clean else null,
                corrected = critique.corrected,
                note = critique.note,
                writtenAt = System.currentTimeMillis(),
            )
        )
        critique
    }

    /**
     * Mark the sentences that were written with no connection.
     *
     * The gap this closes: a sentence written on a plane was saved unmarked and
     * then stayed unmarked for ever, because nothing ever went back for it. The
     * pile is worked oldest first and stops at the first failure — if the
     * network is down again there is no point sending the other nineteen.
     */
    suspend fun markPending(
        apiKey: String,
        language: GlossLanguage,
        limit: Int = 20,
    ): Int = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext 0
        val pending = dao.unmarkedCompositions().take(limit)
        var done = 0
        for (row in pending) {
            val word = content.bundles(listOf(row.wordId)).firstOrNull()
            val marked = runCatching {
                DeepSeek(apiKey).check(
                    sentence = row.text,
                    word = row.hanzi,
                    pinyin = word?.pinyin.orEmpty(),
                    meaning = word?.primaryGloss.orEmpty(),
                    usageNote = word?.usageNotes?.firstOrNull(),
                    language = if (language == GlossLanguage.ENGLISH) "English"
                    else "Indonesian",
                )
            }.getOrNull() ?: break

            dao.putComposition(
                row.copy(
                    source = "deepseek",
                    correct = marked.correct,
                    corrected = marked.corrected,
                    note = marked.note,
                )
            )
            done++
        }
        done
    }

    suspend fun pendingCount(): Int =
        withContext(Dispatchers.IO) { dao.unmarkedCompositions().size }

    /**
     * What is going wrong, read back out of everything already recorded.
     *
     * Nothing here is new data — the schedules, the lapses and the slips have
     * been accumulating since the first review. This is the first thing that
     * asks them a question.
     */
    suspend fun diagnose(): Diagnosis = withContext(Dispatchers.IO) {
        val skills = dao.skillSummary().map {
            SkillGap(
                skill = skillFromKey(it.skill),
                words = it.words,
                meanBox = it.meanBox,
                lapses = it.lapses,
            )
        }.sortedBy { it.meanBox }

        val trouble = dao.troubleWords(12)
        val bundles = content.bundles(trouble.map { it.wordId }.filter { it > 0 })
            .associateBy { it.id }
        val words = trouble.mapNotNull { row ->
            val word = bundles[row.wordId] ?: return@mapNotNull null
            val slips = dao.slipsFor(row.wordId)
            TroubleWord(
                wordId = row.wordId,
                hanzi = word.hanzi,
                pinyin = word.pinyin,
                gloss = word.primaryGloss.orEmpty(),
                lapses = row.lapses,
                // What was actually answered, commonest first.
                answeredInstead = slips.mapNotNull { it.chose }
                    .filter { it != word.hanzi && it != word.primaryGloss }
                    .groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(3).map { it.key },
                confusableWith = word.confusables.map { it.hanzi }.take(3),
                skill = slips.firstOrNull()?.let { skillFromKey(it.skill) },
            )
        }

        Diagnosis(
            skills = skills,
            words = words,
            characters = troubleCharacters(),
            reviewsCounted = dao.reviewTotal(),
        )
    }

    /**
     * Characters whose words are lost more often than the rest of the deck's.
     *
     * Measured as a lift over the learner's own average rather than an absolute
     * rate: someone who loses a third of everything would otherwise be told
     * every character is a problem. A character must appear in at least three
     * reviewed words before it can show a pattern at all, and be meaningfully
     * worse than average before it is worth naming — a diagnosis that finds
     * something every week stops being read.
     */
    private suspend fun troubleCharacters(): List<TroubleCharacter> {
        val trouble = dao.wordTrouble().filter { it.reviews > 0 }
        // Under twenty reviewed words there is no "average" worth comparing to.
        if (trouble.size < 20) return emptyList()

        val rate = trouble.associate { it.wordId to it.lapses.toDouble() / it.reviews }
        val found = CharacterLift.find(rate, content.wordCharacters())
        if (found.isEmpty()) return emptyList()

        val names = content.bundles(rate.keys.filter { it > 0 }).associate { it.id to it.hanzi }
        return found.mapNotNull { hit ->
            val card = content.character(hit.hanzi) ?: return@mapNotNull null
            // A character the deck does not teach on its own explains nothing,
            // and grammar explains nothing either: every word contains 不.
            if (card.gloss.isNullOrBlank() || card.isFunction) return@mapNotNull null
            TroubleCharacter(
                hanzi = hit.hanzi,
                pinyin = card.pinyin,
                gloss = card.gloss,
                lift = hit.lift,
                words = hit.wordIds.sortedByDescending { rate.getValue(it) }
                    .mapNotNull { names[it] }.take(5),
            )
        }.take(5)
    }

    /**
     * The words a passage should be built around: the ones she keeps losing.
     *
     * This is the thing ten fixed passages cannot do. The reading section knows
     * nothing about how the reviews are going; the schedule knows exactly, and
     * has been recording it all along.
     */
    suspend fun weakWords(count: Int = 8): List<WordBundle> =
        withContext(Dispatchers.IO) {
            val ids = dao.weakestWords(count).map { it.wordId }
            if (ids.isEmpty()) emptyList() else content.bundles(ids.filter { it > 0 })
        }

    /**
     * Ask for a passage about them, check it, and keep it.
     *
     * Everything the model returns is treated as a claim. The words it was told
     * to use have to actually be there; the segmentation it supplies has to
     * reassemble into the line it belongs to, or the words in the passage could
     * not be tapped; and anything in it that is neither in the deck nor in
     * HSK 1-3 is counted and shown on the card rather than quietly accepted.
     */
    suspend fun writePassage(
        apiKey: String,
        language: GlossLanguage,
        count: Int = 8,
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No DeepSeek key — add one in Settings.")
            )
        }
        val targets = weakWords(count)
        if (targets.size < 3) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Not enough struggling words yet. Review for a few days and " +
                        "there will be something worth writing about."
                )
            )
        }

        runCatching {
            val draft = DeepSeek(apiKey).writePassage(
                words = targets.map { it.hanzi },
                vocabulary = content.bundles(content.allWordIds().shuffled().take(150))
                    .map { it.hanzi },
                language = if (language == GlossLanguage.ENGLISH) "English"
                else "Indonesian",
            )

            val checked = PassageCheck.check(
                lines = draft.lines.map {
                    PassageCheck.Line(it.zh, it.pinyin, it.gloss, it.words)
                },
                targets = targets.map { it.hanzi },
                known = content.headwords.keys + content.assumedKnown,
            )
            val lines = checked.lines.mapIndexed { i, line ->
                MadeLine(
                    passageId = 0,
                    idx = i,
                    zh = line.zh,
                    pinyin = line.pinyin,
                    gloss = line.gloss,
                    segments = line.pieces.joinToString("\u0000"),
                )
            }

            val id = dao.putMadePassage(
                MadePassage(
                    title = draft.title,
                    titleGloss = draft.titleGloss,
                    targets = targets.joinToString("、") { it.hanzi },
                    unknownCount = checked.unknownWords,
                    madeAt = System.currentTimeMillis(),
                )
            )
            dao.putMadeLines(lines.map { it.copy(passageId = id) })
            dao.putMadeQuestions(
                draft.questions.map {
                    MadeQuestion(
                        passageId = id,
                        q = it.q,
                        choicesJson = org.json.JSONArray(it.choices).toString(),
                        answer = it.answer.coerceIn(0, maxOf(0, it.choices.size - 1)),
                    )
                }
            )
            id
        }
    }

    /**
     * The passages written for her, shaped exactly like the shipped ones.
     *
     * Same [Passage] type on purpose: the reader, the questions and the tappable
     * words all work without knowing where a passage came from.
     */
    suspend fun madePassages(): List<Passage> = madeOfKind("passage")

    private suspend fun madeOfKind(kind: String): List<Passage> = withContext(Dispatchers.IO) {
        dao.madePassages(kind).map { made ->
            val lines = dao.madeLines(made.id)
            Passage(
                id = -made.id,     // negative, so it cannot collide with a shipped one
                title = made.title,
                titleId = made.titleGloss,
                level = 0,
                lines = lines.map { line ->
                    PassageLine(
                        zh = line.zh,
                        pinyin = line.pinyin,
                        gloss = line.gloss,
                        pieces = line.segments.split("\u0000")
                            .filter { it.isNotBlank() }
                            .map { PassagePiece(it, content.headwords[it]) },
                    )
                },
                questions = dao.madeQuestions(made.id).map { q ->
                    val raw = org.json.JSONArray(q.choicesJson)
                    PassageQuestion(
                        q = q.q,
                        choices = (0 until raw.length()).map { raw.getString(it) },
                        answer = q.answer,
                        explain = null,
                    )
                },
                practises = made.targets.split("、").filter { it.isNotBlank() },
                tags = listOf(if (kind == SONG) "song" else "written for you"),
                charCount = lines.sumOf { l -> l.zh.count { it.code in 0x4E00..0x9FFF } },
                deckWords = made.targets.split("、").count { it.isNotBlank() },
                unknownWords = made.unknownCount,
            )
        }
    }

    /**
     * Annotate lyrics the learner pasted, and keep them.
     *
     * The text is hers: it arrives by paste and is stored on her device only.
     * Nothing here fetches a song, and the model is asked to read what it is
     * given rather than to supply anything.
     *
     * Capped at [MAX_SONG_LINES] because one request has to hold the whole
     * thing — annotating half a song in one call and half in another loses the
     * context that makes the second half make sense.
     */
    suspend fun addSong(
        title: String,
        lyrics: String,
        apiKey: String,
        language: GlossLanguage,
    ): Result<Long> = withContext(Dispatchers.IO) {
        val lines = lyrics.lines().map { it.trim() }.filter { it.isNotEmpty() }
        when {
            title.isBlank() ->
                return@withContext Result.failure(IllegalStateException("Give it a title."))
            lines.isEmpty() ->
                return@withContext Result.failure(IllegalStateException("Paste the lines first."))
            lines.size > MAX_SONG_LINES ->
                return@withContext Result.failure(
                    IllegalStateException(
                        "That is ${lines.size} lines. Add it in parts of " +
                            "$MAX_SONG_LINES or fewer."
                    )
                )
            apiKey.isBlank() ->
                return@withContext Result.failure(
                    IllegalStateException("No DeepSeek key — add one in Settings.")
                )
        }

        runCatching {
            val annotated = DeepSeek(apiKey).annotate(
                lines = lines,
                language = if (language == GlossLanguage.ENGLISH) "English"
                else "Indonesian",
            )
            if (annotated.size != lines.size) {
                error(
                    "The reading came back for ${annotated.size} of ${lines.size} " +
                        "lines. Try again."
                )
            }

            val checked = PassageCheck.check(
                lines = annotated.map {
                    PassageCheck.Line(it.zh, it.pinyin, it.gloss, it.words)
                },
                // A song is not written to practise anything, so there is
                // nothing it can have left out.
                targets = emptyList(),
                known = content.headwords.keys + content.assumedKnown,
            )

            val id = dao.putMadePassage(
                MadePassage(
                    title = title.trim(),
                    titleGloss = "",
                    kind = SONG,
                    // Which of her own words turn up in it — the same figure the
                    // shipped passages carry, and the reason a song is worth
                    // more than a nice tune.
                    targets = checked.lines
                        .flatMap { it.pieces }
                        .filter { content.headwords.containsKey(it) }
                        .distinct()
                        .joinToString("、"),
                    unknownCount = checked.unknownWords,
                    madeAt = System.currentTimeMillis(),
                )
            )
            dao.putMadeLines(
                checked.lines.mapIndexed { i, line ->
                    MadeLine(
                        passageId = id,
                        idx = i,
                        zh = line.zh,
                        pinyin = line.pinyin,
                        gloss = line.gloss,
                        segments = line.pieces.joinToString("\u0000"),
                    )
                }
            )
            id
        }
    }

    suspend fun songs(): List<Passage> = madeOfKind(SONG)

    suspend fun deletePassage(id: Long) =
        withContext(Dispatchers.IO) { dao.deleteMadePassage(-id) }

    /** Everything she has written, newest first. */
    suspend fun compositions(limit: Int = 200): List<Composition> =
        withContext(Dispatchers.IO) { dao.compositions(limit) }

    suspend fun compositionCount(): Int =
        withContext(Dispatchers.IO) { dao.compositionCount() }

    /**
     * Every character the deck or its examples contain.
     *
     * Used only to point out characters that appear in neither, which is nearly
     * always a typo. Cached: it is a scan of the whole deck and it never
     * changes between releases.
     */
    private val knownChars: Set<Char> by lazy { content.allCharacters() }

    private fun knownCharacters(): Set<Char> = knownChars

    /** A character card, with every word of yours that is built from it. */
    suspend fun character(hanzi: String): Pair<CharacterCard, List<Triple<Long, String, String?>>>? =
        withContext(Dispatchers.IO) {
            val card = content.character(hanzi) ?: return@withContext null
            card to content.wordsWith(hanzi)
        }

    suspend fun userWords(): List<WordBundle> = withContext(Dispatchers.IO) {
        dao.userWords().map { it.toBundle() }
    }

    suspend fun userWordCount(): Int = withContext(Dispatchers.IO) { dao.userWordCount() }

    suspend fun hasWord(hanzi: String): Boolean = withContext(Dispatchers.IO) {
        dao.userWord(hanzi) != null || content.search(hanzi, 1).isNotEmpty()
    }

    suspend fun addUserWord(word: UserWord): Long = withContext(Dispatchers.IO) {
        dao.addUserWord(word)
    }

    suspend fun deleteUserWord(id: Long) = withContext(Dispatchers.IO) { dao.deleteUserWord(id) }

    suspend fun deckStats(): DeckStats = withContext(Dispatchers.IO) {
        val base = content.deckStats()
        val added = dao.userWords()
        base.copy(
            words = base.words + added.size,
            withGloss = base.withGloss + added.count { it.glossId != null || it.glossEn != null },
            pinyinVerified = base.pinyinVerified + added.count { it.pinyinVerified },
            withExample = base.withExample + added.count { it.exampleZh != null },
        )
    }

    suspend fun dueCount(): Int = withContext(Dispatchers.IO) { dao.dueCount(today()) }

    suspend fun dueBySkill(): Map<Skill, Int> = withContext(Dispatchers.IO) {
        dao.dueBySkill(today()).associate { skillFromKey(it.skill) to it.n }
    }

    suspend fun totalXp(): Int = withContext(Dispatchers.IO) { dao.totalXp() }

    /**
     * The number of consecutive days up to today with at least one review.
     * Counted backwards from today so a gap ends it immediately.
     */
    suspend fun streak(): Int = withContext(Dispatchers.IO) {
        val days = dao.recentDays(400).filter { it.reviews > 0 }.map { it.date }.toSet()
        if (days.isEmpty()) return@withContext 0
        var day = today()
        // Today not being studied yet should not break yesterday's streak.
        if (day !in days) day -= 1
        var count = 0
        while (day in days) {
            count++
            day -= 1
        }
        count
    }

    /**
     * How many words could currently produce each kind of question.
     *
     * The SQL knows what the deck holds; only the app knows whether this phone
     * can speak Chinese. Without the second half the Quiz screen offered a
     * thousand listening questions on a device with no voice installed, and
     * every one of them generated nothing.
     */
    suspend fun quizAvailability(): Map<String, Int> = withContext(Dispatchers.IO) {
        val counts = content.questionAvailability()
        if (canSpeak) return@withContext counts
        val silent = Registry.all
            .filter { Requirement.AUDIO in it.requires }
            .map { it.id }
            .toSet()
        counts.mapValues { (id, n) -> if (id in silent) 0 else n }
    }

    suspend fun buildSession(
        size: Int = 20,
        onlyType: String? = null,
    ): List<SessionItem> = withContext(Dispatchers.IO) {
        distractorCache.clear()
        rotations = dao.reviewCounts().associate { it.wordId to it.n }

        val dueRows = dao.due(today(), size * 2)
        val due = dueRows.map {
            SessionBuilder.Due(it.wordId, skillFromKey(it.skill), it.box, it.dueAt)
        }

        val added = dao.userWords().map { it.toBundle() }
        val addedById = added.associateBy { it.id }

        val seen = dao.seenWordIds().toSet()
        val unseen = (content.allWordIds() + added.map { it.id })
            .filter { it !in seen }
            .shuffled()

        val needed = if (onlyType != null) {
            // Only words this question type can actually use, and only a few
            // times as many as the quiz is long. Loading the whole deck and
            // discarding what did not fit cost about twelve seconds.
            (due.map { it.wordId } +
                content.candidatesFor(onlyType, size * 4) +
                added.map { it.id }).distinct()
        } else {
            (due.map { it.wordId } + unseen.take(SessionBuilder.MAX_NEW_PER_SESSION * 3))
                .distinct()
        }
        val bundles = (content.bundles(needed.filter { it > 0 }) +
            needed.mapNotNull { addedById[it] }).associateBy { it.id }

        SessionBuilder.build(size, due, unseen, bundles, deckContext, Random.Default, onlyType)
    }

    /**
     * Record one answer: advance that word's schedule for that skill, award
     * points, and roll the day's totals.
     *
     * The order matters only in that the schedule is written first — if anything
     * fails after it, the learner loses points rather than a review.
     */
    suspend fun answer(
        item: SessionItem,
        grade: Grade,
        combo: Int,
        /** What was actually answered, when the exercise has an answer to keep. */
        chose: String? = null,
        expected: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        val wordId = item.exercise.word.id
        val skillKey = item.skill.key()

        val existing = dao.mastery(wordId, skillKey)
        val state = Scheduler.State(
            box = existing?.box ?: 0,
            streak = existing?.streak ?: 0,
            lapses = existing?.lapses ?: 0,
        )
        val (next, days) = Scheduler.review(state, grade)

        dao.put(
            Mastery(
                wordId = wordId,
                skill = skillKey,
                box = next.box,
                streak = next.streak,
                lapses = next.lapses,
                dueAt = today() + days,
                lastGrade = grade.name,
                reviewedAt = System.currentTimeMillis(),
            )
        )

        // Only failures are kept. A log of every correct answer would grow
        // for ever and never be read; what the diagnosis needs is the wrong one.
        if (grade == Grade.AGAIN) {
            dao.record(
                Slip(
                    ts = System.currentTimeMillis(),
                    wordId = wordId,
                    skill = skillKey,
                    typeId = item.exercise.typeId,
                    chose = chose,
                    expected = expected,
                )
            )
        }

        val points = Xp.award(grade, item.skill, combo)
        dao.record(
            XpEvent(
                ts = System.currentTimeMillis(),
                wordId = wordId,
                typeId = item.exercise.typeId,
                skill = skillKey,
                xp = points,
                combo = combo,
            )
        )

        val day = today()
        val current = dao.daily(day) ?: Daily(date = day)
        dao.putDaily(
            current.copy(
                xp = current.xp + points,
                reviews = current.reviews + 1,
                correct = current.correct + if (grade == Grade.AGAIN) 0 else 1,
            )
        )

        points
    }

    suspend fun passages(): List<Passage> = withContext(Dispatchers.IO) { content.passages() }

    suspend fun grammarPatterns(): List<GrammarPattern> =
        withContext(Dispatchers.IO) { content.grammarPatterns() }

    // ---- the swipe deck ---------------------------------------------------

    /**
     * The order of one slice of the deck, as ids and states only.
     *
     * Deliberately does not load the words. Loading all 1,173 bundles with their
     * glosses, examples and five thousand relations took eleven seconds to first
     * frame, and the screen only ever shows one card at a time. The ids are
     * cheap; [cardWindow] fetches the handful actually about to be seen.
     *
     * A word never swiped has no state row and counts as due, which is what
     * makes a fresh install open with the whole deck rather than nothing.
     */
    suspend fun cardOrder(filter: CardFilter): List<CardState> =
        withContext(Dispatchers.IO) {
            val states = dao.allCardStates().associateBy { it.wordId }
            val today = today()

            val ids = content.allWordIds() + dao.userWords().map { -it.id }
            val paired = ids.map { states[it] ?: CardState(wordId = it) }

            paired.filter { state ->
                when (filter) {
                    CardFilter.DUE -> state.state != CardState.STATE_RETIRED &&
                        state.dueAt <= today
                    CardFilter.ALL -> state.state != CardState.STATE_RETIRED
                    CardFilter.STRUGGLING -> state.state == CardState.STATE_STRUGGLING
                    CardFilter.LEARNING -> state.state == CardState.STATE_LEARNING
                    CardFilter.KNOWN -> state.state == CardState.STATE_KNOWN
                    CardFilter.RETIRED -> state.state == CardState.STATE_RETIRED
                }
            }.sortedWith(compareBy({ it.dueAt }, { it.box }, { it.wordId }))
        }

    /**
     * The character deck, filtered and ordered exactly as the word deck is.
     *
     * Commonest character first within a due day, so a session spent on
     * characters is spent on the ones that unlock the most words.
     */
    suspend fun characterOrder(filter: CardFilter): List<CharacterState> =
        withContext(Dispatchers.IO) {
            val states = dao.allCharacterStates().associateBy { it.hanzi }
            val today = today()
            val rank = content.teachableCharacters()
                .withIndex()
                .associate { (i, c) -> c.hanzi to i }

            rank.keys
                .map { states[it] ?: CharacterState(hanzi = it) }
                .filter { state ->
                    when (filter) {
                        CardFilter.DUE -> state.state != CardState.STATE_RETIRED &&
                            state.dueAt <= today
                        CardFilter.ALL -> state.state != CardState.STATE_RETIRED
                        CardFilter.STRUGGLING -> state.state == CardState.STATE_STRUGGLING
                        CardFilter.LEARNING -> state.state == CardState.STATE_LEARNING
                        CardFilter.KNOWN -> state.state == CardState.STATE_KNOWN
                        CardFilter.RETIRED -> state.state == CardState.STATE_RETIRED
                    }
                }
                .sortedWith(
                    compareBy({ it.dueAt }, { it.box }, { rank[it.hanzi] ?: 0 })
                )
        }

    suspend fun characterWindow(hanzi: List<String>): Map<String, CharacterCard> =
        withContext(Dispatchers.IO) {
            if (hanzi.isEmpty()) emptyMap()
            else content.teachableCharacters()
                .filter { it.hanzi in hanzi }
                .associateBy { it.hanzi }
        }

    suspend fun swipeCharacter(
        current: CharacterState,
        swipe: CardDeck.Swipe,
    ): CharacterState = withContext(Dispatchers.IO) {
        CardDeck.apply(current, swipe, today()).also { dao.putCharacterState(it) }
    }

    suspend fun restoreCharacterState(state: CharacterState) =
        withContext(Dispatchers.IO) { dao.putCharacterState(state) }

    suspend fun reviveCharacter(hanzi: String): CharacterState? =
        withContext(Dispatchers.IO) {
            val current = dao.characterState(hanzi) ?: return@withContext null
            CardDeck.revive(current, today()).also { dao.putCharacterState(it) }
        }

    suspend fun characterCounts(): Map<CardFilter, Int> = withContext(Dispatchers.IO) {
        val counts = dao.characterStateCounts().associate { it.state to it.n }
        val total = content.teachableCharacters().size
        val touched = counts.values.sum()
        mapOf(
            // Characters that have never been swiped are still learning, the
            // same convention the word deck uses.
            CardFilter.STRUGGLING to (counts[CardState.STATE_STRUGGLING] ?: 0),
            CardFilter.LEARNING to
                (counts[CardState.STATE_LEARNING] ?: 0) + (total - touched),
            CardFilter.KNOWN to (counts[CardState.STATE_KNOWN] ?: 0),
            CardFilter.RETIRED to (counts[CardState.STATE_RETIRED] ?: 0),
        )
    }

    /** The words for a small window of that order — what is on screen and next. */
    suspend fun cardWindow(ids: List<Long>): Map<Long, WordBundle> =
        withContext(Dispatchers.IO) {
            val fromDeck = content.bundles(ids.filter { it > 0 })
            val fromUser = dao.userWords()
                .map { it.toBundle() }
                .filter { it.id in ids }
            (fromDeck + fromUser).associateBy { it.id }
        }

    suspend fun swipe(wordId: Long, swipe: CardDeck.Swipe): CardState =
        withContext(Dispatchers.IO) {
            val current = dao.cardState(wordId) ?: CardState(wordId = wordId)
            val next = CardDeck.apply(current, swipe, today())
            dao.putCardState(next)
            next
        }

    /** Put a card back the way it was, for undo. */
    suspend fun restoreCardState(state: CardState) = withContext(Dispatchers.IO) {
        dao.putCardState(state)
    }

    suspend fun revive(wordId: Long): CardState = withContext(Dispatchers.IO) {
        val current = dao.cardState(wordId) ?: CardState(wordId = wordId)
        val next = CardDeck.revive(current, today())
        dao.putCardState(next)
        next
    }

    suspend fun cardCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        val counts = dao.cardStateCounts().associate { it.state to it.n }.toMutableMap()
        // Words never swiped have no row; they are learning by default, and
        // leaving them out would make the tallies disagree with the deck size.
        val total = content.allWordIds().size + dao.userWordCount()
        val tracked = counts.values.sum()
        counts[CardState.STATE_LEARNING] =
            (counts[CardState.STATE_LEARNING] ?: 0) + (total - tracked)
        counts["due"] = dao.cardsDue(today()) + (total - tracked)
        counts
    }

    suspend fun word(id: Long): WordBundle? = withContext(Dispatchers.IO) {
        if (id < 0) dao.userWords().firstOrNull { -it.id == id }?.toBundle()
        else content.bundle(id)
    }

    suspend fun masteryFor(wordId: Long): Map<Skill, Mastery> =
        withContext(Dispatchers.IO) {
            dao.masteryFor(wordId).associateBy { skillFromKey(it.skill) }
        }

    suspend fun search(query: String): List<WordBundle> = withContext(Dispatchers.IO) {
        val added = dao.userWords().map { it.toBundle() }.filter {
            query.isBlank() || it.hanzi.contains(query, true) ||
                it.pinyin.contains(query, true) ||
                it.glosses.any { g -> g.contains(query, true) }
        }
        val ids = if (query.isBlank()) content.allWordIds().take(200) else content.search(query, 200)
        // Words you added come first: they are the ones you are actively adding
        // and the ones you are most likely looking for.
        added + content.bundles(ids).sortedBy { it.hanzi.length }
    }

    /** Every word in the deck, for the swipeable flashcard browser. */
    suspend fun allWords(limit: Int = 2000): List<WordBundle> = withContext(Dispatchers.IO) {
        dao.userWords().map { it.toBundle() } +
            content.bundles(content.allWordIds().take(limit))
    }

    companion object {
        @Volatile private var instance: Repo? = null

        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(
                ContentDb.open(context.applicationContext),
                ProgressDb.get(context.applicationContext),
            ).also { instance = it }
        }
    }
}
