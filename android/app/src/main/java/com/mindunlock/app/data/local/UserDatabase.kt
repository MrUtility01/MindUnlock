package com.mindunlock.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningProfileDao {
    @Query("SELECT * FROM learning_profile WHERE id = 1 LIMIT 1") suspend fun get(): LearningProfileEntity?
    @Query("SELECT * FROM learning_profile WHERE id = 1 LIMIT 1") fun observe(): Flow<LearningProfileEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(profile: LearningProfileEntity)
    @Query("DELETE FROM learning_profile") suspend fun deleteAll()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress") suspend fun getAll(): List<ProgressEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<ProgressEntity>)
    @Query("DELETE FROM progress") suspend fun deleteAll()
    @Query("SELECT * FROM progress WHERE itemUid = :uid LIMIT 1") suspend fun get(uid: String): ProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(progress: ProgressEntity)
}

@Dao
interface LearningStageProgressDao {
    @Query("SELECT * FROM learning_stage_progress WHERE chapterId = :chapterId ORDER BY stageIndex ASC")
    fun observeByChapter(chapterId: String): Flow<List<LearningStageProgressEntity>>
    @Query("SELECT * FROM learning_stage_progress")
    suspend fun getAll(): List<LearningStageProgressEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LearningStageProgressEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LearningStageProgressEntity>)
    @Query("DELETE FROM learning_stage_progress WHERE chapterId = :chapterId AND stageId = :stageId")
    suspend fun delete(chapterId: String, stageId: String)
    @Query("DELETE FROM learning_stage_progress WHERE chapterId = :chapterId AND stageIndex >= :stageIndex")
    suspend fun deleteFromIndex(chapterId: String, stageIndex: Int)
    @Query("DELETE FROM learning_stage_progress")
    suspend fun deleteAll()
}

@Dao
interface LearningSessionFeedbackDao {
    @Query("SELECT * FROM learning_session_feedback ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LearningSessionFeedbackEntity>>
    @Query("SELECT * FROM learning_session_feedback WHERE sessionId = :sessionId LIMIT 1")
    suspend fun get(sessionId: Long): LearningSessionFeedbackEntity?
    @Query("SELECT * FROM learning_session_feedback")
    suspend fun getAll(): List<LearningSessionFeedbackEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedback: LearningSessionFeedbackEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LearningSessionFeedbackEntity>)
    @Query("DELETE FROM learning_session_feedback WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: Long)
    @Query("DELETE FROM learning_session_feedback")
    suspend fun deleteAll()
}

@Dao
interface LearningSessionDao {
    @Query("SELECT * FROM learning_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<LearningSessionEntity>>
    @Query("SELECT * FROM learning_sessions") suspend fun getAll(): List<LearningSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<LearningSessionEntity>)
    @Query("DELETE FROM learning_sessions") suspend fun deleteAll()

    @Query("SELECT * FROM learning_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<LearningSessionEntity?>

    @Query("SELECT * FROM learning_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): LearningSessionEntity?

    @Insert
    suspend fun insert(session: LearningSessionEntity): Long

    @Transaction
    suspend fun startIfNone(session: LearningSessionEntity): Long? {
        if (getActive() != null) return null
        return insert(session)
    }

    @Query("UPDATE learning_sessions SET endedAt = :endedAt, durationSeconds = :durationSeconds WHERE id = :id AND endedAt IS NULL")
    suspend fun finish(id: Long, endedAt: Long, durationSeconds: Long)
}

@Dao
interface LabStepProgressDao {
    @Query("SELECT * FROM lab_step_progress WHERE itemUid = :uid ORDER BY stepIndex ASC")
    fun observeByItem(uid: String): Flow<List<LabStepProgressEntity>>
    @Query("SELECT * FROM lab_step_progress") suspend fun getAll(): List<LabStepProgressEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<LabStepProgressEntity>)
    @Query("DELETE FROM lab_step_progress") suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(step: LabStepProgressEntity)

    @Query("DELETE FROM lab_step_progress WHERE itemUid = :uid AND stepIndex = :stepIndex")
    suspend fun delete(uid: String, stepIndex: Int)
}

@Dao
interface LabQuestionResponseDao {
    @Query("SELECT * FROM lab_question_responses WHERE itemUid = :uid ORDER BY questionIndex ASC") fun observeByItem(uid: String): Flow<List<LabQuestionResponseEntity>>
    @Query("SELECT * FROM lab_question_responses") suspend fun getAll(): List<LabQuestionResponseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(response: LabQuestionResponseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<LabQuestionResponseEntity>)
    @Query("DELETE FROM lab_question_responses WHERE itemUid = :uid AND questionIndex = :questionIndex") suspend fun delete(uid: String, questionIndex: Int)
    @Query("DELETE FROM lab_question_responses") suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun observeAll(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes") suspend fun getAll(): List<NoteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<NoteEntity>)
    @Query("DELETE FROM notes") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(note: NoteEntity)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun observeAll(): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks") suspend fun getAll(): List<BookmarkEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<BookmarkEntity>)
    @Query("DELETE FROM bookmarks") suspend fun deleteAll()
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE itemUid = :uid)") suspend fun exists(uid: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(bookmark: BookmarkEntity)
    @Query("DELETE FROM bookmarks WHERE itemUid = :uid") suspend fun delete(uid: String)
}

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY completed ASC, COALESCE(dueAt, 9223372036854775807) ASC, createdAt DESC") fun observeAll(): Flow<List<StudyTaskEntity>>
    @Query("SELECT * FROM study_tasks") suspend fun getAll(): List<StudyTaskEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<StudyTaskEntity>)
    @Query("DELETE FROM study_tasks") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(task: StudyTaskEntity): Long
    @Query("DELETE FROM study_tasks WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface MindDumpDao {
    @Query("SELECT * FROM mind_dump ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MindDumpEntity>>
    @Query("SELECT * FROM mind_dump") suspend fun getAll(): List<MindDumpEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<MindDumpEntity>)
    @Query("DELETE FROM mind_dump") suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MindDumpEntity): Long

    @Query("SELECT * FROM mind_dump WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MindDumpEntity?

    @Query("SELECT * FROM study_tasks WHERE id = :id LIMIT 1")
    suspend fun getLinkedTask(id: Long): StudyTaskEntity?

    @Insert
    suspend fun insertTask(task: StudyTaskEntity): Long

    /**
     * Idempotently creates the Planner task and persists its source link in one Room transaction.
     * A valid existing link is reused; a stale link is repaired by creating a replacement task.
     * Returns null only when the source Mind Dump entry no longer exists.
     */
    @Transaction
    suspend fun convertToTask(entryId: Long): Long? {
        val entry = getById(entryId) ?: return null
        val linkedTask = entry.linkedTaskId?.let { getLinkedTask(it) }
        val taskId = linkedTask?.id ?: insertTask(MindDumpTaskMapper.toStudyTask(entry))
        updateStatus(entry.id, "task", System.currentTimeMillis(), taskId)
        return taskId
    }

    @Query("UPDATE mind_dump SET status = :status, updatedAt = :updatedAt, linkedTaskId = :linkedTaskId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long, linkedTaskId: Long?)

    @Query("UPDATE mind_dump SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, updatedAt: Long)

    @Query("DELETE FROM mind_dump WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>
    @Query("SELECT * FROM journal_entries") suspend fun getAll(): List<JournalEntryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<JournalEntryEntity>)
    @Query("DELETE FROM journal_entries") suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @androidx.room.Update
    suspend fun update(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: Long)
}


@Dao
interface LaterTopicDao {
    @Query("SELECT * FROM later_topics ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'parked' THEN 1 ELSE 2 END, priority ASC, COALESCE(revisitAt, 9223372036854775807) ASC, updatedAt DESC")
    fun observeAll(): Flow<List<LaterTopicEntity>>
    @Query("SELECT * FROM later_topics") suspend fun getAll(): List<LaterTopicEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<LaterTopicEntity>)
    @Query("DELETE FROM later_topics") suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: LaterTopicEntity): Long

    @Query("UPDATE later_topics SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    @Update
    suspend fun update(topic: LaterTopicEntity)

    @Query("DELETE FROM later_topics WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ProgressEntity::class, NoteEntity::class, BookmarkEntity::class, StudyTaskEntity::class, MindDumpEntity::class, JournalEntryEntity::class, LaterTopicEntity::class, LabStepProgressEntity::class, LearningSessionEntity::class, LabQuestionResponseEntity::class, LearningProfileEntity::class, LearningSessionFeedbackEntity::class, LearningStageProgressEntity::class], version = 11, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun noteDao(): NoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun mindDumpDao(): MindDumpDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun laterTopicDao(): LaterTopicDao
    abstract fun labStepProgressDao(): LabStepProgressDao
    abstract fun labQuestionResponseDao(): LabQuestionResponseDao
    abstract fun learningSessionDao(): LearningSessionDao
    abstract fun learningProfileDao(): LearningProfileDao
    abstract fun learningSessionFeedbackDao(): LearningSessionFeedbackDao
    abstract fun learningStageProgressDao(): LearningStageProgressDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS mind_dump (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, category TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, linkedTaskId INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mind_dump_createdAt ON mind_dump(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mind_dump_status ON mind_dump(status)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS journal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, mood TEXT, tags TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_createdAt ON journal_entries(createdAt)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS later_topics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, priority INTEGER NOT NULL, revisitAt INTEGER, status TEXT NOT NULL, sourceMindDumpId INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_later_topics_status ON later_topics(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_later_topics_revisitAt ON later_topics(revisitAt)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_step_progress (itemUid TEXT NOT NULL, stepIndex INTEGER NOT NULL, completed INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(itemUid, stepIndex))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_step_progress_itemUid ON lab_step_progress(itemUid)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS learning_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chapterId TEXT, topicTitle TEXT NOT NULL, source TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, durationSeconds INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_sessions_startedAt ON learning_sessions(startedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_sessions_endedAt ON learning_sessions(endedAt)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress ADD COLUMN lastOpenedAt INTEGER")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_question_responses (itemUid TEXT NOT NULL, questionIndex INTEGER NOT NULL, answer TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(itemUid, questionIndex))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_question_responses_itemUid ON lab_question_responses(itemUid)")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS learning_profile (id INTEGER NOT NULL PRIMARY KEY, answersCsv TEXT NOT NULL, sessionMinutes INTEGER NOT NULL, maxTopics INTEGER NOT NULL, guidedMode TEXT NOT NULL, completed INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS learning_session_feedback (sessionId INTEGER NOT NULL PRIMARY KEY, comprehension INTEGER NOT NULL, perceivedDifficulty INTEGER NOT NULL, focus INTEGER NOT NULL, energyBefore INTEGER, energyAfter INTEGER, note TEXT, updatedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS learning_stage_progress (chapterId TEXT NOT NULL, stageId TEXT NOT NULL, stageIndex INTEGER NOT NULL, completed INTEGER NOT NULL, completedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(chapterId, stageId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_stage_progress_chapterId ON learning_stage_progress(chapterId)")
            }
        }
        @Volatile private var instance: UserDatabase? = null
        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "mindunlock-user.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build().also { instance = it }
        }
    }
}
