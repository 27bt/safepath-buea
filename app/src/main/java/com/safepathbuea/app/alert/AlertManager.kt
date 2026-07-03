package com.safepathbuea.app.alert

import com.safepathbuea.app.speech.SpeechPriority
import com.safepathbuea.app.speech.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central priority queue for everything that wants to speak: camera
 * detections (Phase 2) and nearby-hazard reads from Firestore (Phase 3).
 *
 * URGENT alerts (close + centered obstacles) flush TTS and interrupt
 * whatever is currently being read. NORMAL alerts are queued FIFO and only
 * spoken once TTS falls idle. A per-[Alert.cooldownKey] cooldown stops the
 * same alert type from repeating within [cooldownMillis].
 */
class AlertManager(
    private val tts: TtsManager,
    private val scope: CoroutineScope,
    private val cooldownMillis: Long = 10_000L,
    private val onAlertAnnounced: (Alert) -> Unit = {},
) {
    private val mutex = Mutex()
    private val pending = ArrayDeque<Alert>()
    private val cooldowns = HashMap<String, Long>()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    fun submit(alert: Alert) {
        scope.launch {
            var shouldDrain = false
            mutex.withLock {
                val now = System.currentTimeMillis()
                val last = cooldowns[alert.cooldownKey]
                if (last != null && now - last < cooldownMillis) return@withLock
                cooldowns[alert.cooldownKey] = now

                if (alert.priority == AlertPriority.URGENT) {
                    // An urgent alert pre-empts any same-type alert still
                    // waiting in the queue; it will be re-announced fresh.
                    pending.removeAll { it.cooldownKey == alert.cooldownKey }
                    tts.speak(alert.message, SpeechPriority.URGENT)
                    onAlertAnnounced(alert)
                } else {
                    pending.addLast(alert)
                    shouldDrain = true
                }
                _pendingCount.value = pending.size
            }
            if (shouldDrain) drainQueue()
        }
    }

    private suspend fun drainQueue() {
        while (true) {
            var next: Alert? = null
            mutex.withLock {
                if (!tts.isSpeaking.value && pending.isNotEmpty()) {
                    next = pending.removeFirst()
                    _pendingCount.value = pending.size
                }
            }
            val alert = next ?: return
            tts.speak(alert.message, SpeechPriority.NORMAL)
            onAlertAnnounced(alert)
            // Give TTS a moment to flip isSpeaking to true before re-checking,
            // otherwise a fast loop could dequeue the next item immediately.
            delay(150)
        }
    }

    fun clear() {
        scope.launch {
            mutex.withLock {
                pending.clear()
                _pendingCount.value = 0
            }
        }
    }
}
