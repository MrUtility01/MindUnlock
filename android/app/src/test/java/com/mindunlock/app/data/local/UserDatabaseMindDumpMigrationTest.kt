package com.mindunlock.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserDatabaseMindDumpMigrationTest {
    private val databaseName = "mindunlock-user-migration-test.db"
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun versionOneUserDataSurvivesMindDumpSchemaMigration() = runBlocking {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL("CREATE TABLE progress (itemUid TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, completed INTEGER NOT NULL, completedAt INTEGER, attempts INTEGER NOT NULL, correct INTEGER NOT NULL, wrong INTEGER NOT NULL, lastResult TEXT, quality INTEGER NOT NULL, intervalDays INTEGER NOT NULL, dueAt INTEGER, updatedAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemUid TEXT, chapterId TEXT, title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE INDEX index_notes_itemUid ON notes(itemUid)")
        legacy.execSQL("CREATE INDEX index_notes_chapterId ON notes(chapterId)")
        legacy.execSQL("CREATE TABLE bookmarks (itemUid TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE study_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, taskType TEXT NOT NULL, dueAt INTEGER, remindAt INTEGER, completed INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE INDEX index_study_tasks_remindAt ON study_tasks(remindAt)")
        legacy.execSQL("INSERT INTO progress (itemUid, chapterId, completed, completedAt, attempts, correct, wrong, lastResult, quality, intervalDays, dueAt, updatedAt) VALUES ('old-item', 'old-chapter', 1, 900, 2, 1, 1, 'correct', 4, 3, 2000, 1000)")
        legacy.execSQL("INSERT INTO notes (id, itemUid, chapterId, title, body, updatedAt) VALUES (7, NULL, NULL, 'یادداشت قبلی', 'محتوای حفظ‌شده', 1000)")
        legacy.execSQL("INSERT INTO study_tasks (id, title, details, taskType, dueAt, remindAt, completed, createdAt) VALUES (9, 'کار قبلی', 'جزئیات قبلی', 'task', NULL, NULL, 0, 1000)")
        legacy.version = 1
        legacy.close()

        val migrated = androidx.room.Room.databaseBuilder(
            context,
            UserDatabase::class.java,
            databaseName
        ).addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_3, UserDatabase.MIGRATION_3_4, UserDatabase.MIGRATION_4_5, UserDatabase.MIGRATION_5_6, UserDatabase.MIGRATION_6_7, UserDatabase.MIGRATION_7_8).allowMainThreadQueries().build()

        try {
            val preservedProgress = migrated.progressDao().get("old-item")
            assertEquals(true, preservedProgress?.completed)
            assertEquals(null, preservedProgress?.lastOpenedAt)
            migrated.progressDao().upsert(preservedProgress!!.copy(lastOpenedAt = 5_000L))
            assertEquals(5_000L, migrated.progressDao().get("old-item")?.lastOpenedAt)

            val notes = migrated.noteDao().observeAll().first()
            val tasks = migrated.studyTaskDao().observeAll().first()
            assertEquals(1, notes.size)
            assertEquals("یادداشت قبلی", notes.single().title)
            assertEquals("محتوای حفظ‌شده", notes.single().body)
            assertEquals(1, tasks.size)
            assertEquals("کار قبلی", tasks.single().title)

            val newId = migrated.mindDumpDao().insert(
                MindDumpEntity(content = "ایده جدید پس از ارتقا", category = "idea")
            )
            val newItem = migrated.mindDumpDao().observeAll().first().single()
            assertEquals(1L, newId)
            assertEquals("ایده جدید پس از ارتقا", newItem.content)
            assertEquals("inbox", newItem.status)

            val journalId = migrated.journalEntryDao().insert(
                JournalEntryEntity(title = "یادداشت روزانه", body = "ثبت یک روز جدید", mood = "calm", tags = "روزانه,شخصی")
            )
            val journal = migrated.journalEntryDao().observeAll().first().single()
            assertEquals(1L, journalId)
            assertEquals("یادداشت روزانه", journal.title)
            assertEquals("ثبت یک روز جدید", journal.body)

            assertEquals("calm", journal.mood)

            val laterId = migrated.laterTopicDao().insert(
                LaterTopicEntity(
                    title = "بررسی VLANها",
                    details = "بعد از تکمیل فصل مسیریابی بررسی شود.",
                    priority = 1,
                    status = "parked",
                    sourceMindDumpId = 42L
                )
            )
            val later = migrated.laterTopicDao().observeAll().first().single()
            assertEquals(1L, laterId)
            assertEquals("بررسی VLANها", later.title)
            assertEquals("بعد از تکمیل فصل مسیریابی بررسی شود.", later.details)
            assertEquals(1, later.priority)
            assertEquals(42L, later.sourceMindDumpId)
            migrated.laterTopicDao().updateStatus(later.id, "active", System.currentTimeMillis())
            assertEquals("active", migrated.laterTopicDao().observeAll().first().single().status)

            migrated.labStepProgressDao().upsert(
                LabStepProgressEntity(itemUid = "lab-vlan-01", stepIndex = 0, completed = true, updatedAt = 2000)
            )
            val labSteps = migrated.labStepProgressDao().observeByItem("lab-vlan-01").first()
            assertEquals(1, labSteps.size)
            assertEquals(0, labSteps.single().stepIndex)
            assertEquals(true, labSteps.single().completed)

            migrated.labQuestionResponseDao().upsert(
                LabQuestionResponseEntity(
                    itemUid = "lab-vlan-01",
                    questionIndex = 0,
                    answer = "saved answer after migration",
                    updatedAt = 12_000L
                )
            )
            val migratedAnswer = migrated.labQuestionResponseDao().observeByItem("lab-vlan-01").first().single()
            assertEquals(0, migratedAnswer.questionIndex)
            assertEquals("saved answer after migration", migratedAnswer.answer)
            assertEquals(12_000L, migratedAnswer.updatedAt)

            // The composite key must update one answer without overwriting another question.
            migrated.labQuestionResponseDao().upsert(
                LabQuestionResponseEntity(
                    itemUid = "lab-vlan-01",
                    questionIndex = 0,
                    answer = "edited answer",
                    updatedAt = 13_000L
                )
            )
            migrated.labQuestionResponseDao().upsert(
                LabQuestionResponseEntity(
                    itemUid = "lab-vlan-01",
                    questionIndex = 1,
                    answer = "second answer",
                    updatedAt = 14_000L
                )
            )
            val savedLabAnswers = migrated.labQuestionResponseDao().getAll()
                .filter { it.itemUid == "lab-vlan-01" }
                .sortedBy { it.questionIndex }
            assertEquals(2, savedLabAnswers.size)
            assertEquals("edited answer", savedLabAnswers[0].answer)
            assertEquals(13_000L, savedLabAnswers[0].updatedAt)
            assertEquals("second answer", savedLabAnswers[1].answer)

            migrated.labQuestionResponseDao().delete("lab-vlan-01", 0)
            val remainingLabAnswers = migrated.labQuestionResponseDao()
                .observeByItem("lab-vlan-01").first()
            assertEquals(1, remainingLabAnswers.size)
            assertEquals(1, remainingLabAnswers.single().questionIndex)
            assertEquals("second answer", remainingLabAnswers.single().answer)

            val sessionId = migrated.learningSessionDao().insert(
                LearningSessionEntity(
                    chapterId = "network-basics",
                    topicTitle = "مبانی شبکه",
                    startedAt = 10_000L
                )
            )
            val activeSession = migrated.learningSessionDao().observeActive().first()
            assertEquals(sessionId, activeSession?.id)
            assertEquals(null, activeSession?.endedAt)
            migrated.learningSessionDao().finish(sessionId, 16_000L, 6L)
            val finishedSession = migrated.learningSessionDao().observeAll().first().single()
            assertEquals(16_000L, finishedSession.endedAt)
            assertEquals(6L, finishedSession.durationSeconds)
            assertEquals(null, migrated.learningSessionDao().observeActive().first())

        } finally {
            migrated.close()
        }
    }
}
