package com.mindunlock.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.mindunlock.app.data.local.*
import com.mindunlock.app.data.model.ContentItem
import com.mindunlock.app.data.model.ManifestChapter
import com.mindunlock.app.data.remote.SyncApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SyncRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val chapterDao = db.chapterDao()
    private val itemDao = db.contentItemDao()
    private val configDao = db.serverConfigDao()
    private val gson = Gson()

    fun observeChapters(): Flow<List<ChapterEntity>> = chapterDao.observeChapters()
    fun observeServerConfig(): Flow<ServerConfigEntity?> = configDao.observe()
    fun observeItems(chapterId: String): Flow<List<ContentItemEntity>> =
        itemDao.observeItemsForChapter(chapterId)

    suspend fun getServerConfig(): ServerConfigEntity? = configDao.get()

    suspend fun saveServerConfig(host: String, port: Int) {
        configDao.upsert(
            ServerConfigEntity(
                id = 1,
                host = host.trim(),
                port = port,
                lastConnectedAt = System.currentTimeMillis()
            )
        )
    }

    private fun buildBaseUrl(host: String, port: Int): String {
        val h = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        return "http://$h:$port/"
    }

    suspend fun testConnection(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val api = SyncApiFactory.create(buildBaseUrl(host, port))
            val health = api.health()
            val version = health["version"]?.toString() ?: "unknown"
            Result.success("متصل شد — نسخه سرور: $version")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchManifest(host: String, port: Int): Result<List<ManifestChapter>> =
        withContext(Dispatchers.IO) {
            try {
                val api = SyncApiFactory.create(buildBaseUrl(host, port))
                val manifest = api.getManifest()
                Result.success(manifest.chapters)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun syncChapter(
        host: String,
        port: Int,
        chapter: ManifestChapter
    ): Result<ChapterEntity> = withContext(Dispatchers.IO) {
        try {
            val existing = chapterDao.getChapter(chapter.id)
            if (existing != null &&
                !chapter.contentHash.isNullOrBlank() &&
                existing.contentHash == chapter.contentHash
            ) {
                return@withContext Result.success(existing)
            }

            val api = SyncApiFactory.create(buildBaseUrl(host, port))
            val response = api.getChapter(chapter.id)
            val detail = response.chapter

            val entity = ChapterEntity(
                id = chapter.id,
                title = detail?.title ?: chapter.title,
                orderIndex = detail?.order ?: chapter.order,
                groupName = detail?.group ?: chapter.group,
                description = detail?.description ?: chapter.description,
                itemCount = response.items.size,
                contentHash = chapter.contentHash,
                rawJson = gson.toJson(response),
                syncedAt = System.currentTimeMillis()
            )
            chapterDao.upsert(entity)

            itemDao.deleteForChapter(chapter.id)
            val itemEntities = response.items.mapIndexed { index, item ->
                toItemEntity(chapter.id, item, index)
            }
            if (itemEntities.isNotEmpty()) {
                itemDao.upsertAll(itemEntities)
            }

            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAllChapters(
        host: String,
        port: Int,
        onProgress: (current: Int, total: Int, title: String) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val manifestResult = fetchManifest(host, port)
            if (manifestResult.isFailure) return@withContext Result.failure(manifestResult.exceptionOrNull()!!)

            val chapters = manifestResult.getOrThrow()
            var ok = 0
            chapters.forEachIndexed { index, ch ->
                onProgress(index + 1, chapters.size, ch.title)
                val r = syncChapter(host, port, ch)
                if (r.isSuccess) ok++
            }
            saveServerConfig(host, port)
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChapterItems(chapterId: String): List<ContentItemEntity> =
        itemDao.getItemsForChapter(chapterId)

    suspend fun getChapter(chapterId: String): ChapterEntity? = chapterDao.getChapter(chapterId)

    private fun toItemEntity(chapterId: String, item: ContentItem, fallbackOrder: Int): ContentItemEntity {
        val uid = item.sourceUid?.takeIf { it.isNotBlank() }
            ?: item.id?.takeIf { it.isNotBlank() }
            ?: "${chapterId}_${fallbackOrder}_${item.title?.hashCode() ?: 0}"

        return ContentItemEntity(
            uid = uid,
            chapterId = chapterId,
            type = item.type,
            title = item.title,
            content = item.content,
            orderIndex = if (item.order != 0) item.order else fallbackOrder,
            optionsJson = item.options?.let { gson.toJson(it) },
            answer = item.answer,
            explanation = item.explanation,
            metadataJson = item.metadata?.let { gson.toJson(it) }
        )
    }
}
