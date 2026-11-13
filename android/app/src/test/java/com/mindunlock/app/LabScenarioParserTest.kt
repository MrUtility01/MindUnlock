package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabScenarioParserTest {
    @Test
    fun parsesStructuredScenarioAndAliases() {
        val result = LabScenarioParser.parse(
            """{"scenario":{"objective":"Configure VLANs","requirements":["Switch","Two PCs"],"network_topology":"PC1—SW1—PC2","devices":["SW1"],"systems":["DHCP"],"procedure":["Create VLAN 10","Assign access port"],"expected_results":["PCs receive correct subnet"],"common_errors":["Wrong access VLAN"],"review_questions":["Why separate broadcast domains?"],"level":"Intermediate","duration_minutes":45}}"""
        )
        assertEquals("Configure VLANs", result.goal)
        assertEquals(listOf("Switch", "Two PCs"), result.prerequisites)
        assertEquals("PC1—SW1—PC2", result.topology)
        assertEquals(listOf("SW1"), result.equipment)
        assertEquals(listOf("DHCP"), result.services)
        assertEquals(2, result.commands.size)
        assertEquals(45, result.estimatedMinutes)
        assertTrue(result.hasStructuredDetails)
    }

    @Test
    fun toleratesMissingOrInvalidMetadata() {
        assertFalse(LabScenarioParser.parse(null).hasStructuredDetails)
        assertFalse(LabScenarioParser.parse("not-json").hasStructuredDetails)
        assertFalse(LabScenarioParser.parse("{}").hasStructuredDetails)
    }

    @Test
    fun parsesFlatMetadataAndNewlineSeparatedSteps() {
        val result = LabScenarioParser.parse(
            """{"goal":"Inspect DNS","commands":"nslookup example.local\nipconfig /all","difficulty":"Basic","minutes":20}"""
        )
        assertEquals("Inspect DNS", result.goal)
        assertEquals(listOf("nslookup example.local", "ipconfig /all"), result.commands)
        assertEquals(20, result.estimatedMinutes)
    }

    @Test
    fun parsesReferenceAnswersForLearningQuestions() {
        val result = LabScenarioParser.parse(
            """{"questions":["Why use VLANs?","What does DHCP provide?"],"question_answers":["To separate broadcast domains.","Automatic IP configuration."]}"""
        )
        assertEquals(2, result.questions.size)
        assertEquals(
            listOf("To separate broadcast domains.", "Automatic IP configuration."),
            result.questionAnswers
        )
        assertTrue(result.hasStructuredDetails)
    }

    @Test
    fun ignoresUnreasonableDuration() {
        val result = LabScenarioParser.parse("""{"estimated_minutes":99999}""")
        assertEquals(null, result.estimatedMinutes)
    }
}
