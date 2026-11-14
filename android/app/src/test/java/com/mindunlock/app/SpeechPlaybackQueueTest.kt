package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlaybackQueueTest {
    @Test
    fun latestUtteranceReplacesOlderPendingSpeech() {
        val queue = SpeechPlaybackQueue()
        val first = queue.enqueue("gyro", "متن اول", 1f)
        val second = queue.enqueue("gyro", "متن دوم", 1.1f)

        assertFalse(queue.isCurrent(first))
        assertTrue(queue.isCurrent(second))
        assertEquals(second, queue.take("gyro"))
        assertNull(queue.take("gyro"))
    }

    @Test
    fun selectingTheSameVoicePreservesPendingUtteranceWithFreshGeneration() {
        val queue = SpeechPlaybackQueue()
        val original = queue.enqueue("amir", "سلام", 1f)

        queue.switchVoice("amir")
        val ready = queue.take("amir")

        assertTrue(ready != null)
        assertEquals(original.voiceId, ready?.voiceId)
        assertEquals(original.text, ready?.text)
        assertEquals(original.speed, ready?.speed)
        assertFalse(queue.isCurrent(original))
        assertTrue(ready?.let(queue::isCurrent) == true)
    }

    @Test
    fun switchingToAnotherVoiceDiscardsWrongVoiceUtterance() {
        val queue = SpeechPlaybackQueue()
        queue.enqueue("gyro", "متن", 1f)

        queue.switchVoice("haaniye")

        assertNull(queue.take("gyro"))
        assertNull(queue.take("haaniye"))
    }

    @Test
    fun cancelInvalidatesQueuedAndInFlightSpeech() {
        val queue = SpeechPlaybackQueue()
        val request = queue.enqueue("gyro", "متن", 1f)

        queue.cancel()

        assertFalse(queue.isCurrent(request))
        assertNull(queue.take("gyro"))
    }
}
