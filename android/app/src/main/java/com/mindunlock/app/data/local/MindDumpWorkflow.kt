package com.mindunlock.app.data.local

import androidx.room.withTransaction

object MindDumpWorkflow {
    private fun titleFrom(content: String, fallback: String): String =
        content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(120) ?: fallback

    private fun mergeTitleAndBody(title: String, body: String): String {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        return when {
            cleanTitle.isBlank() -> cleanBody
            cleanBody.isBlank() -> cleanTitle
            cleanBody.startsWith(cleanTitle) -> cleanBody
            else -> "$cleanTitle\n\n$cleanBody"
        }
    }

    suspend fun convertToNote(database: UserDatabase, entryId: Long): Long? =
        database.withTransaction {
            val source = database.mindDumpDao().getById(entryId) ?: return@withTransaction null
            val existing = database.noteDao().findBySourceMindDumpId(entryId)
            val noteId = existing?.id ?: database.noteDao().insertFromMindDump(
                NoteEntity(
                    title = titleFrom(source.content, "یادداشت از صندوق ورودی"),
                    body = source.content,
                    updatedAt = System.currentTimeMillis(),
                    sourceMindDumpId = source.id
                )
            )
            check(noteId > 0L) { "ساخت یادداشت پیوندشده انجام نشد." }
            database.mindDumpDao().updateStatus(source.id, "note", System.currentTimeMillis(), null)
            noteId
        }

    suspend fun convertToJournal(database: UserDatabase, entryId: Long): Long? =
        database.withTransaction {
            val source = database.mindDumpDao().getById(entryId) ?: return@withTransaction null
            val existing = database.journalEntryDao().findBySourceMindDumpId(entryId)
            val journalId = existing?.id ?: database.journalEntryDao().insertFromMindDump(
                JournalEntryEntity(
                    title = titleFrom(source.content, "یادداشت روزانه"),
                    body = source.content,
                    tags = "منبع: صندوق ورودی",
                    createdAt = source.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    sourceMindDumpId = source.id
                )
            )
            check(journalId > 0L) { "ساخت نوشته پیوندشده انجام نشد." }
            database.mindDumpDao().updateStatus(source.id, "journal", System.currentTimeMillis(), null)
            journalId
        }

    suspend fun restoreNoteToInbox(database: UserDatabase, noteId: Long): Long? =
        database.withTransaction {
            val note = database.noteDao().getById(noteId) ?: return@withTransaction null
            val content = mergeTitleAndBody(note.title, note.body)
            if (content.isBlank()) return@withTransaction null
            val now = System.currentTimeMillis()
            val sourceId = note.sourceMindDumpId
            val restoredId = if (sourceId != null && database.mindDumpDao().getById(sourceId) != null) {
                database.mindDumpDao().restoreToInbox(sourceId, content, now)
                sourceId
            } else {
                database.mindDumpDao().insert(MindDumpEntity(content = content, category = "thought", createdAt = now, updatedAt = now))
            }
            database.noteDao().upsert(note.copy(sourceMindDumpId = null, updatedAt = now))
            restoredId
        }

    suspend fun restoreJournalToInbox(database: UserDatabase, journalId: Long): Long? =
        database.withTransaction {
            val entry = database.journalEntryDao().getById(journalId) ?: return@withTransaction null
            val content = mergeTitleAndBody(entry.title, entry.body)
            if (content.isBlank()) return@withTransaction null
            val now = System.currentTimeMillis()
            val sourceId = entry.sourceMindDumpId
            val restoredId = if (sourceId != null && database.mindDumpDao().getById(sourceId) != null) {
                database.mindDumpDao().restoreToInbox(sourceId, content, now)
                sourceId
            } else {
                database.mindDumpDao().insert(MindDumpEntity(content = content, category = "thought", createdAt = now, updatedAt = now))
            }
            database.journalEntryDao().update(entry.copy(sourceMindDumpId = null, updatedAt = now))
            restoredId
        }

    suspend fun restoreTaskToInbox(database: UserDatabase, taskId: Long): Long? =
        database.withTransaction {
            val task = database.studyTaskDao().getById(taskId) ?: return@withTransaction null
            val content = mergeTitleAndBody(task.title, task.details)
            if (content.isBlank()) return@withTransaction null
            val now = System.currentTimeMillis()
            val sourceId = task.sourceMindDumpId ?: database.mindDumpDao().getByLinkedTaskId(task.id)?.id
            val restoredId = if (sourceId != null && database.mindDumpDao().getById(sourceId) != null) {
                database.mindDumpDao().restoreToInbox(sourceId, content, now)
                sourceId
            } else {
                database.mindDumpDao().insert(MindDumpEntity(content = content, category = "task", createdAt = now, updatedAt = now))
            }
            database.studyTaskDao().upsert(task.copy(sourceMindDumpId = null))
            restoredId
        }

    suspend fun deleteNoteAndRestoreInbox(database: UserDatabase, noteId: Long): Boolean =
        database.withTransaction {
            val note = database.noteDao().getById(noteId) ?: return@withTransaction false
            if (note.sourceMindDumpId != null) {
                val content = mergeTitleAndBody(note.title, note.body)
                if (content.isBlank()) return@withTransaction false
                val now = System.currentTimeMillis()
                val sourceId = note.sourceMindDumpId
                if (database.mindDumpDao().getById(sourceId) != null) {
                    database.mindDumpDao().restoreToInbox(sourceId, content, now)
                } else {
                    database.mindDumpDao().insert(MindDumpEntity(content = content, category = "thought", createdAt = now, updatedAt = now))
                }
            }
            database.noteDao().delete(noteId)
            true
        }

    suspend fun deleteJournalAndRestoreInbox(database: UserDatabase, journalId: Long): Boolean =
        database.withTransaction {
            val entry = database.journalEntryDao().getById(journalId) ?: return@withTransaction false
            if (entry.sourceMindDumpId != null) {
                val content = mergeTitleAndBody(entry.title, entry.body)
                if (content.isBlank()) return@withTransaction false
                val now = System.currentTimeMillis()
                val sourceId = entry.sourceMindDumpId
                if (database.mindDumpDao().getById(sourceId) != null) {
                    database.mindDumpDao().restoreToInbox(sourceId, content, now)
                } else {
                    database.mindDumpDao().insert(MindDumpEntity(content = content, category = "thought", createdAt = now, updatedAt = now))
                }
            }
            database.journalEntryDao().delete(journalId)
            true
        }

    suspend fun deleteTaskAndRestoreInbox(database: UserDatabase, taskId: Long): Boolean =
        database.withTransaction {
            val task = database.studyTaskDao().getById(taskId) ?: return@withTransaction false
            val sourceId = task.sourceMindDumpId ?: database.mindDumpDao().getByLinkedTaskId(task.id)?.id
            if (sourceId != null) {
                val content = mergeTitleAndBody(task.title, task.details)
                if (content.isBlank()) return@withTransaction false
                val now = System.currentTimeMillis()
                if (database.mindDumpDao().getById(sourceId) != null) {
                    database.mindDumpDao().restoreToInbox(sourceId, content, now)
                } else {
                    database.mindDumpDao().insert(MindDumpEntity(content = content, category = "task", createdAt = now, updatedAt = now))
                }
            }
            database.studyTaskDao().delete(taskId)
            true
        }
}
