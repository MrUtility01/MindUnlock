package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStagePolicyTest {
    @Test
    fun definesNineStagesInStableOrder() {
        assertEquals(9, LearningStagePolicy.stages.size)
        assertEquals((0..8).toList(), LearningStagePolicy.stages.map { it.order })
        assertEquals("why", LearningStagePolicy.stages.first().id)
        assertEquals("knowledge", LearningStagePolicy.stages.last().id)
    }

    @Test
    fun guidedModeUnlocksOnlyTheNextSequentialStage() {
        val completed = setOf("why", "overview")
        assertTrue(LearningStagePolicy.canComplete("concepts", completed, "guided"))
        assertFalse(LearningStagePolicy.canComplete("scenario", completed, "guided"))
        assertFalse(LearningStagePolicy.canComplete("unknown", completed, "guided"))
        assertEquals("concepts", LearningStagePolicy.nextGuidedStage(completed)?.id)
    }

    @Test
    fun flexibleModeAllowsOutOfOrderCompletion() {
        val completed = setOf("why")
        assertTrue(LearningStagePolicy.canComplete("scenario", completed, "flexible"))
        assertTrue(LearningStagePolicy.canComplete("knowledge", completed, "flexible"))
        assertFalse(LearningStagePolicy.canComplete("unknown", completed, "flexible"))
    }

    @Test
    fun completionPercentageIgnoresUnknownIds() {
        assertEquals(0, LearningStagePolicy.completionPercent(emptySet()))
        assertEquals(22, LearningStagePolicy.completionPercent(setOf("why", "overview", "not-a-stage")))
        assertEquals(100, LearningStagePolicy.completionPercent(LearningStagePolicy.stages.map { it.id }.toSet()))
    }
}
