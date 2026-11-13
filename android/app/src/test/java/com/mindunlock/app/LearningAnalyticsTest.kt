package com.mindunlock.app

import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ContentItemEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.local.LearningSessionEntity
import com.mindunlock.app.data.local.LearningSessionFeedbackEntity
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAnalyticsTest {
    private val now = 1_800_000_000_000L

    @Test
    fun calculatesLearningTotalsAndChapterAccuracy() {
        val chapter = ChapterEntity(id = "network", title = "شبکه", itemCount = 3)
        val items = listOf(
            ContentItemEntity(uid = "a", chapterId = "network", type = "flashcard"),
            ContentItemEntity(uid = "b", chapterId = "network", type = "question"),
            ContentItemEntity(uid = "c", chapterId = "network", type = "question", metadataJson = "{\"options\":[\"A\",\"B\"],\"correct_index\":1}"),
            ContentItemEntity(uid = "d", chapterId = "network", type = "lesson", sourceStatus = "unverified")
        )
        val progress = listOf(
            ProgressEntity(itemUid = "a", chapterId = "network", completed = true, attempts = 3, correct = 0, wrong = 3, quality = 2, dueAt = now - 1, updatedAt = now),
            ProgressEntity(itemUid = "b", chapterId = "network", attempts = 3, correct = 3, wrong = 0, quality = 4, dueAt = now + 1000, updatedAt = now)
        )
        val sessions = listOf(
            LearningSessionEntity(id = 1, chapterId = "network", topicTitle = "شبکه", startedAt = now - 3_600_000L, endedAt = now, durationSeconds = 3600L),
            LearningSessionEntity(id = 2, topicTitle = "جلسه فعال", startedAt = now - 900_000L)
        )
        val result = LearningAnalytics.build(listOf(chapter), items, progress, emptyList(), sessions, now, ZoneOffset.UTC)
        assertEquals(3, result.totalItems)
        assertEquals(1, result.completedItems)
        assertEquals(6, result.attempts)
        assertEquals(3, result.correct)
        assertEquals(3, result.wrong)
        assertEquals(50, result.accuracyPercent)
        assertEquals(2, result.scheduledReviewsDue) // unseen valid question plus overdue flashcard
        assertEquals(1, result.weakItems)
        assertEquals(60L, result.totalStudyMinutes)
        assertEquals(1, result.completedSessions)
        assertEquals(60L, result.averageSessionMinutes)
        assertEquals(60L, result.recentActivity.last().sessionMinutes)
        assertEquals(PersianDate.format(result.recentActivity.last().date), result.recentActivity.last().dateLabel)
        assertTrue(result.chapters.single().needsPractice)
    }

    @Test
    fun derivesStudyTimeFromStartAndEndTimestampsInsteadOfCachedDuration() {
        val accurate = LearningSessionEntity(
            id = 31,
            topicTitle = "Timestamp source",
            startedAt = now - 60_000L,
            endedAt = now,
            durationSeconds = 99_999L
        )
        val invalidReversed = LearningSessionEntity(
            id = 32,
            topicTitle = "Invalid clock range",
            startedAt = now,
            endedAt = now - 1_000L,
            durationSeconds = 50_000L
        )
        val active = LearningSessionEntity(
            id = 33,
            topicTitle = "Still active",
            startedAt = now - 120_000L
        )

        val result = LearningAnalytics.build(
            chapters = emptyList(),
            items = emptyList(),
            progress = emptyList(),
            tasks = emptyList(),
            sessions = listOf(accurate, invalidReversed, active),
            now = now,
            zone = ZoneOffset.UTC
        )

        assertEquals(1, result.completedSessions)
        assertEquals(1L, result.totalStudyMinutes)
        assertEquals(1L, result.averageSessionMinutes)
        assertEquals(1L, result.recentActivity.last().sessionMinutes)
    }

    @Test
    fun ignoresFutureSessionsAndBoundsCorruptedProgressCounters() {
        val item = ContentItemEntity(uid = "bounded", chapterId = "network")
        val malformed = ProgressEntity(
            itemUid = "bounded",
            chapterId = "network",
            attempts = 2,
            correct = 9,
            wrong = 7,
            quality = 9,
            intervalDays = -4
        )
        val valid = LearningSessionEntity(
            id = 1,
            topicTitle = "Completed",
            startedAt = now - 60_000L,
            endedAt = now,
            durationSeconds = 1L
        )
        val futureEnd = LearningSessionEntity(
            id = 2,
            topicTitle = "Future end",
            startedAt = now - 60_000L,
            endedAt = now + 60_000L
        )
        val futureStart = LearningSessionEntity(
            id = 3,
            topicTitle = "Future start",
            startedAt = now + 60_000L,
            endedAt = now + 120_000L
        )

        val result = LearningAnalytics.build(
            chapters = emptyList(),
            items = listOf(item),
            progress = listOf(malformed),
            tasks = emptyList(),
            sessions = listOf(valid, futureEnd, futureStart),
            now = now,
            zone = ZoneOffset.UTC
        )

        assertEquals(2, result.attempts)
        assertEquals(2, result.correct)
        assertEquals(0, result.wrong)
        assertEquals(100, result.accuracyPercent)
        assertEquals(1, result.completedSessions)
        assertEquals(1L, result.totalStudyMinutes)
    }

    @Test
    fun reportsOverdueAndCompletedTasksWithoutReadingPrivateJournalOrInbox() {
        val tasks = listOf(
            StudyTaskEntity(id = 1, title = "Overdue", dueAt = now - 1),
            StudyTaskEntity(id = 2, title = "Future", dueAt = now + 1000),
            StudyTaskEntity(id = 3, title = "Done", completed = true, dueAt = now - 100)
        )
        val result = LearningAnalytics.build(emptyList(), emptyList(), emptyList(), tasks, emptyList(), now, ZoneOffset.UTC)
        assertEquals(2, result.openTasks)
        assertEquals(1, result.overdueTasks)
        assertEquals(1, result.completedTasks)
        assertEquals(7, result.recentActivity.size)
    }


    @Test
    fun summarizesOnlyValidFeedbackForCompletedSessionsAndRecentDays() {
        val completed = LearningSessionEntity(
            id = 21,
            topicTitle = "Subnetting",
            startedAt = now - 3_600_000L,
            endedAt = now,
            durationSeconds = 3600L
        )
        val active = LearningSessionEntity(id = 22, topicTitle = "Active", startedAt = now - 600_000L)
        val feedback = listOf(
            LearningSessionFeedbackEntity(
                sessionId = 21,
                comprehension = 4,
                perceivedDifficulty = 3,
                focus = 5,
                energyBefore = 2,
                energyAfter = 4
            ),
            LearningSessionFeedbackEntity(
                sessionId = 22,
                comprehension = 1,
                perceivedDifficulty = 5,
                focus = 1
            ),
            LearningSessionFeedbackEntity(
                sessionId = 999,
                comprehension = 5,
                perceivedDifficulty = 1,
                focus = 5
            ),
            LearningSessionFeedbackEntity(
                sessionId = 21,
                comprehension = 6,
                perceivedDifficulty = 3,
                focus = 3
            )
        )

        val result = LearningAnalytics.build(
            chapters = emptyList(),
            items = emptyList(),
            progress = emptyList(),
            tasks = emptyList(),
            sessions = listOf(completed, active),
            now = now,
            zone = ZoneOffset.UTC,
            sessionFeedback = feedback
        )

        assertEquals(1, result.sessionReflection.feedbackCount)
        assertEquals(40, result.sessionReflection.averageComprehensionTenths)
        assertEquals(30, result.sessionReflection.averageDifficultyTenths)
        assertEquals(50, result.sessionReflection.averageFocusTenths)
        assertEquals(20, result.sessionReflection.averageEnergyChangeTenths)
        assertEquals(0, result.sessionReflection.lowFocusSessions)
        assertEquals(1, result.sessionReflection.recentDays.last().feedbackCount)
        assertEquals(40, result.sessionReflection.recentDays.last().averageComprehensionTenths)
        assertEquals(50, result.sessionReflection.recentDays.last().averageFocusTenths)
    }

    @Test
    fun reportsEmptyReflectionStatsWithoutInventingScores() {
        val result = LearningAnalytics.build(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), now, ZoneOffset.UTC
        )
        assertEquals(0, result.sessionReflection.feedbackCount)
        assertEquals(null, result.sessionReflection.averageComprehensionTenths)
        assertEquals(null, result.sessionReflection.averageDifficultyTenths)
        assertEquals(null, result.sessionReflection.averageFocusTenths)
        assertEquals(null, result.sessionReflection.averageEnergyChangeTenths)
        assertEquals(0, result.sessionReflection.lowFocusSessions)
        assertEquals(7, result.sessionReflection.recentDays.size)
        assertTrue(result.sessionReflection.recentDays.all { it.feedbackCount == 0 })
    }

    @Test
    fun ignoresProgressForMissingOrUnverifiedItems() {
        val items = listOf(ContentItemEntity(uid = "verified", chapterId = "c", sourceStatus = "verified"))
        val progress = listOf(
            ProgressEntity(itemUid = "verified", chapterId = "c", attempts = 1, correct = 1),
            ProgressEntity(itemUid = "deleted", chapterId = "c", attempts = 20, wrong = 20)
        )
        val result = LearningAnalytics.build(emptyList(), items, progress, emptyList(), emptyList(), now, ZoneOffset.UTC)
        assertEquals(1, result.attempts)
        assertEquals(0, result.weakItems)
        assertFalse(result.chapters.single().needsPractice)
    }
}
