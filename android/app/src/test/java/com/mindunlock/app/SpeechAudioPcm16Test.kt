package com.mindunlock.app

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SpeechAudioPcm16Test {
    @Test
    fun convertsNormalizedFloatSamplesToPcm16() {
        val result = SpeechAudioPcm16.convert(floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f))
        assertArrayEquals(
            shortArrayOf(Short.MIN_VALUE, -16384, 0, 16384, Short.MAX_VALUE),
            result
        )
    }

    @Test
    fun clampsOutOfRangeAndReplacesNonFiniteSamples() {
        val result = SpeechAudioPcm16.convert(floatArrayOf(-2f, 2f, Float.NaN, Float.POSITIVE_INFINITY))
        assertArrayEquals(shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE, 0, 0), result)
    }

    @Test
    fun convertsOnlyRequestedChunk() {
        val result = SpeechAudioPcm16.convert(floatArrayOf(-1f, -0.5f, 0.5f, 1f), 1, 2)
        assertArrayEquals(shortArrayOf(-16384, 16384), result)
    }
}
