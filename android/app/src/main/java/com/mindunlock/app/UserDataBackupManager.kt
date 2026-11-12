package com.mindunlock.app

import android.content.Context
import com.mindunlock.app.data.local.UserDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.withContext

object UserDataBackupManager {
    suspend fun export(
        context: Context,
        userDb: UserDatabase,
        exportedAt: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.IO) {
        val payload = userDb.withTransaction {
            UserDataBackupPayload(
                progress = userDb.progressDao().getAll(),
                notes = userDb.noteDao().getAll(),
                bookmarks = userDb.bookmarkDao().getAll(),
                tasks = userDb.studyTaskDao().getAll(),
                mindDump = userDb.mindDumpDao().getAll(),
                journalEntries = userDb.journalEntryDao().getAll(),
                laterTopics = userDb.laterTopicDao().getAll(),
                labStepProgress = userDb.labStepProgressDao().getAll(),
                learningSessions = userDb.learningSessionDao().getAll()
            )
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
        UserDataBackupCodec.encode(payload, exportedAt, version)
    }

    suspend fun restore(context: Context, userDb: UserDatabase, document: UserDataBackupDocument) = withContext(Dispatchers.IO) {
        require(document.schemaVersion == UserDataBackupCodec.CURRENT_SCHEMA_VERSION) {
            "نسخه ساختار پشتیبان پشتیبانی نمی‌شود."
        }
        val safetyDirectory = File(context.filesDir, "backup-safety")
        if (!safetyDirectory.exists() && !safetyDirectory.mkdirs()) {
            throw IOException("ایجاد پوشه پشتیبان ایمنی پیش از بازیابی ناموفق بود.")
        }
        if (!safetyDirectory.isDirectory) {
            throw IOException("مسیر پشتیبان ایمنی معتبر نیست.")
        }

        // Persist a complete safety copy before touching any database table.
        val safetyJson = export(context, userDb)
        val temporaryFile = File.createTempFile("restore-safety-", ".tmp", safetyDirectory)
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(safetyJson.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val safetyFile = File(
                safetyDirectory,
                "before-restore-${System.currentTimeMillis()}-${UUID.randomUUID()}.json"
            )
            if (!temporaryFile.renameTo(safetyFile)) {
                throw IOException("ذخیره اتمیک پشتیبان ایمنی ناموفق بود؛ بازیابی انجام نشد.")
            }
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }

        safetyDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)?.forEach { it.delete() }

        userDb.withTransaction {
            userDb.progressDao().deleteAll()
            userDb.noteDao().deleteAll()
            userDb.bookmarkDao().deleteAll()
            userDb.studyTaskDao().deleteAll()
            userDb.mindDumpDao().deleteAll()
            userDb.journalEntryDao().deleteAll()
            userDb.laterTopicDao().deleteAll()
            userDb.labStepProgressDao().deleteAll()
            userDb.learningSessionDao().deleteAll()

            document.payload.progress.takeIf { it.isNotEmpty() }?.let { userDb.progressDao().upsertAll(it) }
            document.payload.notes.takeIf { it.isNotEmpty() }?.let { userDb.noteDao().upsertAll(it) }
            document.payload.bookmarks.takeIf { it.isNotEmpty() }?.let { userDb.bookmarkDao().upsertAll(it) }
            document.payload.tasks.takeIf { it.isNotEmpty() }?.let { userDb.studyTaskDao().upsertAll(it) }
            document.payload.mindDump.takeIf { it.isNotEmpty() }?.let { userDb.mindDumpDao().upsertAll(it) }
            document.payload.journalEntries.takeIf { it.isNotEmpty() }?.let { userDb.journalEntryDao().upsertAll(it) }
            document.payload.laterTopics.takeIf { it.isNotEmpty() }?.let { userDb.laterTopicDao().upsertAll(it) }
            document.payload.labStepProgress.takeIf { it.isNotEmpty() }?.let { userDb.labStepProgressDao().upsertAll(it) }
            document.payload.learningSessions.takeIf { it.isNotEmpty() }?.let { userDb.learningSessionDao().upsertAll(it) }
        }
    }
}
