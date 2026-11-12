package com.mindunlock.app

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mindunlock.app.data.local.BookmarkEntity
import com.mindunlock.app.data.local.JournalEntryEntity
import com.mindunlock.app.data.local.LabStepProgressEntity
import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.LearningSessionEntity
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class UserDataBackupPayload(
    val progress: List<ProgressEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val tasks: List<StudyTaskEntity> = emptyList(),
    val mindDump: List<MindDumpEntity> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val laterTopics: List<LaterTopicEntity> = emptyList(),
    val labStepProgress: List<LabStepProgressEntity> = emptyList(),
    val learningSessions: List<LearningSessionEntity> = emptyList()
) {
    val totalRecords: Int
        get() = progress.size + notes.size + bookmarks.size + tasks.size + mindDump.size +
            journalEntries.size + laterTopics.size + labStepProgress.size + learningSessions.size
}

data class UserDataBackupDocument(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val payload: UserDataBackupPayload
)

object UserDataBackupCodec {
    const val CURRENT_SCHEMA_VERSION = 1
    private val gson = Gson()
    private val requiredCollections = listOf(
        "progress", "notes", "bookmarks", "tasks", "mindDump",
        "journalEntries", "laterTopics", "labStepProgress", "learningSessions"
    )

    fun encode(payload: UserDataBackupPayload, exportedAt: Long, appVersion: String): String {
        require(exportedAt > 0) { "زمان پشتیبان‌گیری نامعتبر است." }
        val payloadJson = gson.toJsonTree(payload)
        val root = JsonObject().apply {
            addProperty("schemaVersion", CURRENT_SCHEMA_VERSION)
            addProperty("exportedAt", exportedAt)
            addProperty("appVersion", appVersion.take(80))
            add("payload", payloadJson)
            addProperty("sha256", sha256(payloadJson.toString()))
        }
        return gson.toJson(root)
    }

    private const val ENCRYPTED_FORMAT = "mindunlock-encrypted-backup-v1"
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encrypt(plainJson: String, password: CharArray): String {
        var key: ByteArray? = null
        try {
            require(password.size >= 8) { "گذرواژه پشتیبان باید حداقل ۸ نویسه باشد." }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val encrypted = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
            return gson.toJson(JsonObject().apply {
                addProperty("format", ENCRYPTED_FORMAT)
                addProperty("kdf", "PBKDF2WithHmacSHA256")
                addProperty("iterations", PBKDF2_ITERATIONS)
                addProperty("cipher", "AES-256-GCM")
                addProperty("salt", Base64.getEncoder().encodeToString(salt))
                addProperty("iv", Base64.getEncoder().encodeToString(iv))
                addProperty("ciphertext", Base64.getEncoder().encodeToString(encrypted))
            })
        } finally {
            key?.fill(0)
            password.fill(Char.MIN_VALUE)
        }
    }

    fun decryptIfEncrypted(input: String, password: CharArray): String {
        var key: ByteArray? = null
        try {
            val root = runCatching { JsonParser.parseString(input).asJsonObject }.getOrNull()
                ?: return input
            if (root.get("format")?.takeIf { it.isJsonPrimitive }?.asString != ENCRYPTED_FORMAT) {
                return input
            }
            require(password.size >= 8) { "برای بازکردن این پشتیبان، گذرواژه حداقل ۸ نویسه‌ای را وارد کن." }
            val iterations = root.get("iterations")?.asInt ?: 0
            require(iterations in 100_000..500_000) { "پارامتر مشتق‌سازی کلید در فایل معتبر نیست." }
            val salt = decodeBase64(root, "salt", 16)
            val iv = decodeBase64(root, "iv", 12)
            val ciphertext = decodeBase64(root, "ciphertext", 25 * 1024 * 1024)
            require(iv.size == 12 && salt.size == 16 && ciphertext.size >= 16) { "ساختار داده رمزگذاری‌شده ناقص است." }
            key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            return try {
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: AEADBadTagException) {
                throw IllegalArgumentException("گذرواژه اشتباه است یا فایل رمزگذاری‌شده دستکاری شده است.")
            } catch (error: Exception) {
                throw IllegalArgumentException("بازکردن پشتیبان رمزگذاری‌شده ناموفق بود: " + (error.message ?: "خطای رمزنگاری"))
            }
        } finally {
            key?.fill(0)
            password.fill(Char.MIN_VALUE)
        }
    }

    private fun decodeBase64(root: JsonObject, field: String, maxBytes: Int): ByteArray {
        val encoded = root.get(field)?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("فیلد رمزگذاری «" + field + "» وجود ندارد.")
        require(encoded.length <= maxBytes * 2) { "فیلد رمزگذاری بیش از حد بزرگ است." }
        return runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("فیلد رمزگذاری «" + field + "» خراب است.") }
            .also { require(it.size <= maxBytes) { "داده رمزگذاری‌شده بیش از حد بزرگ است." } }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun decode(json: String): UserDataBackupDocument {
        require(json.isNotBlank()) { "فایل پشتیبان خالی است." }
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw IllegalArgumentException("ساختار JSON فایل پشتیبان معتبر نیست.") }
        val schema = root.get("schemaVersion")?.takeIf { it.isJsonPrimitive }?.asInt
            ?: throw IllegalArgumentException("نسخه ساختار پشتیبان مشخص نیست.")
        require(schema == CURRENT_SCHEMA_VERSION) { "نسخه پشتیبان پشتیبانی نمی‌شود: $schema" }
        val exportedAt = root.get("exportedAt")?.takeIf { it.isJsonPrimitive }?.asLong
            ?: throw IllegalArgumentException("تاریخ ساخت پشتیبان وجود ندارد.")
        require(exportedAt > 0) { "تاریخ ساخت پشتیبان نامعتبر است." }
        val appVersion = root.get("appVersion")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val payloadJson = root.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("بخش داده‌های پشتیبان وجود ندارد.")
        requiredCollections.forEach { key ->
            val collection = payloadJson.get(key)
            if (collection != null) {
                require(collection.isJsonArray) { "ساختار بخش داده «$key» معتبر نیست." }
                require(collection.asJsonArray.size() <= 50_000) { "تعداد رکوردهای «$key» از حد مجاز بیشتر است." }
            }
        }
        val expected = root.get("sha256")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("امضای صحت فایل وجود ندارد.")
        val actual = sha256(payloadJson.toString())
        require(MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))) {
            "صحت فایل پشتیبان تأیید نشد؛ فایل تغییر کرده یا آسیب دیده است."
        }
        // Schema v1 backups created by older app versions may not contain collections
        // introduced later. Verify the original payload hash first, then normalize
        // absent collections to empty arrays so Gson can safely decode the payload.
        requiredCollections.forEach { key ->
            if (!payloadJson.has(key)) payloadJson.add(key, com.google.gson.JsonArray())
        }
        val payload = runCatching { gson.fromJson(payloadJson, UserDataBackupPayload::class.java) }
            .getOrElse { throw IllegalArgumentException("خواندن داده‌های پشتیبان ناموفق بود.") }
            ?: throw IllegalArgumentException("داده‌های پشتیبان خالی هستند.")
        validateUniqueKeys(payload)
        return UserDataBackupDocument(schema, exportedAt, appVersion, payload)
    }

    private fun validateUniqueKeys(payload: UserDataBackupPayload) {
        require(payload.progress.map { it.itemUid }.distinct().size == payload.progress.size) { "رکورد پیشرفت تکراری است." }
        require(payload.bookmarks.map { it.itemUid }.distinct().size == payload.bookmarks.size) { "نشانک تکراری است." }
        require(payload.labStepProgress.map { it.itemUid to it.stepIndex }.distinct().size == payload.labStepProgress.size) { "مرحله آزمایشگاه تکراری است." }
        require(payload.notes.map { it.id }.distinct().size == payload.notes.size) { "یادداشت تکراری است." }
        require(payload.tasks.map { it.id }.distinct().size == payload.tasks.size) { "کار تکراری است." }
        require(payload.mindDump.map { it.id }.distinct().size == payload.mindDump.size) { "ورودی صندوق تکراری است." }
        require(payload.journalEntries.map { it.id }.distinct().size == payload.journalEntries.size) { "یادداشت روزانه تکراری است." }
        require(payload.laterTopics.map { it.id }.distinct().size == payload.laterTopics.size) { "موضوع تکراری است." }
        require(payload.learningSessions.map { it.id }.distinct().size == payload.learningSessions.size) { "جلسه یادگیری تکراری است." }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
