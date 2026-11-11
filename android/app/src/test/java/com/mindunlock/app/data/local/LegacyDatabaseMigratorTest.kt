package com.mindunlock.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyDatabaseMigratorTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var content: AppDatabase
    private lateinit var user: UserDatabase

    @Before
    fun setUp() {
        context.deleteDatabase("mindunlock.db")
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .edit().clear().commit()
        content = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        user = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        content.close()
        user.close()
        context.deleteDatabase("mindunlock.db")
    }

    @Test
    fun migratesAllEightTablesPreservesValuesAndSkipsRepeatRun() = runBlocking {
        val legacyFile = context.getDatabasePath("mindunlock.db")
        legacyFile.parentFile?.mkdirs()
        val source = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
        try {
            source.execSQL("""CREATE TABLE chapters (
                id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, orderIndex INTEGER NOT NULL,
                groupName TEXT, description TEXT, itemCount INTEGER NOT NULL, contentHash TEXT,
                rawJson TEXT, syncedAt INTEGER NOT NULL
            )""")
            source.execSQL("""INSERT INTO chapters VALUES
                ('chapter-test', 'فصل آزمایشی', 7, 'مبانی', 'شرح فصل', 1, 'chapter-hash', '{}', 101)""")

            source.execSQL("""CREATE TABLE content_items (
                uid TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, type TEXT, title TEXT,
                summary TEXT, content TEXT, orderIndex INTEGER NOT NULL, optionsJson TEXT,
                answer TEXT, explanation TEXT, metadataJson TEXT,
                sourceStatus TEXT NOT NULL DEFAULT 'verified', rawJson TEXT
            )""")
            source.execSQL("""INSERT INTO content_items VALUES
                ('item-test', 'chapter-test', 'lesson', 'عنوان محتوا', 'خلاصه', 'متن کامل',
                2, '[]', 'پاسخ', 'توضیح', '{}', 'verified', '{"origin":"legacy"}')""")

            source.execSQL("""CREATE TABLE server_config (
                id INTEGER NOT NULL PRIMARY KEY, host TEXT NOT NULL, port INTEGER NOT NULL,
                lastConnectedAt INTEGER
            )""")
            source.execSQL("INSERT INTO server_config VALUES (1, '192.168.1.20', 8080, 20261111)")

            source.execSQL("""CREATE TABLE media_cache (
                relativePath TEXT NOT NULL PRIMARY KEY, localPath TEXT NOT NULL,
                sha256 TEXT NOT NULL, size INTEGER NOT NULL, downloadedAt INTEGER NOT NULL
            )""")
            source.execSQL("""INSERT INTO media_cache VALUES
                ('images/one.webp', '/cache/one.webp', 'media-sha256', 4096, 20261110)""")

            source.execSQL("""CREATE TABLE progress (
                itemUid TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL,
                completed INTEGER NOT NULL, completedAt INTEGER, attempts INTEGER NOT NULL,
                correct INTEGER NOT NULL, wrong INTEGER NOT NULL, lastResult TEXT,
                quality INTEGER NOT NULL, intervalDays INTEGER NOT NULL, dueAt INTEGER,
                updatedAt INTEGER NOT NULL
            )""")
            source.execSQL("""INSERT INTO progress VALUES
                ('item-test', 'chapter-test', 1, 20261101, 5, 4, 1, 'correct',
                4, 7, 20261118, 20261111)""")

            source.execSQL("""CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemUid TEXT, chapterId TEXT,
                title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL
            )""")
            source.execSQL("""INSERT INTO notes (id, itemUid, chapterId, title, body, updatedAt)
                VALUES (21, 'item-test', 'chapter-test', 'یادداشت آزمایشی', 'بدنه یادداشت', 20261109)""")

            source.execSQL("""CREATE TABLE bookmarks (
                itemUid TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, createdAt INTEGER NOT NULL
            )""")
            source.execSQL("INSERT INTO bookmarks VALUES ('item-test', 'chapter-test', 20261108)")

            source.execSQL("""CREATE TABLE study_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                details TEXT NOT NULL, taskType TEXT NOT NULL, dueAt INTEGER, remindAt INTEGER,
                completed INTEGER NOT NULL, createdAt INTEGER NOT NULL
            )""")
            source.execSQL("""INSERT INTO study_tasks
                (id, title, details, taskType, dueAt, remindAt, completed, createdAt)
                VALUES (31, 'مرور فصل', 'جزئیات کار', 'review', 20261120, 20261119, 0, 20261107)""")
        } finally {
            source.close()
        }

        LegacyDatabaseMigrator.migrate(context, content, user)

        val contentDb = content.openHelper.readableDatabase
        val userDb = user.openHelper.readableDatabase
        assertRowCount(contentDb, "chapters", 1)
        assertRowCount(contentDb, "content_items", 1)
        assertRowCount(contentDb, "server_config", 1)
        assertRowCount(contentDb, "media_cache", 1)
        assertRowCount(userDb, "progress", 1)
        assertRowCount(userDb, "notes", 1)
        assertRowCount(userDb, "bookmarks", 1)
        assertRowCount(userDb, "study_tasks", 1)

        // Assert every legacy column in all eight tables, not only representative fields.
        assertQuery(contentDb, "SELECT * FROM chapters WHERE id='chapter-test'") {
            assertEquals("chapter-test", getString(0))
            assertEquals("فصل آزمایشی", getString(1))
            assertEquals(7, getInt(2))
            assertEquals("مبانی", getString(3))
            assertEquals("شرح فصل", getString(4))
            assertEquals(1, getInt(5))
            assertEquals("chapter-hash", getString(6))
            assertEquals("{}", getString(7))
            assertEquals(101L, getLong(8))
        }
        assertQuery(contentDb, "SELECT * FROM content_items WHERE uid='item-test'") {
            assertEquals("item-test", getString(0))
            assertEquals("chapter-test", getString(1))
            assertEquals("lesson", getString(2))
            assertEquals("عنوان محتوا", getString(3))
            assertEquals("خلاصه", getString(4))
            assertEquals("متن کامل", getString(5))
            assertEquals(2, getInt(6))
            assertEquals("[]", getString(7))
            assertEquals("پاسخ", getString(8))
            assertEquals("توضیح", getString(9))
            assertEquals("{}", getString(10))
            assertEquals("verified", getString(11))
            assertEquals("""{"origin":"legacy"}""", getString(12))
        }
        assertQuery(contentDb, "SELECT * FROM server_config WHERE id=1") {
            assertEquals(1, getInt(0))
            assertEquals("192.168.1.20", getString(1))
            assertEquals(8080, getInt(2))
            assertEquals(20261111L, getLong(3))
        }
        assertQuery(contentDb, "SELECT * FROM media_cache WHERE relativePath='images/one.webp'") {
            assertEquals("images/one.webp", getString(0))
            assertEquals("/cache/one.webp", getString(1))
            assertEquals("media-sha256", getString(2))
            assertEquals(4096L, getLong(3))
            assertEquals(20261110L, getLong(4))
        }
        assertQuery(userDb, "SELECT * FROM progress WHERE itemUid='item-test'") {
            assertEquals("item-test", getString(0))
            assertEquals("chapter-test", getString(1))
            assertEquals(1, getInt(2))
            assertEquals(20261101L, getLong(3))
            assertEquals(5, getInt(4))
            assertEquals(4, getInt(5))
            assertEquals(1, getInt(6))
            assertEquals("correct", getString(7))
            assertEquals(4, getInt(8))
            assertEquals(7, getInt(9))
            assertEquals(20261118L, getLong(10))
            assertEquals(20261111L, getLong(11))
        }
        assertQuery(userDb, "SELECT * FROM notes WHERE id=21") {
            assertEquals(21L, getLong(0))
            assertEquals("item-test", getString(1))
            assertEquals("chapter-test", getString(2))
            assertEquals("یادداشت آزمایشی", getString(3))
            assertEquals("بدنه یادداشت", getString(4))
            assertEquals(20261109L, getLong(5))
        }
        assertQuery(userDb, "SELECT * FROM bookmarks WHERE itemUid='item-test'") {
            assertEquals("item-test", getString(0))
            assertEquals("chapter-test", getString(1))
            assertEquals(20261108L, getLong(2))
        }
        assertQuery(userDb, "SELECT * FROM study_tasks WHERE id=31") {
            assertEquals(31L, getLong(0))
            assertEquals("مرور فصل", getString(1))
            assertEquals("جزئیات کار", getString(2))
            assertEquals("review", getString(3))
            assertEquals(20261120L, getLong(4))
            assertEquals(20261119L, getLong(5))
            assertEquals(0, getInt(6))
            assertEquals(20261107L, getLong(7))
        }

        assertTrue(
            context.getSharedPreferences("mindunlock_db_migration", 0)
                .getBoolean("split_v1_complete", false)
        )

        // A second invocation must honor the completion marker and leave target rows unchanged.
        LegacyDatabaseMigrator.migrate(context, content, user)
        assertRowCount(contentDb, "chapters", 1)
        assertRowCount(contentDb, "content_items", 1)
        assertRowCount(userDb, "progress", 1)
        assertRowCount(userDb, "notes", 1)
    }

    @Test
fun marksMigrationCompleteWhenLegacyDatabaseDoesNotExist() {
    LegacyDatabaseMigrator.migrate(context, content, user)

    assertTrue(
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .getBoolean("split_v1_complete", false)
    )
    assertRowCount(content.openHelper.readableDatabase, "chapters", 0)
    assertRowCount(content.openHelper.readableDatabase, "content_items", 0)
    assertRowCount(content.openHelper.readableDatabase, "server_config", 0)
    assertRowCount(content.openHelper.readableDatabase, "media_cache", 0)
    assertRowCount(user.openHelper.readableDatabase, "progress", 0)
    assertRowCount(user.openHelper.readableDatabase, "notes", 0)
    assertRowCount(user.openHelper.readableDatabase, "bookmarks", 0)
    assertRowCount(user.openHelper.readableDatabase, "study_tasks", 0)
}

@Test
fun skipsUnknownOrMissingTablesInAnEmptyLegacyDatabase() {
    val legacyFile = context.getDatabasePath("mindunlock.db")
    legacyFile.parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(legacyFile, null).close()

    LegacyDatabaseMigrator.migrate(context, content, user)

    assertTrue(
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .getBoolean("split_v1_complete", false)
    )
    assertRowCount(content.openHelper.readableDatabase, "chapters", 0)
    assertRowCount(content.openHelper.readableDatabase, "content_items", 0)
    assertRowCount(content.openHelper.readableDatabase, "server_config", 0)
    assertRowCount(content.openHelper.readableDatabase, "media_cache", 0)
    assertRowCount(user.openHelper.readableDatabase, "progress", 0)
    assertRowCount(user.openHelper.readableDatabase, "notes", 0)
    assertRowCount(user.openHelper.readableDatabase, "bookmarks", 0)
    assertRowCount(user.openHelper.readableDatabase, "study_tasks", 0)
}

@Test
fun preservesNullableColumnsAndMigratesWhenOnlyOneLegacyTableExists() {
    val legacyFile = context.getDatabasePath("mindunlock.db")
    legacyFile.parentFile?.mkdirs()
    val source = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
    try {
        source.execSQL("""CREATE TABLE chapters (
            id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, orderIndex INTEGER NOT NULL,
            groupName TEXT, description TEXT, itemCount INTEGER NOT NULL, contentHash TEXT,
            rawJson TEXT, syncedAt INTEGER NOT NULL
        )""")
        source.execSQL("""INSERT INTO chapters
            (id, title, orderIndex, groupName, description, itemCount, contentHash, rawJson, syncedAt)
            VALUES ('nullable-chapter', 'فصل با مقادیر تهی', 3, NULL, NULL, 0, NULL, NULL, 77)""")
    } finally {
        source.close()
    }

    LegacyDatabaseMigrator.migrate(context, content, user)

    val contentDb = content.openHelper.readableDatabase
    assertRowCount(contentDb, "chapters", 1)
    assertQuery(contentDb, "SELECT title, orderIndex, groupName, description, contentHash, rawJson, syncedAt FROM chapters WHERE id='nullable-chapter'") {
        assertEquals("فصل با مقادیر تهی", getString(0))
        assertEquals(3, getInt(1))
        assertNull(getString(2))
        assertNull(getString(3))
        assertNull(getString(4))
        assertNull(getString(5))
        assertEquals(77L, getLong(6))
    }

    // All other legacy tables are absent; their target Room tables must remain empty.
    assertRowCount(contentDb, "content_items", 0)
    assertRowCount(contentDb, "server_config", 0)
    assertRowCount(contentDb, "media_cache", 0)
    val userDb = user.openHelper.readableDatabase
    assertRowCount(userDb, "progress", 0)
    assertRowCount(userDb, "notes", 0)
    assertRowCount(userDb, "bookmarks", 0)
    assertRowCount(userDb, "study_tasks", 0)
    assertTrue(
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .getBoolean("split_v1_complete", false)
    )
}

@Test
fun rollsBackBothTargetsAfterMidMigrationFailureAndAllowsRetry() {
    val legacyFile = context.getDatabasePath("mindunlock.db")
    legacyFile.parentFile?.mkdirs()
    val source = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
    try {
        source.execSQL("""CREATE TABLE chapters (
            id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, orderIndex INTEGER NOT NULL,
            groupName TEXT, description TEXT, itemCount INTEGER NOT NULL, contentHash TEXT,
            rawJson TEXT, syncedAt INTEGER NOT NULL
        )""")
        source.execSQL("""INSERT INTO chapters
            (id, title, orderIndex, itemCount, syncedAt)
            VALUES ('rollback-chapter', 'فصل برای آزمون بازگشت', 1, 0, 10)""")
        // Legacy schema permits NULL here; the destination Room schema does not.
        source.execSQL("""CREATE TABLE progress (
            itemUid TEXT, chapterId TEXT NOT NULL, completed INTEGER NOT NULL,
            completedAt INTEGER, attempts INTEGER NOT NULL, correct INTEGER NOT NULL,
            wrong INTEGER NOT NULL, lastResult TEXT, quality INTEGER NOT NULL,
            intervalDays INTEGER NOT NULL, dueAt INTEGER, updatedAt INTEGER NOT NULL
        )""")
        source.execSQL("""INSERT INTO progress VALUES
            (NULL, 'rollback-chapter', 0, NULL, 0, 0, 0, NULL, 0, 0, NULL, 10)""")
    } finally {
        source.close()
    }

    val failed = runCatching { LegacyDatabaseMigrator.migrate(context, content, user) }
    assertTrue("Migration should fail on the destination NOT NULL constraint", failed.isFailure)

    // The exception occurs after the content table copy; neither destination may retain partial rows.
    val contentDb = content.openHelper.readableDatabase
    val userDb = user.openHelper.readableDatabase
    assertRowCount(contentDb, "chapters", 0)
    assertRowCount(userDb, "progress", 0)
    assertFalse(
        "Completion marker must not be set after a failed migration",
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .getBoolean("split_v1_complete", false)
    )

    // Repair only the legacy test fixture and retry the same migration.
    val repair = SQLiteDatabase.openDatabase(legacyFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
    try {
        repair.execSQL("UPDATE progress SET itemUid='retry-item' WHERE itemUid IS NULL")
    } finally {
        repair.close()
    }

    LegacyDatabaseMigrator.migrate(context, content, user)

    assertRowCount(contentDb, "chapters", 1)
    assertRowCount(userDb, "progress", 1)
    assertQuery(userDb, "SELECT itemUid, chapterId FROM progress WHERE itemUid='retry-item'") {
        assertEquals("retry-item", getString(0))
        assertEquals("rollback-chapter", getString(1))
    }
    assertTrue(
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .getBoolean("split_v1_complete", false)
    )
}

@Test
fun legacyPrimaryKeyCollisionReplacesExistingDestinationRow() {
    // Seed the destination with an older row using the same primary key.
    val targetDb = content.openHelper.writableDatabase
    targetDb.execSQL("""INSERT INTO chapters
        (id, title, orderIndex, groupName, description, itemCount, contentHash, rawJson, syncedAt)
        VALUES ('collision-chapter', 'مقدار مقصد', 1, 'قدیمی', 'رکورد قبلی', 0, 'old-hash', NULL, 1)""")

    val legacyFile = context.getDatabasePath("mindunlock.db")
    legacyFile.parentFile?.mkdirs()
    val source = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
    try {
        source.execSQL("""CREATE TABLE chapters (
            id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, orderIndex INTEGER NOT NULL,
            groupName TEXT, description TEXT, itemCount INTEGER NOT NULL, contentHash TEXT,
            rawJson TEXT, syncedAt INTEGER NOT NULL
        )""")
        source.execSQL("""INSERT INTO chapters
            (id, title, orderIndex, groupName, description, itemCount, contentHash, rawJson, syncedAt)
            VALUES ('collision-chapter', 'مقدار legacy', 9, 'جدید', 'رکورد مهاجرتی', 2, 'new-hash', '{"source":"legacy"}', 99)""")
    } finally {
        source.close()
    }

    LegacyDatabaseMigrator.migrate(context, content, user)

    assertRowCount(content.openHelper.readableDatabase, "chapters", 1)
    assertQuery(content.openHelper.readableDatabase,
        "SELECT title, orderIndex, groupName, description, contentHash, rawJson, syncedAt FROM chapters WHERE id='collision-chapter'") {
        assertEquals("مقدار legacy", getString(0))
        assertEquals(9, getInt(1))
        assertEquals("جدید", getString(2))
        assertEquals("رکورد مهاجرتی", getString(3))
        assertEquals("new-hash", getString(4))
        assertEquals("""{"source":"legacy"}""", getString(5))
        assertEquals(99L, getLong(6))
    }
    assertTrue(
        context.getSharedPreferences("mindunlock_db_migration", 0)
            .getBoolean("split_v1_complete", false)
    )
}

private fun assertRowCount(db: SupportSQLiteDatabase, table: String, expected: Int) {
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue("COUNT query returned no row for $table", cursor.moveToFirst())
            assertEquals("Unexpected row count for $table", expected, cursor.getInt(0))
        }
    }

    private inline fun assertQuery(
        db: SupportSQLiteDatabase,
        sql: String,
        assertions: android.database.Cursor.() -> Unit
    ) {
        db.query(sql).use { cursor ->
            assertTrue("Query returned no row: $sql", cursor.moveToFirst())
            cursor.assertions()
        }
    }
}
