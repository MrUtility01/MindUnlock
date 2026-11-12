package com.mindunlock.app

import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ProgressEntity

data class StudySessionPlan(
    val learnMinutes: Int,
    val practiceMinutes: Int,
    val reviewMinutes: Int
) {
    val totalMinutes: Int get() = learnMinutes + practiceMinutes + reviewMinutes
}

object LearningPathPolicy {
    fun sessionPlan(requestedMinutes: Int): StudySessionPlan {
        val total = requestedMinutes.coerceIn(15, 90)
        val learn = total / 3
        val practice = total / 2
        return StudySessionPlan(learn, practice, total - learn - practice)
    }

    fun nextIncompleteChapter(
        chapters: List<ChapterEntity>,
        progress: List<ProgressEntity>
    ): ChapterEntity? {
        val ordered = chapters.sortedBy { it.orderIndex }
        return ordered.firstOrNull { chapter ->
            val chapterProgress = progress.filter { it.chapterId == chapter.id }
            if (chapter.itemCount <= 0) {
                chapterProgress.isEmpty() || chapterProgress.any { !it.completed }
            } else {
                chapterProgress.count { it.completed } < chapter.itemCount
            }
        } ?: ordered.firstOrNull()
    }
}
