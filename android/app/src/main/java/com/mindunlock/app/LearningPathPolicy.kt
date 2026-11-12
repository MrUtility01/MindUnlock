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

enum class ResumeActionKind {
    REVIEW,
    TODAY_TASKS,
    ACTIVE_TOPIC,
    RESUME_ITEM,
    RESUME_CHAPTER,
    START_LEARNING
}

data class ResumeAction(
    val kind: ResumeActionKind,
    val title: String,
    val description: String,
    val targetId: String? = null
)

/** Selects one explainable dashboard action using an explicit, testable priority order. */
object ResumeActionPolicy {
    fun select(
        dueReviewCount: Int,
        todayTaskCount: Int,
        activeTopicTitle: String?,
        lastReadItemUid: String?,
        lastReadChapterId: String?,
        fallbackChapterId: String?
    ): ResumeAction = when {
        dueReviewCount > 0 -> ResumeAction(
            ResumeActionKind.REVIEW,
            "مرور مطالب سررسیدشده",
            "$dueReviewCount مورد برای مرور آماده است."
        )
        todayTaskCount > 0 -> ResumeAction(
            ResumeActionKind.TODAY_TASKS,
            "رسیدگی به برنامهٔ امروز",
            "$todayTaskCount کار زمان‌بندی‌شده برای امروز داری."
        )
        !activeTopicTitle.isNullOrBlank() -> ResumeAction(
            ResumeActionKind.ACTIVE_TOPIC,
            "پیگیری موضوع فعال: $activeTopicTitle",
            "به موضوعی که برای پیگیری انتخاب کرده‌ای برگرد."
        )
        !lastReadItemUid.isNullOrBlank() -> ResumeAction(
            ResumeActionKind.RESUME_ITEM,
            "ادامهٔ آخرین مطلب",
            "از همان مطلبی که آخرین بار باز کرده‌ای ادامه بده.",
            lastReadItemUid
        )
        !lastReadChapterId.isNullOrBlank() -> ResumeAction(
            ResumeActionKind.RESUME_CHAPTER,
            "ادامهٔ فصل اخیر",
            "مطالعه را از فصل آخرین جلسه ادامه بده.",
            lastReadChapterId
        )
        !fallbackChapterId.isNullOrBlank() -> ResumeAction(
            ResumeActionKind.RESUME_CHAPTER,
            "شروع مطالعه",
            "از اولین فصل موجود شروع کن.",
            fallbackChapterId
        )
        else -> ResumeAction(
            ResumeActionKind.START_LEARNING,
            "انتخاب اولین موضوع یادگیری",
            "یک موضوع یا فصل برای شروع انتخاب کن."
        )
    }
}
