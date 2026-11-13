package com.mindunlock.app

import android.content.Context
import com.mindunlock.app.data.local.UserDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Human-readable export of user-owned records. This complements (does not replace)
 * the versioned, integrity-checked JSON backup used for lossless restore.
 */
object UserDataMarkdownExporter {
    suspend fun export(context: Context, userDb: UserDatabase): String {
        val document = UserDataBackupCodec.decode(UserDataBackupManager.export(context, userDb))
        val p = document.payload
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        fun stamp(value: Long?): String = value?.takeIf { it > 0 }?.let { date.format(Date(it)) } ?: "—"
        fun clean(value: String): String = value.replace("\r", "").replace("\n", "  ").replace("|", "\\|").trim()
        fun section(title: String, rows: List<String>): String = buildString {
            append("\n## ").append(title).append("\n\n")
            if (rows.isEmpty()) append("_موردی ثبت نشده است._\n")
            else rows.forEach { append("- ").append(it).append("\n") }
        }

        return buildString {
            append("# خروجی اطلاعات شخصی MindUnlock\n\n")
            append("- زمان خروجی: ").append(stamp(document.exportedAt)).append("\n")
            append("- نسخه برنامه: ").append(clean(document.appVersion)).append("\n")
            append("- تعداد رکوردها: ").append(p.totalRecords).append("\n")
            append("> این فایل برای مطالعه و انتقال متنی است؛ برای بازیابی کامل و بدون اتلاف، از پشتیبان JSON استفاده کن.\n")
            append(section("یادداشت‌ها", p.notes.map { "**${clean(it.title)}** — ${clean(it.body)} _(ویرایش: ${stamp(it.updatedAt)})_" }))
            append(section("وظایف", p.tasks.map { "${if (it.completed) "[x]" else "[ ]"} **${clean(it.title)}** — ${clean(it.details)} _(موعد: ${stamp(it.dueAt)})_" }))
            append(section("صندوق ورودی", p.mindDump.map { "**${clean(it.category)} · ${clean(it.status)}** — ${clean(it.content)} _(ثبت: ${stamp(it.createdAt)})_" }))
            append(section("ژورنال", p.journalEntries.map { "**${clean(it.title.ifBlank { "بدون عنوان" })}** — ${clean(it.body)} _(حال‌وهوا: ${clean(it.mood.orEmpty())}؛ تاریخ: ${stamp(it.createdAt)})_" }))
            append(section("موضوعات برای بعد", p.laterTopics.map { "**${clean(it.title)}** — ${clean(it.details)} _(اولویت: ${it.priority}؛ بازبینی: ${stamp(it.revisitAt)})_" }))
            append(section("نشانک‌ها", p.bookmarks.map { "${clean(it.itemUid)} _(فصل: ${clean(it.chapterId)}؛ ثبت: ${stamp(it.createdAt)})_" }))
            append(section("پیشرفت یادگیری", p.progress.map { "${clean(it.itemUid)} — ${if (it.completed) "تکمیل‌شده" else "در حال یادگیری"}؛ تلاش: ${it.attempts}؛ درست: ${it.correct}؛ نادرست: ${it.wrong}" }))
            append(section("جلسه‌های یادگیری", p.learningSessions.map { "**${clean(it.topicTitle)}** — شروع: ${stamp(it.startedAt)}؛ پایان: ${stamp(it.endedAt)}؛ منبع: ${clean(it.source)}" }))
            append(section("پاسخ‌های آزمایشگاه", p.labQuestionResponses.map { "**${clean(it.itemUid)} · پرسش ${it.questionIndex + 1}** — ${clean(it.answer)}" }))
            append(section("پیشرفت مراحل آزمایشگاه", p.labStepProgress.map { "${clean(it.itemUid)} — مرحله ${it.stepIndex + 1}: ${if (it.completed) "انجام‌شده" else "ناتمام"}" }))
            append(section("نقشه دانش — گره‌ها", p.mindMapNodes.map { "**${clean(it.title)}** — ${clean(it.description)} _(نوع: ${clean(it.nodeType)})_" }))
            append(section("نقشه دانش — ارتباط‌ها", p.mindMapEdges.map { "گره ${it.sourceNodeId} → گره ${it.targetNodeId} _(رابطه: ${clean(it.relationType)})_" }))
            append(section("بازخورد جلسه‌ها", p.learningSessionFeedback.map { "جلسه ${it.sessionId}: درک ${it.comprehension}/5، تمرکز ${it.focus}/5، دشواری ${it.perceivedDifficulty}/5؛ ${clean(it.note.orEmpty())}" }))
            append(section("مراحل مسیر یادگیری", p.learningStageProgress.map { "${clean(it.chapterId)} — ${clean(it.stageId)}: ${if (it.completed) "تکمیل" else "ناتمام"}" }))
            append(section("پروفایل یادگیری", p.learningProfile.map { "حالت: ${clean(it.guidedMode)}؛ زمان جلسه: ${it.sessionMinutes} دقیقه؛ حداکثر موضوع: ${it.maxTopics}؛ پاسخ‌ها: ${clean(it.answersCsv)}" }))
            append("\n---\n_ساخته‌شده توسط MindUnlock_\n")
        }
    }
}
