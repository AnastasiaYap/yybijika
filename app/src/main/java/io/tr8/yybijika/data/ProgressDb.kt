package io.tr8.yybijika.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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

@Entity(tableName = "daily")
data class Daily(
    @PrimaryKey val date: Long,        // epoch day
    val xp: Int = 0,
    val reviews: Int = 0,
    val correct: Int = 0,
)

@Dao
interface ProgressDao {

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
    @Query(
        """SELECT * FROM mastery
           WHERE due_at <= :today
           ORDER BY due_at ASC, box ASC
           LIMIT :limit"""
    )
    suspend fun due(today: Long, limit: Int): List<Mastery>

    @Query("SELECT COUNT(*) FROM mastery WHERE due_at <= :today")
    suspend fun dueCount(today: Long): Int

    @Query("SELECT skill, COUNT(*) AS n FROM mastery WHERE due_at <= :today GROUP BY skill")
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
}

data class SkillCount(val skill: String, val n: Int)

@Database(
    entities = [Mastery::class, XpEvent::class, Daily::class, UserWord::class],
    version = 2,
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

        fun get(context: Context): ProgressDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, ProgressDb::class.java, "progress.db"
            )
                // Nothing in this database is reconstructible from elsewhere, so
                // a destructive migration would throw away the user's progress.
                // Version 2 only adds user_word; Room handles that automatically
                // once the migration is declared.
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}

fun Skill.key(): String = name
fun skillFromKey(key: String): Skill = Skill.valueOf(key)
