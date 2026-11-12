package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeActionPolicyTest {
    @Test
    fun dueReviewsTakePriorityOverEveryOtherAction() {
        val action = ResumeActionPolicy.select(2, 3, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.REVIEW, action.kind)
        assertEquals("مرور مطالب سررسیدشده", action.title)
    }

    @Test
    fun todaysTasksTakePriorityWhenNoReviewIsDue() {
        val action = ResumeActionPolicy.select(0, 1, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.TODAY_TASKS, action.kind)
    }

    @Test
    fun activeTopicTakesPriorityOverReadingResume() {
        val action = ResumeActionPolicy.select(0, 0, "موضوع فعال", "item-1", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.ACTIVE_TOPIC, action.kind)
    }

    @Test
    fun exactLastReadItemIsPreferredOverChapterResume() {
        val action = ResumeActionPolicy.select(0, 0, null, "item-42", "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.RESUME_ITEM, action.kind)
        assertEquals("item-42", action.targetId)
    }

    @Test
    fun lastSessionChapterIsUsedWhenNoLastReadItemExists() {
        val action = ResumeActionPolicy.select(0, 0, null, null, "chapter-1", "chapter-0")
        assertEquals(ResumeActionKind.RESUME_CHAPTER, action.kind)
        assertEquals("chapter-1", action.targetId)
    }

    @Test
    fun emptyStateStartsLearningWithoutInventingATarget() {
        val action = ResumeActionPolicy.select(0, 0, null, null, null, null)
        assertEquals(ResumeActionKind.START_LEARNING, action.kind)
        assertEquals(null, action.targetId)
    }
}
