package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningPersonalizationTest {
    @Test fun practicalPreferenceBuildsScenarioFirstPlan() {
        val answers = List(20) { 2 }.toMutableList().apply { for (i in 4..7) this[i] = 5 }
        val plan = LearningPersonalization.build(answers, 45, 2, "guided")!!
        assertEquals("عملی", plan.dominantPreference)
        assertTrue(plan.teachingEmphasis.contains("سناریوی واقعی"))
        assertEquals("سناریوی واقعی", plan.sessionStructure.first())
        assertEquals(45, plan.recommendedSessionMinutes)
        assertTrue(plan.modeDescription.contains("هدایت‌شده"))
    }
    @Test fun visualPreferenceBuildsConceptMapPlanAndClampsSettings() {
        val answers = List(20) { 1 }.toMutableList().apply { for (i in 0..3) this[i] = 5 }
        val plan = LearningPersonalization.build(answers, 120, 0, "flexible")!!
        assertEquals("دیداری", plan.dominantPreference)
        assertTrue(plan.teachingEmphasis.contains("نقشه مفهومی"))
        assertEquals(90, plan.recommendedSessionMinutes)
        assertEquals(1, plan.maxActiveTopics)
        assertTrue(plan.modeDescription.contains("آزاد"))
    }
    @Test fun incompleteOrInvalidAnswersAreNotPersonalized() {
        assertNull(LearningPersonalization.build(List(19) { 3 }, 30, 2, "guided"))
        assertNull(LearningPersonalization.build(List(20) { 6 }, 30, 2, "guided"))
    }
    @Test fun tiesUseStableQuestionnaireOrder() {
        assertEquals("دیداری", LearningPersonalization.build(List(20) { 4 }, 30, 2, "guided")!!.dominantPreference)
    }
}
