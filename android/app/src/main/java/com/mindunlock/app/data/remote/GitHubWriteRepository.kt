package com.mindunlock.app.data.remote

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GitHubUploadResult(
    val branch: String,
    val pullRequestNumber: Int,
    val pullRequestUrl: String,
    val filePath: String
)

/**
 * Submits user-selected text files to a dedicated content inbox branch and opens a PR.
 * Never writes to main. Requires the GitHub App to be installed with Contents and Pull requests
 * write permissions on this repository.
 */
class GitHubWriteRepository(
    private val auth: GitHubDeviceAuth = GitHubDeviceAuth(),
    private val owner: String = "MrUtility01",
    private val repository: String = "MindUnlock",
    private val baseBranch: String = "main",
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    suspend fun submitTextFile(
        context: Context,
        fileName: String,
        text: String,
        title: String
    ): GitHubUploadResult = withContext(Dispatchers.IO) {
        val safeName = fileName.trim()
        require(safeName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}\\.(md|markdown|txt|json|yaml|yml|csv|xml|html|css|js|py|sh|kt|kts|sql|log|ini|conf)", RegexOption.IGNORE_CASE))) {
            "نام فایل یا پسوند آن مجاز نیست. فقط فایل‌های متنی پشتیبانی می‌شوند."
        }
        require(!safeName.contains("..") && !safeName.startsWith(".")) { "نام فایل معتبر نیست." }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(bytes.isNotEmpty()) { "فایل خالی است." }
        require(bytes.size <= MAX_UPLOAD_BYTES) { "حداکثر اندازه فایل قابل ارسال ۱ مگابایت است." }
        require(!text.contains(0.toChar())) { "محتوا شامل داده باینری است." }
        val cleanTitle = title.trim().take(120).ifBlank { safeName }
        val token = auth.getValidAccessToken(context)
            ?: throw IllegalStateException("نشست GitHub معتبر نیست؛ دوباره وارد GitHub شو.")

        val repoInfo = api("GET", "/repos/$owner/$repository", token)
        val permissions = repoInfo.optJSONObject("permissions")
        if (permissions?.optBoolean("push", true) == false) {
            throw IllegalStateException(
                "حساب متصل اجازه نوشتن در این مخزن را ندارد. GitHub App را روی MrUtility01/MindUnlock نصب کن و دسترسی Contents و Pull requests را روی Read and write بگذار."
            )
        }
        val mainRef = api("GET", "/repos/$owner/$repository/git/ref/heads/$baseBranch", token)
        val baseSha = mainRef.getJSONObject("object").getString("sha")
        val branch = "mindunlock-content-" + UUID.randomUUID().toString().replace("-", "").take(16)
        api(
            "POST",
            "/repos/$owner/$repository/git/refs",
            token,
            JSONObject()
                .put("ref", "refs/heads/$branch")
                .put("sha", baseSha)
        )
        val path = "content/inbox/${UUID.randomUUID().toString().take(8)}-$safeName"
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        api(
            "PUT",
            "/repos/$owner/$repository/contents/$path",
            token,
            JSONObject()
                .put("message", "content: add $safeName")
                .put("content", encoded)
                .put("branch", branch)
        )
        val pull = api(
            "POST",
            "/repos/$owner/$repository/pulls",
            token,
            JSONObject()
                .put("title", cleanTitle)
                .put("head", branch)
                .put("base", baseBranch)
                .put("body", "این فایل از برنامه MindUnlock ارسال شده است.\n\nمسیر پیشنهادی: `$path`\n\nاین تغییر در شاخه جداگانه قرار دارد و برای ورود به شاخه اصلی نیازمند بازبینی و ادغام است.")
        )
        GitHubUploadResult(
            branch = branch,
            pullRequestNumber = pull.getInt("number"),
            pullRequestUrl = pull.getString("html_url"),
            filePath = path
        )
    }

    private fun api(method: String, path: String, token: String, json: JSONObject? = null): JSONObject {
        val builder = Request.Builder()
            .url("https://api.github.com$path")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "MindUnlock-Android")
        val body = json?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "{}".toRequestBody("application/json".toMediaType()))
            "PUT" -> builder.put(body ?: "{}".toRequestBody("application/json".toMediaType()))
            else -> error("روش HTTP پشتیبانی نمی‌شود.")
        }
        http.newCall(builder.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val message = result.optString("message")
                val detail = when (response.code) {
                    401 -> "نشست GitHub منقضی شده است؛ دوباره وارد شو."
                    403 -> "GitHub اجازه این عملیات را نداد؛ مجوزهای نصب برنامه و محدودیت API را بررسی کن."
                    404 -> "مخزن یا نصب GitHub App برای این حساب قابل دسترسی نیست."
                    409, 422 -> "تعارض در ایجاد شاخه یا Pull Request رخ داد؛ عملیات را دوباره با فایل/نام دیگر انجام بده."
                    else -> "عملیات GitHub ناموفق بود (HTTP ${response.code})."
                }
                throw IllegalStateException(if (message.isBlank()) detail else "$detail ($message)")
            }
            return result
        }
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 1_000_000
    }
}
