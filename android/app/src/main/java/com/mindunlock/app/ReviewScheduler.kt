package com.mindunlock.app

data class ReviewSchedule(
    val intervalDays: Int,
    val dueAt: Long
)

object ReviewScheduler {
    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 86_400_000L
    const val MAX_INTERVAL_DAYS = 180

    fun schedule(quality: Int, previousIntervalDays: Int, now: Long): ReviewSchedule {
        val rating = quality.coerceIn(0, 5)
        val previous = previousIntervalDays.coerceAtLeast(0)
        val interval = when {
            rating < 3 -> 0
            rating == 3 -> when (previous) {
                0 -> 1
                else -> (previous * 1.2).toInt().coerceAtLeast(previous + 1)
            }
            rating == 4 -> when (previous) {
                0 -> 1
                1 -> 3
                else -> (previous * 2.0).toInt().coerceAtLeast(previous + 1)
            }
            else -> when (previous) {
                0 -> 4
                1 -> 6
                else -> (previous * 2.5).toInt().coerceAtLeast(previous + 1)
            }
        }.coerceAtMost(MAX_INTERVAL_DAYS)
        val due = if (rating < 3) now + 10 * MINUTE_MS else now + interval * DAY_MS
        return ReviewSchedule(interval, due)
    }
}
