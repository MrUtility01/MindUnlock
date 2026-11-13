package com.mindunlock.app.data.local

/**
 * Converts a captured thought into a planner task without discarding the source text.
 * The category is used only to distinguish project work from ordinary tasks.
 */
object MindDumpTaskMapper {
    fun toStudyTask(entry: MindDumpEntity): StudyTaskEntity {
        val title = entry.content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(80)
            ?: "کار جدید"
        return StudyTaskEntity(
            title = title,
            details = entry.content,
            taskType = if (entry.category == "project") "project" else "task"
        )
    }
}
