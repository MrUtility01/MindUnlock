package com.mindunlock.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY orderIndex ASC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY orderIndex ASC, title COLLATE NOCASE ASC")
    suspend fun getAll(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: ChapterEntity)
}

data class ContentItemListEntry(
    val uid: String,
    val chapterId: String,
    val type: String?,
    val title: String?,
    val summary: String?,
    val orderIndex: Int,
    val sourceStatus: String
)

@Dao
interface ContentItemDao {
    @Query("SELECT uid, chapterId, type, title, orderIndex, optionsJson, answer, explanation, sourceStatus, metadataJson FROM content_items WHERE uid = :uid LIMIT 1")
    fun observeMetadata(uid: String): Flow<ContentItemEntity?>

    @Query("SELECT content FROM content_items WHERE uid = :uid LIMIT 1")
    fun observeContent(uid: String): Flow<String?>
    
    @Query("SELECT ci.* FROM content_items ci LEFT JOIN chapters c ON c.id = ci.chapterId ORDER BY COALESCE(c.orderIndex, 2147483647), ci.orderIndex, ci.title COLLATE NOCASE")
    fun observeAll(): Flow<List<ContentItemEntity>>

    @Query("SELECT COUNT(*) FROM content_items")
    fun observeItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM content_items WHERE lower(type) = 'flashcard'")
    fun observeFlashcardCount(): Flow<Int>

    @Query("SELECT * FROM content_items WHERE uid IN (:uids) ORDER BY chapterId, orderIndex, title")
    fun observeByUids(uids: List<String>): Flow<List<ContentItemEntity>>

    @Query("SELECT COUNT(*) FROM content_items WHERE chapterId = :chapterId AND (:type = '' OR lower(type) = :type) AND (:query = '' OR COALESCE(title, '') LIKE '%' || :query || '%' OR COALESCE(summary, '') LIKE '%' || :query || '%' OR COALESCE(content, '') LIKE '%' || :query || '%')")
    suspend fun getChapterFilteredCount(chapterId: String, type: String, query: String): Int

    @Query("SELECT uid, chapterId, type, title, summary, orderIndex, sourceStatus FROM content_items WHERE chapterId = :chapterId AND (:type = '' OR lower(type) = :type) AND (:query = '' OR COALESCE(title, '') LIKE '%' || :query || '%' OR COALESCE(summary, '') LIKE '%' || :query || '%' OR COALESCE(content, '') LIKE '%' || :query || '%') ORDER BY orderIndex, title LIMIT :limit OFFSET :offset")
    suspend fun getChapterPage(chapterId: String, type: String, query: String, limit: Int, offset: Int): List<ContentItemListEntry>

    @Query("SELECT * FROM content_items WHERE chapterId = :chapterId ORDER BY orderIndex, title")
    fun observeForChapter(chapterId: String): Flow<List<ContentItemEntity>>

    @Query("SELECT uid, chapterId, type, title, orderIndex, optionsJson, answer, explanation, sourceStatus, metadataJson FROM content_items WHERE chapterId = :chapterId ORDER BY orderIndex, title LIMIT :limit OFFSET :offset")
    fun observeForChapterPage(chapterId: String, limit: Int, offset: Int): Flow<List<ContentItemEntity>>

    @Query("SELECT * FROM content_items WHERE chapterId = :chapterId ORDER BY orderIndex, title")
    suspend fun getForChapter(chapterId: String): List<ContentItemEntity>

    @Query("SELECT * FROM content_items WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): Flow<ContentItemEntity?>

    @Query("SELECT * FROM content_items WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): ContentItemEntity?

    @Query("SELECT ci.* FROM content_items ci LEFT JOIN chapters c ON c.id = ci.chapterId WHERE lower(ci.type) IN (:types) ORDER BY COALESCE(c.orderIndex, 2147483647), ci.orderIndex, ci.title COLLATE NOCASE")
    fun observeByTypes(types: List<String>): Flow<List<ContentItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContentItemEntity>)

    @Query("DELETE FROM content_items WHERE chapterId = :chapterId")
    suspend fun deleteForChapter(chapterId: String)
}

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    suspend fun get(): ServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ServerConfigEntity)
}

@Dao
interface MediaCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(media: MediaCacheEntity)

    @Query("SELECT * FROM media_cache")
    fun observeAll(): Flow<List<MediaCacheEntity>>
}

@Database(
    entities = [ChapterEntity::class, ContentItemEntity::class, ServerConfigEntity::class, MediaCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chapterDao(): ChapterDao
    abstract fun contentItemDao(): ContentItemDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun mediaCacheDao(): MediaCacheDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE content_items ADD COLUMN summary TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_items_chapterId ON content_items(chapterId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_items_type ON content_items(type)")
                db.execSQL("ALTER TABLE content_items ADD COLUMN sourceStatus TEXT NOT NULL DEFAULT 'verified'")
                db.execSQL("ALTER TABLE content_items ADD COLUMN rawJson TEXT")
                db.execSQL("ALTER TABLE progress ADD COLUMN wrong INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE progress ADD COLUMN quality INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE progress ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE progress ADD COLUMN dueAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemUid TEXT, chapterId TEXT, title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_itemUid ON notes(itemUid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_chapterId ON notes(chapterId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (itemUid TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, taskType TEXT NOT NULL, dueAt INTEGER, remindAt INTEGER, completed INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_tasks_remindAt ON study_tasks(remindAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS media_cache (relativePath TEXT NOT NULL PRIMARY KEY, localPath TEXT NOT NULL, sha256 TEXT NOT NULL, size INTEGER NOT NULL, downloadedAt INTEGER NOT NULL)")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mindunlock-content.db"
            ).build().also { instance = it }
        }
    }
}
