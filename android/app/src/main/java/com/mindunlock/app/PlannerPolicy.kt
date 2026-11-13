package com.mindunlock.app

import com.mindunlock.app.data.local.StudyTaskEntity
import java.time.Instant
import java.time.ZoneId

/** Selects up to three open tasks by urgency without persisting derived priority. */
object PlannerPolicy {
    fun selectDailyFocusTasks(tasks: List<StudyTaskEntity>, nowMillis: Long, limit: Int = 3): List<StudyTaskEntity> {
        if (limit <= 0) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return tasks.asSequence()
            .filterNot { it.completed }
            .sortedWith(
                compareBy<StudyTaskEntity> {
                    when {
                        it.dueAt == null -> 3
                        it.dueAt < todayStart -> 0
                        it.dueAt < tomorrowStart -> 1
                        else -> 2
                    }
                }.thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            .take(limit)
            .toList()
    }
}
