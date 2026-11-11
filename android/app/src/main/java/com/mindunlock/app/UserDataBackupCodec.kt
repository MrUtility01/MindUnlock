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
            require(payloadJson.get(key)?.isJsonArray == true) { "بخش داده «$key» ناقص است." }
            require(payloadJson.getAsJsonArray(key).size() <= 50_000) { "تعداد رکوردهای «$key» از حد مجاز بیشتر است." }
        }
        val expected = root.get("sha256")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("امضای صحت فایل وجود ندارد.")
        val actual = sha256(payloadJson.toString())
        require(MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))) {
            "صحت فایل پشتیبان تأیید نشد؛ فایل تغییر کرده یا آسیب دیده است."
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
