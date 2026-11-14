package com.mindunlock.app.data.remote

import android.util.Base64
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class GitHubPublicEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val sha: String
) {
    val isDirectory: Boolean get() = type == "dir"
}

data class GitHubPublicFile(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val text: String
)

/** Read-only access to the public MindUnlock repository. No credentials are sent or stored. */
class GitHubPublicRepository(
    private val owner: String = DEFAULT_OWNER,
    private val repository: String = DEFAULT_REPOSITORY,
    private val branch: String = DEFAULT_BRANCH,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    suspend fun list(path: String = ""): List<GitHubPublicEntry> = withContext(Dispatchers.IO) {
        val safePath = GitHubPathPolicy.normalize(path)
        val suffix = if (safePath.isBlank()) "" else "/${GitHubPathPolicy.encodePath(safePath)}"
        val url = "https://api.github.com/repos/$owner/$repository/contents$suffix?ref=${encode(branch)}"
        val root = getJson(url)
        require(root.isJsonArray) { "پاسخ فهرست فایل‌ها معتبر نیست." }
        root.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val type = item.get("type")?.asString ?: return@mapNotNull null
            if (type != "dir" && type != "file") return@mapNotNull null
            val itemPath = item.get("path")?.asString ?: return@mapNotNull null
            GitHubPublicEntry(
                name = item.get("name")?.asString ?: itemPath.substringAfterLast('/'),
                path = GitHubPathPolicy.normalize(itemPath),
                type = type,
                size = item.get("size")?.asLong ?: 0L,
                sha = item.get("sha")?.asString.orEmpty()
            )
        }.sortedWith(compareBy<GitHubPublicEntry>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun readTextFile(path: String): GitHubPublicFile = withContext(Dispatchers.IO) {
        val safePath = GitHubPathPolicy.normalize(path)
        require(safePath.isNotBlank()) { "مسیر فایل خالی است." }
        require(GitHubPathPolicy.isPreviewableTextPath(safePath)) { "پیش‌نمایش این نوع فایل متنی پشتیبانی نمی‌شود." }
        val url = "https://api.github.com/repos/$owner/$repository/contents/${GitHubPathPolicy.encodePath(safePath)}?ref=${encode(branch)}"
        val json = getJson(url)
        require(json.isJsonObject) { "پاسخ فایل معتبر نیست." }
        val item = json.asJsonObject
        require(item.get("type")?.asString == "file") { "این مسیر یک فایل متنی نیست." }
        val size = item.get("size")?.asLong ?: 0L
        require(size in 0..MAX_TEXT_FILE_BYTES) { "فایل بزرگ‌تر از سقف پیش‌نمایش ۱٫۵ مگابایت است." }
        val encoding = item.get("encoding")?.asString
        require(encoding == "base64") { "قالب فایل برای پیش‌نمایش متنی پشتیبانی نمی‌شود." }
        val encoded = item.get("content")?.asString ?: throw IllegalStateException("محتوای فایل در پاسخ GitHub وجود ندارد.")
        val bytes = Base64.decode(encoded.filterNot(Char::isWhitespace), Base64.DEFAULT)
        require(bytes.size.toLong() == size) { "اندازه محتوای دریافت‌شده با اندازه اعلام‌شده یکسان نیست." }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("فایل UTF-8 معتبر نیست و به‌عنوان متن باز نمی‌شود.")
        }
        require(!text.contains('\u0000')) { "فایل باینری است و به‌عنوان متن باز نمی‌شود." }
        GitHubPublicFile(
            name = item.get("name")?.asString ?: safePath.substringAfterLast('/'),
            path = safePath,
            sha = item.get("sha")?.asString.orEmpty(),
            size = size,
            text = text
        )
    }

    private fun getJson(url: String) = run {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "MindUnlock-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                403, 429 -> throw IllegalStateException("محدودیت درخواست GitHub فعال شده است؛ چند دقیقه بعد دوباره تلاش کن.")
                404 -> throw IllegalStateException("مسیر در شاخه $branch پیدا نشد.")
                in 200..299 -> Unit
                else -> throw IllegalStateException("دریافت از GitHub ناموفق بود (HTTP ${response.code}).")
            }
            require(body.length <= MAX_RESPONSE_CHARS) { "پاسخ GitHub بیش از حد بزرگ است." }
            JsonParser.parseString(body)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        const val DEFAULT_OWNER = "MrUtility01"
        const val DEFAULT_REPOSITORY = "MindUnlock"
        const val DEFAULT_BRANCH = "main"
        const val MAX_TEXT_FILE_BYTES = 1_000_000L
        private const val MAX_RESPONSE_CHARS = 5_000_000
    }
}

internal object GitHubPathPolicy {
    fun normalize(path: String): String {
        val value = path.trim().replace('\\', '/').trim('/')
        if (value.isEmpty()) return ""
        require(value.length <= 500) { "مسیر بیش از حد طولانی است." }
        require(value.split('/').none { it == "." || it == ".." || it.isBlank() }) {
            "مسیر فایل معتبر نیست."
        }
        require(value.none { it.code < 32 || it == '?' || it == '#' }) { "مسیر فایل دارای نویسه نامعتبر است." }
        return value
    }

    fun isPreviewableTextPath(path: String): Boolean {
        val name = normalize(path).substringAfterLast('/').lowercase()
        val extensions = listOf(
            ".md", ".markdown", ".txt", ".json", ".yaml", ".yml", ".xml",
            ".kt", ".kts", ".gradle", ".properties", ".toml", ".csv",
            ".py", ".sh", ".html", ".css", ".js", ".java", ".sql",
            ".log", ".ini", ".conf"
        )
        return name == "readme" || extensions.any { name.endsWith(it) }
    }

    fun encodePath(path: String): String = normalize(path).split('/')
        .joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20") }
}
