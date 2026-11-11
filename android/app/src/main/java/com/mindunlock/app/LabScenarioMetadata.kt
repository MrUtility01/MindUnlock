package com.mindunlock.app

import com.google.gson.JsonParser

data class LabScenarioMetadata(
    val goal: String = "",
    val prerequisites: List<String> = emptyList(),
    val topology: String = "",
    val equipment: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val expectedOutput: List<String> = emptyList(),
    val troubleshooting: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
    val difficulty: String = "",
    val estimatedMinutes: Int? = null
) {
    val hasStructuredDetails: Boolean
        get() = goal.isNotBlank() || prerequisites.isNotEmpty() || topology.isNotBlank() ||
            equipment.isNotEmpty() || services.isNotEmpty() || commands.isNotEmpty() ||
            expectedOutput.isNotEmpty() || troubleshooting.isNotEmpty() ||
            questions.isNotEmpty() || difficulty.isNotBlank() || estimatedMinutes != null
}

object LabScenarioParser {
    fun parse(json: String?): LabScenarioMetadata {
        if (json.isNullOrBlank()) return LabScenarioMetadata()
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return LabScenarioMetadata()
        val source = root.get("scenario")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        fun scalar(vararg keys: String): String {
            keys.forEach { key ->
                val value = source.get(key) ?: return@forEach
                if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    return value.asString.trim()
                }
            }
            return ""
        }
        fun list(vararg keys: String): List<String> {
            keys.forEach { key ->
                val value = source.get(key) ?: return@forEach
                val result = when {
                    value.isJsonArray -> value.asJsonArray.mapNotNull { item ->
                        when {
                            item.isJsonPrimitive -> item.asString.trim().takeIf(String::isNotBlank)
                            item.isJsonObject -> item.asJsonObject.entrySet().joinToString(" · ") { (k, v) -> "$k: $v" }.takeIf(String::isNotBlank)
                            else -> null
                        }
                    }
                    value.isJsonPrimitive -> value.asString.split("\\n").map(String::trim).filter(String::isNotBlank)
                    else -> emptyList()
                }
                if (result.isNotEmpty()) return result
            }
            return emptyList()
        }
        val minutes = listOf("estimated_minutes", "duration_minutes", "minutes").firstNotNullOfOrNull { key ->
            source.get(key)?.let { runCatching { it.asInt }.getOrNull() }
        }?.takeIf { it in 1..1440 }
        return LabScenarioMetadata(
            goal = scalar("goal", "objective", "target"),
            prerequisites = list("prerequisites", "requirements", "before_you_start"),
            topology = scalar("topology", "network_topology", "diagram_description"),
            equipment = list("equipment", "devices", "hardware"),
            services = list("services", "systems"),
            commands = list("commands", "steps", "procedure"),
            expectedOutput = list("expected_output", "expected_results", "validation"),
            troubleshooting = list("troubleshooting", "common_errors", "faults"),
            questions = list("questions", "review_questions", "checkpoints"),
            difficulty = scalar("difficulty", "level"),
            estimatedMinutes = minutes
        )
    }
}
