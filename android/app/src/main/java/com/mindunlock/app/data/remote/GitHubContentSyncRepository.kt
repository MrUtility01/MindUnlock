package com.mindunlock.app.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mindunlock.app.data.local.AppDatabase
import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ContentItemEntity
import java.security.MessageDigest

data class GitHubManifestItem(
    val id: String,
    val title: String,
    val groupName: String,
    val orderNum: Int,
    val path: String,
    val format: String,
    val contentHash: String,
    val updatedAt: String,
    val schemaVersion: Int
)

data class GitHubContentSyncEntry(
    val item: GitHubManifestItem,
    val status: GitHubContentSyncStatus,
    val localTitle: String? = null
)

enum class GitHubContentSyncStatus(val label: String) {
    NEW("محتوای جدید"),
    UP_TO_DATE("به‌روز"),
    REMOTE_UPDATE("نسخه جدید در GitHub"),
    LOCAL_MODIFIED("نسخه محلی تغییر کرده"),
    CONFLICT("تعارض دوطرفه"),
    UNTRACKED_LOCAL("محتوای محلیِ مدیریت‌نشده")
}

data class GitHubContentSyncResult(
    val imported: Int,
    val skipped: Int,
    val blocked: List<String>
)

/**
 * Validates the public content manifest and imports explicitly selected chapters.
 * Existing local content is never silently overwritten: only new items and remote-only
 * updates are eligible for import. Learner progress and the Windows sync provider are untouched.
 */
class GitHubContentSyncRepository(
    context: Context,
    private val remote: GitHubPublicRepository = GitHubPublicRepository(),
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun inspect(): List<GitHubContentSyncEntry> {
        val manifestFile = remote.readTextFile(MANIFEST_PATH)
        require(manifestFile.size <= MAX_MANIFEST_BYTES) { "فایل فهرست محتوا بیش از حد بزرگ است." }
        val manifest = GitHubManifestPolicy.parse(manifestFile.text)
        return manifest.map { item ->
            val local = database.chapterDao().get(item.id)
            if (local == null) {
                GitHubContentSyncEntry(item, GitHubContentSyncStatus.NEW)
            } else {
                val localItems = database.contentItemDao().getForChapter(item.id)
                val currentFingerprint = fingerprint(local, localItems)
                val baseRemote = preferences.getString(remoteBaseKey(item.id), null)
                val baseLocal = preferences.getString(localBaseKey(item.id), null)
                val status = when {
                    baseRemote == null || baseLocal == null -> GitHubContentSyncStatus.UNTRACKED_LOCAL
                    else -> {
                        val localChanged = currentFingerprint != baseLocal
                        val remoteChanged = item.contentHash != baseRemote
                        when {
                            !localChanged && !remoteChanged -> GitHubContentSyncStatus.UP_TO_DATE
                            !localChanged && remoteChanged -> GitHubContentSyncStatus.REMOTE_UPDATE
                            localChanged && !remoteChanged -> GitHubContentSyncStatus.LOCAL_MODIFIED
                            else -> GitHubContentSyncStatus.CONFLICT
                        }
                    }
                }
                GitHubContentSyncEntry(item, status, local.title)
            }
        }
    }

    suspend fun importSelected(
        selectedIds: Set<String>,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): GitHubContentSyncResult {
        require(selectedIds.isNotEmpty()) { "حداقل یک فصل را برای واردسازی انتخاب کن." }
        val entries = inspect().filter { it.item.id in selectedIds }
        require(entries.size == selectedIds.size) { "یک یا چند شناسه انتخاب‌شده در Manifest وجود ندارد؛ فهرست را تازه‌سازی کن." }

        val blocked = entries.filter {
            it.status !in setOf(GitHubContentSyncStatus.NEW, GitHubContentSyncStatus.REMOTE_UPDATE)
        }.map { "${it.item.title}: ${it.status.label}" }
        val eligible = entries.filter {
            it.status in setOf(GitHubContentSyncStatus.NEW, GitHubContentSyncStatus.REMOTE_UPDATE)
        }
        var imported = 0
        var skipped = 0
        eligible.forEachIndexed { index, entry ->
            onProgress(index + 1, eligible.size, entry.item.title)
            val file = remote.readTextFile(entry.item.path)
            require(file.size <= MAX_CONTENT_BYTES) { "فایل «${entry.item.title}» از سقف مجاز بزرگ‌تر است." }
            val actualHash = sha256(file.text.toByteArray(Charsets.UTF_8))
            require(actualHash.equals(entry.item.contentHash, ignoreCase = true)) {
                "هش فایل «${entry.item.title}» با Manifest مطابقت ندارد؛ واردسازی متوقف شد."
            }
            val parsed = parseContent(entry.item, file.text)
            val latest = database.chapterDao().get(entry.item.id)
            if (entry.status == GitHubContentSyncStatus.NEW && latest != null) {
                blocked + "${entry.item.title}: هم‌زمان در دستگاه ایجاد شده؛ برای جلوگیری از بازنویسی نادیده گرفته شد."
                skipped++
                return@forEachIndexed
            }
            database.withTransaction {
                database.chapterDao().upsert(parsed.first)
                database.contentItemDao().deleteForChapter(entry.item.id)
                if (parsed.second.isNotEmpty()) database.contentItemDao().upsertAll(parsed.second)
            }
            val saved = database.chapterDao().get(entry.item.id)
                ?: error("ثبت فصل «${entry.item.title}» در پایگاه داده تأیید نشد.")
            val savedItems = database.contentItemDao().getForChapter(entry.item.id)
            preferences.edit()
                .putString(remoteBaseKey(entry.item.id), entry.item.contentHash)
                .putString(localBaseKey(entry.item.id), fingerprint(saved, savedItems))
                .apply()
            imported++
        }
        return GitHubContentSyncResult(imported, skipped, blocked)
    }

    private fun parseContent(
        manifest: GitHubManifestItem,
        text: String
    ): Pair<ChapterEntity, List<ContentItemEntity>> {
        val now = System.currentTimeMillis()
        if (manifest.format == "md" || manifest.format == "markdown" || manifest.format == "txt") {
            val chapter = ChapterEntity(
                id = manifest.id,
                title = manifest.title,
                orderIndex = manifest.orderNum,
                groupName = manifest.groupName,
                description = "محتوای دریافت‌شده از GitHub",
                itemCount = 1,
                contentHash = manifest.contentHash,
                rawJson = null,
                syncedAt = now
            )
            val item = ContentItemEntity(
                uid = "${manifest.id}_content",
                chapterId = manifest.id,
                type = "lesson",
                title = manifest.title,
                content = text,
                orderIndex = 0,
                sourceStatus = "verified"
            )
            return chapter to listOf(item)
        }

        val root = JsonParser.parseString(text)
        require(root.isJsonObject) { "ساختار JSON فصل «${manifest.title}» معتبر نیست." }
        val obj = root.asJsonObject
        val detail = obj.get("chapter")?.takeIf { it.isJsonObject }?.asJsonObject ?: obj
        val chapterId = detail.string("id") ?: manifest.id
        require(chapterId == manifest.id) { "شناسه فصل در فایل با Manifest یکسان نیست." }
        val rawItems = obj.get("items")
        require(rawItems != null && rawItems.isJsonArray) { "فایل فصل «${manifest.title}» باید آرایه items داشته باشد." }
        val mapped = rawItems.asJsonArray.mapIndexed { index, element ->
            require(element.isJsonObject) { "ساختار یکی از آیتم‌های فصل معتبر نیست." }
            val item = element.asJsonObject
            val title = item.string("title")?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("عنوان یکی از آیتم‌های فصل خالی است.")
            val uid = item.string("source_uid") ?: item.string("uid") ?: item.string("id")
                ?: "${manifest.id}_$index"
            val metadata = item.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            val options = item.get("options") ?: metadata?.get("options")
            ContentItemEntity(
                uid = uid,
                chapterId = manifest.id,
                type = item.string("kind") ?: item.string("type") ?: "lesson",
                title = title,
                summary = item.string("summary"),
                content = item.string("content") ?: "",
                orderIndex = item.int("order_num", item.int("order", index)),
                optionsJson = options?.takeIf { it.isJsonArray }?.toString(),
                answer = item.string("answer") ?: metadata?.string("answer"),
                explanation = item.string("explanation") ?: metadata?.string("explanation"),
                metadataJson = metadata?.toString(),
                sourceStatus = item.string("source_status") ?: "verified",
                rawJson = item.toString()
            )
        }
        require(mapped.map { it.uid }.distinct().size == mapped.size) {
            "شناسه آیتم‌های فصل تکراری است؛ واردسازی امن نیست."
        }
        val chapter = ChapterEntity(
            id = manifest.id,
            title = detail.string("title") ?: manifest.title,
            orderIndex = detail.int("order_num", detail.int("order", manifest.orderNum)),
            groupName = detail.string("group_name") ?: detail.string("group") ?: manifest.groupName,
            description = detail.string("description"),
            itemCount = mapped.size,
            contentHash = manifest.contentHash,
            rawJson = detail.toString(),
            syncedAt = now
        )
        return chapter to mapped
    }

    private fun fingerprint(chapter: ChapterEntity, items: List<ContentItemEntity>): String {
        val canonical = buildString {
            append(chapter.id).append('\u001f')
            append(chapter.title).append('\u001f')
            append(chapter.orderIndex).append('\u001f')
            append(chapter.groupName.orEmpty()).append('\u001f')
            append(chapter.description.orEmpty()).append('\n')
            items.sortedBy { it.uid }.forEach { item ->
                append(item.uid).append('\u001f')
                append(item.type.orEmpty()).append('\u001f')
                append(item.title.orEmpty()).append('\u001f')
                append(item.summary.orEmpty()).append('\u001f')
                append(item.content.orEmpty()).append('\u001f')
                append(item.orderIndex).append('\u001f')
                append(item.optionsJson.orEmpty()).append('\u001f')
                append(item.answer.orEmpty()).append('\u001f')
                append(item.explanation.orEmpty()).append('\u001f')
                append(item.metadataJson.orEmpty()).append('\u001f')
                append(item.sourceStatus).append('\n')
            }
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun remoteBaseKey(id: String) = "remote:$id"
    private fun localBaseKey(id: String) = "local:$id"

    companion object {
        const val MANIFEST_PATH = "content/manifest.json"
        private const val PREFS = "github_content_sync_v1"
        private const val MAX_MANIFEST_BYTES = 1_000_000
        private const val MAX_CONTENT_BYTES = 1_000_000

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

internal object GitHubManifestPolicy {
    fun parse(text: String): List<GitHubManifestItem> {
        require(text.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Manifest بیش از حد بزرگ است." }
        val root = JsonParser.parseString(text)
        require(root.isJsonObject) { "ساختار Manifest معتبر نیست." }
        val obj = root.asJsonObject
        val schemaVersion = obj.int("schema_version", -1)
        require(schemaVersion == 1) { "نسخه Manifest پشتیبانی نمی‌شود: $schemaVersion" }
        val entries = obj.get("items")
        require(entries != null && entries.isJsonArray) { "Manifest باید آرایه items داشته باشد." }
        require(entries.asJsonArray.size() <= 500) { "تعداد ورودی‌های Manifest بیش از حد مجاز است." }
        val parsed = entries.asJsonArray.map { element ->
            require(element.isJsonObject) { "یکی از ورودی‌های Manifest ساختار معتبری ندارد." }
            val entry = element.asJsonObject
            val id = entry.string("id")?.trim().orEmpty()
            val title = entry.string("title")?.trim().orEmpty()
            val group = entry.string("group_name")?.trim().orEmpty()
            val path = entry.string("path")?.trim().orEmpty()
            val format = entry.string("format")?.trim()?.lowercase().orEmpty()
            val hash = entry.string("content_hash")?.trim()?.lowercase().orEmpty()
            val updated = entry.string("updated_at")?.trim().orEmpty()
            val itemSchema = entry.int("schema_version", schemaVersion)
            require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))) { "شناسه Manifest نامعتبر است." }
            require(title.isNotBlank() && title.length <= 160) { "عنوان Manifest نامعتبر است." }
            require(group.length <= 100) { "نام گروه بیش از حد طولانی است." }
            val safePath = GitHubPathPolicy.normalize(path)
            require(safePath.startsWith("content/library/") && safePath.length > "content/library/".length) {
                "فایل محتوا باید داخل content/library باشد."
            }
            require(GitHubPathPolicy.isPreviewableTextPath(safePath)) { "نوع فایل Manifest پشتیبانی نمی‌شود." }
            require(format in setOf("json", "md", "markdown", "txt")) { "قالب محتوا پشتیبانی نمی‌شود." }
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 ورودی Manifest معتبر نیست." }
            require(updated.isNotBlank() && updated.length <= 40) { "زمان به‌روزرسانی Manifest معتبر نیست." }
            require(itemSchema == 1) { "نسخه ساختار ورودی پشتیبانی نمی‌شود." }
            require(safePath.substringAfterLast('.').lowercase() == format ||
                (format == "markdown" && safePath.endsWith(".md", true))) {
                "پسوند فایل با قالب اعلام‌شده در Manifest یکسان نیست."
            }
            GitHubManifestItem(
                id = id,
                title = title,
                groupName = group,
                orderNum = entry.int("order_num", 0),
                path = safePath,
                format = format,
                contentHash = hash,
                updatedAt = updated,
                schemaVersion = itemSchema
            )
        }
        require(parsed.map { it.id }.distinct().size == parsed.size) { "شناسه تکراری در Manifest وجود دارد." }
        require(parsed.map { it.path }.distinct().size == parsed.size) { "مسیر تکراری در Manifest وجود دارد." }
        return parsed.sortedWith(compareBy<GitHubManifestItem>({ it.orderNum }, { it.title.lowercase() }))
    }
}

private fun JsonObject.string(name: String): String? {
    val value = get(name) ?: return null
    return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
}

private fun JsonObject.int(name: String, default: Int): Int {
    val value: JsonElement = get(name) ?: return default
    return runCatching { value.asInt }.getOrDefault(default)
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
