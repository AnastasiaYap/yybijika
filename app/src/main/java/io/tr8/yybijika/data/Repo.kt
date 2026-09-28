package io.tr8.yybijika.data

import android.content.Context
import io.tr8.yybijika.exercise.DeckContext
import io.tr8.yybijika.exercise.ExampleSentence
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
class Repo(
    private val content: ContentDb,
    private val progress: ProgressDb,
    private var ttsReady: Boolean = false,
) {

    private val dao = progress.dao()

    /** Distractor pools are reused across a session; rebuilding them per question
     *  would mean a random query per card for no gain in variety. */
    private val distractorCache = java.util.concurrent.ConcurrentHashMap<Long, List<WordBundle>>()

    fun setTtsAvailable(available: Boolean) {
        ttsReady = available
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    val deckContext: DeckContext = object : DeckContext {
        override val ttsAvailable: Boolean get() = ttsReady

        // Read once and held: 44 rows that never change between releases, and
        // every measure-word question asks for the same list.
        override val measureWords: List<String> by lazy { content.measureWords() }

        override fun distractorGlosses(word: WordBundle, count: Int): List<String> =
            pool(word)
                .mapNotNull { it.primaryGloss }
                .filter { it != word.primaryGloss }
                .distinct()
                .take(count)

        override fun distractorHanzi(word: WordBundle, count: Int): List<String> =
            pool(word)
                .map { it.hanzi }
                .filter { it != word.hanzi }
                .distinct()
                .take(count)

        override fun shuffleSeed(word: WordBundle, typeId: String): Long =
            word.id * 31 + typeId.hashCode()
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
        if (ttsReady) return@withContext counts
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
