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

    @Test
    fun successfulRestoreReplacesAndRestoresAllNineCollections() = runBlocking {
        // Seed stale records in every collection; a successful restore must replace them.
        database.progressDao().upsert(
            ProgressEntity(itemUid = "stale-progress", chapterId = "old")
        )
        database.noteDao().upsert(NoteEntity(title = "یادداشت قدیمی", body = "حذف شود"))
        database.bookmarkDao().upsert(BookmarkEntity(itemUid = "stale-bookmark", chapterId = "old"))
        database.studyTaskDao().upsert(StudyTaskEntity(title = "کار قدیمی"))
        database.mindDumpDao().insert(MindDumpEntity(content = "ورودی قدیمی"))
        database.journalEntryDao().insert(JournalEntryEntity(title = "روزنگار قدیمی", body = "حذف شود"))
        database.laterTopicDao().insert(LaterTopicEntity(title = "موضوع قدیمی"))
        database.labStepProgressDao().upsert(
            LabStepProgressEntity(itemUid = "stale-lab", stepIndex = 1, completed = true)
        )
        database.learningSessionDao().insert(
            LearningSessionEntity(topicTitle = "جلسه قدیمی", startedAt = 1_600_000_000_000L)
        )

        val restoredPayload = UserDataBackupPayload(
            progress = listOf(
                ProgressEntity(
                    itemUid = "restored-progress",
                    chapterId = "networking",
                    completed = true,
                    attempts = 4,
                    correct = 3,
                    wrong = 1,
                    dueAt = 1_810_000_000_000L
                )
            ),
            notes = listOf(
                NoteEntity(id = 101, itemUid = "item-101", chapterId = "networking",
                    title = "یادداشت بازیابی‌شده", body = "متن کامل یادداشت", updatedAt = 1_810_000_000_100L)
            ),
            bookmarks = listOf(
                BookmarkEntity(itemUid = "restored-bookmark", chapterId = "networking", createdAt = 1_810_000_000_200L)
            ),
            tasks = listOf(
                StudyTaskEntity(id = 102, title = "کار بازیابی‌شده", details = "جزئیات کار",
                    taskType = "task", dueAt = 1_810_000_000_300L, completed = false,
                    createdAt = 1_810_000_000_300L)
            ),
            mindDump = listOf(
                MindDumpEntity(id = 103, content = "ورودی بازیابی‌شده", category = "idea",
                    status = "inbox", createdAt = 1_810_000_000_400L, updatedAt = 1_810_000_000_500L)
            ),
            journalEntries = listOf(
                JournalEntryEntity(id = 104, title = "روزنگار بازیابی‌شده", body = "متن روزنگار",
                    mood = "calm", tags = "آموزش,شبکه",
                    createdAt = 1_810_000_000_600L, updatedAt = 1_810_000_000_700L)
            ),
            laterTopics = listOf(
                LaterTopicEntity(id = 105, title = "موضوع بازیابی‌شده", details = "جزئیات موضوع",
                    priority = 1, revisitAt = 1_820_000_000_000L, status = "parked",
                    createdAt = 1_810_000_000_800L, updatedAt = 1_810_000_000_900L)
            ),
            labStepProgress = listOf(
                LabStepProgressEntity(itemUid = "restored-lab", stepIndex = 3,
                    completed = true, updatedAt = 1_810_000_001_000L)
            ),
            learningSessions = listOf(
                LearningSessionEntity(id = 106, chapterId = "networking",
                    topicTitle = "جلسه بازیابی‌شده", source = "learning_engine",
                    startedAt = 1_810_000_001_100L, endedAt = 1_810_000_002_100L,
                    durationSeconds = 1_000)
            )
        )
        val document = UserDataBackupDocument(
            schemaVersion = UserDataBackupCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = 1_810_000_003_000L,
            appVersion = "transaction-test",
            payload = restoredPayload
        )

        UserDataBackupManager.restore(context, database, document)

        val progress = database.progressDao().getAll()
        assertEquals(1, progress.size)
        assertEquals("restored-progress", progress.single().itemUid)
        assertEquals(4, progress.single().attempts)
        assertEquals(3, progress.single().correct)
        assertEquals(1, progress.single().wrong)
        assertEquals(1_810_000_000_000L, progress.single().dueAt)

        val notes = database.noteDao().getAll()
        assertEquals(1, notes.size)
        assertEquals(101L, notes.single().id)
        assertEquals("یادداشت بازیابی‌شده", notes.single().title)
        assertEquals("متن کامل یادداشت", notes.single().body)

        val bookmarks = database.bookmarkDao().getAll()
        assertEquals(1, bookmarks.size)
        assertEquals("restored-bookmark", bookmarks.single().itemUid)

        val tasks = database.studyTaskDao().getAll()
        assertEquals(1, tasks.size)
        assertEquals(102L, tasks.single().id)
        assertEquals("کار بازیابی‌شده", tasks.single().title)
        assertEquals("جزئیات کار", tasks.single().details)
        assertEquals(1_810_000_000_300L, tasks.single().dueAt)

        val mindDump = database.mindDumpDao().getAll()
        assertEquals(1, mindDump.size)
        assertEquals(103L, mindDump.single().id)
        assertEquals("ورودی بازیابی‌شده", mindDump.single().content)
        assertEquals("idea", mindDump.single().category)

        val journal = database.journalEntryDao().getAll()
        assertEquals(1, journal.size)
        assertEquals(104L, journal.single().id)
        assertEquals("روزنگار بازیابی‌شده", journal.single().title)
        assertEquals("متن روزنگار", journal.single().body)
        assertEquals("calm", journal.single().mood)
        assertEquals("آموزش,شبکه", journal.single().tags)

        val later = database.laterTopicDao().getAll()
        assertEquals(1, later.size)
        assertEquals(105L, later.single().id)
        assertEquals("موضوع بازیابی‌شده", later.single().title)
        assertEquals(1, later.single().priority)
        assertEquals("parked", later.single().status)
        assertEquals(1_820_000_000_000L, later.single().revisitAt)

        val lab = database.labStepProgressDao().getAll()
        assertEquals(1, lab.size)
        assertEquals("restored-lab", lab.single().itemUid)
        assertEquals(3, lab.single().stepIndex)
        assertTrue(lab.single().completed)

        val sessions = database.learningSessionDao().getAll()
        assertEquals(1, sessions.size)
        assertEquals(106L, sessions.single().id)
        assertEquals("جلسه بازیابی‌شده", sessions.single().topicTitle)
        assertEquals(1_810_000_001_100L, sessions.single().startedAt)
        assertEquals(1_810_000_002_100L, sessions.single().endedAt)
        assertEquals(1_000L, sessions.single().durationSeconds)

        val safetyFiles = safetyDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            .orEmpty()
        assertEquals(1, safetyFiles.size)
        val safetyDocument = UserDataBackupCodec.decode(safetyFiles.single().readText(Charsets.UTF_8))
        assertEquals("stale-progress", safetyDocument.payload.progress.single().itemUid)
        assertEquals("یادداشت قدیمی", safetyDocument.payload.notes.single().title)
        assertEquals("stale-bookmark", safetyDocument.payload.bookmarks.single().itemUid)
        assertEquals("کار قدیمی", safetyDocument.payload.tasks.single().title)
        assertEquals("ورودی قدیمی", safetyDocument.payload.mindDump.single().content)
        assertEquals("روزنگار قدیمی", safetyDocument.payload.journalEntries.single().title)
        assertEquals("موضوع قدیمی", safetyDocument.payload.laterTopics.single().title)
        assertEquals("stale-lab", safetyDocument.payload.labStepProgress.single().itemUid)
        assertEquals("جلسه قدیمی", safetyDocument.payload.learningSessions.single().topicTitle)
    }
}
