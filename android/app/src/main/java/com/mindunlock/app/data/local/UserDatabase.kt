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
    @Query("SELECT * FROM progress WHERE itemUid = :uid LIMIT 1") suspend fun get(uid: String): ProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(progress: ProgressEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun observeAll(): Flow<List<NoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(note: NoteEntity)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") fun observeAll(): Flow<List<BookmarkEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE itemUid = :uid)") suspend fun exists(uid: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(bookmark: BookmarkEntity)
    @Query("DELETE FROM bookmarks WHERE itemUid = :uid") suspend fun delete(uid: String)
}

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY completed ASC, COALESCE(dueAt, 9223372036854775807) ASC, createdAt DESC") fun observeAll(): Flow<List<StudyTaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(task: StudyTaskEntity): Long
    @Query("DELETE FROM study_tasks WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface MindDumpDao {
    @Query("SELECT * FROM mind_dump ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MindDumpEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: Long)
}


@Dao
interface LaterTopicDao {
    @Query("SELECT * FROM later_topics ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'parked' THEN 1 ELSE 2 END, priority ASC, COALESCE(revisitAt, 9223372036854775807) ASC, updatedAt DESC")
    fun observeAll(): Flow<List<LaterTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: LaterTopicEntity): Long

    @Query("UPDATE later_topics SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM later_topics WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ProgressEntity::class, NoteEntity::class, BookmarkEntity::class, StudyTaskEntity::class, MindDumpEntity::class, JournalEntryEntity::class, LaterTopicEntity::class], version = 4, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun noteDao(): NoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun mindDumpDao(): MindDumpDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun laterTopicDao(): LaterTopicDao
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
        val MIGRATION_3_4 = object : Migration(3, 4) {\n            override fun migrate(db: SupportSQLiteDatabase) {\n                db.execSQL("CREATE TABLE IF NOT EXISTS later_topics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, priority INTEGER NOT NULL, revisitAt INTEGER, status TEXT NOT NULL, sourceMindDumpId INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")\n                db.execSQL("CREATE INDEX IF NOT EXISTS index_later_topics_status ON later_topics(status)")\n                db.execSQL("CREATE INDEX IF NOT EXISTS index_later_topics_revisitAt ON later_topics(revisitAt)")\n            }\n        }\n        @Volatile private var instance: UserDatabase? = null
        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "mindunlock-user.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
