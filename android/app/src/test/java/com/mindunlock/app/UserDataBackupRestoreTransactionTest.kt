package com.mindunlock.app

import androidx.room.Room
import com.mindunlock.app.data.local.BookmarkEntity
import com.mindunlock.app.data.local.JournalEntryEntity
import com.mindunlock.app.data.local.LabStepProgressEntity
import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.LearningSessionEntity
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserDataBackupRestoreTransactionTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: UserDatabase
    private val safetyDirectory: File
        get() = File(context.filesDir, "backup-safety")

    @Before
    fun setUp() {
        safetyDirectory.deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        safetyDirectory.deleteRecursively()
    }

    @Test
    fun restoreFailureRollsBackEverySeededTableAndPreservesSafetyBackup() = runBlocking {
        database.progressDao().upsert(
            ProgressEntity(
                itemUid = "existing-progress",
                chapterId = "existing-chapter",
                completed = true,
                correct = 3
            )
        )
        database.noteDao().upsert(
            NoteEntity(title = "یادداشت قبلی", body = "این داده باید حفظ شود.")
        )
        database.bookmarkDao().upsert(
            BookmarkEntity(itemUid = "existing-bookmark", chapterId = "existing-chapter")
        )
        database.studyTaskDao().upsert(
            StudyTaskEntity(title = "کار قبلی", details = "جزئیات حفظ‌شده")
        )
        database.mindDumpDao().insert(
            MindDumpEntity(content = "ورودی قبلی صندوق", category = "thought")
        )
        database.journalEntryDao().insert(
            JournalEntryEntity(title = "روزنگار قبلی", body = "متن خصوصی حفظ‌شده")
        )
        database.laterTopicDao().insert(
            LaterTopicEntity(title = "موضوع بعداً", details = "برای زمان مناسب")
        )
        database.labStepProgressDao().upsert(
            LabStepProgressEntity(itemUid = "existing-lab", stepIndex = 2, completed = true)
        )
        database.learningSessionDao().insert(
            LearningSessionEntity(topicTitle = "جلسه قبلی", startedAt = 1_700_000_000_000L)
        )

        // Force a deterministic SQLite failure after several replacement collections
        // have already been inserted inside the restore transaction.
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_restore_mind_dump
            BEFORE INSERT ON mind_dump
            BEGIN
                SELECT RAISE(ABORT, 'forced restore failure');
            END
            """.trimIndent()
        )

        val document = UserDataBackupDocument(
            schemaVersion = UserDataBackupCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = 1_800_000_000_000L,
            appVersion = "transaction-test",
            payload = UserDataBackupPayload(
                progress = listOf(
                    ProgressEntity(
                        itemUid = "replacement-progress",
                        chapterId = "replacement-chapter",
                        completed = true
                    )
                ),
                notes = listOf(
                    NoteEntity(title = "یادداشت جایگزین", body = "نباید ثبت شود.")
                ),
                bookmarks = listOf(
                    BookmarkEntity(itemUid = "replacement-bookmark", chapterId = "replacement-chapter")
                ),
                tasks = listOf(
                    StudyTaskEntity(title = "کار جایگزین")
                ),
                mindDump = listOf(
                    MindDumpEntity(content = "ورودی جایگزین", category = "idea")
                )
            )
        )

        val result = runCatching {
            UserDataBackupManager.restore(context, database, document)
        }

        assertTrue("Restore must fail when SQLite rejects a mid-transaction insert.", result.isFailure)

        val progressAfterFailure = database.progressDao().getAll()
        assertEquals(1, progressAfterFailure.size)
        assertEquals("existing-progress", progressAfterFailure.single().itemUid)
        assertEquals(3, progressAfterFailure.single().correct)

        val notesAfterFailure = database.noteDao().getAll()
        assertEquals(1, notesAfterFailure.size)
        assertEquals("یادداشت قبلی", notesAfterFailure.single().title)
        assertEquals("این داده باید حفظ شود.", notesAfterFailure.single().body)

        val bookmarksAfterFailure = database.bookmarkDao().getAll()
        assertEquals(1, bookmarksAfterFailure.size)
        assertEquals("existing-bookmark", bookmarksAfterFailure.single().itemUid)

        val tasksAfterFailure = database.studyTaskDao().getAll()
        assertEquals(1, tasksAfterFailure.size)
        assertEquals("کار قبلی", tasksAfterFailure.single().title)

        val mindDumpAfterFailure = database.mindDumpDao().getAll()
        assertEquals(1, mindDumpAfterFailure.size)
        assertEquals("ورودی قبلی صندوق", mindDumpAfterFailure.single().content)

        val journalAfterFailure = database.journalEntryDao().getAll()
        assertEquals(1, journalAfterFailure.size)
        assertEquals("روزنگار قبلی", journalAfterFailure.single().title)

        val laterAfterFailure = database.laterTopicDao().getAll()
        assertEquals(1, laterAfterFailure.size)
        assertEquals("موضوع بعداً", laterAfterFailure.single().title)

        val labAfterFailure = database.labStepProgressDao().getAll()
        assertEquals(1, labAfterFailure.size)
        assertEquals("existing-lab", labAfterFailure.single().itemUid)
        assertEquals(2, labAfterFailure.single().stepIndex)

        val sessionsAfterFailure = database.learningSessionDao().getAll()
        assertEquals(1, sessionsAfterFailure.size)
        assertEquals("جلسه قبلی", sessionsAfterFailure.single().topicTitle)

        val safetyFiles = safetyDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            .orEmpty()
        assertEquals("A safety backup must be persisted before the restore transaction starts.", 1, safetyFiles.size)

        val safetyDocument = UserDataBackupCodec.decode(safetyFiles.single().readText(Charsets.UTF_8))
        assertEquals("existing-progress", safetyDocument.payload.progress.single().itemUid)
        assertEquals("یادداشت قبلی", safetyDocument.payload.notes.single().title)
        assertEquals("existing-bookmark", safetyDocument.payload.bookmarks.single().itemUid)
        assertEquals("کار قبلی", safetyDocument.payload.tasks.single().title)
        assertEquals("ورودی قبلی صندوق", safetyDocument.payload.mindDump.single().content)
        assertEquals("روزنگار قبلی", safetyDocument.payload.journalEntries.single().title)
        assertEquals("موضوع بعداً", safetyDocument.payload.laterTopics.single().title)
        assertEquals("existing-lab", safetyDocument.payload.labStepProgress.single().itemUid)
        assertEquals("جلسه قبلی", safetyDocument.payload.learningSessions.single().topicTitle)
    }
}
