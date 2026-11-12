package com.mindunlock.app

import androidx.room.Room
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.ProgressEntity
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
    fun restoreFailureRollsBackAllTablesAndPreservesSafetyBackup() = runBlocking {
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
        database.mindDumpDao().insert(
            MindDumpEntity(content = "ورودی قبلی صندوق", category = "thought")
        )

        // Force a deterministic SQLite constraint-style failure after earlier tables
        // have already been cleared and partially repopulated in the restore transaction.
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

        val mindDumpAfterFailure = database.mindDumpDao().getAll()
        assertEquals(1, mindDumpAfterFailure.size)
        assertEquals("ورودی قبلی صندوق", mindDumpAfterFailure.single().content)

        val safetyFiles = safetyDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            .orEmpty()
        assertEquals("A safety backup must be persisted before the restore transaction starts.", 1, safetyFiles.size)

        val safetyDocument = UserDataBackupCodec.decode(safetyFiles.single().readText(Charsets.UTF_8))
        assertEquals("existing-progress", safetyDocument.payload.progress.single().itemUid)
        assertEquals("یادداشت قبلی", safetyDocument.payload.notes.single().title)
        assertEquals("ورودی قبلی صندوق", safetyDocument.payload.mindDump.single().content)
    }
}
