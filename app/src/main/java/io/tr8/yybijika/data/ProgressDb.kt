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
}

data class SkillCount(val skill: String, val n: Int)

@Database(
    entities = [Mastery::class, XpEvent::class, Daily::class],
    version = 1,
    exportSchema = false,
)
abstract class ProgressDb : RoomDatabase() {
    abstract fun dao(): ProgressDao

    companion object {
        @Volatile private var instance: ProgressDb? = null

        fun get(context: Context): ProgressDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, ProgressDb::class.java, "progress.db"
            ).build().also { instance = it }
        }
    }
}

fun Skill.key(): String = name
fun skillFromKey(key: String): Skill = Skill.valueOf(key)
