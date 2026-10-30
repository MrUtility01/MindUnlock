package com.mindunlock.app.data.local

import androidx.room.Entity
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

@Entity(tableName = "content_items")
data class ContentItemEntity(
    @PrimaryKey val uid: String,
    val chapterId: String,
    val type: String? = null,
    val title: String? = null,
    val content: String? = null,
    val orderIndex: Int = 0,
    val optionsJson: String? = null,
    val answer: String? = null,
    val explanation: String? = null,
    val metadataJson: String? = null
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val itemUid: String,
    val chapterId: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val attempts: Int = 0,
    val correct: Int = 0,
    val lastResult: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 1,
    val host: String = "",
    val port: Int = 82,
    val lastConnectedAt: Long? = null
)
