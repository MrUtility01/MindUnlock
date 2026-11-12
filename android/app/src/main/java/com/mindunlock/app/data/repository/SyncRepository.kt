package com.mindunlock.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mindunlock.app.data.local.AppDatabase
import com.mindunlock.app.data.local.ChapterEntity
import com.mindunlock.app.data.local.ContentItemEntity
import com.mindunlock.app.data.local.MediaCacheEntity
import com.mindunlock.app.data.local.ServerConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Read-only Windows-to-Android content synchronizer. Learner progress remains local. */
data class ManifestChapter(
    val id: String,
    val title: String,
    val orderIndex: Int,
    val groupName: String?,
    val description: String?,
    val itemCount: Int,
    val contentHash: String?
)

private data class ManifestMedia(
    val path: String,
    val url: String,
    val size: Long,
    val sha256: String
)

private class SessionCookieJar : CookieJar {
    private val byHost = ConcurrentHashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val merged = byHost[url.host].orEmpty().filter { it.expiresAt > now }.toMutableList()
        cookies.forEach { incoming ->
            merged.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > now) merged += incoming
        }
        if (merged.isEmpty()) byHost.remove(url.host) else byHost[url.host] = merged
    }
    override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> =
        byHost[url.host].orEmpty().filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
}

class SyncRepository(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    private val prefs = appContext.getSharedPreferences("mindunlock_connection", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cookieJar = SessionCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun savedHost(): String = prefs.getString("host", "") ?: ""
    fun savedPort(): Int = prefs.getInt("port", 82)

    /** Runs explicit network, HTTP service, authentication and manifest checks. */
    suspend fun diagnoseConnection(
        host: String, port: Int, username: String, password: String
    ): ConnectionDiagnostics = withContext(Dispatchers.IO) {
        val clean = cleanHost(host)
        if (clean.isBlank()) return@withContext ConnectionDiagnostics(false, false, false, false, "آدرس IP یا نام میزبان وارد نشده است.")
        if (port !in 1..65535) return@withContext ConnectionDiagnostics(false, false, false, false, "شماره پورت معتبر نیست؛ بازه مجاز ۱ تا ۶۵۵۳۵ است.")
        val tcp = try {
            Socket().use { socket -> socket.connect(InetSocketAddress(clean, port), 3500) }
            true
        } catch (_: Exception) { false }
        if (!tcp) return@withContext ConnectionDiagnostics(false, false, false, false, "اتصال TCP برقرار نشد. IP، پورت، روشن بودن برنامه ویندوز، شبکه مشترک و Firewall را بررسی کن.")
        val base = baseUrl(clean, port)
        val http = try {
            val req = Request.Builder().url("$base/api/auth/status").get().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
        if (!http) return@withContext ConnectionDiagnostics(true, false, false, false, "پورت باز است اما پاسخ HTTP معتبر از API دریافت نشد؛ احتمالاً سرویس وب روی این پورت اجرا نیست یا مسیر شبکه/پروکسی مشکل دارد.")
        val auth = try {
            val status = getJson("$base/api/auth/status").asJsonObject
            val configured = status.get("configured")?.asBoolean ?: true
            val already = status.get("authenticated")?.asBoolean ?: false
            if (!configured) throw IllegalStateException("رمز ویندوز هنوز تنظیم نشده؛ تنظیم اولیه باید از خود رایانه انجام شود.")
            if (already) true else {
                if (username.isBlank() || password.isBlank()) throw IllegalStateException("نام کاربری و رمز عبور وارد نشده است.")
                val body = JSONObject().put("username", username).put("password", password).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                postJson("$base/api/auth/login", body)
                true
            }
        } catch (e: Exception) {
            return@withContext ConnectionDiagnostics(true, true, false, false, e.message ?: "احراز هویت ناموفق بود.")
        }
        val manifestOk = try {
            getJson("$base/api/sync/manifest").asJsonObject.has("chapters")
        } catch (_: Exception) { false }
        ConnectionDiagnostics(true, true, auth, manifestOk, if (manifestOk) "همه آزمون‌ها موفق بود؛ API و ورود و فهرست محتوا در دسترس‌اند." else "ورود انجام شد اما دریافت فهرست محتوا ناموفق بود؛ مجوز دسترسی یا نسخه API ویندوز را بررسی کن.")
    }

    suspend fun fetchManifest(
        host: String,
        port: Int,
        username: String,
        password: String
    ): List<ManifestChapter> = withContext(Dispatchers.IO) {
        val base = baseUrl(host, port)
        ensureAuthenticated(base, username, password)
        val root = getJson("$base/api/sync/manifest").asJsonObject
        val chapters = root.getAsJsonArray("chapters") ?: JsonArray()
        val result = chapters.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val id = string(obj, "id") ?: return@mapNotNull null
            ManifestChapter(
                id = id,
                title = string(obj, "title") ?: id,
                orderIndex = int(obj, "order_num", int(obj, "order", 0)),
                groupName = string(obj, "group_name") ?: string(obj, "group"),
                description = string(obj, "description"),
                itemCount = int(obj, "item_count", 0),
                contentHash = string(obj, "content_hash")
            )
        }
        database.serverConfigDao().upsert(
            ServerConfigEntity(host = cleanHost(host), port = port, lastConnectedAt = System.currentTimeMillis())
        )
        prefs.edit().putString("host", cleanHost(host)).putInt("port", port).apply()
        result
    }

    suspend fun syncSelected(
        host: String,
        port: Int,
        selected: List<ManifestChapter>,
        allMedia: Boolean = true,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        val base = baseUrl(host, port)
        var downloaded = 0
        var skipped = 0
        val failures = mutableListOf<String>()
        val mediaManifest = if (allMedia) runCatching { getJson("$base/api/sync/manifest").asJsonObject.getAsJsonArray("media_files") }.getOrNull() else null
        selected.forEachIndexed { index, chapter ->
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.Main) { onProgress(index + 1, selected.size, chapter.title) }
            try {
                val existing = database.chapterDao().get(chapter.id)
                if (existing != null && !chapter.contentHash.isNullOrBlank() &&
                    existing.contentHash == chapter.contentHash) {
                    skipped++
                } else {
                    val json = getJson("$base/api/sync/chapter/${encodePathSegment(chapter.id)}").asJsonObject
                    persistChapter(chapter, json)
                    downloaded++
                    if (allMedia && mediaManifest != null) downloadReferencedMedia(base, json, mediaManifest)
                }
            } catch (error: Exception) {
                failures += "${chapter.title}: ${error.message ?: "خطای نامشخص"}"
            }
        }
        prefs.edit().putString("host", cleanHost(host)).putInt("port", port).apply()
        SyncResult(downloaded, skipped, failures)
    }

    private suspend fun ensureAuthenticated(base: String, username: String, password: String) {
        val status = getJson("$base/api/auth/status").asJsonObject
        val configured = status.get("configured")?.asBoolean ?: true
        val authenticated = status.get("authenticated")?.asBoolean ?: false
        if (!configured) {
            throw IllegalStateException("هنوز رمز برنامهٔ ویندوز تنظیم نشده است. تنظیم اولیه باید یک‌بار از خود رایانهٔ ویندوز انجام شود.")
        }
        if (!authenticated) {
            if (username.isBlank() || password.isBlank()) {
                throw IllegalStateException("ورود لازم است؛ نام کاربری و رمز برنامهٔ ویندوز را وارد کن.")
            }
            val body = JSONObject().put("username", username).put("password", password)
                .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            postJson("$base/api/auth/login", body)
        }
    }

    private suspend fun persistChapter(manifest: ManifestChapter, response: JsonObject) {
        val detail = response.getAsJsonObject("chapter") ?: JsonObject()
        val items = response.getAsJsonArray("items") ?: JsonArray()
        val chapterId = string(detail, "id") ?: manifest.id
        val chapter = ChapterEntity(
            id = chapterId,
            title = string(detail, "title") ?: manifest.title,
            orderIndex = int(detail, "order_num", int(detail, "order", manifest.orderIndex)),
            groupName = string(detail, "group_name") ?: string(detail, "group") ?: manifest.groupName,
            description = string(detail, "description") ?: manifest.description,
            itemCount = items.size(),
            contentHash = manifest.contentHash,
            rawJson = gson.toJson(detail),
            syncedAt = System.currentTimeMillis()
        )
        val mappedItems = items.mapIndexedNotNull { index, value ->
            if (!value.isJsonObject) return@mapIndexedNotNull null
            val obj = value.asJsonObject
            val title = string(obj, "title") ?: return@mapIndexedNotNull null
            val sourceUid = string(obj, "source_uid") ?: string(obj, "uid") ?: string(obj, "id")
            val uid = sourceUid?.takeIf { it.isNotBlank() }
                ?: "${chapterId}_${index}_${title.hashCode()}"
            val metadata = obj.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            val options = obj.get("options") ?: metadata?.get("options")
            ContentItemEntity(
                uid = uid,
                chapterId = chapterId,
                type = string(obj, "kind") ?: string(obj, "type") ?: "lesson",
                title = title,
                summary = string(obj, "summary"),
                content = string(obj, "content") ?: "",
                orderIndex = int(obj, "order_num", int(obj, "order", index)),
                optionsJson = options?.takeIf { it.isJsonArray }?.toString(),
                answer = string(obj, "answer") ?: string(metadata, "answer"),
                explanation = string(obj, "explanation") ?: string(metadata, "explanation"),
                metadataJson = metadata?.toString(),
                sourceStatus = string(obj, "source_status") ?: "verified",
                rawJson = obj.toString()
            )
        }
        database.withTransaction {
            database.chapterDao().upsert(chapter)
            database.contentItemDao().deleteForChapter(chapterId)
            if (mappedItems.isNotEmpty()) database.contentItemDao().upsertAll(mappedItems)
        }
    }

    private suspend fun downloadReferencedMedia(base: String, chapter: JsonObject, files: JsonArray) {
        val chapterText = chapter.toString()
        files.forEach { entry ->
            if (!entry.isJsonObject) return@forEach
            val obj = entry.asJsonObject
            val path = string(obj, "path") ?: return@forEach
            if (!isSafeRelativePath(path)) return@forEach
            val urlPath = string(obj, "url") ?: "/media/$path"
            val size = long(obj, "size", 0L)
            val hash = string(obj, "sha256") ?: return@forEach
            val fileName = path.substringAfterLast('/')
            if (!chapterText.contains(urlPath) && !chapterText.contains(path) && !chapterText.contains(fileName)) return@forEach
            if (size !in 1..(40L * 1024L * 1024L)) return@forEach
            // Media are downloaded only when a selected chapter references them, then SHA-256 verified.
            val request = Request.Builder().url(base.trimEnd('/') + "/" + urlPath.trimStart('/')).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val bytes = response.body?.bytes() ?: return@use
                if (bytes.size.toLong() != size) return@use
                val actualHash = sha256(bytes)
                if (!actualHash.equals(hash, ignoreCase = true)) return@use
                val root = File(appContext.filesDir, "media").canonicalFile
                val target = File(root, path).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) return@use
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + ".part")
                temp.writeBytes(bytes)
                if (temp.length() != size || sha256(temp.readBytes()) != actualHash) {
                    temp.delete()
                    return@use
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                database.mediaCacheDao().upsert(
                    MediaCacheEntity(path, target.absolutePath, actualHash, bytes.size.toLong())
                )
            }
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.isNotBlank() && !normalized.startsWith('/') &&
            normalized.split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun baseUrl(host: String, port: Int): String {
        val clean = cleanHost(host)
        require(clean.isNotBlank()) { "آدرس رایانه را وارد کن." }
        require(port in 1..65535) { "پورت باید بین ۱ تا ۶۵۵۳۵ باشد." }
        require(!clean.contains('/') && !clean.contains('@')) { "فقط IP یا نام میزبان را وارد کن؛ بدون مسیر یا اطلاعات ورود." }
        return "http://$clean:$port"
    }

    private fun cleanHost(host: String): String = host.trim()
        .removePrefix("http://").removePrefix("https://").trimEnd('/')

    private fun getJson(url: String): JsonElement = request(url, null)

    private fun postJson(url: String, body: okhttp3.RequestBody): JsonElement = request(url, body)

    private fun request(url: String, body: okhttp3.RequestBody?): JsonElement {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (body == null) builder.get() else builder.post(body)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JsonParser.parseString(text).asJsonObject.get("detail")?.asString }.getOrNull()
                val message = detail ?: "درخواست به ویندوز ناموفق بود (HTTP ${response.code})."
                if (response.code == 401) throw IllegalStateException("نشست ورود منقضی شده یا اطلاعات ورود معتبر نیست؛ دوباره همگام‌سازی را با رمز ویندوز انجام بده.")
                throw IllegalStateException(message)
            }
            return if (text.isBlank()) JsonObject() else JsonParser.parseString(text)
        }
    }

    private fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun string(obj: JsonObject?, key: String): String? {
        val v = obj?.get(key) ?: return null
        return if (v.isJsonNull || v.isJsonObject || v.isJsonArray) null else runCatching { v.asString }.getOrNull()
    }
    private fun int(obj: JsonObject?, key: String, default: Int): Int =
        runCatching { obj?.get(key)?.asInt ?: default }.getOrDefault(default)
    private fun long(obj: JsonObject?, key: String, default: Long): Long =
        runCatching { obj?.get(key)?.asLong ?: default }.getOrDefault(default)

    private fun string(obj: JsonElement?, key: String): String? =
        if (obj != null && obj.isJsonObject) string(obj.asJsonObject, key) else null
}

data class SyncResult(val downloaded: Int, val skipped: Int, val failures: List<String>)

data class ConnectionDiagnostics(
    val tcpReachable: Boolean, val httpReachable: Boolean, val authenticated: Boolean,
    val manifestAvailable: Boolean, val message: String
)
