package com.mindunlock.app

import java.util.concurrent.atomic.AtomicLong

internal data class QueuedSpeechRequest(
    val voiceId: String,
    val text: String,
    val speed: Float,
    val generation: Long
)

/**
 * Keeps only the latest speech request and invalidates work on stop or voice
 * changes. An utterance is retained only while its selected voice is retained.
 */
internal class SpeechPlaybackQueue {
    private val sequence = AtomicLong(0L)
    @Volatile private var pending: QueuedSpeechRequest? = null

    @Synchronized
    fun enqueue(voiceId: String, text: String, speed: Float): QueuedSpeechRequest {
        val request = QueuedSpeechRequest(
            voiceId = voiceId,
            text = text,
            speed = speed,
            generation = sequence.incrementAndGet()
        )
        pending = request
        return request
    }

    @Synchronized
    fun switchVoice(voiceId: String) {
        val generation = sequence.incrementAndGet()
        pending = pending
            ?.takeIf { it.voiceId == voiceId }
            ?.copy(generation = generation)
    }

    @Synchronized
    fun take(voiceId: String): QueuedSpeechRequest? {
        val request = pending ?: return null
        if (request.voiceId != voiceId || request.generation != sequence.get()) return null
        pending = null
        return request
    }

    fun isCurrent(request: QueuedSpeechRequest): Boolean =
        request.generation == sequence.get()

    @Synchronized
    fun cancel() {
        pending = null
        sequence.incrementAndGet()
    }
}
