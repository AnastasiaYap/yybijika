package io.tr8.yybijika.data

import android.content.Context
import androidx.core.content.edit

/**
 * User preferences, including the DeepSeek key.
 *
 * The key is stored in this app's private SharedPreferences, which no other app
 * can read on a non-rooted device. That is the same protection the OS gives any
 * app's own credentials, and it is deliberately not dressed up as more: the key
 * is used to call an external service from the phone, so it has to be decryptable
 * by this process. Anyone with the unlocked device and a debugger could reach it.
 */
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

    /** Show the Indonesian gloss first rather than the English one. */
    var indonesianFirst: Boolean
        get() = prefs.getBoolean(KEY_ID_FIRST, true)
        set(value) = prefs.edit { putBoolean(KEY_ID_FIRST, value) }

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
        const val KEY_ID_FIRST = "indonesian_first"
        const val KEY_AUTO_UPDATE = "auto_check_updates"
        const val KEY_AUDIO = "audio_enabled"
    }
}
