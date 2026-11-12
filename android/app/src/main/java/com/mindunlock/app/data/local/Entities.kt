package com.mindunlock.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val title: String,
    val orderIndex: Int = 0,
    val groupName: String? = null,
    val description: String? = null,
    val itemCount: Int = 0,
    val contentHash: String? = null,
    val rawJson: String? = null,
    val syncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "content_items", indices = [Index("chapterId"), Index("type")])
data class ContentItemEntity(
    @PrimaryKey val uid: String,
    val chapterId: String,
    val type: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val orderIndex: Int = 0,
    val optionsJson: String? = null,
    val answer: String? = null,
    val explanation: String? = null,
    val metadataJson: String? = null,
    val sourceStatus: String = "verified",
    val rawJson: String? = null
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val itemUid: String,
    val chapterId: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val attempts: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val lastResult: String? = null,
    val quality: Int = 0,
    val intervalDays: Int = 0,
    val dueAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null
)

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 1,
    val host: String = "",
    val port: Int = 82,
    val lastConnectedAt: Long? = null
)

@Entity(tableName = "notes", indices = [Index("itemUid"), Index("chapterId")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemUid: String? = null,
    val chapterId: String? = null,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val itemUid: String,
    val chapterId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_tasks", indices = [Index("remindAt")])
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val taskType: String = "task",
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mind_dump", indices = [Index("createdAt"), Index("status")])
data class MindDumpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val category: String = "thought",
    val status: String = "inbox",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val linkedTaskId: Long? = null
)

@Entity(tableName = "journal_entries", indices = [Index("createdAt")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String,
    val mood: String? = null,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_cache")
data class MediaCacheEntity(
    @PrimaryKey val relativePath: String,
    val localPath: String,
    val sha256: String,
    val size: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "later_topics", indices = [Index("status"), Index("revisitAt")])
data class LaterTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val priority: Int = 2,
    val revisitAt: Long? = null,
    val status: String = "parked",
    val sourceMindDumpId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lab_step_progress", primaryKeys = ["itemUid", "stepIndex"], indices = [Index("itemUid")])
data class LabStepProgressEntity(
    val itemUid: String,
    val stepIndex: Int,
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "lab_question_responses", primaryKeys = ["itemUid", "questionIndex"], indices = [Index("itemUid")])
data class LabQuestionResponseEntity(val itemUid: String, val questionIndex: Int, val answer: String, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "learning_sessions", indices = [Index("startedAt"), Index("endedAt")])
data class LearningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: String? = null,
    val topicTitle: String = "مطالعه آزاد",
    val source: String = "learning_engine",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val durationSeconds: Long = 0
)


@Entity(tableName = "learning_profile")
data class LearningProfileEntity(
    @PrimaryKey val id: Int = 1,
    val answersCsv: String,
    val sessionMinutes: Int = 30,
    val maxTopics: Int = 2,
    val guidedMode: String = "guided",
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Learner-reported session reflection; subjective signals, not assessment scores.
 */
@Entity(tableName = "learning_session_feedback")
data class LearningSessionFeedbackEntity(
    @PrimaryKey val sessionId: Long,
    val comprehension: Int,
    val perceivedDifficulty: Int,
    val focus: Int,
    val energyBefore: Int? = null,
    val energyAfter: Int? = null,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
