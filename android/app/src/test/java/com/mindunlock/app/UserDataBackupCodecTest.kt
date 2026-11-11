package com.mindunlock.app

import com.mindunlock.app.data.local.JournalEntryEntity
import com.mindunlock.app.data.local.LabStepProgressEntity
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserDataBackupCodecTest {
    @Test
    fun encodesAndDecodesAllSupportedPersonalDataCollections() {
        val payload = UserDataBackupPayload(
            progress = listOf(ProgressEntity("item-1", "chapter-1", attempts = 4, correct = 3, wrong = 1, intervalDays = 3)),
            tasks = listOf(StudyTaskEntity(id = 5, title = "مرور DNS")),
            mindDump = listOf(MindDumpEntity(id = 8, content = "بررسی DHCP")),
            journalEntries = listOf(JournalEntryEntity(id = 9, body = "یادداشت خصوصی")),
            labStepProgress = listOf(LabStepProgressEntity("lab-1", 2, true)),
        )
        val json = UserDataBackupCodec.encode(payload, 1_800_000_000_000L, "0.6.5")
        val restored = UserDataBackupCodec.decode(json)
        assertEquals(1, restored.schemaVersion)
        assertEquals("0.6.5", restored.appVersion)
        assertEquals(5, restored.payload.totalRecords)
        assertEquals(3, restored.payload.progress.single().correct)
        assertEquals("مرور DNS", restored.payload.tasks.single().title)
        assertEquals("یادداشت خصوصی", restored.payload.journalEntries.single().body)
        assertEquals(true, restored.payload.labStepProgress.single().completed)
    }

    @Test
    fun rejectsTamperedPayload() {
        val json = UserDataBackupCodec.encode(
            UserDataBackupPayload(mindDump = listOf(MindDumpEntity(content = "Original"))),
            1_800_000_000_000L,
            "test"
        ).replace("Original", "Modified")
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(json)
        }
    }

    @Test
    fun rejectsUnsupportedSchemaAndMissingCollections() {
        val json = UserDataBackupCodec.encode(UserDataBackupPayload(), 1_800_000_000_000L, "test")
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(json.replace("\"schemaVersion\":1", "\"schemaVersion\":99"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode("""{"schemaVersion":1,"exportedAt":1800000000000,"appVersion":"test","payload":{},"sha256":"x"}""")
        }
    }

    @Test
    fun rejectsDuplicatePrimaryKeys() {
        val payload = UserDataBackupPayload(
            progress = listOf(
                ProgressEntity("same", "c"),
                ProgressEntity("same", "c")
            )
        )
        val json = UserDataBackupCodec.encode(payload, 1_800_000_000_000L, "test")
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(json)
        }
    }
}
