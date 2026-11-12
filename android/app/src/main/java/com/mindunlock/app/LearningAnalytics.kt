package com.mindunlock.app

import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ContentItemEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.local.LearningSessionEntity
import com.google.gson.JsonParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class ChapterLearningStats(
    val chapterId: String,
    val title: String,
    val totalItems: Int,
    val completedItems: Int,
    val attempts: Int,
    val correct: Int,
    val wrong: Int,
    val accuracyPercent: Int,
    val needsPractice: Boolean
)

data class RecentLearningDay(
    val date: LocalDate,
    val label: String,
    val dateLabel: String,
    val updatedItems: Int,
    val sessionMinutes: Long,
    val sessions: Int
)

data class LearningAnalyticsSnapshot(
    val totalItems: Int,
    val completedItems: Int,
    val attempts: Int,
    val correct: Int,
    val wrong: Int,
    val accuracyPercent: Int,
    val scheduledReviewsDue: Int,
    val weakItems: Int,
    val openTasks: Int,
    val overdueTasks: Int,
    val completedTasks: Int,
    val totalStudyMinutes: Long,
    val completedSessions: Int,
    val averageSessionMinutes: Long,
    val chapters: List<ChapterLearningStats>,
    val recentActivity: List<RecentLearningDay>
)

object LearningAnalytics {
    private val reviewTypes = setOf("flashcard", "question", "quiz", "exercise", "practice", "exam")

    fun build(
        chapters: List<ChapterEntity>,
        items: List<ContentItemEntity>,
        progress: List<ProgressEntity>,
        tasks: List<StudyTaskEntity>,
        sessions: List<LearningSessionEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): LearningAnalyticsSnapshot {
        val verified = items.filter { it.sourceStatus == "verified" }
        val validIds = verified.mapTo(HashSet()) { it.uid }
        val records = progress.filter { it.itemUid in validIds }
        val byItem = records.associateBy { it.itemUid }
        val totalAttempts = records.sumOf { it.attempts }
        val totalCorrect = records.sumOf { it.correct }
        val totalWrong = records.sumOf { it.wrong }
        val accuracy = if (totalAttempts == 0) 0 else (totalCorrect * 100f / totalAttempts).roundToInt()
        val weak = records.count { it.attempts > 0 && (it.wrong > it.correct || it.quality in 1..2) }
        val due = verified.count { item ->
            if (!isReviewCandidate(item)) false
            else {
                val record = byItem[item.uid]
                record == null || (record.dueAt != null && record.dueAt <= now)
            }
        }
        val chapterStats = verified.groupBy { it.chapterId }.map { (chapterId, chapterItems) ->
            val chapterRecords = chapterItems.mapNotNull { byItem[it.uid] }
            val attempts = chapterRecords.sumOf { it.attempts }
            val correct = chapterRecords.sumOf { it.correct }
            val wrong = chapterRecords.sumOf { it.wrong }
            val chapterAccuracy = if (attempts == 0) 0 else (correct * 100f / attempts).roundToInt()
            ChapterLearningStats(
                chapterId = chapterId,
                title = chapters.firstOrNull { it.id == chapterId }?.title ?: chapterId,
                totalItems = chapterItems.size,
                completedItems = chapterRecords.count { it.completed },
                attempts = attempts,
                correct = correct,
                wrong = wrong,
                accuracyPercent = chapterAccuracy,
                needsPractice = attempts > 0 && (wrong > correct || (attempts >= 3 && chapterAccuracy < 60))
            )
        }.sortedWith(compareBy<ChapterLearningStats> { !it.needsPractice }.thenBy { it.accuracyPercent }.thenBy { it.title })

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val completedSessions = sessions.filter { it.endedAt != null }
        val totalStudyMinutes = completedSessions.sumOf { it.durationSeconds.coerceAtLeast(0L) } / 60L
        val recent = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val count = records.count { record ->
                Instant.ofEpochMilli(record.updatedAt).atZone(zone).toLocalDate() == date
            }
            val daySessions = completedSessions.filter { session ->
                Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate() == date
            }
            RecentLearningDay(
                date = date,
                label = persianWeekday(date.dayOfWeek.value),
                dateLabel = PersianDate.format(date),
                updatedItems = count,
                sessionMinutes = daySessions.sumOf { it.durationSeconds.coerceAtLeast(0L) } / 60L,
                sessions = daySessions.size
            )
        }
        val open = tasks.filterNot { it.completed }
        return LearningAnalyticsSnapshot(
            totalItems = verified.size,
            completedItems = records.count { it.completed },
            attempts = totalAttempts,
            correct = totalCorrect,
            wrong = totalWrong,
            accuracyPercent = accuracy,
            scheduledReviewsDue = due,
            weakItems = weak,
            openTasks = open.size,
            overdueTasks = open.count { it.dueAt != null && it.dueAt < now },
            completedTasks = tasks.count { it.completed },
            totalStudyMinutes = totalStudyMinutes,
            completedSessions = completedSessions.size,
            averageSessionMinutes = if (completedSessions.isEmpty()) 0L else totalStudyMinutes / completedSessions.size,
            chapters = chapterStats,
            recentActivity = recent
        )
    }

    private fun isReviewCandidate(item: ContentItemEntity): Boolean {
        val type = item.type?.lowercase() ?: return false
        if (type == "flashcard") return true
        if (type !in reviewTypes) return false
        val metadata = runCatching { JsonParser.parseString(item.metadataJson ?: "{}").asJsonObject }.getOrNull()
        val optionsElement = item.optionsJson?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
            ?: metadata?.get("options")
        val options = runCatching {
            optionsElement?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
        }.getOrDefault(emptyList())
        val correctIndex = metadata?.get("correct_index")?.let { runCatching { it.asInt }.getOrNull() }
        return options.isNotEmpty() && correctIndex != null && correctIndex in options.indices
    }

    private fun persianWeekday(day: Int): String = when (day) {
        1 -> "دوشنبه"
        2 -> "سه‌شنبه"
        3 -> "چهارشنبه"
        4 -> "پنجشنبه"
        5 -> "جمعه"
        6 -> "شنبه"
        else -> "یکشنبه"
    }
}
