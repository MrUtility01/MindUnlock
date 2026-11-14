package com.mindunlock.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

object LegacyDatabaseMigrator {
    @Synchronized fun migrate(context: Context, content: AppDatabase, user: UserDatabase, onProgress: (String) -> Unit = {}) {
        val startedAt = System.nanoTime()
        onProgress("بررسی انتقال اطلاعات قدیمی...")
        android.util.Log.i("MindUnlockStartup", "Legacy migration started")
        val prefs = context.getSharedPreferences("mindunlock_db_migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean("split_v1_complete", false)) { onProgress("انتقال اطلاعات قبلی قبلاً انجام شده است."); android.util.Log.i("MindUnlockStartup", "Legacy migration skipped: complete marker present"); return }
        val legacyFile = context.getDatabasePath("mindunlock.db")
        if (!legacyFile.isFile) {
            prefs.edit().putBoolean("split_v1_complete", true).apply()
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            onProgress("انتقال اطلاعات کامل شد؛ زمان: " + elapsedMs + " میلی‌ثانیه")
            android.util.Log.i("MindUnlockStartup", "Legacy migration completed in " + elapsedMs + "ms")
            return
        }
        val source = SQLiteDatabase.openDatabase(legacyFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val contentTarget = content.openHelper.writableDatabase
            val userTarget = user.openHelper.writableDatabase
            contentTarget.beginTransaction()
            try {
                // Begin the second transaction inside the first one's try/finally.
                // If the user database cannot start a transaction (for example,
                // storage is full), the content transaction must still be closed.
                userTarget.beginTransaction()
                try {
                    onProgress("انتقال فصل‌ها...")
                    copyTable(source, contentTarget, "chapters")
                    onProgress("انتقال محتوای آموزشی...")
                    copyTable(source, contentTarget, "content_items")
                    onProgress("انتقال تنظیمات سرور...")
                    copyTable(source, contentTarget, "server_config")
                    onProgress("انتقال حافظه رسانه...")
                    copyTable(source, contentTarget, "media_cache")
                    onProgress("انتقال پیشرفت مطالعه...")
                    copyTable(source, userTarget, "progress")
                    onProgress("انتقال یادداشت‌ها...")
                    copyTable(source, userTarget, "notes")
                    onProgress("انتقال نشانک‌ها...")
                    copyTable(source, userTarget, "bookmarks")
                    onProgress("انتقال برنامه‌های مطالعه...")
                    copyTable(source, userTarget, "study_tasks")
                    contentTarget.setTransactionSuccessful()
                    userTarget.setTransactionSuccessful()
                } finally {
                    userTarget.endTransaction()
                }
            } finally {
                contentTarget.endTransaction()
            }
            prefs.edit().putBoolean("split_v1_complete", true).apply()
        } finally { source.close() }
    }

    private fun copyTable(source: SQLiteDatabase, target: SupportSQLiteDatabase, table: String) {
        val exists = source.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
        if (!exists) return
        source.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
            val names = cursor.columnNames
            while (cursor.moveToNext()) {
                val values = ContentValues()
                names.forEachIndexed { index, name ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                        Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(index))
                        Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(index))
                        else -> values.put(name, cursor.getString(index))
                    }
                }
                target.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        }
    }
}
