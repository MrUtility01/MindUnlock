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
    fun dailyFocusTreatsEarlierTodayDeadlinesAsOverdue() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 11, 13)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val earlierToday = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val laterToday = today.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrow = today.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            StudyTaskEntity(id = 3, title = "فردا", dueAt = tomorrow),
            StudyTaskEntity(id = 2, title = "امروز عصر", dueAt = laterToday),
            StudyTaskEntity(id = 1, title = "امروز صبح", dueAt = earlierToday)
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
    fun selectedDayIncludesOnlyDueTasksWithinLocalDayAndSortsByTime() {
        val zone = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 11, 13)
        fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            StudyTaskEntity(id = 4, title = "بدون موعد"),
            StudyTaskEntity(id = 3, title = "فردا", dueAt = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
            StudyTaskEntity(id = 2, title = "عصر", dueAt = at(17)),
            StudyTaskEntity(id = 1, title = "صبح", dueAt = at(8)),
            StudyTaskEntity(id = 5, title = "نیمه‌شب قبل", dueAt = day.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        )
        assertEquals(listOf(1L, 2L), PlannerPolicy.tasksForLocalDate(tasks, day, zone).map { it.id })
    }

    @Test
    fun selectedDayUsesLocalCalendarBoundariesAcrossDaylightSavingChange() {
        val zone = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 3, 29)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            StudyTaskEntity(id = 1, title = "شروع روز", dueAt = start),
            StudyTaskEntity(id = 2, title = "انتهای روز", dueAt = end - 1),
            StudyTaskEntity(id = 3, title = "روز بعد", dueAt = end)
        )
        assertEquals(listOf(1L, 2L), PlannerPolicy.tasksForLocalDate(tasks, day, zone).map { it.id })
        assertEquals(23 * 60 * 60 * 1000L, end - start)
    }

    @Test
    fun rolloverMovesOpenTaskToNextLocalDayAndPreservesReminderOffset() {
        val zone = ZoneId.of("Europe/Berlin")
        val due = java.time.LocalDateTime.of(2026, 11, 13, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val reminder = java.time.LocalDateTime.of(2026, 11, 13, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val task = StudyTaskEntity(id = 9, title = "کار", dueAt = due, remindAt = reminder)
        val moved = PlannerPolicy.moveIncompleteTaskToTomorrow(task, zone)
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
            StudyTaskEntity(id = 10, title = "DST", dueAt = due, remindAt = reminder), zone
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
            StudyTaskEntity(id = 1, title = "انجام‌شده", dueAt = 1000L, completed = true)
        ))
        assertEquals(null, PlannerPolicy.moveIncompleteTaskToTomorrow(
            StudyTaskEntity(id = 2, title = "بدون موعد")
        ))
    }
}
