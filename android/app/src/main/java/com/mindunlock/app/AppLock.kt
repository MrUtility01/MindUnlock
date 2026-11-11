package com.mindunlock.app

import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLock {
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    fun isConfigured(prefs: SharedPreferences): Boolean = !prefs.getString("app_lock_hash", null).isNullOrBlank()
    fun setPassword(prefs: SharedPreferences, password: CharArray) {
        require(password.size >= 4) { "رمز برنامه باید حداقل ۴ کاراکتر داشته باشد." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        prefs.edit().putString("app_lock_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("app_lock_hash", Base64.encodeToString(hash, Base64.NO_WRAP)).apply()
        password.fill('\u0000')
    }
    fun verify(prefs: SharedPreferences, password: CharArray): Boolean {
        val saltText = prefs.getString("app_lock_salt", null) ?: return false
        val hashText = prefs.getString("app_lock_hash", null) ?: return false
        return try {
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = derive(password, Base64.decode(saltText, Base64.NO_WRAP))
            java.security.MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) { false } finally { password.fill('\u0000') }
    }
    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
