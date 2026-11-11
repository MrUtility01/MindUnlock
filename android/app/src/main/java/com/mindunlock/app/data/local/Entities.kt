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
    val updatedAt: Long = System.currentTimeMillis()
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

@Entity(tableName = "media_cache")
data class MediaCacheEntity(
    @PrimaryKey val relativePath: String,
    val localPath: String,
    val sha256: String,
    val size: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)
