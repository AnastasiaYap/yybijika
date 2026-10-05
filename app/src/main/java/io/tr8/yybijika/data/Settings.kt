package io.tr8.yybijika.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime
import io.tr8.yybijika.remind.Reminder
import io.tr8.yybijika.ui.theme.Palette
import io.tr8.yybijika.ui.theme.ThemeMode

/**
 * User preferences, including the DeepSeek key.
 *
 * The key is stored in this app's private SharedPreferences, which no other app
 * can read on a non-rooted device. That is the same protection the OS gives any
 * app's own credentials, and it is deliberately not dressed up as more: the key
 * is used to call an external service from the phone, so it has to be decryptable
 * by this process. Anyone with the unlocked device and a debugger could reach it.
 */
/** The language a question is asked in. The deck holds both for every word. */
enum class GlossLanguage(val code: String, val label: String) {
    INDONESIAN("id", "Indonesia"),
    ENGLISH("en", "English");

    companion object {
        fun of(code: String?): GlossLanguage =
            entries.firstOrNull { it.code == code } ?: INDONESIAN
    }
}

class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("yybijika", Context.MODE_PRIVATE)

    var deepseekKey: String
        get() = prefs.getString(KEY_DEEPSEEK, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_DEEPSEEK, value.trim()) }

    val hasDeepseekKey: Boolean get() = deepseekKey.isNotBlank()

    /** New words introduced per study session. More is not better here. */
    var newPerSession: Int
        get() = prefs.getInt(KEY_NEW_PER_SESSION, 10)
        set(value) = prefs.edit { putInt(KEY_NEW_PER_SESSION, value.coerceIn(0, 40)) }

    var sessionSize: Int
        get() = prefs.getInt(KEY_SESSION_SIZE, 20)
        set(value) = prefs.edit { putInt(KEY_SESSION_SIZE, value.coerceIn(5, 100)) }

    /**
     * Which language the questions are asked and answered in.
     *
     * Every word in the deck carries both an Indonesian and an English meaning,
     * and until now the Indonesian one was simply always the answer. That is
     * the wrong default for anyone more at home in English, and it is a
     * preference rather than a fact about the deck.
     *
     * Only the *question* language changes. Both meanings stay on the word's
     * own card, because looking a word up is when having the second one helps.
     */
    var glossLanguage: GlossLanguage
        get() = GlossLanguage.of(prefs.getString(KEY_GLOSS_LANG, null))
        set(value) = prefs.edit { putString(KEY_GLOSS_LANG, value.code) }

    /**
     * Whether the app is allowed to make a sound.
     *
     * Separate from whether the phone *can* speak Chinese. A learner on a bus
     * wants silence without being told the listening drills are unavailable, so
     * this is a choice rather than a capability — but the two combine, because a
     * listening question you have muted is a question you cannot answer.
     */
    var audioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO, true)
        set(value) = prefs.edit { putBoolean(KEY_AUDIO, value) }

    /**
     * The palette and whether it follows the phone's light or dark setting.
     *
     * Stored as names rather than indices so adding a palette in the middle of
     * the list cannot silently repaint someone's app.
     */
    var palette: Palette
        get() = Palette.of(prefs.getString(KEY_PALETTE, null))
        set(value) = prefs.edit { putString(KEY_PALETTE, value.name) }

    var themeMode: ThemeMode
        get() = ThemeMode.of(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value.name) }

    /**
     * Whether reading passages show their pinyin.
     *
     * Off by default, and that is the pedagogical position rather than a
     * space-saving one: with the reading printed under every line the eye goes
     * to it and the characters never have to be read at all. It is there for
     * the line you are stuck on, not for the whole page.
     */
    var passagePinyin: Boolean
        get() = prefs.getBoolean(KEY_PASSAGE_PINYIN, false)
        set(value) = prefs.edit { putBoolean(KEY_PASSAGE_PINYIN, value) }

    /**
     * A daily nudge, and when.
     *
     * Off by default. An app that starts sending notifications the moment it is
     * installed has decided on the user's behalf that it is important, and the
     * usual answer to that is to turn off notifications for it permanently —
     * which costs the one reminder that would have been welcome.
     */
    var remindEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMIND, false)
        set(value) = prefs.edit { putBoolean(KEY_REMIND, value) }

    var remindHour: Int
        get() = prefs.getInt(KEY_REMIND_HOUR, Reminder.DEFAULT_TIME.hour)
        set(value) = prefs.edit { putInt(KEY_REMIND_HOUR, value.coerceIn(0, 23)) }

    var remindMinute: Int
        get() = prefs.getInt(KEY_REMIND_MINUTE, Reminder.DEFAULT_TIME.minute)
        set(value) = prefs.edit { putInt(KEY_REMIND_MINUTE, value.coerceIn(0, 59)) }

    /** Epoch day of the last reminder shown, so one day never gets two. */
    var lastRemindedDay: Long
        get() = prefs.getLong(KEY_REMINDED_DAY, -1)
        set(value) = prefs.edit { putLong(KEY_REMINDED_DAY, value) }

    val remindAt: LocalTime get() = LocalTime.of(remindHour, remindMinute)

    /** Check GitHub for a new release on launch. */
    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_UPDATE, value) }

    /** Masked for display, so the screen never shows the whole key. */
    fun maskedKey(): String {
        val key = deepseekKey
        if (key.isBlank()) return "not set"
        if (key.length <= 10) return "•".repeat(key.length)
        return key.take(6) + "…" + key.takeLast(4)
    }

    private companion object {
        const val KEY_DEEPSEEK = "deepseek_key"
        const val KEY_NEW_PER_SESSION = "new_per_session"
        const val KEY_SESSION_SIZE = "session_size"
        const val KEY_GLOSS_LANG = "gloss_language"
        const val KEY_AUTO_UPDATE = "auto_check_updates"
        const val KEY_AUDIO = "audio_enabled"
        const val KEY_PASSAGE_PINYIN = "passage_pinyin"
        const val KEY_REMIND = "remind_enabled"
        const val KEY_REMIND_HOUR = "remind_hour"
        const val KEY_REMIND_MINUTE = "remind_minute"
        const val KEY_REMINDED_DAY = "last_reminded_day"
        const val KEY_PALETTE = "palette"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
