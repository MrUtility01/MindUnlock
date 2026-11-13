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
                        it.dueAt < nowMillis -> 0
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

    /**
     * Moves an open dated task to the next local calendar day, preserving wall-clock time.
     * The reminder is shifted by the same instant delta so its offset from the due date remains stable.
     */
    fun moveIncompleteTaskToTomorrow(
        task: StudyTaskEntity,
        zone: ZoneId = ZoneId.systemDefault()
    ): StudyTaskEntity? {
        if (task.completed) return null
        val oldDueAt = task.dueAt ?: return null
        val oldDue = Instant.ofEpochMilli(oldDueAt).atZone(zone)
        val newDueAt = oldDue.toLocalDate().plusDays(1)
            .atTime(oldDue.toLocalTime())
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val delta = newDueAt - oldDueAt
        return task.copy(
            dueAt = newDueAt,
            remindAt = task.remindAt?.plus(delta)
        )
    }
}
