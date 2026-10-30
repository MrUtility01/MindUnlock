package com.mindunlock.app.data.model

import com.google.gson.annotations.SerializedName

data class SyncManifest(
    val format: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null,
    val chapters: List<ManifestChapter> = emptyList(),
    @SerializedName("media_files") val mediaFiles: List<MediaFileInfo> = emptyList()
)

data class ManifestChapter(
    val id: String,
    val title: String,
    val order: Int = 0,
    val group: String? = null,
    val description: String? = null,
    @SerializedName("item_count") val itemCount: Int = 0,
    @SerializedName("content_hash") val contentHash: String? = null
)

data class MediaFileInfo(
    val path: String,
    val size: Long = 0,
    @SerializedName("sha256") val sha256: String? = null
)

data class ChapterResponse(
    val chapter: ChapterDetail? = null,
    val items: List<ContentItem> = emptyList()
)

data class ChapterDetail(
    val id: String,
    val title: String,
    val order: Int = 0,
    val group: String? = null,
    val description: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class ContentItem(
    val id: String? = null,
    @SerializedName("source_uid") val sourceUid: String? = null,
    val type: String? = null,
    val title: String? = null,
    val content: String? = null,
    val order: Int = 0,
    val metadata: Map<String, Any?>? = null,
    val options: List<String>? = null,
    val answer: String? = null,
    val explanation: String? = null
)
