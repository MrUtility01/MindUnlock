package com.mindunlock.app

import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LearningPathPolicyTest {
    @Test
    fun defaultThirtyMinuteSessionUsesTenFifteenFiveSplit() {
        val plan = LearningPathPolicy.sessionPlan(30)
        assertEquals(10, plan.learnMinutes)
        assertEquals(15, plan.practiceMinutes)
        assertEquals(5, plan.reviewMinutes)
        assertEquals(30, plan.totalMinutes)
    }

    @Test
    fun sessionDurationIsBoundedAndAlwaysAddsUp() {
        assertEquals(15, LearningPathPolicy.sessionPlan(1).totalMinutes)
        assertEquals(90, LearningPathPolicy.sessionPlan(120).totalMinutes)
        for (minutes in 15..90) {
            assertEquals(minutes, LearningPathPolicy.sessionPlan(minutes).totalMinutes)
        }
    }

    @Test
    fun nextChapterIsFirstChapterWithUnfinishedItems() {
        val first = ChapterEntity(id = "one", title = "اول", orderIndex = 1, itemCount = 2)
        val second = ChapterEntity(id = "two", title = "دوم", orderIndex = 2, itemCount = 2)
        val progress = listOf(
            ProgressEntity(itemUid = "a", chapterId = "one", completed = true),
            ProgressEntity(itemUid = "b", chapterId = "one", completed = true),
            ProgressEntity(itemUid = "c", chapterId = "two", completed = true)
        )
        val next = LearningPathPolicy.nextIncompleteChapter(listOf(second, first), progress)
        assertNotNull(next)
        assertEquals("two", next?.id)
    }

    @Test
    fun dueReviewsTakePriorityOverEveryOtherAction() {
        val action = ResumeActionPolicy.select(2, 0, 3, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.REVIEW, action.kind)
        assertEquals("مرور مطالب سررسیدشده", action.title)
    }

    @Test
    fun overdueTasksTakePriorityOverTodaysTasksAndLearning() {
        val action = ResumeActionPolicy.select(0, 2, 3, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.OVERDUE_TASKS, action.kind)
        assertEquals("رسیدگی به کارهای عقب‌افتاده", action.title)
    }

    @Test
    fun dueReviewsTakePriorityOverOverdueTasks() {
        val action = ResumeActionPolicy.select(1, 2, 3, null, null, null, null)
        assertEquals(ResumeActionKind.REVIEW, action.kind)
    }

    @Test
    fun todaysTasksTakePriorityWhenNoReviewIsDue() {
        val action = ResumeActionPolicy.select(0, 0, 1, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.TODAY_TASKS, action.kind)
    }

    @Test
    fun essentialSideQuestionsTakePriorityOverActiveTopicAndReadingResume() {
        val action = ResumeActionPolicy.select(
            dueReviewCount = 0,
            overdueTaskCount = 0,
            todayTaskCount = 0,
            activeTopicTitle = "موضوع فعال",
            lastReadItemUid = "item-1",
            lastReadChapterId = "chapter-1",
            fallbackChapterId = "chapter-0",
            essentialTopicCount = 2
        )
        assertEquals(ResumeActionKind.ESSENTIAL_TOPICS, action.kind)
        assertEquals("رسیدگی به سؤال‌های ضروری", action.title)
    }

    @Test
    fun activeTopicTakesPriorityOverReadingResume() {
        val action = ResumeActionPolicy.select(0, 0, 0, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.ACTIVE_TOPIC, action.kind)
    }

    @Test
    fun exactLastReadItemIsPreferredOverChapterResume() {
        val action = ResumeActionPolicy.select(0, 0, 0, null, "item-42", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.RESUME_ITEM, action.kind)
        assertEquals("item-42", action.targetId)
    }

    @Test
    fun lastSessionChapterIsUsedWhenNoLastReadItemExists() {
        val action = ResumeActionPolicy.select(0, 0, 0, null, null, "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.RESUME_CHAPTER, action.kind)
        assertEquals("chapter-1", action.targetId)
    }

    @Test
    fun emptyStateStartsLearningWithoutInventingATarget() {
        val action = ResumeActionPolicy.select(0, 0, 0, null, null, null, null)
        assertEquals(ResumeActionKind.START_LEARNING, action.kind)
        assertEquals(null, action.targetId)
    }
}
