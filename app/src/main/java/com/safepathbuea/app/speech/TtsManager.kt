package com.safepathbuea.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class SpeechPriority { NORMAL, URGENT }

/**
 * Thin wrapper around [TextToSpeech]. NORMAL utterances are queued; URGENT
 * ones flush the queue first so a close/central hazard always interrupts
 * whatever is currently being read (AlertManager relies on this in Phase 2).
 */
class TtsManager(context: Context) {

    private var engine: TextToSpeech? = null
    private var isReady = false
    private val pendingOnReady = mutableListOf<() -> Unit>()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var lastSpokenText: String? = null
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                engine?.language = Locale.getDefault()
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                val queued = pendingOnReady.toList()
                pendingOnReady.clear()
                queued.forEach { it.invoke() }
            }
        }
    }

    fun setLanguage(locale: Locale) {
        runWhenReady { engine?.language = locale }
    }

    /** 0.5 (slow) .. 2.0 (fast); 1.0 is normal speed. */
    fun setSpeechRate(rate: Float) {
        runWhenReady { engine?.setSpeechRate(rate.coerceIn(0.5f, 2.0f)) }
    }

    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL) {
        if (text.isBlank()) return
        lastSpokenText = text
        val queueMode = if (priority == SpeechPriority.URGENT) {
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }
        runWhenReady {
            engine?.speak(text, queueMode, null, UUID.randomUUID().toString())
        }
    }

    fun repeatLast() {
        lastSpokenText?.let { speak(it, SpeechPriority.URGENT) }
    }

    fun stop() {
        engine?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun runWhenReady(action: () -> Unit) {
        if (isReady) action() else pendingOnReady.add(action)
    }
}
