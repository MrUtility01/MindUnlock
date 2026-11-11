package com.mindunlock.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

@Database(entities = [ProgressEntity::class, NoteEntity::class, BookmarkEntity::class, StudyTaskEntity::class], version = 1, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun noteDao(): NoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun studyTaskDao(): StudyTaskDao
    companion object {
        @Volatile private var instance: UserDatabase? = null
        fun getInstance(context: Context): UserDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, "mindunlock-user.db").build().also { instance = it }
        }
    }
}
