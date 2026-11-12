package com.mindunlock.app

data class LearningStageDefinition(
    val id: String,
    val title: String,
    val description: String,
    val order: Int
)

/**
 * Nine-stage lesson workflow. Guided mode unlocks stages in order; flexible mode
 * allows any stage while retaining the same learning objectives.
 */
object LearningStagePolicy {
    val stages = listOf(
        LearningStageDefinition("why", "چرایی یادگیری", "هدف، کاربرد و مسئله‌ای که این مبحث حل می‌کند.", 0),
        LearningStageDefinition("overview", "نمای کلی و نقشه مفهومی", "جایگاه موضوع و ارتباط اجزای اصلی را مشخص کن.", 1),
        LearningStageDefinition("concepts", "مفاهیم و اجزا", "اصطلاحات، اجزا، پیش‌نیازها و رابطه آن‌ها را بشناس.", 2),
        LearningStageDefinition("explanation", "توضیح آموزشی", "مفهوم را مرحله‌به‌مرحله با مثال و جزئیات یاد بگیر.", 3),
        LearningStageDefinition("real_world", "کاربرد در دنیای واقعی", "کاربرد موضوع را در محیط کار و زیرساخت واقعی بررسی کن.", 4),
        LearningStageDefinition("scenario", "سناریوی عملی", "یک سناریوی واقعی را طراحی یا اجرا و نتیجه را ثبت کن.", 5),
        LearningStageDefinition("practice", "تمرین و پرسش", "بدون نگاه به پاسخ، سؤال‌ها و تمرین‌ها را حل کن.", 6),
        LearningStageDefinition("recap", "جمع‌بندی شخصی", "موضوع را با زبان خودت خلاصه و نقاط مبهم را مشخص کن.", 7),
        LearningStageDefinition("knowledge", "ثبت در پایگاه دانش", "نکته‌ها، فرمان‌ها و ارتباط مفاهیم را برای مراجعه بعدی ثبت کن.", 8)
    )

    fun canComplete(stageId: String, completedStageIds: Set<String>, mode: String): Boolean {
        val index = stages.indexOfFirst { it.id == stageId }
        if (index < 0) return false
        if (mode == "flexible") return true
        return stages.take(index).all { it.id in completedStageIds }
    }

    fun nextGuidedStage(completedStageIds: Set<String>): LearningStageDefinition? =
        stages.firstOrNull { it.id !in completedStageIds }

    fun completionPercent(completedStageIds: Set<String>): Int =
        (stages.count { it.id in completedStageIds } * 100) / stages.size
}
