package com.mindunlock.app.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MindDumpWorkflowTest {
    private lateinit var database: UserDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            UserDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun editedNoteTitleAndBodyReturnToTheOriginalInboxEntry() = runBlocking {
        val sourceId = database.mindDumpDao().insert(
            MindDumpEntity(content = "عنوان اولیه\nجزئیات اولیه", category = "thought")
        )
        val noteId = MindDumpWorkflow.convertToNote(database, sourceId)!!
        val note = database.noteDao().getById(noteId)!!
        database.noteDao().upsert(note.copy(title = "عنوان تازه", body = "متن ویرایش‌شده"))

        assertEquals(sourceId, MindDumpWorkflow.restoreNoteToInbox(database, noteId))
        assertEquals("عنوان تازه\n\nمتن ویرایش‌شده", database.mindDumpDao().getById(sourceId)?.content)
        assertEquals("inbox", database.mindDumpDao().getById(sourceId)?.status)
        assertEquals(null, database.noteDao().getById(noteId)?.sourceMindDumpId)
    }

    @Test
    fun editedTaskTitleAndDetailsReturnToTheOriginalInboxEntry() = runBlocking {
        val sourceId = database.mindDumpDao().insert(
            MindDumpEntity(content = "عنوان اولیه\nجزئیات اولیه", category = "task")
        )
        val taskId = database.mindDumpDao().convertToTask(sourceId)!!
        val task = database.studyTaskDao().getById(taskId)!!
        database.studyTaskDao().upsert(task.copy(title = "عنوان تازه", details = "متن ویرایش‌شده"))

        assertEquals(sourceId, MindDumpWorkflow.restoreTaskToInbox(database, taskId))
        assertEquals("عنوان تازه\n\nمتن ویرایش‌شده", database.mindDumpDao().getById(sourceId)?.content)
        assertEquals("inbox", database.mindDumpDao().getById(sourceId)?.status)
        assertEquals(null, database.studyTaskDao().getById(taskId)?.sourceMindDumpId)
    }

    @Test
    fun deletingLinkedNoteJournalAndTaskRestoresTheirLatestText() = runBlocking {
        val noteSource = database.mindDumpDao().insert(MindDumpEntity(content = "یادداشت اولیه"))
        val noteId = MindDumpWorkflow.convertToNote(database, noteSource)!!
        val note = database.noteDao().getById(noteId)!!
        database.noteDao().upsert(note.copy(title = "یادداشت ویرایش‌شده"))
        assertTrue(MindDumpWorkflow.deleteNoteAndRestoreInbox(database, noteId))
        assertEquals("یادداشت ویرایش‌شده\n\nیادداشت اولیه", database.mindDumpDao().getById(noteSource)?.content)
        assertEquals(null, database.noteDao().getById(noteId))

        val journalSource = database.mindDumpDao().insert(MindDumpEntity(content = "نوشته اولیه"))
        val journalId = MindDumpWorkflow.convertToJournal(database, journalSource)!!
        val journal = database.journalEntryDao().getById(journalId)!!
        database.journalEntryDao().update(journal.copy(title = "عنوان روزانه", body = "متن نهایی"))
        assertTrue(MindDumpWorkflow.deleteJournalAndRestoreInbox(database, journalId))
        assertEquals("عنوان روزانه\n\nمتن نهایی", database.mindDumpDao().getById(journalSource)?.content)
        assertEquals(null, database.journalEntryDao().getById(journalId))

        val taskSource = database.mindDumpDao().insert(MindDumpEntity(content = "کار اولیه", category = "task"))
        val taskId = database.mindDumpDao().convertToTask(taskSource)!!
        val task = database.studyTaskDao().getById(taskId)!!
        database.studyTaskDao().upsert(task.copy(title = "کار نهایی", details = "شرح ویرایش‌شده"))
        assertTrue(MindDumpWorkflow.deleteTaskAndRestoreInbox(database, taskId))
        assertEquals("کار نهایی\n\nشرح ویرایش‌شده", database.mindDumpDao().getById(taskSource)?.content)
        assertEquals(null, database.studyTaskDao().getById(taskId))
    }

    @Test
    fun returningEditedLaterTopicRestoresTheLatestTopicText() = runBlocking {
        val sourceId = database.mindDumpDao().insert(
            MindDumpEntity(content = "عنوان اولیه\nشرح اولیه", category = "learning")
        )
        val topicId = database.mindDumpDao().convertToLaterTopic(sourceId, 2)!!
        val topic = database.laterTopicDao().getAll().single()
        database.laterTopicDao().update(topic.copy(title = "عنوان به‌روزشده", details = "شرح جدید"))

        assertEquals(sourceId, database.mindDumpDao().returnLaterTopicToInbox(topicId))
        assertEquals("عنوان به‌روزشده\n\nشرح جدید", database.mindDumpDao().getById(sourceId)?.content)
        assertEquals("inbox", database.mindDumpDao().getById(sourceId)?.status)
        assertTrue(database.laterTopicDao().getAll().isEmpty())
    }
}
