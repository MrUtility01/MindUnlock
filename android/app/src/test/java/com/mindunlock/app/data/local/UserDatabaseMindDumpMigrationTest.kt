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
        legacy.execSQL("INSERT INTO notes (id, itemUid, chapterId, title, body, updatedAt) VALUES (7, NULL, NULL, 'یادداشت قبلی', 'محتوای حفظ‌شده', 1000)")
        legacy.execSQL("INSERT INTO study_tasks (id, title, details, taskType, dueAt, remindAt, completed, createdAt) VALUES (9, 'کار قبلی', 'جزئیات قبلی', 'task', NULL, NULL, 0, 1000)")
        legacy.version = 1
        legacy.close()

        val migrated = androidx.room.Room.databaseBuilder(
            context,
            UserDatabase::class.java,
            databaseName
        ).addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_3).allowMainThreadQueries().build()

        try {
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
        } finally {
            migrated.close()
        }
    }
}
