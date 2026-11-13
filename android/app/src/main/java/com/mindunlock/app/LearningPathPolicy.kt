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

    /**
     * Returns the first unfinished chapter in curriculum order.
     * Progress is indexed once (rather than rescanning the complete progress list
     * for every chapter), and a fully completed curriculum has no resume target.
     */
    fun nextIncompleteChapter(
        chapters: List<ChapterEntity>,
        progress: List<ProgressEntity>
    ): ChapterEntity? {
        if (chapters.isEmpty()) return null

        val completedByChapter = progress.asSequence()
            .filter { it.completed }
            .groupBy { it.chapterId }
            .mapValues { (_, records) -> records.asSequence().map { it.itemUid }.toHashSet().size }

        return chapters.asSequence()
            .sortedBy { it.orderIndex }
            .firstOrNull { chapter ->
                val completedCount = completedByChapter[chapter.id] ?: 0
                if (chapter.itemCount <= 0) {
                    progress.none { it.chapterId == chapter.id } ||
                        progress.any { it.chapterId == chapter.id && !it.completed }
                } else {
                    completedCount < chapter.itemCount
                }
            }
    }
}

enum class ResumeActionKind {
    REVIEW,
    OVERDUE_TASKS,
    TODAY_TASKS,
    ESSENTIAL_TOPICS,
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
        overdueTaskCount: Int,
        todayTaskCount: Int,
        activeTopicTitle: String?,
        lastReadItemUid: String?,
        lastReadChapterId: String?,
        fallbackChapterId: String?,
        essentialTopicCount: Int = 0
    ): ResumeAction = when {
        dueReviewCount > 0 -> ResumeAction(
            ResumeActionKind.REVIEW,
            "مرور مطالب سررسیدشده",
            "$dueReviewCount مورد برای مرور آماده است."
        )
        overdueTaskCount > 0 -> ResumeAction(
            ResumeActionKind.OVERDUE_TASKS,
            "رسیدگی به کارهای عقب‌افتاده",
            "$overdueTaskCount کار از موعد خود گذشته است."
        )
        todayTaskCount > 0 -> ResumeAction(
            ResumeActionKind.TODAY_TASKS,
            "رسیدگی به برنامهٔ امروز",
            "$todayTaskCount کار زمان‌بندی‌شده برای امروز داری."
        )
        essentialTopicCount > 0 -> ResumeAction(
            ResumeActionKind.ESSENTIAL_TOPICS,
            "رسیدگی به سؤال‌های ضروری",
            "$essentialTopicCount سؤال ضروری در انتظار تصمیم یا پیگیری است."
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
