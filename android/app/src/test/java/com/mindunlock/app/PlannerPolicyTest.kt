package com.mindunlock.app

import com.mindunlock.app.data.local.StudyTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlannerPolicyTest {
    @Test
    fun dailyFocusPrioritizesOverdueThenTodayThenFutureAndSkipsCompleted() {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.of(2026, 11, 13).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = todayStart + 12 * 60 * 60 * 1000L
        val tasks = listOf(
            StudyTaskEntity(id = 4, title = "بدون موعد", createdAt = 4),
            StudyTaskEntity(id = 5, title = "انجام‌شده", dueAt = todayStart - 1000L, completed = true),
            StudyTaskEntity(id = 3, title = "آینده", dueAt = todayStart + 2 * 86_400_000L, createdAt = 3),
            StudyTaskEntity(id = 2, title = "امروز", dueAt = todayStart + 3_600_000L, createdAt = 2),
            StudyTaskEntity(id = 1, title = "عقب‌افتاده", dueAt = todayStart - 86_400_000L, createdAt = 1)
        )
        assertEquals(listOf(1L, 2L, 3L), PlannerPolicy.selectDailyFocusTasks(tasks, now).map { it.id })
    }

    @Test
    fun dailyFocusHonorsLimitAndReturnsEmptyForNonPositiveLimit() {
        val tasks = (1L..5L).map { StudyTaskEntity(id = it, title = "کار $it", createdAt = it) }
        assertEquals(listOf(1L, 2L, 3L), PlannerPolicy.selectDailyFocusTasks(tasks, 0L).map { it.id })
        assertEquals(emptyList<Long>(), PlannerPolicy.selectDailyFocusTasks(tasks, 0L, limit = 0).map { it.id })
    }

    @Test
    fun dailyFocusUsesCreationTimeAndIdForStableUndatedOrdering() {
        val tasks = listOf(
            StudyTaskEntity(id = 3, title = "سوم", createdAt = 20),
            StudyTaskEntity(id = 2, title = "دوم", createdAt = 10),
            StudyTaskEntity(id = 1, title = "اول", createdAt = 10)
        )
        assertEquals(listOf(1L, 2L, 3L), PlannerPolicy.selectDailyFocusTasks(tasks, 0L).map { it.id })
    }

    @Test
    fun rolloverMovesOpenTaskToNextLocalDayAndPreservesReminderOffset() {
        val zone = ZoneId.of("Europe/Berlin")
        val due = java.time.LocalDateTime.of(2026, 11, 13, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val reminder = java.time.LocalDateTime.of(2026, 11, 13, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val task = StudyTaskEntity(id = 9, title = "کار", dueAt = due, remindAt = reminder)
        val moved = PlannerPolicy.moveIncompleteTaskToTomorrow(task, due, zone)
        assertEquals(
            java.time.LocalDateTime.of(2026, 11, 14, 9, 30).atZone(zone).toInstant().toEpochMilli(),
            moved?.dueAt
        )
        assertEquals(
            java.time.LocalDateTime.of(2026, 11, 14, 8, 30).atZone(zone).toInstant().toEpochMilli(),
            moved?.remindAt
        )
        assertEquals(task.id, moved?.id)
        assertEquals(task.title, moved?.title)
    }

    @Test
    fun rolloverUsesCalendarDayAcrossDaylightSavingTransition() {
        val zone = ZoneId.of("Europe/Berlin")
        val due = java.time.LocalDateTime.of(2026, 3, 28, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val reminder = java.time.LocalDateTime.of(2026, 3, 28, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val moved = PlannerPolicy.moveIncompleteTaskToTomorrow(
            StudyTaskEntity(id = 10, title = "DST", dueAt = due, remindAt = reminder), due, zone
        )
        assertEquals(
            java.time.LocalDateTime.of(2026, 3, 29, 9, 30).atZone(zone).toInstant().toEpochMilli(),
            moved?.dueAt
        )
        assertEquals(
            java.time.LocalDateTime.of(2026, 3, 29, 8, 30).atZone(zone).toInstant().toEpochMilli(),
            moved?.remindAt
        )
    }

    @Test
    fun rolloverSkipsCompletedOrUndatedTasks() {
        assertEquals(null, PlannerPolicy.moveIncompleteTaskToTomorrow(
            StudyTaskEntity(id = 1, title = "انجام‌شده", dueAt = 1000L, completed = true), 0L
        ))
        assertEquals(null, PlannerPolicy.moveIncompleteTaskToTomorrow(
            StudyTaskEntity(id = 2, title = "بدون موعد"), 0L
        ))
    }
}
