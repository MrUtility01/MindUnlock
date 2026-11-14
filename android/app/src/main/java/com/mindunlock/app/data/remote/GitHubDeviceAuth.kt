package com.mindunlock.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mindunlock.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GitHubDeviceCode(
    val userCode: String,
    val verificationUri: String,
    internal val deviceCode: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long
)

data class GitHubAccount(val login: String, val name: String?)

data class GitHubStoredTokens(
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAtMillis: Long,
    val refreshTokenExpiresAtMillis: Long
)

class GitHubDeviceAuth(
    private val clientId: String = BuildConfig.GITHUB_APP_CLIENT_ID,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(20))
        .callTimeout(java.time.Duration.ofSeconds(25))
        .followRedirects(false)
        .build()
) {
    suspend fun requestDeviceCode(): GitHubDeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "شناسه GitHub App در نسخه برنامه تنظیم نشده است." }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "offline_access")
            .build()
        val json = postJson("https://github.com/login/device/code", body)
        GitHubDeviceCode(
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            deviceCode = json.getString("device_code"),
            expiresInSeconds = json.optLong("expires_in", 900L),
            intervalSeconds = json.optLong("interval", 5L).coerceAtLeast(1L)
        )
    }

    suspend fun authorize(
        context: Context,
        code: GitHubDeviceCode,
        onWaiting: (String) -> Unit = {}
    ): GitHubAccount = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + code.expiresInSeconds.coerceAtLeast(1L) * 1000L
        var interval = code.intervalSeconds.coerceAtLeast(1L)
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(interval * 1000L)
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", code.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val response = postJson("https://github.com/login/oauth/access_token", body)
            when (response.optString("error")) {
                "authorization_pending" -> withContext(Dispatchers.Main.immediate) {
                    onWaiting("در انتظار تأیید در GitHub…")
                }
                "slow_down" -> {
                    interval += 5L
                    withContext(Dispatchers.Main.immediate) {
                        onWaiting("گیت‌هاب فاصله بررسی را افزایش داد؛ در انتظار…")
                    }
                }
                "access_denied" -> error("درخواست ورود در GitHub رد شد.")
                "expired_token", "token_expired" -> error("کد ورود منقضی شد؛ دوباره تلاش کن.")
                "incorrect_client_credentials" -> error("Client ID با تنظیمات GitHub App مطابقت ندارد.")
                "unsupported_grant_type" -> error("نوع مجوز Device Flow پشتیبانی نشد.")
                "device_flow_disabled" -> error("Device Flow در تنظیمات GitHub App فعال نشده است.")
                "incorrect_device_code" -> error("کد دستگاه معتبر نیست؛ ورود را از ابتدا شروع کن.")
                else -> {
                    val token = response.optString("access_token")
                    if (token.isNotBlank()) {
                        val account = fetchAccount(token)
                        val now = System.currentTimeMillis()
                        val expiresIn = response.optLong("expires_in", 0L)
                        val refreshExpiresIn = response.optLong("refresh_token_expires_in", 0L)
                        GitHubTokenVault.save(
                            context = context.applicationContext,
                            tokens = GitHubStoredTokens(
                                accessToken = token,
                                refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() },
                                accessTokenExpiresAtMillis = if (expiresIn > 0L) now + expiresIn * 1000L else 0L,
                                refreshTokenExpiresAtMillis = if (refreshExpiresIn > 0L) now + refreshExpiresIn * 1000L else 0L
                            )
                        )
                        return@withContext account
                    }
                    if (response.has("error")) {
                        error("ورود GitHub ناموفق بود: ${response.optString("error_description", response.optString("error"))}")
                    }
                    error("پاسخ ورود GitHub قابل تشخیص نیست.")
                }
            }
        }
        error("زمان تأیید ورود تمام شد؛ دوباره تلاش کن.")
    }

    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val stored = GitHubTokenVault.read(context) ?: return@withContext null
        val now = System.currentTimeMillis()
        if (stored.accessTokenExpiresAtMillis == 0L || stored.accessTokenExpiresAtMillis > now + 60_000L) {
            return@withContext stored.accessToken
        }
        val refresh = stored.refreshToken ?: run {
            GitHubTokenVault.clear(context)
            return@withContext null
        }
        if (stored.refreshTokenExpiresAtMillis > 0L && stored.refreshTokenExpiresAtMillis <= now) {
            GitHubTokenVault.clear(context)
            return@withContext null
        }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        val response = postJson("https://github.com/login/oauth/access_token", body)
        val newAccess = response.optString("access_token")
        val newRefresh = response.optString("refresh_token")
        if (newAccess.isBlank() || newRefresh.isBlank()) {
            GitHubTokenVault.clear(context)
            return@withContext null
        }
        val refreshedAt = System.currentTimeMillis()
        GitHubTokenVault.save(
            context,
            GitHubStoredTokens(
                accessToken = newAccess,
                refreshToken = newRefresh,
                accessTokenExpiresAtMillis = refreshedAt + response.optLong("expires_in", 28_800L) * 1000L,
                refreshTokenExpiresAtMillis = refreshedAt + response.optLong("refresh_token_expires_in", 15_897_600L) * 1000L
            )
        )
        newAccess
    }

    private fun fetchAccount(token: String): GitHubAccount {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "MindUnlock-Android")
            .get().build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("اعتبارسنجی حساب GitHub ناموفق بود (HTTP ${response.code}).")
            val user = JSONObject(payload)
            GitHubAccount(user.optString("login"), user.optString("name").takeIf { it.isNotBlank() })
        }
    }

    private fun postJson(url: String, body: FormBody): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MindUnlock-Android")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            val json = try {
                JSONObject(payload)
            } catch (_: Exception) {
                if (!response.isSuccessful) error("ارتباط با سرویس ورود GitHub ناموفق بود (HTTP ${response.code}).")
                error("پاسخ سرویس ورود GitHub معتبر نیست.")
            }
            if (!response.isSuccessful && !json.has("error")) {
                error("ارتباط با سرویس ورود GitHub ناموفق بود (HTTP ${response.code}).")
            }
            return json
        }
    }
}

object GitHubTokenVault {
    private const val PREFS = "github_auth_secure"
    private const val TOKENS_KEY = "github_tokens_encrypted"
    private const val IV_KEY = "github_tokens_iv"
    private const val KEY_ALIAS = "mindunlock_github_token_aes"

    fun save(context: Context, tokens: GitHubStoredTokens) {
        require(tokens.accessToken.isNotBlank())
        val plain = JSONObject()
            .put("access_token", tokens.accessToken)
            .put("refresh_token", tokens.refreshToken ?: JSONObject.NULL)
            .put("access_expires_at", tokens.accessTokenExpiresAtMillis)
            .put("refresh_expires_at", tokens.refreshTokenExpiresAtMillis)
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs(context).edit()
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(TOKENS_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(context: Context): GitHubStoredTokens? {
        val store = prefs(context)
        val iv = store.getString(IV_KEY, null) ?: return null
        val encrypted = store.getString(TOKENS_KEY, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            val json = JSONObject(String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8))
            GitHubStoredTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" },
                accessTokenExpiresAtMillis = json.optLong("access_expires_at", 0L),
                refreshTokenExpiresAtMillis = json.optLong("refresh_expires_at", 0L)
            )
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
