package com.mindunlock.app

import androidx.room.Room
import com.mindunlock.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserDataDeletionTest {
    @Test
    fun deletesEveryUserOwnedTableAndPrivateRecoveryFilesButKeepsDatabaseUsable() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.progressDao().upsert(ProgressEntity(itemUid = "item-1", chapterId = "chapter-1"))
            db.noteDao().upsert(NoteEntity(title = "یادداشت", body = "متن"))
            db.bookmarkDao().upsert(BookmarkEntity(itemUid = "item-1", chapterId = "chapter-1"))
            db.studyTaskDao().upsert(StudyTaskEntity(title = "کار"))
            db.mindDumpDao().insert(MindDumpEntity(content = "ورودی"))
            db.journalEntryDao().insert(JournalEntryEntity(body = "روزنگار"))
            db.laterTopicDao().insert(LaterTopicEntity(title = "موضوع"))
            db.labStepProgressDao().upsert(LabStepProgressEntity(itemUid = "item-1", stepIndex = 0))
            val sessionId = db.learningSessionDao().insert(LearningSessionEntity(topicTitle = "جلسه"))
            db.labQuestionResponseDao().upsert(LabQuestionResponseEntity(itemUid = "item-1", questionIndex = 0, answer = "پاسخ"))
            db.learningProfileDao().upsert(LearningProfileEntity(answersCsv = "1,2,3"))
            db.learningSessionFeedbackDao().upsert(LearningSessionFeedbackEntity(sessionId = sessionId, comprehension = 4, perceivedDifficulty = 2, focus = 5))
            db.learningStageProgressDao().upsert(LearningStageProgressEntity(chapterId = "chapter-1", stageId = "stage-1", stageIndex = 0))
            val firstNode = db.mindMapDao().insertNode(MindMapNodeEntity(title = "گره اول"))
            val secondNode = db.mindMapDao().insertNode(MindMapNodeEntity(title = "گره دوم"))
            db.mindMapDao().connect(firstNode, secondNode, "مرتبط")

            val safety = java.io.File(context.filesDir, "backup-safety").apply { mkdirs() }
            java.io.File(safety, "before-wipe.json").writeText("{}")
            val diagnostics = java.io.File(context.filesDir, "diagnostics").apply { mkdirs() }
            java.io.File(diagnostics, "last-crash.txt").writeText("private diagnostic")

            UserDataBackupManager.deleteAllPersonalData(context, db)

            assertEquals(0, db.progressDao().getAll().size)
            assertEquals(0, db.noteDao().getAll().size)
            assertEquals(0, db.bookmarkDao().getAll().size)
            assertEquals(0, db.studyTaskDao().getAll().size)
            assertEquals(0, db.mindDumpDao().getAll().size)
            assertEquals(0, db.journalEntryDao().getAll().size)
            assertEquals(0, db.laterTopicDao().getAll().size)
            assertEquals(0, db.labStepProgressDao().getAll().size)
            assertEquals(0, db.learningSessionDao().getAll().size)
            assertEquals(0, db.labQuestionResponseDao().getAll().size)
            assertEquals(null, db.learningProfileDao().get())
            assertEquals(0, db.learningSessionFeedbackDao().getAll().size)
            assertEquals(0, db.learningStageProgressDao().getAll().size)
            assertEquals(0, db.mindMapDao().getAllNodes().size)
            assertEquals(0, db.mindMapDao().getAllEdges().size)
            assertFalse(safety.exists())
            assertFalse(diagnostics.exists())

            // The database remains usable after the transactional wipe.
            db.noteDao().upsert(NoteEntity(title = "بعد از حذف", body = "داده جدید"))
            assertEquals(1, db.noteDao().getAll().size)
        } finally {
            db.close()
        }
    }
}
