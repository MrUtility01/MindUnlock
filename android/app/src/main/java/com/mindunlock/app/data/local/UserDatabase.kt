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
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

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
interface LearningSessionDao {
    @Query("SELECT * FROM learning_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<LearningSessionEntity>>
    @Query("SELECT * FROM learning_sessions") suspend fun getAll(): List<LearningSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<LearningSessionEntity>)
    @Query("DELETE FROM learning_sessions") suspend fun deleteAll()

    @Query("SELECT * FROM learning_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<LearningSessionEntity?>

    @Insert
    suspend fun insert(session: LearningSessionEntity): Long

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

    @Query("UPDATE mind_dump SET status = :status, updatedAt = :updatedAt, linkedTaskId = :linkedTaskId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long, linkedTaskId: Long?)

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

    @Query("DELETE FROM later_topics WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ProgressEntity::class, NoteEntity::class, BookmarkEntity::class, StudyTaskEntity::class, MindDumpEntity::class, JournalEntryEntity::class, LaterTopicEntity::class, LabStepProgressEntity::class, LearningSessionEntity::class], version = 7, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun noteDao(): NoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun mindDumpDao(): MindDumpDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun laterTopicDao(): LaterTopicDao
    abstract fun labStepProgressDao(): LabStepProgressDao
    abstract fun learningSessionDao(): LearningSessionDao
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
        @Volatile private var instance: UserDatabase? = null
        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "mindunlock-user.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { instance = it }
        }
    }
}
