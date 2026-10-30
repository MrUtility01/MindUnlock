package com.mindunlock.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY orderIndex ASC, title ASC")
    fun observeChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY orderIndex ASC, title ASC")
    suspend fun getAllChapters(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapter(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters")
    suspend fun clearAll()
}

@Dao
interface ContentItemDao {
    @Query("SELECT * FROM content_items WHERE chapterId = :chapterId ORDER BY orderIndex ASC")
    suspend fun getItemsForChapter(chapterId: String): List<ContentItemEntity>

    @Query("SELECT * FROM content_items WHERE chapterId = :chapterId ORDER BY orderIndex ASC")
    fun observeItemsForChapter(chapterId: String): Flow<List<ContentItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContentItemEntity>)

    @Query("DELETE FROM content_items WHERE chapterId = :chapterId")
    suspend fun deleteForChapter(chapterId: String)

    @Query("DELETE FROM content_items")
    suspend fun clearAll()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE itemUid = :uid LIMIT 1")
    suspend fun get(uid: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE chapterId = :chapterId")
    suspend fun getForChapter(chapterId: String): List<ProgressEntity>
}

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    suspend fun get(): ServerConfigEntity?

    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ServerConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ServerConfigEntity)
}

@Database(
    entities = [
        ChapterEntity::class,
        ContentItemEntity::class,
        ProgressEntity::class,
        ServerConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chapterDao(): ChapterDao
    abstract fun contentItemDao(): ContentItemDao
    abstract fun progressDao(): ProgressDao
    abstract fun serverConfigDao(): ServerConfigDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindunlock.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
