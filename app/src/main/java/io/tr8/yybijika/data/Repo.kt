package io.tr8.yybijika.data

import android.content.Context
import io.tr8.yybijika.exercise.DeckContext
import io.tr8.yybijika.exercise.ExampleSentence
import io.tr8.yybijika.exercise.WordBundle
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
    private val distractorCache = mutableMapOf<Long, List<WordBundle>>()

    fun setTtsAvailable(available: Boolean) {
        ttsReady = available
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    val deckContext: DeckContext = object : DeckContext {
        override val ttsAvailable: Boolean get() = ttsReady

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
                )
            }
        ),
        tags = listOf("added"),
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

    suspend fun buildSession(size: Int = 20): List<SessionItem> = withContext(Dispatchers.IO) {
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

        val needed = (due.map { it.wordId } + unseen.take(SessionBuilder.MAX_NEW_PER_SESSION * 3))
            .distinct()
        val bundles = (content.bundles(needed.filter { it > 0 }) +
            needed.mapNotNull { addedById[it] }).associateBy { it.id }

        SessionBuilder.build(size, due, unseen, bundles, deckContext, Random.Default)
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
