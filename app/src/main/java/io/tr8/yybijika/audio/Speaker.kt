package io.tr8.yybijika.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks Chinese, or honestly reports that it cannot.
 *
 * The listening exercises are only offered when [available] is true. Android's
 * TTS will happily accept a Chinese string with no Chinese voice installed and
 * then say nothing at all, so the language availability is checked explicitly
 * on init rather than assumed from a successful engine bind.
 */
class Speaker(context: Context) {

    private var engine: TextToSpeech? = null

    @Volatile
    var available: Boolean = false
        private set

    @Volatile
    private var ready = false

    private var onReady: ((Boolean) -> Unit)? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            available = ready && supportsChinese()
            if (available) {
                engine?.language = Locale.SIMPLIFIED_CHINESE
                // Slightly under normal pace: learners need the tones to land,
                // and the default rate clips the fourth tone on short words.
                engine?.setSpeechRate(0.85f)
            }
            onReady?.invoke(available)
        }
    }

    private fun supportsChinese(): Boolean {
        val result = engine?.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
        return result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /** Called once the engine has reported in, since that happens asynchronously. */
    fun whenReady(block: (Boolean) -> Unit) {
        if (ready) block(available) else onReady = block
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!available) {
            onDone?.invoke()
            return
        }
        val id = text.hashCode().toString()
        if (onDone != null) {
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = onDone()
                @Deprecated("required by the base class")
                override fun onError(utteranceId: String?) = onDone()
            })
        }
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        engine?.stop()
    }

    fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
