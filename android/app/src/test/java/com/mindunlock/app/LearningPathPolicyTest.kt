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
}
