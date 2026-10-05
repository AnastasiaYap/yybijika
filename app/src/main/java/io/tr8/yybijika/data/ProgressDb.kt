package io.tr8.yybijika.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Upsert
import io.tr8.yybijika.learn.Skill

/**
 * Everything the learner has earned.
 *
 * Deliberately a separate database from the deck. content.db is replaced on
 * every release; this file is never touched by one, so new vocabulary can ship
 * without resetting a single interval. It also means a word id is the only thing
 * the two share — and [Mastery] rows for words that later disappear from the
 * deck are harmless orphans rather than foreign key violations.
 */
@Entity(tableName = "mastery", primaryKeys = ["wordId", "skill"])
data class Mastery(
    val wordId: Long,
    val skill: String,
    val box: Int = 0,
    val streak: Int = 0,
    val lapses: Int = 0,
    @ColumnInfo(name = "due_at") val dueAt: Long = 0,   // epoch day
    @ColumnInfo(name = "last_grade") val lastGrade: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long = 0,
)

@Entity(tableName = "xp_event")
data class XpEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val wordId: Long,
    val typeId: String,
    val skill: String,
    val xp: Int,
    val combo: Int,
)

/**
 * A word you added yourself, by pasting notes into the app.
 *
 * Lives here rather than in content.db for the same reason mastery does: the
 * deck file is replaced wholesale by every update, and words you added after
 * the last release would vanish with it.
 *
 * Ids are negative so they can never collide with a content.db id, which means
 * mastery, XP and every exercise treat a pasted word exactly like a shipped one.
 */
/**
 * One wrong answer, and what was chosen instead.
 *
 * mastery counts lapses and xp_event records that a point was not scored, but
 * neither says *what went wrong* — and "you lose 索赔 four times in five" is a
 * far weaker sentence than "and three of those you answered 赔偿". The second
 * one names the confusion, which is the thing that can actually be fixed.
 *
 * Only wrong answers are stored. A log of every correct one would grow without
 * ever being read, and the diagnosis is about failures.
 */
// The index is declared here as well as created in the migration. Room verifies
// the schema it finds against the one these annotations describe, and an index
// present in only one of the two is a mismatch — which fails at launch, on an
// existing database, where it is least welcome.
@Entity(
    tableName = "slip",
    indices = [Index(value = ["word_id"], name = "idx_slip_word")],
)
data class Slip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    @ColumnInfo(name = "word_id") val wordId: Long,
    val skill: String,
    @ColumnInfo(name = "type_id") val typeId: String,
    /** What was answered. Null when the exercise has no answer to capture. */
    val chose: String? = null,
    val expected: String? = null,
)

/**
 * A card the learner says is wrong.
 *
 * The deck is one person's notes, typed over months, and some of it is simply
 * mistaken — a gloss that does not fit, a reading that is off, an example that
 * uses the word a way nobody would. Until now there was nothing to do about it
 * mid-session except remember and hope: the answer stays wrong, and being
 * marked against it teaches the error.
 *
 * Lives here rather than in content.db because content.db is read-only and
 * replaced by every release, and the whole point is that a flag survives until
 * the note behind it is actually fixed.
 */
@Entity(
    tableName = "card_flag",
    indices = [Index(value = ["word_id"], name = "idx_flag_word")],
)
data class CardFlag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id") val wordId: Long,
    /** Copied rather than joined: the word may be gone by the time this is read. */
    val hanzi: String,
    val pinyin: String,
    /** One of [io.tr8.yybijika.learn.FlagReason]. */
    val reason: String,
    @ColumnInfo(name = "type_id") val typeId: String?,
    /** What the card actually said, so the report is readable months later. */
    val shown: String?,
    val note: String? = null,
    @ColumnInfo(name = "flagged_at") val flaggedAt: Long,
    /** Set when the note behind it has been corrected; null while still open. */
    @ColumnInfo(name = "fixed_at") val fixedAt: Long? = null,
)

@Entity(tableName = "user_word")
data class UserWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hanzi: String,
    val pinyin: String,
    @ColumnInfo(name = "pinyin_verified") val pinyinVerified: Boolean = false,
    @ColumnInfo(name = "gloss_id") val glossId: String?,
    @ColumnInfo(name = "gloss_en") val glossEn: String?,
    @ColumnInfo(name = "example_zh") val exampleZh: String? = null,
    @ColumnInfo(name = "example_pinyin") val examplePinyin: String? = null,
    @ColumnInfo(name = "example_gloss") val exampleGloss: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    /** Where the content came from: "notes" for what you typed, "deepseek" for filled blanks. */
    val source: String = "notes",
)

/**
 * How well you know a word, as judged by hand on the Cards screen.
 *
 * Deliberately separate from [Mastery], which tracks four skills per word and is
 * driven by graded answers. This is the coarser judgement you make while leafing
 * through the deck — "yes", "no", "done with this one" — and it needs its own
 * schedule because the two are answering different questions. A word you can
 * read on sight but cannot hear should be retired here and still due in Review.
 */
@Entity(tableName = "card_state")
data class CardState(
    @PrimaryKey val wordId: Long,
    /** learning | struggling | known | retired */
    val state: String = STATE_LEARNING,
    val box: Int = 0,
    @ColumnInfo(name = "due_at") val dueAt: Long = 0,      // epoch day
    @ColumnInfo(name = "seen_count") val seenCount: Int = 0,
    @ColumnInfo(name = "touched_at") val touchedAt: Long = 0,
) {
    companion object {
        const val STATE_LEARNING = "learning"
        const val STATE_STRUGGLING = "struggling"
        const val STATE_KNOWN = "known"
        const val STATE_RETIRED = "retired"
    }
}

/**
 * The same grooming state, for a character.
 *
 * A separate table rather than a shared one keyed by a string, because a word id
 * and a character are different things and a single column holding either would
 * be a join waiting to go wrong. The ladder they climb is the same, and lives in
 * one place: [io.tr8.yybijika.learn.CardDeck.step].
 */
@Entity(tableName = "character_state")
data class CharacterState(
    @PrimaryKey val hanzi: String,
    /** learning | struggling | known | retired */
    val state: String = "learning",
    val box: Int = 0,
    @ColumnInfo(name = "due_at") val dueAt: Long = 0,      // epoch day
    @ColumnInfo(name = "seen_count") val seenCount: Int = 0,
    @ColumnInfo(name = "touched_at") val touchedAt: Long = 0,
)

/**
 * A sentence the learner wrote, and what came back.
 *
 * Kept rather than discarded after marking, for two reasons. The obvious one is
 * that a year of your own sentences is the only record in the app of what you
 * can actually say. The other is that when there is no network and no key, the
 * app can still take the sentence, run the checks it can, and file it — writing
 * it was most of the value, and the marking can wait.
 */
@Entity(tableName = "composition")
data class Composition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id") val wordId: Long,
    val hanzi: String,
    val prompt: String,
    val text: String,
    /** 'offline' when only the mechanical checks ran, 'deepseek' when marked. */
    val source: String = "offline",
    /** Null when it was never marked — the pending pile. */
    val correct: Boolean? = null,
    val corrected: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "written_at") val writtenAt: Long = 0,
)

/**
 * A passage written for her, from the words she keeps failing.
 *
 * In progress.db rather than content.db, and that is the whole reason this is a
 * separate set of tables rather than rows in the shipped ones: content.db is
 * replaced wholesale on every release, and a story written about your own weak
 * words is not something an app update gets to throw away.
 */
@Entity(tableName = "made_passage")
data class MadePassage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "title_gloss") val titleGloss: String,
    /**
     * 'passage' when the app wrote it, 'song' when the words are hers.
     *
     * The same tables because a song and a passage are the same thing to
     * everything downstream — lines of Chinese with a reading, a meaning and
     * tappable words. Only where the text came from differs.
     */
    val kind: String = "passage",
    /** The words it was asked to practise, comma separated, for the card. */
    val targets: String,
    /** Words in it that are in neither the deck nor HSK 1-3 — stated, not hidden. */
    @ColumnInfo(name = "unknown_count") val unknownCount: Int = 0,
    @ColumnInfo(name = "made_at") val madeAt: Long = 0,
)

@Entity(tableName = "made_line")
data class MadeLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "passage_id") val passageId: Long,
    val idx: Int,
    val zh: String,
    val pinyin: String,
    val gloss: String,
    /** NUL-separated, as in the shipped passages, so the reader is unchanged. */
    val segments: String,
)

@Entity(tableName = "made_question")
data class MadeQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "passage_id") val passageId: Long,
    val q: String,
    @ColumnInfo(name = "choices_json") val choicesJson: String,
    val answer: Int,
)

@Entity(tableName = "daily")
data class Daily(
    @PrimaryKey val date: Long,        // epoch day
    val xp: Int = 0,
    val reviews: Int = 0,
    val correct: Int = 0,
)

@Dao
interface ProgressDao {

    /**
     * How often each word has been reviewed, across every skill.
     *
     * One query for the whole deck rather than one per word: it is read while
     * a session is being built, and a query per card would be 20 round trips
     * before the first question appears.
     */
    @Query("SELECT wordId, SUM(box + lapses) AS n FROM mastery GROUP BY wordId")
    suspend fun reviewCounts(): List<WordCount>

    @Insert
    suspend fun record(slip: Slip)

    /** Every wrong answer for a word, most recent first. */
    @Query("SELECT * FROM slip WHERE word_id = :wordId ORDER BY ts DESC LIMIT :limit")
    suspend fun slipsFor(wordId: Long, limit: Int = 12): List<Slip>

    @Insert
    suspend fun flag(flag: CardFlag)

    /** Everything still waiting to be fixed, newest first. */
    @Query("SELECT * FROM card_flag WHERE fixed_at IS NULL ORDER BY flagged_at DESC")
    suspend fun openFlags(): List<CardFlag>

    @Query("SELECT COUNT(*) FROM card_flag WHERE fixed_at IS NULL")
    suspend fun openFlagCount(): Int

    @Query("UPDATE card_flag SET fixed_at = :ts WHERE id = :id")
    suspend fun markFlagFixed(id: Long, ts: Long)

    @Query("DELETE FROM card_flag WHERE id = :id")
    suspend fun dropFlag(id: Long)

    /** Words with an open flag, so a doubted card can be kept out of a session. */
    @Query("SELECT DISTINCT word_id FROM card_flag WHERE fixed_at IS NULL")
    suspend fun flaggedWordIds(): List<Long>

    /**
     * How each skill is going: how many words have been met in it, how far
     * along they are on average, and how often they are lost.
     *
     * The comparison is the point. Five schedules per word exist so a listening
     * weakness cannot hide behind a reading strength, and until now nothing has
     * ever put the two side by side.
     */
    /**
     * Roughly how many answers have been given.
     *
     * box + lapses per schedule: a word resting at box 5 was answered at least
     * five times to get there, and each lapse was an answer too. Counting
     * schedules instead would say 26 for someone who has answered two hundred
     * questions, which is the difference between "not enough to go on" and a
     * screenful of findings.
     */
    @Query("SELECT COALESCE(SUM(box + lapses), 0) FROM mastery")
    suspend fun reviewTotal(): Int

    /**
     * Words genuinely held: at the given box or beyond in at least one skill.
     *
     * Distinct on the word, not the schedule — knowing 合同 by sight and by ear
     * is one word learned twice over, not two words.
     */
    @Query("SELECT COUNT(DISTINCT wordId) FROM mastery WHERE box >= :box")
    suspend fun heldWords(box: Int): Int

    /** Whether any reviews were done on a particular day. */
    @Query("SELECT COUNT(*) > 0 FROM daily WHERE date = :day AND reviews > 0")
    suspend fun studiedOn(day: Long): Boolean

    /** The most recent day with any review at all; null if there has never been one. */
    @Query("SELECT MAX(date) FROM daily WHERE reviews > 0")
    suspend fun lastStudiedDay(): Long?

    @Query(
        """SELECT skill,
                  COUNT(*) AS words,
                  AVG(box) AS meanBox,
                  SUM(lapses) AS lapses
           FROM mastery GROUP BY skill"""
    )
    suspend fun skillSummary(): List<SkillRow>

    /** The words going wrong most, by how often they are lost. */
    @Query(
        """SELECT m.wordId AS wordId, SUM(m.lapses) AS lapses,
                  MIN(m.box) AS box, COUNT(*) AS skills
           FROM mastery m
           LEFT JOIN card_state c ON c.wordId = m.wordId
           WHERE (c.state IS NULL OR c.state != 'retired') AND m.lapses > 0
           GROUP BY m.wordId ORDER BY lapses DESC, box ASC LIMIT :limit"""
    )
    suspend fun troubleWords(limit: Int): List<TroubleRow>

    /** Lapses and reviews for every word that has been seen at all. */
    @Query("SELECT wordId, SUM(lapses) AS lapses, SUM(box + lapses) AS reviews " +
        "FROM mastery GROUP BY wordId")
    suspend fun wordTrouble(): List<WordTrouble>

    @Query("SELECT * FROM mastery WHERE wordId = :wordId")
    suspend fun masteryFor(wordId: Long): List<Mastery>

    @Query("SELECT * FROM mastery WHERE wordId IN (:wordIds)")
    suspend fun masteryForAll(wordIds: List<Long>): List<Mastery>

    @Query("SELECT * FROM mastery WHERE wordId = :wordId AND skill = :skill")
    suspend fun mastery(wordId: Long, skill: String): Mastery?

    /**
     * Cards that are due, most overdue first.
     *
     * Ordering by due date rather than by word means a listening card left for
     * three weeks outranks a recognition card that came due this morning, which
     * is what keeps the weaker skill from being quietly starved.
     */
    /**
     * What is due, minus anything the learner has reported as wrong.
     *
     * The exclusion lives in the SQL rather than in the caller because three
     * queries answer "what is due" — the session, the count on Home, and the
     * per-skill breakdown — and a count that promises a session the builder
     * will then refuse to produce is worse than either answer on its own.
     */
    @Query(
        """SELECT * FROM mastery
           WHERE due_at <= :today AND wordId NOT IN (SELECT word_id FROM card_flag WHERE fixed_at IS NULL)
           ORDER BY due_at ASC, box ASC
           LIMIT :limit"""
    )
    suspend fun due(today: Long, limit: Int): List<Mastery>

    @Query(
        """SELECT COUNT(*) FROM mastery
           WHERE due_at <= :today AND wordId NOT IN (SELECT word_id FROM card_flag WHERE fixed_at IS NULL)"""
    )
    suspend fun dueCount(today: Long): Int

    @Query(
        """SELECT skill, COUNT(*) AS n FROM mastery
           WHERE due_at <= :today AND wordId NOT IN (SELECT word_id FROM card_flag WHERE fixed_at IS NULL)
           GROUP BY skill"""
    )
    suspend fun dueBySkill(today: Long): List<SkillCount>

    @Query("SELECT DISTINCT wordId FROM mastery")
    suspend fun seenWordIds(): List<Long>

    @Upsert
    suspend fun put(mastery: Mastery)

    @Insert
    suspend fun record(event: XpEvent)

    @Query("SELECT COALESCE(SUM(xp), 0) FROM xp_event")
    suspend fun totalXp(): Int

    @Upsert
    suspend fun putDaily(daily: Daily)

    @Query("SELECT * FROM daily WHERE date = :date")
    suspend fun daily(date: Long): Daily?

    @Query("SELECT * FROM daily ORDER BY date DESC LIMIT :limit")
    suspend fun recentDays(limit: Int): List<Daily>

    @Query("SELECT * FROM user_word ORDER BY added_at DESC")
    suspend fun userWords(): List<UserWord>

    @Query("SELECT COUNT(*) FROM user_word")
    suspend fun userWordCount(): Int

    @Query("SELECT * FROM user_word WHERE hanzi = :hanzi LIMIT 1")
    suspend fun userWord(hanzi: String): UserWord?

    @Insert
    suspend fun addUserWord(word: UserWord): Long

    @Query("DELETE FROM user_word WHERE id = :id")
    suspend fun deleteUserWord(id: Long)

    // ---- card state --------------------------------------------------

    @Query("SELECT * FROM card_state")
    suspend fun allCardStates(): List<CardState>

    @Query("SELECT * FROM card_state WHERE wordId = :wordId")
    suspend fun cardState(wordId: Long): CardState?

    @Upsert
    suspend fun putCardState(state: CardState)

    @Query("SELECT state, COUNT(*) AS n FROM card_state GROUP BY state")
    suspend fun cardStateCounts(): List<StateCount>

    @Query("SELECT * FROM character_state")
    suspend fun allCharacterStates(): List<CharacterState>

    @Query("SELECT * FROM character_state WHERE hanzi = :hanzi")
    suspend fun characterState(hanzi: String): CharacterState?

    @Upsert
    suspend fun putCharacterState(state: CharacterState)

    @Query("SELECT state, COUNT(*) AS n FROM character_state GROUP BY state")
    suspend fun characterStateCounts(): List<StateCount>

    @Upsert
    suspend fun putComposition(composition: Composition): Long

    @Upsert
    suspend fun putMadePassage(passage: MadePassage): Long

    @Upsert
    suspend fun putMadeLines(lines: List<MadeLine>)

    @Upsert
    suspend fun putMadeQuestions(questions: List<MadeQuestion>)

    @Query("SELECT * FROM made_passage WHERE kind = :kind ORDER BY made_at DESC")
    suspend fun madePassages(kind: String = "passage"): List<MadePassage>

    @Query("SELECT * FROM made_line WHERE passage_id = :id ORDER BY idx")
    suspend fun madeLines(id: Long): List<MadeLine>

    @Query("SELECT * FROM made_question WHERE passage_id = :id ORDER BY id")
    suspend fun madeQuestions(id: Long): List<MadeQuestion>

    @Query("DELETE FROM made_passage WHERE id = :id")
    suspend fun deleteMadePassage(id: Long)

    /**
     * The words worth writing a story about: struggling first, then whatever
     * has lapsed most. Excludes retired cards — a word you have deliberately
     * put down is not one you want a passage built around.
     */
    @Query(
        """SELECT m.wordId AS wordId, SUM(m.lapses) * 10 - MIN(m.box) AS n
           FROM mastery m
           LEFT JOIN card_state c ON c.wordId = m.wordId
           WHERE (c.state IS NULL OR c.state != 'retired')
           GROUP BY m.wordId
           HAVING SUM(m.lapses) > 0 OR MIN(m.box) <= 1
           ORDER BY n DESC
           LIMIT :limit"""
    )
    suspend fun weakestWords(limit: Int): List<WordCount>

    @Query("SELECT * FROM composition ORDER BY written_at DESC LIMIT :limit")
    suspend fun compositions(limit: Int): List<Composition>

    @Query("SELECT * FROM composition WHERE correct IS NULL ORDER BY written_at")
    suspend fun unmarkedCompositions(): List<Composition>

    @Query("SELECT COUNT(*) FROM composition")
    suspend fun compositionCount(): Int

    @Query("SELECT COUNT(*) FROM card_state WHERE state != 'retired' AND due_at <= :today")
    suspend fun cardsDue(today: Long): Int
}

data class SkillCount(val skill: String, val n: Int)

data class WordCount(val wordId: Long, val n: Int)

data class SkillRow(val skill: String, val words: Int, val meanBox: Double, val lapses: Int)

data class TroubleRow(val wordId: Long, val lapses: Int, val box: Int, val skills: Int)

data class WordTrouble(val wordId: Long, val lapses: Int, val reviews: Int)

data class StateCount(val state: String, val n: Int)

@Database(
    entities = [Mastery::class, XpEvent::class, Daily::class, UserWord::class,
                CardState::class, CharacterState::class, Composition::class,
                MadePassage::class, MadeLine::class, MadeQuestion::class,
                Slip::class, CardFlag::class],
    version = 9,
    exportSchema = false,
)
abstract class ProgressDb : RoomDatabase() {
    abstract fun dao(): ProgressDao

    companion object {
        @Volatile private var instance: ProgressDb? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS user_word (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         hanzi TEXT NOT NULL,
                         pinyin TEXT NOT NULL,
                         pinyin_verified INTEGER NOT NULL DEFAULT 0,
                         gloss_id TEXT,
                         gloss_en TEXT,
                         example_zh TEXT,
                         example_pinyin TEXT,
                         example_gloss TEXT,
                         notes TEXT,
                         added_at INTEGER NOT NULL,
                         source TEXT NOT NULL DEFAULT 'notes'
                       )"""
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS card_state (
                         wordId INTEGER PRIMARY KEY NOT NULL,
                         state TEXT NOT NULL DEFAULT 'learning',
                         box INTEGER NOT NULL DEFAULT 0,
                         due_at INTEGER NOT NULL DEFAULT 0,
                         seen_count INTEGER NOT NULL DEFAULT 0,
                         touched_at INTEGER NOT NULL DEFAULT 0
                       )"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS character_state (
                         hanzi TEXT PRIMARY KEY NOT NULL,
                         state TEXT NOT NULL DEFAULT 'learning',
                         box INTEGER NOT NULL DEFAULT 0,
                         due_at INTEGER NOT NULL DEFAULT 0,
                         seen_count INTEGER NOT NULL DEFAULT 0,
                         touched_at INTEGER NOT NULL DEFAULT 0
                       )"""
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS composition (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         word_id INTEGER NOT NULL,
                         hanzi TEXT NOT NULL,
                         prompt TEXT NOT NULL,
                         text TEXT NOT NULL,
                         source TEXT NOT NULL DEFAULT 'offline',
                         correct INTEGER,
                         corrected TEXT,
                         note TEXT,
                         written_at INTEGER NOT NULL DEFAULT 0
                       )"""
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS made_passage (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         title TEXT NOT NULL,
                         title_gloss TEXT NOT NULL,
                         targets TEXT NOT NULL,
                         unknown_count INTEGER NOT NULL DEFAULT 0,
                         made_at INTEGER NOT NULL DEFAULT 0
                       )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS made_line (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         passage_id INTEGER NOT NULL,
                         idx INTEGER NOT NULL,
                         zh TEXT NOT NULL,
                         pinyin TEXT NOT NULL,
                         gloss TEXT NOT NULL,
                         segments TEXT NOT NULL
                       )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS made_question (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         passage_id INTEGER NOT NULL,
                         q TEXT NOT NULL,
                         choices_json TEXT NOT NULL,
                         answer INTEGER NOT NULL
                       )"""
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE made_passage ADD COLUMN kind TEXT NOT NULL " +
                        "DEFAULT 'passage'"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS slip (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         ts INTEGER NOT NULL,
                         word_id INTEGER NOT NULL,
                         skill TEXT NOT NULL,
                         type_id TEXT NOT NULL,
                         chose TEXT,
                         expected TEXT
                       )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_slip_word ON slip(word_id)")
            }
        }

        /**
         * Flags: a card the learner says is wrong.
         *
         * The index is declared on the entity too — Room checks the schema it
         * finds against the annotations at launch, on an existing database,
         * which is the worst possible place to discover a mismatch.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS card_flag (
                         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                         word_id INTEGER NOT NULL,
                         hanzi TEXT NOT NULL,
                         pinyin TEXT NOT NULL,
                         reason TEXT NOT NULL,
                         type_id TEXT,
                         shown TEXT,
                         note TEXT,
                         flagged_at INTEGER NOT NULL,
                         fixed_at INTEGER
                       )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_flag_word ON card_flag(word_id)")
            }
        }

        fun get(context: Context): ProgressDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, ProgressDb::class.java, "progress.db"
            )
                // Nothing in this database is reconstructible from elsewhere, so
                // a destructive migration would throw away the user's progress.
                // Version 2 only adds user_word; Room handles that automatically
                // once the migration is declared.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
                .also { instance = it }
        }
    }
}

fun Skill.key(): String = name
fun skillFromKey(key: String): Skill = Skill.valueOf(key)
