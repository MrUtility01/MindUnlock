package com.mindunlock.app

import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.MindDumpEntity

/**
 * Resolves personal learning topics through their original Mind Dump entry.
 * The database remains the source of truth; no duplicate learning records are created.
 */
object LearningPersonalTopicPolicy {
    fun topicsForLearningEntries(
        topics: List<LaterTopicEntity>,
        entries: List<MindDumpEntity>
    ): List<LaterTopicEntity> {
        val eligibleSourceIds = entries.asSequence()
            .filter { it.category == "learning" && it.status == "later" }
            .map { it.id }
            .toHashSet()

        return topics.asSequence()
            .filter { topic -> topic.status != "done" && topic.sourceMindDumpId?.let(eligibleSourceIds::contains) == true }
            .distinctBy { it.sourceMindDumpId }
            .toList()
    }
}
