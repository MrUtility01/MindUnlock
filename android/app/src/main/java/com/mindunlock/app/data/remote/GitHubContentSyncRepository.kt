package com.mindunlock.app.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mindunlock.app.data.local.AppDatabase
import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ContentItemEntity
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

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

enum class GitHubContentSyncStatus(
    val label: String,
    val canKeepLocal: Boolean = false,
    val canTakeRemote: Boolean = false
) {
    NEW("محتوای جدید"),
    UP_TO_DATE("به‌روز"),
    REMOTE_UPDATE("نسخه جدید در GitHub", canKeepLocal = true, canTakeRemote = true),
    LOCAL_MODIFIED("نسخه محلی تغییر کرده", canKeepLocal = true),
    CONFLICT("تعارض دوطرفه", canKeepLocal = true, canTakeRemote = true),
    UNTRACKED_LOCAL("محتوای محلیِ مدیریت‌نشده", canKeepLocal = true, canTakeRemote = true)
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
            if (item.format in COMPRESSED_COURSE_FORMATS) {
                val existing = githubBundleChapters()
                val rootLocal = database.chapterDao().get(localChapterId(item.id))
                val baseRemote = preferences.getString(remoteBaseKey(item.id), null)
                val baseLocal = preferences.getString(localBaseKey(item.id), null)
                if (existing.isEmpty()) {
                    GitHubContentSyncEntry(item, GitHubContentSyncStatus.NEW)
                } else if (baseRemote == null || baseLocal == null || rootLocal == null) {
                    GitHubContentSyncEntry(item, GitHubContentSyncStatus.UNTRACKED_LOCAL, rootLocal?.title)
                } else {
                    val currentFingerprint = githubBundleFingerprint()
                    val localChanged = currentFingerprint != baseLocal
                    val remoteChanged = item.contentHash != baseRemote
                    val status = when {
                        !localChanged && !remoteChanged -> GitHubContentSyncStatus.UP_TO_DATE
                        !localChanged && remoteChanged -> GitHubContentSyncStatus.REMOTE_UPDATE
                        localChanged && !remoteChanged -> GitHubContentSyncStatus.LOCAL_MODIFIED
                        else -> GitHubContentSyncStatus.CONFLICT
                    }
                    GitHubContentSyncEntry(item, status, rootLocal.title)
                }
            } else {
                val local = database.chapterDao().get(localChapterId(item.id))
                if (local == null) {
                    GitHubContentSyncEntry(item, GitHubContentSyncStatus.NEW)
                } else {
                    val localItems = database.contentItemDao().getForChapter(localChapterId(item.id))
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
        }.map { "${it.item.title}: ${it.status.label}" }.toMutableList()
        val eligible = entries.filter {
            it.status in setOf(GitHubContentSyncStatus.NEW, GitHubContentSyncStatus.REMOTE_UPDATE)
        }
        var imported = 0
        var skipped = 0
        eligible.forEachIndexed { index, entry ->
            onProgress(index + 1, eligible.size, entry.item.title)
            if (entry.item.format in COMPRESSED_COURSE_FORMATS) {
                val bundleResult = importCompressedCourse(entry)
                if (bundleResult.second != null) {
                    blocked += "${entry.item.title}: ${bundleResult.second}"
                    skipped++
                } else {
                    imported += bundleResult.first
                }
                return@forEachIndexed
            }
            val file = remote.readTextFile(entry.item.path)
            require(file.size <= MAX_CONTENT_BYTES) { "فایل «${entry.item.title}» از سقف مجاز بزرگ‌تر است." }
            val actualHash = sha256(file.text.toByteArray(Charsets.UTF_8))
            require(actualHash.equals(entry.item.contentHash, ignoreCase = true)) {
                "هش فایل «${entry.item.title}» با Manifest مطابقت ندارد؛ واردسازی متوقف شد."
            }
            val parsed = parseContent(entry.item, file.text)
            val expectedLocal = if (entry.status == GitHubContentSyncStatus.REMOTE_UPDATE) {
                preferences.getString(localBaseKey(entry.item.id), null)
            } else null
            val rejection = database.withTransaction {
                val localId = localChapterId(entry.item.id)
                val latest = database.chapterDao().get(localId)
                when {
                    entry.status == GitHubContentSyncStatus.NEW && latest != null ->
                        "هم‌زمان در دستگاه ایجاد شده؛ برای جلوگیری از بازنویسی نادیده گرفته شد."
                    entry.status == GitHubContentSyncStatus.REMOTE_UPDATE && latest == null ->
                        "نسخه محلی هنگام واردسازی حذف شده؛ فهرست را تازه‌سازی کن."
                    entry.status == GitHubContentSyncStatus.REMOTE_UPDATE && (
                        expectedLocal == null ||
                            fingerprint(latest!!, database.contentItemDao().getForChapter(localId)) != expectedLocal
                    ) -> "نسخه محلی هنگام واردسازی تغییر کرده؛ برای جلوگیری از ازدست‌رفتن تغییرات متوقف شد."
                    else -> {
                        val uidCollision = parsed.second.firstNotNullOfOrNull { candidate ->
                            database.contentItemDao().get(candidate.uid)
                                ?.takeIf { it.chapterId != localId }?.uid
                        }
                        if (uidCollision != null) {
                            "شناسه آیتم $uidCollision قبلاً در فصل دیگری استفاده شده است."
                        } else {
                            database.chapterDao().upsert(parsed.first)
                            database.contentItemDao().deleteForChapter(localId)
                            if (parsed.second.isNotEmpty()) database.contentItemDao().upsertAll(parsed.second)
                            null
                        }
                    }
                }
            }
            if (rejection != null) {
                blocked += "${entry.item.title}: $rejection"
                skipped++
                return@forEachIndexed
            }
            val saved = database.chapterDao().get(localChapterId(entry.item.id))
                ?: error("ثبت فصل «${entry.item.title}» در پایگاه داده تأیید نشد.")
            val savedItems = database.contentItemDao().getForChapter(localChapterId(entry.item.id))
            preferences.edit()
                .putString(remoteBaseKey(entry.item.id), entry.item.contentHash)
                .putString(localBaseKey(entry.item.id), fingerprint(saved, savedItems))
                .apply()
            imported++
        }
        return GitHubContentSyncResult(imported, skipped, blocked)
    }

    /**
     * Explicitly keeps the current local revision and records the currently reviewed remote
     * revision as the comparison base. No local content is modified or uploaded.
     */
    suspend fun keepLocalVersion(id: String) {
        val entry = inspect().singleOrNull { it.item.id == id }
            ?: throw IllegalArgumentException("محتوا در Manifest فعلی پیدا نشد.")
        require(entry.status.canKeepLocal) { "حفظ نسخه محلی برای این وضعیت مجاز نیست." }
        val localHash = if (entry.item.format in COMPRESSED_COURSE_FORMATS) {
            githubBundleFingerprint()
        } else {
            val localId = localChapterId(id)
            val local = database.chapterDao().get(localId)
                ?: throw IllegalStateException("نسخه محلی دیگر وجود ندارد؛ فهرست را تازه‌سازی کن.")
            fingerprint(local, database.contentItemDao().getForChapter(localId))
        }
        preferences.edit()
            .putString(remoteBaseKey(id), entry.item.contentHash)
            .putString(localBaseKey(id), localHash)
            .apply()
    }

    /**
     * Explicitly accepts the GitHub copy for a conflicting item. The local fingerprint is
     * rechecked inside the Room transaction so a concurrent edit is never overwritten.
     */
    suspend fun takeRemoteVersion(id: String) {
        val entry = inspect().singleOrNull { it.item.id == id }
            ?: throw IllegalArgumentException("محتوا در Manifest فعلی پیدا نشد.")
        require(entry.status.canTakeRemote) { "جایگزینی با نسخه GitHub برای این وضعیت مجاز نیست." }
        if (entry.item.format in COMPRESSED_COURSE_FORMATS) {
            takeRemoteCompressedCourse(entry)
            return
        }
        val localId = localChapterId(id)
        val expectedLocal = database.withTransaction {
            val chapter = database.chapterDao().get(localId)
                ?: throw IllegalStateException("نسخه محلی حذف شده؛ فهرست را تازه‌سازی کن.")
            fingerprint(chapter, database.contentItemDao().getForChapter(localId))
        }
        val file = remote.readTextFile(entry.item.path)
        require(file.size <= MAX_CONTENT_BYTES) { "فایل از سقف مجاز بزرگ‌تر است." }
        val actualHash = sha256(file.text.toByteArray(Charsets.UTF_8))
        require(actualHash.equals(entry.item.contentHash, ignoreCase = true)) {
            "هش فایل با Manifest مطابقت ندارد؛ واردسازی متوقف شد."
        }
        val parsed = parseContent(entry.item, file.text)
        database.withTransaction {
            val latest = database.chapterDao().get(localId)
                ?: throw IllegalStateException("نسخه محلی حذف شده؛ فهرست را تازه‌سازی کن.")
            val items = database.contentItemDao().getForChapter(localId)
            val current = fingerprint(latest, items)
            require(current == expectedLocal) {
                "نسخه محلی هنگام حل تعارض تغییر کرده؛ عملیات متوقف شد و باید دوباره بررسی کنی."
            }
            val collision = parsed.second.firstNotNullOfOrNull { candidate ->
                database.contentItemDao().get(candidate.uid)?.takeIf { it.chapterId != localId }?.uid
            }
            require(collision == null) { "شناسه آیتم $collision در فصل دیگری استفاده شده است." }
            database.chapterDao().upsert(parsed.first)
            database.contentItemDao().deleteForChapter(localId)
            if (parsed.second.isNotEmpty()) database.contentItemDao().upsertAll(parsed.second)
        }
        val saved = database.chapterDao().get(localId)
            ?: error("ثبت نسخه راه‌دور تأیید نشد.")
        val savedItems = database.contentItemDao().getForChapter(localId)
        preferences.edit()
            .putString(remoteBaseKey(id), entry.item.contentHash)
            .putString(localBaseKey(id), fingerprint(saved, savedItems))
            .apply()
    }

    private data class ParsedCourseBundle(
        val packages: List<Pair<ChapterEntity, List<ContentItemEntity>>>,
        val itemCount: Int
    )

    private suspend fun importCompressedCourse(
        entry: GitHubContentSyncEntry
    ): Pair<Int, String?> {
        val parsed = readAndParseCourseBundle(entry.item)
        val expectedLocal = if (entry.status == GitHubContentSyncStatus.REMOTE_UPDATE) {
            preferences.getString(localBaseKey(entry.item.id), null)
        } else null
        val rejection = database.withTransaction {
            val existingChapters = githubBundleChapters()
            when {
                entry.status == GitHubContentSyncStatus.NEW && existingChapters.isNotEmpty() ->
                    "بخشی از این دوره از قبل در دستگاه وجود دارد؛ ابتدا وضعیت همگام‌سازی را بررسی کن."
                entry.status == GitHubContentSyncStatus.REMOTE_UPDATE && existingChapters.isEmpty() ->
                    "نسخه محلی دوره هنگام واردسازی حذف شده؛ فهرست را تازه‌سازی کن."
                entry.status == GitHubContentSyncStatus.REMOTE_UPDATE &&
                    (expectedLocal == null || githubBundleFingerprint() != expectedLocal) ->
                    "یکی از صفحات دوره هنگام واردسازی تغییر کرده؛ برای جلوگیری از ازدست‌رفتن یادداشت‌ها عملیات متوقف شد."
                else -> {
                    val targetIds = parsed.packages.map { it.first.id }.toSet()
                    val candidateUids = parsed.packages.flatMap { it.second }.map { it.uid }.distinct()
                    if (candidateUids.size != parsed.itemCount) {
                        "شناسه یادداشت‌ها در بسته یکتا نیست؛ واردسازی متوقف شد."
                    } else {
                        val collisions = candidateUids.chunked(500).flatMap { chunk ->
                            database.contentItemDao().getByUids(chunk)
                        }.firstOrNull { it.chapterId !in targetIds }
                        if (collisions != null) {
                            "شناسه یادداشت ${collisions.uid} در فصل دیگری استفاده شده است."
                        } else {
                            parsed.packages.forEach { (chapter, items) ->
                                database.chapterDao().upsert(chapter)
                                database.contentItemDao().deleteForChapter(chapter.id)
                                if (items.isNotEmpty()) database.contentItemDao().upsertAll(items)
                            }
                            null
                        }
                    }
                }
            }
        }
        if (rejection != null) return 0 to rejection
        val localHash = githubBundleFingerprint()
        preferences.edit()
            .putString(remoteBaseKey(entry.item.id), entry.item.contentHash)
            .putString(localBaseKey(entry.item.id), localHash)
            .apply()
        return parsed.packages.size to null
    }

    private suspend fun takeRemoteCompressedCourse(entry: GitHubContentSyncEntry) {
        val parsed = readAndParseCourseBundle(entry.item)
        val expectedLocal = githubBundleFingerprint()
        database.withTransaction {
            require(githubBundleFingerprint() == expectedLocal) {
                "یکی از صفحات دوره هنگام حل تعارض تغییر کرده؛ عملیات متوقف شد و باید دوباره بررسی کنی."
            }
            val targetIds = parsed.packages.map { it.first.id }.toSet()
            val candidateUids = parsed.packages.flatMap { it.second }.map { it.uid }.distinct()
            require(candidateUids.size == parsed.itemCount) {
                "شناسه یادداشت‌ها در بسته یکتا نیست؛ جایگزینی متوقف شد."
            }
            val collisions = candidateUids.chunked(500).flatMap { chunk ->
                database.contentItemDao().getByUids(chunk)
            }.firstOrNull { it.chapterId !in targetIds }
            require(collisions == null) {
                "شناسه یادداشت ${collisions?.uid} در فصل دیگری استفاده شده است."
            }
            parsed.packages.forEach { (chapter, items) ->
                database.chapterDao().upsert(chapter)
                database.contentItemDao().deleteForChapter(chapter.id)
                if (items.isNotEmpty()) database.contentItemDao().upsertAll(items)
            }
        }
        preferences.edit()
            .putString(remoteBaseKey(entry.item.id), entry.item.contentHash)
            .putString(localBaseKey(entry.item.id), githubBundleFingerprint())
            .apply()
    }

    private suspend fun readAndParseCourseBundle(item: GitHubManifestItem): ParsedCourseBundle {
        val encodedPayload = if (item.format == COMPRESSED_COURSE_MULTIPART_FORMAT) {
            readMultipartBase64(item.path)
        } else {
            remote.readTextFile(item.path).text
        }
        require(encodedPayload.toByteArray(Charsets.UTF_8).size <= MAX_CONTENT_BYTES) {
            "بسته دوره از سقف حجم مجاز دانلود بزرگ‌تر است."
        }
        val actualHash = sha256(encodedPayload.toByteArray(Charsets.UTF_8))
        require(actualHash.equals(item.contentHash, ignoreCase = true)) {
            "هش بسته دوره با Manifest مطابقت ندارد؛ واردسازی متوقف شد."
        }
        val json = GitHubContentCompression.decodeBase64Gzip(
            encodedPayload,
            MAX_BUNDLE_UNCOMPRESSED_BYTES
        )
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "ساختار بسته فشرده دوره معتبر نیست." }
        val obj = root.asJsonObject
        require(obj.int("schema_version", -1) == 1) { "نسخه ساختار بسته دوره پشتیبانی نمی‌شود." }
        val rawPackages = obj.get("packages")
        require(rawPackages != null && rawPackages.isJsonArray) { "بسته دوره باید آرایه packages داشته باشد." }
        require(rawPackages.asJsonArray.size() in 1..MAX_COURSE_PACKAGES) { "تعداد فصل‌های بسته خارج از محدوده مجاز است." }
        val parsed = rawPackages.asJsonArray.map { element ->
            require(element.isJsonObject) { "یکی از بسته‌های فصل ساختار معتبری ندارد." }
            val packageObject = element.asJsonObject
            val chapter = packageObject.get("chapter")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IllegalArgumentException("یکی از بسته‌ها اطلاعات chapter ندارد.")
            val id = chapter.string("id")?.trim().orEmpty()
            require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))) { "شناسه فصل در بسته معتبر نیست." }
            val packageTitle = chapter.string("title")?.trim().orEmpty()
            require(packageTitle.isNotBlank()) { "عنوان فصل در بسته خالی است." }
            val group = chapter.string("group")?.trim().orEmpty()
            val order = chapter.int("order", 0)
            parseContent(
                GitHubManifestItem(
                    id = id,
                    title = packageTitle,
                    groupName = group,
                    orderNum = order,
                    path = "content/library/technology-path/$id.json",
                    format = "json",
                    contentHash = sha256(packageObject.toString().toByteArray(Charsets.UTF_8)),
                    updatedAt = item.updatedAt,
                    schemaVersion = 1
                ),
                packageObject.toString()
            )
        }
        val ids = parsed.map { it.first.id }
        require(ids.distinct().size == ids.size) { "شناسه فصل‌های بسته تکراری است." }
        val itemCount = parsed.sumOf { it.second.size }
        val declaredItems = obj.int("content_item_count", -1)
        require(itemCount == declaredItems) {
            "تعداد یادداشت‌های بسته با مقدار اعلام‌شده مطابقت ندارد (دریافت‌شده: $itemCount)."
        }
        val declaredPackages = obj.int("package_count", parsed.size)
        require(parsed.size == declaredPackages) { "تعداد فصل‌های بسته با مقدار اعلام‌شده مطابقت ندارد." }
        require(obj.int("domain_count", -1) == 16) { "فهرست حوزه‌های دوره ناقص یا ناسازگار است." }
        require(obj.int("numbered_chapter_count", -1) == 68) { "تعداد فصل‌های اصلی دوره معتبر نیست." }
        require(obj.int("supplemental_specialty_count", -1) == 3) { "تعداد تخصص‌های تکمیلی معتبر نیست." }
        val uniqueUids = parsed.flatMap { it.second }.map { it.uid }
        require(uniqueUids.distinct().size == uniqueUids.size) { "شناسه یادداشت‌ها در بسته تکراری است." }
        return ParsedCourseBundle(parsed, itemCount)
    }

    private suspend fun readMultipartBase64(firstPartPath: String): String {
        val match = Regex("^(.*)\\.part-001-of-(\\d{3})\\.b64$", RegexOption.IGNORE_CASE)
            .matchEntire(firstPartPath)
            ?: throw IllegalArgumentException("مسیر بخش اول بسته چندقسمتی معتبر نیست.")
        val basePath = match.groupValues[1]
        val partCount = match.groupValues[2].toInt()
        require(partCount in 1..32) { "تعداد بخش‌های بسته از حد مجاز بیشتر است." }
        val combined = StringBuilder()
        for (index in 1..partCount) {
            val suffix = index.toString().padStart(3, '0')
            val partPath = "$basePath.part-$suffix-of-${partCount.toString().padStart(3, '0')}.b64"
            val part = remote.readTextFile(partPath)
            require(part.size in 1..MAX_CONTENT_BYTES) { "یکی از بخش‌های بسته خالی یا بیش از حد بزرگ است." }
            combined.append(part.text.trim())
            require(combined.length <= MAX_CONTENT_BYTES) {
                "حجم کل بخش‌های بسته از سقف مجاز بیشتر است."
            }
        }
        return combined.toString()
    }

    private suspend fun githubBundleChapters(): List<ChapterEntity> =
        database.chapterDao().getAll()
            .filter { it.id.startsWith(GITHUB_BUNDLE_CHAPTER_PREFIX) }

    private suspend fun githubBundleFingerprint(): String {
        val chapters = githubBundleChapters().sortedBy { it.id }
        val canonical = buildString {
            chapters.forEach { chapter ->
                append(fingerprint(chapter, database.contentItemDao().getForChapter(chapter.id)))
                    .append('\n')
            }
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun parseContent(
        manifest: GitHubManifestItem,
        text: String
    ): Pair<ChapterEntity, List<ContentItemEntity>> {
        val now = System.currentTimeMillis()
        if (manifest.format == "md" || manifest.format == "markdown" || manifest.format == "txt") {
            val chapter = ChapterEntity(
                id = localChapterId(manifest.id),
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
                uid = "github_${manifest.id}_content",
                chapterId = localChapterId(manifest.id),
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
            val sourceUid = item.string("source_uid") ?: item.string("uid") ?: item.string("id")
                ?: index.toString()
            val uid = "github_${manifest.id}_$sourceUid"
            val metadata = item.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            val options = item.get("options") ?: metadata?.get("options")
            ContentItemEntity(
                uid = uid,
                chapterId = localChapterId(manifest.id),
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
            id = localChapterId(manifest.id),
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

    private fun localChapterId(id: String) = githubContentChapterId(id)

    private fun remoteBaseKey(id: String) = "remote:$id"
    private fun localBaseKey(id: String) = "local:$id"

    companion object {
        const val MANIFEST_PATH = "content/manifest.json"
        const val COMPRESSED_COURSE_FORMAT = "json-gzip-base64"
        const val COMPRESSED_COURSE_MULTIPART_FORMAT = "json-gzip-base64-multipart"
        private val COMPRESSED_COURSE_FORMATS = setOf(
            COMPRESSED_COURSE_FORMAT,
            COMPRESSED_COURSE_MULTIPART_FORMAT
        )
        const val GITHUB_BUNDLE_CHAPTER_PREFIX = "github_technology-path-"
        private const val PREFS = "github_content_sync_v1"
        private const val MAX_MANIFEST_BYTES = 1_000_000
        private const val MAX_CONTENT_BYTES = 1_000_000
        private const val MAX_BUNDLE_UNCOMPRESSED_BYTES = 25_000_000
        private const val MAX_COURSE_PACKAGES = 100
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
            require(format in setOf("json", "md", "markdown", "txt", COMPRESSED_COURSE_FORMAT, COMPRESSED_COURSE_MULTIPART_FORMAT)) { "قالب محتوا پشتیبانی نمی‌شود." }
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 ورودی Manifest معتبر نیست." }
            require(updated.isNotBlank() && updated.length <= 40) { "زمان به‌روزرسانی Manifest معتبر نیست." }
            require(itemSchema == 1) { "نسخه ساختار ورودی پشتیبانی نمی‌شود." }
            require(
                (format in setOf(COMPRESSED_COURSE_FORMAT, COMPRESSED_COURSE_MULTIPART_FORMAT) && safePath.endsWith(".b64", true)) ||
                    safePath.substringAfterLast('.').lowercase() == format ||
                    (format == "markdown" && (safePath.endsWith(".md", true) || safePath.endsWith(".markdown", true)))
            ) {
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


internal object GitHubContentCompression {
    fun decodeBase64Gzip(encoded: String, maxOutputBytes: Int): String {
        require(maxOutputBytes in 1..100_000_000) { "حد خروجی فشرده‌گشایی معتبر نیست." }
        val compressed = try {
            Base64.getMimeDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("بسته دوره Base64 معتبر نیست.")
        }
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = gzip.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxOutputBytes) {
                    "حجم محتوای بازشده از سقف امن مجاز بیشتر است."
                }
                output.write(buffer, 0, count)
            }
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("محتوای بازشده UTF-8 معتبر نیست.")
        }
    }
}

/** Stable namespace for GitHub-imported chapters, isolated from Windows LAN content. */
internal fun githubContentChapterId(id: String): String = "github_$id"
