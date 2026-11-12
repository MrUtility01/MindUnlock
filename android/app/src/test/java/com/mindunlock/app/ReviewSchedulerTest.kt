package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulerTest {
    @Test
    fun againSchedulesTenMinuteRetryWithoutIncreasingInterval() {
        val now = 1_800_000_000_000L
        val result = ReviewScheduler.schedule(0, 8, now)
        assertEquals(0, result.intervalDays)
        assertEquals(now + 600_000L, result.dueAt)
    }

    @Test
    fun goodRatingUsesGraduatedIntervals() {
        val now = 1_800_000_000_000L
        assertEquals(1, ReviewScheduler.schedule(4, 0, now).intervalDays)
        assertEquals(3, ReviewScheduler.schedule(4, 1, now).intervalDays)
        assertEquals(6, ReviewScheduler.schedule(4, 3, now).intervalDays)
    }

    @Test
    fun easyRatingUsesLongerIntervals() {
        val now = 1_800_000_000_000L
        assertEquals(4, ReviewScheduler.schedule(5, 0, now).intervalDays)
        assertEquals(6, ReviewScheduler.schedule(5, 1, now).intervalDays)
        assertEquals(15, ReviewScheduler.schedule(5, 6, now).intervalDays)
    }

    @Test
    fun intervalNeverExceedsOneHundredEightyDays() {
        val result = ReviewScheduler.schedule(5, 180, 1_800_000_000_000L)
        assertEquals(180, result.intervalDays)
        assertEquals(1_800_000_000_000L + 180L * 86_400_000L, result.dueAt)
    }

    @Test
    fun qualityIsClampedToSupportedRange() {
        val now = 1_800_000_000_000L
        assertEquals(0, ReviewScheduler.schedule(-10, 0, now).intervalDays)
        assertEquals(4, ReviewScheduler.schedule(99, 0, now).intervalDays)
    }
}
