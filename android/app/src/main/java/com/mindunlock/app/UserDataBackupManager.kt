package com.mindunlock.app

import android.content.Context
import com.mindunlock.app.data.local.UserDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
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

    suspend fun restore(userDb: UserDatabase, document: UserDataBackupDocument) = withContext(Dispatchers.IO) {
        require(document.schemaVersion == UserDataBackupCodec.CURRENT_SCHEMA_VERSION) {
            "نسخه ساختار پشتیبان پشتیبانی نمی‌شود."
        }
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
