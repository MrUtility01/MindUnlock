package com.mindunlock.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mindunlock.app.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
                "authorization_pending" -> onWaiting("در انتظار تأیید در GitHub…")
                "slow_down" -> {
                    interval += 5L
                    onWaiting("گیت‌هاب درخواست کرده فاصله بررسی بیشتر شود…")
                }
                "access_denied" -> error("درخواست ورود در GitHub رد شد.")
                "expired_token" -> error("کد ورود منقضی شد؛ دوباره تلاش کن.")
                "incorrect_client_credentials" -> error("Client ID با تنظیمات GitHub App مطابقت ندارد.")
                "unsupported_grant_type" -> error("Device Flow برای این GitHub App فعال نیست.")
                else -> {
                    val token = response.optString("access_token")
                    if (token.isNotBlank()) {
                        val account = fetchAccount(token)
                        GitHubTokenVault.save(context = appContext, token = token)
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

    // Set only during the authorize call; no token or device code is written to logs.
    @Volatile private var appContext: Context? = null

    suspend fun authorize(
        context: Context,
        code: GitHubDeviceCode,
        onWaiting: (String) -> Unit = {}
    ): GitHubAccount {
        appContext = context.applicationContext
        return try { authorize(code, onWaiting) } finally { appContext = null }
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
            if (!response.isSuccessful) error("ارتباط با سرویس ورود GitHub ناموفق بود (HTTP ${response.code}).")
            return JSONObject(payload)
        }
    }
}

object GitHubTokenVault {
    private const val PREFS = "github_auth_secure"
    private const val TOKEN_KEY = "github_user_token"
    private const val KEY_ALIAS = "mindunlock_github_token_aes"

    fun save(context: Context, token: String) {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs(context).edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(context: Context): String? {
        val store = prefs(context)
        val iv = store.getString("iv", null) ?: return null
        val encrypted = store.getString(TOKEN_KEY, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
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
