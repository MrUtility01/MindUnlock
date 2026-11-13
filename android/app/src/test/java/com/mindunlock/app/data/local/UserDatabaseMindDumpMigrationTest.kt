package com.mindunlock.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
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
    fun mindDumpTaskMapperPreservesFullTextAndMapsProjectCategory() {
        val project = MindDumpTaskMapper.toStudyTask(
            MindDumpEntity(
                content = "  پروژهٔ ارتقای شبکه  \nشرح کامل پروژه با جزئیات",
                category = "project"
            )
        )
        assertEquals("پروژهٔ ارتقای شبکه", project.title)
        assertEquals("  پروژهٔ ارتقای شبکه  \nشرح کامل پروژه با جزئیات", project.details)
        assertEquals("project", project.taskType)

        val learning = MindDumpTaskMapper.toStudyTask(
            MindDumpEntity(content = "مرور OSPF\nتمرین عملی", category = "learning")
        )
        assertEquals("مرور OSPF", learning.title)
        assertEquals("task", learning.taskType)
        assertEquals("مرور OSPF\nتمرین عملی", learning.details)
    }

    @Test
    fun plannerTasksSupportEditingCompletionAndDeletionWithoutLosingScheduleFields() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dueAt = 1_800_000_000_000L
            val id = db.studyTaskDao().upsert(
                StudyTaskEntity(
                    title = "مرور VLAN",
                    details = "مرور سناریوی شبکه",
                    taskType = "task",
                    dueAt = dueAt,
                    remindAt = dueAt
                )
            )
            val original = db.studyTaskDao().getAll().single()
            assertEquals(id, original.id)
            assertEquals(dueAt, original.dueAt)
            assertEquals(dueAt, original.remindAt)

            db.studyTaskDao().upsert(
                original.copy(
                    title = "مرور پیشرفته VLAN",
                    details = "بررسی trunk و inter-VLAN routing",
                    taskType = "project",
                    dueAt = dueAt + 3_600_000L,
                    remindAt = dueAt + 3_600_000L,
                    completed = true
                )
            )
            val edited = db.studyTaskDao().getAll().single()
            assertEquals("مرور پیشرفته VLAN", edited.title)
            assertEquals("project", edited.taskType)
            assertEquals(true, edited.completed)
            assertEquals(dueAt + 3_600_000L, edited.dueAt)
            assertEquals(dueAt + 3_600_000L, edited.remindAt)

            db.studyTaskDao().delete(id)
            assertEquals(0, db.studyTaskDao().getAll().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun mindDumpConversionsCommitAtomicallyAndRollbackOnFailure() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val rollbackId = db.mindDumpDao().insert(
                MindDumpEntity(content = "ورودی برای آزمون rollback", category = "thought")
            )

            runCatching {
                db.withTransaction {
                    db.noteDao().upsert(
                        NoteEntity(title = "یادداشت موقت", body = "نباید بعد از rollback باقی بماند")
                    )
                    db.mindDumpDao().updateStatus(
                        rollbackId, "note", 2_000L, null
                    )
                    error("simulate failure between conversion writes")
                }
            }

            val rolledBackEntry = db.mindDumpDao().getAll().single()
            assertEquals("inbox", rolledBackEntry.status)
            assertEquals(0, db.noteDao().getAll().size)

            val taskId = db.mindDumpDao().insert(
                MindDumpEntity(content = "ورودی تبدیل‌شده به کار", category = "task")
            )
            var linkedTaskId = 0L
            db.withTransaction {
                linkedTaskId = db.studyTaskDao().upsert(
                    StudyTaskEntity(title = "ورودی تبدیل‌شده به کار", details = "جزئیات")
                )
                db.mindDumpDao().updateStatus(taskId, "task", 3_000L, linkedTaskId)
            }
            val convertedTask = db.mindDumpDao().getAll().first { it.id == taskId }
            assertEquals("task", convertedTask.status)
            assertEquals(linkedTaskId, convertedTask.linkedTaskId)
            assertEquals("ورودی تبدیل‌شده به کار", db.studyTaskDao().getAll().single().title)

            val laterId = db.mindDumpDao().insert(
                MindDumpEntity(content = "موضوع برای بعد", category = "learning")
            )
            db.withTransaction {
                db.laterTopicDao().insert(
                    LaterTopicEntity(
                        title = "موضوع برای بعد",
                        details = "متن کامل ورودی",
                        sourceMindDumpId = laterId
                    )
                )
                db.mindDumpDao().updateStatus(laterId, "later", 4_000L, null)
            }
            assertEquals("later", db.mindDumpDao().getAll().first { it.id == laterId }.status)
            assertEquals(laterId, db.laterTopicDao().getAll().single().sourceMindDumpId)
        } finally {
            db.close()
        }
    }

    @Test
    fun convertingMindDumpToLaterTopicIsIdempotentAndPreservesTheSourceLink() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sourceText = "\n  موضوع برنامه‌ریزی شبکه  \nشرح کامل و چندخطی موضوع"
            val sourceId = db.mindDumpDao().insert(
                MindDumpEntity(content = sourceText, category = "idea")
            )

            val firstTopicId = db.mindDumpDao().convertToLaterTopic(sourceId, 1)
            val secondTopicId = db.mindDumpDao().convertToLaterTopic(sourceId, 3)

            assertEquals(firstTopicId, secondTopicId)
            assertEquals(1, db.laterTopicDao().getAll().size)
            val topic = db.laterTopicDao().getAll().single()
            assertEquals(firstTopicId, topic.id)
            assertEquals("موضوع برنامه‌ریزی شبکه", topic.title)
            assertEquals(sourceText, topic.details)
            assertEquals(sourceId, topic.sourceMindDumpId)
            assertEquals(1, topic.priority)
            val source = db.mindDumpDao().getById(sourceId)
            assertEquals("later", source?.status)

            // A retry must reuse the original topic rather than overwrite user edits.
            db.laterTopicDao().update(topic.copy(title = "عنوان ویرایش‌شده", details = "جزئیات شخصی‌سازی‌شده"))
            assertEquals(firstTopicId, db.mindDumpDao().convertToLaterTopic(sourceId))
            val editedTopic = db.laterTopicDao().getAll().single()
            assertEquals("عنوان ویرایش‌شده", editedTopic.title)
            assertEquals("جزئیات شخصی‌سازی‌شده", editedTopic.details)
            assertEquals("priority must also remain unchanged on retry", 1, editedTopic.priority)

            // If the linked topic was deleted, the next conversion repairs the link.
            db.laterTopicDao().delete(firstTopicId!!)
            val replacementTopicId = db.mindDumpDao().convertToLaterTopic(sourceId)
            assertEquals(true, replacementTopicId != null && replacementTopicId != firstTopicId)
            assertEquals(1, db.laterTopicDao().getAll().size)
            assertEquals(replacementTopicId, db.laterTopicDao().getAll().single().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun inboxConversionStoresSelectedEssentialDeferredOrCuriosityPriority() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val expected = listOf(1, 2, 3)
            expected.forEachIndexed { index, priority ->
                val sourceId = db.mindDumpDao().insert(
                    MindDumpEntity(content = "ورودی اولویت $priority", category = "learning")
                )
                val topicId = db.mindDumpDao().convertToLaterTopic(sourceId, priority)
                val topic = db.laterTopicDao().getAll().single { it.id == topicId }
                assertEquals(priority, topic.priority)
                assertEquals(sourceId, topic.sourceMindDumpId)
                assertEquals("ورودی اولویت $priority", topic.details)
                assertEquals("later", db.mindDumpDao().getById(sourceId)?.status)
            }

            val invalidSourceId = db.mindDumpDao().insert(
                MindDumpEntity(content = "اولویت خارج از محدوده", category = "idea")
            )
            val invalidTopicId = db.mindDumpDao().convertToLaterTopic(invalidSourceId, 99)
            val normalized = db.laterTopicDao().getAll().single { it.id == invalidTopicId }
            assertEquals(3, normalized.priority)
        } finally {
            db.close()
        }
    }

    @Test
    fun convertingMissingMindDumpToLaterTopicReturnsNullWithoutCreatingTopic() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(null, db.mindDumpDao().convertToLaterTopic(404L))
            assertEquals(0, db.laterTopicDao().getAll().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun convertingMindDumpToTaskIsIdempotentAndReusesTheExistingLink() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sourceId = db.mindDumpDao().insert(
                MindDumpEntity(
                    content = "  پروژه ارتقای شبکه  \nجزئیات کامل و چندخطی",
                    category = "project"
                )
            )

            val firstTaskId = db.mindDumpDao().convertToTask(sourceId)
            val secondTaskId = db.mindDumpDao().convertToTask(sourceId)

            assertEquals(firstTaskId, secondTaskId)
            assertEquals(1, db.studyTaskDao().getAll().size)
            val task = db.studyTaskDao().getAll().single()
            assertEquals(firstTaskId, task.id)
            assertEquals("پروژه ارتقای شبکه", task.title)
            assertEquals("  پروژه ارتقای شبکه  \nجزئیات کامل و چندخطی", task.details)
            assertEquals("project", task.taskType)

            val linkedEntry = db.mindDumpDao().getById(sourceId)
            assertEquals("task", linkedEntry?.status)
            assertEquals(firstTaskId, linkedEntry?.linkedTaskId)

            // Editing source content after conversion must not break or duplicate the linked task.
            db.mindDumpDao().updateContent(sourceId, "متن جدید منبع", 20_000L)
            assertEquals(firstTaskId, db.mindDumpDao().convertToTask(sourceId))
            assertEquals(1, db.studyTaskDao().getAll().size)
            assertEquals("  پروژه ارتقای شبکه  \nجزئیات کامل و چندخطی", db.studyTaskDao().getAll().single().details)
        } finally {
            db.close()
        }
    }

    @Test
    fun convertingMindDumpRepairsStaleTaskLinksAndReturnsNullForMissingEntries() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sourceId = db.mindDumpDao().insert(
                MindDumpEntity(
                    content = "  مرور DNS  \nبررسی رکوردهای داخلی",
                    category = "learning",
                    linkedTaskId = 987_654L
                )
            )
            val repairedTaskId = db.mindDumpDao().convertToTask(sourceId)
            val entry = db.mindDumpDao().getById(sourceId)

            assertEquals(true, repairedTaskId != null && repairedTaskId != 987_654L)
            assertEquals(repairedTaskId, entry?.linkedTaskId)
            assertEquals("task", entry?.status)
            assertEquals(1, db.studyTaskDao().getAll().size)
            assertEquals("مرور DNS", db.studyTaskDao().getAll().single().title)
            assertEquals(null, db.mindDumpDao().convertToTask(999_999L))
        } finally {
            db.close()
        }
    }

    @Test
    fun returningLaterTopicToInboxRestoresSourceAndRepairsMissingLinksAtomically() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sourceId = db.mindDumpDao().insert(
                MindDumpEntity(
                    content = "سؤال درباره مسیریابی",
                    category = "learning",
                    status = "later",
                    linkedTaskId = 123L
                )
            )
            val topicId = db.laterTopicDao().insert(
                LaterTopicEntity(
                    title = "سؤال درباره مسیریابی",
                    details = "متن کامل سؤال و زمینه آن",
                    sourceMindDumpId = sourceId
                )
            )

            assertEquals(sourceId, db.mindDumpDao().returnLaterTopicToInbox(topicId))
            val restored = db.mindDumpDao().getById(sourceId)
            assertEquals("inbox", restored?.status)
            assertEquals(null, restored?.linkedTaskId)
            assertEquals("سؤال درباره مسیریابی", restored?.content)
            assertEquals(0, db.laterTopicDao().getAll().size)

            val orphanTopicId = db.laterTopicDao().insert(
                LaterTopicEntity(title = "موضوع مستقل", details = "بدون منبع قبلی")
            )
            val orphanRestoredId = db.mindDumpDao().returnLaterTopicToInbox(orphanTopicId)
            val orphan = orphanRestoredId?.let { db.mindDumpDao().getById(it) }
            assertEquals("موضوع مستقل\n\nبدون منبع قبلی", orphan?.content)
            assertEquals("inbox", orphan?.status)

            val staleSourceTopicId = db.laterTopicDao().insert(
                LaterTopicEntity(
                    title = "بازیابی پیوند خراب",
                    details = "این متن باید بازیابی شود",
                    sourceMindDumpId = 999_999L
                )
            )
            val staleRestoredId = db.mindDumpDao().returnLaterTopicToInbox(staleSourceTopicId)
            val staleRestored = staleRestoredId?.let { db.mindDumpDao().getById(it) }
            assertEquals("بازیابی پیوند خراب\n\nاین متن باید بازیابی شود", staleRestored?.content)
            assertEquals("inbox", staleRestored?.status)
            assertEquals(null, db.mindDumpDao().returnLaterTopicToInbox(staleSourceTopicId))
            assertEquals(null, db.mindDumpDao().returnLaterTopicToInbox(999_999L))
            assertEquals(0, db.laterTopicDao().getAll().size)
            assertEquals(3, db.mindDumpDao().getAll().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun mindDumpEntriesCanBeEditedWithoutChangingTheirCategoryInboxStateOrLinks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val id = db.mindDumpDao().insert(
                MindDumpEntity(
                    content = "متن اولیه",
                    category = "learning",
                    status = "inbox",
                    createdAt = 1_000L,
                    updatedAt = 2_000L,
                    linkedTaskId = 17L
                )
            )
            db.mindDumpDao().updateContent(id, "متن اصلاح‌شده", 5_000L)
            val edited = db.mindDumpDao().getAll().single()
            assertEquals("متن اصلاح‌شده", edited.content)
            assertEquals("learning", edited.category)
            assertEquals("inbox", edited.status)
            assertEquals(1_000L, edited.createdAt)
            assertEquals(5_000L, edited.updatedAt)
            assertEquals(17L, edited.linkedTaskId)
        } finally {
            db.close()
        }
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
        ).addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_3, UserDatabase.MIGRATION_3_4, UserDatabase.MIGRATION_4_5, UserDatabase.MIGRATION_5_6, UserDatabase.MIGRATION_6_7, UserDatabase.MIGRATION_7_8, UserDatabase.MIGRATION_8_9, UserDatabase.MIGRATION_9_10, UserDatabase.MIGRATION_10_11, UserDatabase.MIGRATION_11_12, UserDatabase.MIGRATION_12_13, UserDatabase.MIGRATION_13_14).allowMainThreadQueries().build()

        try {
            val migratedProfile = LearningProfileEntity(answersCsv = List(20) { "5" }.joinToString(","), sessionMinutes = 60, maxTopics = 3, guidedMode = "flexible", completed = true)
            migrated.learningProfileDao().upsert(migratedProfile)
            val storedProfile = migrated.learningProfileDao().get()
            assertEquals(60, storedProfile?.sessionMinutes)
            assertEquals(3, storedProfile?.maxTopics)
            assertEquals("flexible", storedProfile?.guidedMode)
            assertEquals(20, storedProfile?.answersCsv?.split(",")?.size)

            // The version-1 fixture must traverse every migration, including the knowledge graph.
            val graphFirstId = migrated.mindMapDao().insertNode(
                MindMapNodeEntity(title = "شبکه", nodeType = "یادگیری")
            )
            val graphSecondId = migrated.mindMapDao().insertNode(
                MindMapNodeEntity(title = "DNS", nodeType = "مفهوم")
            )
            assertEquals(true, migrated.mindMapDao().connect(graphFirstId, graphSecondId, "پیش‌نیاز"))
            assertEquals(false, migrated.mindMapDao().connect(graphFirstId, graphSecondId, "تکراری"))
            assertEquals(1, migrated.mindMapDao().getAllEdges().size)

            val linkedGraphId = migrated.mindMapDao().createLinkedNode(
                MindMapNodeEntity(
                    title = "ایده مهاجرت‌شده",
                    sourceType = "mind_dump",
                    sourceId = 777L
                )
            )
            assertEquals(true, linkedGraphId > 0L)
            assertEquals(-1L, migrated.mindMapDao().createLinkedNode(
                MindMapNodeEntity(
                    title = "تکرار منبع",
                    sourceType = "mind_dump",
                    sourceId = 777L
                )
            ))
            assertEquals("mind_dump", migrated.mindMapDao().getAllNodes().first { it.id == linkedGraphId }.sourceType)

            migrated.mindMapDao().deleteNode(graphFirstId)
            assertEquals(0, migrated.mindMapDao().getAllEdges().size)

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
            migrated.journalEntryDao().update(
                journal.copy(
                    title = "عنوان ویرایش‌شده",
                    body = "متن اصلاح‌شده",
                    tags = "روزانه,ویرایش",
                    updatedAt = 20_000L
                )
            )
            val editedJournal = migrated.journalEntryDao().getAll().single()
            assertEquals("عنوان ویرایش‌شده", editedJournal.title)
            assertEquals("متن اصلاح‌شده", editedJournal.body)
            assertEquals("روزانه,ویرایش", editedJournal.tags)
            assertEquals(20_000L, editedJournal.updatedAt)

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
            val activeTopic = migrated.laterTopicDao().getAll().single()
            migrated.laterTopicDao().update(
                activeTopic.copy(
                    title = "بررسی پیشرفته VLANها",
                    details = "توضیحات تکمیل‌شده",
                    priority = 2,
                    revisitAt = 50_000L,
                    updatedAt = 51_000L
                )
            )
            val editedTopic = migrated.laterTopicDao().getAll().single()
            assertEquals("بررسی پیشرفته VLANها", editedTopic.title)
            assertEquals("توضیحات تکمیل‌شده", editedTopic.details)
            assertEquals(2, editedTopic.priority)
            assertEquals(50_000L, editedTopic.revisitAt)
            assertEquals(42L, editedTopic.sourceMindDumpId)
            assertEquals("active", editedTopic.status)

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
            migrated.learningSessionDao().finish(sessionId, 16_000L)
            val finishedSession = migrated.learningSessionDao().observeAll().first().single()
            assertEquals(16_000L, finishedSession.endedAt)
            assertEquals(6L, finishedSession.durationSeconds)
            assertEquals(null, migrated.learningSessionDao().observeActive().first())


            migrated.learningSessionFeedbackDao().upsert(
                LearningSessionFeedbackEntity(
                    sessionId = sessionId,
                    comprehension = 4,
                    perceivedDifficulty = 2,
                    focus = 5,
                    energyBefore = 2,
                    energyAfter = 4,
                    note = "بازخورد پس از جلسه"
                )
            )
            val feedback = migrated.learningSessionFeedbackDao().get(sessionId)
            assertEquals(4, feedback?.comprehension)
            assertEquals(2, feedback?.perceivedDifficulty)
            assertEquals(5, feedback?.focus)
            assertEquals(2, feedback?.energyBefore)
            assertEquals(4, feedback?.energyAfter)
            assertEquals("بازخورد پس از جلسه", feedback?.note)
            migrated.learningSessionFeedbackDao().delete(sessionId)


            migrated.learningStageProgressDao().upsert(
                LearningStageProgressEntity(
                    chapterId = "network-basics",
                    stageId = "why",
                    stageIndex = 0,
                    completed = true,
                    completedAt = 18_000L,
                    updatedAt = 18_000L
                )
            )
            val stageProgress = migrated.learningStageProgressDao().observeByChapter("network-basics").first()
            assertEquals(1, stageProgress.size)
            assertEquals("why", stageProgress.single().stageId)
            assertEquals(true, stageProgress.single().completed)
            migrated.learningStageProgressDao().deleteFromIndex("network-basics", 0)
            assertEquals(0, migrated.learningStageProgressDao().observeByChapter("network-basics").first().size)


            assertEquals(null, migrated.learningSessionFeedbackDao().get(sessionId))



        } finally {
            migrated.close()
        }
    }

    @Test
    fun learningSessionPauseResumeExcludesBackgroundTimeAndCanFinishWhilePaused() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val activeId = db.learningSessionDao().insert(
                LearningSessionEntity(topicTitle = "آزمون مکث", startedAt = 10_000L)
            )
            db.learningSessionDao().pause(activeId, 16_000L)
            db.learningSessionDao().pause(activeId, 20_000L)
            val paused = db.learningSessionDao().getActive()
            assertEquals(6L, paused?.durationSeconds)
            assertEquals(16_000L, paused?.pausedAt)
            assertEquals(null, paused?.resumedAt)
            assertEquals(null, db.learningSessionDao().startIfNone(
                LearningSessionEntity(topicTitle = "جلسه دوم", startedAt = 21_000L)
            ))

            db.learningSessionDao().resume(activeId, 30_000L)
            val resumed = db.learningSessionDao().getActive()
            assertEquals(null, resumed?.pausedAt)
            assertEquals(30_000L, resumed?.resumedAt)

            db.learningSessionDao().finish(activeId, 36_000L)
            val finished = db.learningSessionDao().getAll().single()
            assertEquals(12L, finished.durationSeconds)
            assertEquals(36_000L, finished.endedAt)
            assertEquals(null, finished.pausedAt)
            assertEquals(null, finished.resumedAt)
            assertEquals(null, db.learningSessionDao().getActive())
        } finally {
            db.close()
        }
    }

    @Test
    fun learningSessionFinishedWhilePausedDoesNotCountPausedInterval() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val id = db.learningSessionDao().insert(
                LearningSessionEntity(topicTitle = "جلسه مکث‌شده", startedAt = 1_000L)
            )
            db.learningSessionDao().pause(id, 7_000L)
            db.learningSessionDao().finish(id, 99_000L)
            val finished = db.learningSessionDao().getAll().single()
            assertEquals(6L, finished.durationSeconds)
            assertEquals(99_000L, finished.endedAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun personalLearningTopicStartsAtMostOneActiveSessionAndKeepsSourceTrace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sourceId = db.mindDumpDao().insert(
                MindDumpEntity(
                    content = "مرور DNS داخلی",
                    category = "learning",
                    status = "later"
                )
            )
            val topicId = db.laterTopicDao().insert(
                LaterTopicEntity(
                    title = "مرور DNS داخلی",
                    details = "بررسی رکوردهای A و PTR",
                    sourceMindDumpId = sourceId
                )
            )
            val topic = db.laterTopicDao().getAll().single()
            assertEquals(topicId, topic.id)
            assertEquals(sourceId, topic.sourceMindDumpId)

            val firstSessionId = db.learningSessionDao().startIfNone(
                LearningSessionEntity(
                    topicTitle = topic.title,
                    source = "mind_dump:$sourceId",
                    startedAt = 30_000L
                )
            )
            val duplicateSessionId = db.learningSessionDao().startIfNone(
                LearningSessionEntity(
                    topicTitle = topic.title,
                    source = "mind_dump:$sourceId",
                    startedAt = 31_000L
                )
            )

            assertEquals(true, firstSessionId != null)
            assertEquals(null, duplicateSessionId)
            val active = db.learningSessionDao().getActive()
            assertEquals(firstSessionId, active?.id)
            assertEquals("مرور DNS داخلی", active?.topicTitle)
            assertEquals("mind_dump:$sourceId", active?.source)
            assertEquals(1, db.learningSessionDao().getAll().size)
        } finally {
            db.close()
        }
    }
    @Test
    fun inboxClarificationUpdatesTextAndCategoryTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val id = db.mindDumpDao().insert(
                MindDumpEntity(content = "ایده اولیه", category = "thought")
            )
            db.mindDumpDao().updateContentAndCategory(
                id = id,
                content = "بررسی و پیاده‌سازی DNS داخلی",
                category = "learning",
                updatedAt = 42_000L
            )
            val updated = db.mindDumpDao().getById(id)
            assertEquals("بررسی و پیاده‌سازی DNS داخلی", updated?.content)
            assertEquals("learning", updated?.category)
            assertEquals(42_000L, updated?.updatedAt)
            assertEquals("inbox", updated?.status)
        } finally {
            db.close()
        }
    }


}
