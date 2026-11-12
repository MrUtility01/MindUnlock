package com.mindunlock.app

import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ContentItemEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.local.LearningSessionEntity
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
