package io.tr8.yybijika.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * What the engine actually reported, in words.
 *
 * Kept because "no sound came out" has half a dozen causes that look identical
 * from the outside — no engine, engine but no Chinese, Chinese but the voice
 * data was never downloaded, a speak call the engine rejected — and only the
 * last of those is something the app can fix. Settings shows this so the
 * difference is visible instead of guessed at.
 */
data class VoiceReport(
    val engineName: String? = null,
    val languageStatus: String = "not checked yet",
    val voiceCount: Int = 0,
    val lastSpeakResult: String? = null,
    val lastError: String? = null,
) {
    val summary: String
        get() = buildString {
            append(engineName ?: "no engine")
            append(" · ").append(languageStatus)
            if (voiceCount > 0) append(" · $voiceCount Chinese voices")
            lastSpeakResult?.let { append(" · last: $it") }
            lastError?.let { append(" · error: $it") }
        }
}

/**
 * Speaks Chinese, or honestly reports that it cannot.
 *
 * Android's TTS will accept a Chinese string with no Chinese voice installed and
 * then say nothing at all, so language availability is checked explicitly rather
 * than assumed from a successful bind — and, since a phone can report the
 * language available while the voice data is missing, the result of every call
 * is recorded too.
 */
class Speaker(private val context: Context) {

    private var engine: TextToSpeech? = null

    @Volatile
    var available: Boolean = false
        private set

    @Volatile
    var report: VoiceReport = VoiceReport()
        private set

    @Volatile
    private var ready = false

    private var onReady: ((Boolean) -> Unit)? = null

    init {
        start()
    }

    private fun start() {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                report = report.copy(languageStatus = "engine failed to start")
                available = false
                onReady?.invoke(false)
                return@TextToSpeech
            }

            val result = engine?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            available = result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

            if (available) {
                // Route through the media stream explicitly. Left to the
                // default, some engines speak on the notification or
                // accessibility stream, which many phones keep at zero — the
                // app then looks broken while the engine reports success.
                engine?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // Slightly under normal pace: learners need the tones to land,
                // and the default rate clips the fourth tone on short words.
                engine?.setSpeechRate(0.85f)
            }

            report = report.copy(
                engineName = engine?.defaultEngine,
                languageStatus = describe(result),
                voiceCount = chineseVoices(),
            )
            onReady?.invoke(available)
        }
    }

    private fun describe(result: Int?): String = when (result) {
        TextToSpeech.LANG_AVAILABLE,
        TextToSpeech.LANG_COUNTRY_AVAILABLE,
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "Chinese available"
        TextToSpeech.LANG_MISSING_DATA -> "Chinese voice data not installed"
        TextToSpeech.LANG_NOT_SUPPORTED -> "engine does not support Chinese"
        else -> "unknown status $result"
    }

    private fun chineseVoices(): Int = try {
        engine?.voices.orEmpty().count { voice: Voice ->
            voice.locale.language == "zh"
        }
    } catch (e: Exception) {
        // Some vendor engines throw rather than return an empty set.
        0
    }

    /** Called once the engine has reported in, since that happens asynchronously. */
    fun whenReady(block: (Boolean) -> Unit) {
        if (ready) block(available) else onReady = block
    }

    /**
     * Re-run the whole check.
     *
     * The learner may have gone to the system settings to install a voice, and
     * an engine bound before that happened will keep saying no.
     */
    fun recheck(block: (Boolean) -> Unit) {
        engine?.shutdown()
        engine = null
        ready = false
        available = false
        onReady = block
        start()
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!available) {
            report = report.copy(lastSpeakResult = "skipped, no Chinese voice")
            onDone?.invoke()
            return
        }
        val id = text.hashCode().toString()
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                report = report.copy(lastSpeakResult = "spoke ok", lastError = null)
                onDone?.invoke()
            }

            @Deprecated("required by the base class")
            override fun onError(utteranceId: String?) {
                report = report.copy(lastError = "engine failed mid-utterance")
                onDone?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                report = report.copy(lastError = "engine error $errorCode")
                onDone?.invoke()
            }
        })
        val queued = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (queued != TextToSpeech.SUCCESS) {
            report = report.copy(lastSpeakResult = "engine refused the request")
        }
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
