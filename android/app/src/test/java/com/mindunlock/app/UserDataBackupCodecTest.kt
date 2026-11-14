package com.mindunlock.app

import com.mindunlock.app.data.local.JournalEntryEntity
import com.mindunlock.app.data.local.LearningSessionEntity
import com.mindunlock.app.data.local.LabStepProgressEntity
import com.mindunlock.app.data.local.LabQuestionResponseEntity
import com.mindunlock.app.data.local.LearningProfileEntity
import com.mindunlock.app.data.local.LearningSessionFeedbackEntity
import com.mindunlock.app.data.local.LearningStageProgressEntity
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.MindMapNodeEntity
import com.mindunlock.app.data.local.MindMapEdgeEntity
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
            labQuestionResponses = listOf(LabQuestionResponseEntity("lab-1", 0, "پاسخ ذخیره‌شده", 1_800_000_000_000L)),
            learningProfile = listOf(LearningProfileEntity(answersCsv = List(20) { "4" }.joinToString(","), sessionMinutes = 45, maxTopics = 2, guidedMode = "guided", completed = true)),
            learningSessionFeedback = listOf(LearningSessionFeedbackEntity(sessionId = 12, comprehension = 4, perceivedDifficulty = 3, focus = 5, energyBefore = 2, energyAfter = 4, note = "تمرین عملی مفید بود")),
            learningStageProgress = listOf(LearningStageProgressEntity(chapterId = "chapter-1", stageId = "overview", stageIndex = 1, completedAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_000L)),
            learningSessions = listOf(
                LearningSessionEntity(
                    id = 12,
                    chapterId = "chapter-1",
                    topicTitle = "مرور شبکه",
                    startedAt = 1_800_000_000_000L,
                    endedAt = 1_800_001_800_000L,
                    durationSeconds = 1800
                )
            ),
        )
        val json = UserDataBackupCodec.encode(payload, 1_800_000_000_000L, "0.6.5")
        val restored = UserDataBackupCodec.decode(json)
        assertEquals(3, restored.schemaVersion)
        assertEquals(1_800_000_000_000L, restored.exportedAt)
        assertEquals("0.6.5", restored.appVersion)
        assertEquals(10, restored.payload.totalRecords)
        assertEquals(3, restored.payload.progress.single().correct)
        assertEquals("مرور DNS", restored.payload.tasks.single().title)
        assertEquals("یادداشت خصوصی", restored.payload.journalEntries.single().body)
        assertEquals(true, restored.payload.labStepProgress.single().completed)
        assertEquals("پاسخ ذخیره‌شده", restored.payload.labQuestionResponses.single().answer)
        assertEquals(45, restored.payload.learningProfile.single().sessionMinutes)
        assertEquals("guided", restored.payload.learningProfile.single().guidedMode)
        val feedback = restored.payload.learningSessionFeedback.single()
        assertEquals(12L, feedback.sessionId)
        assertEquals(4, feedback.comprehension)
        assertEquals(3, feedback.perceivedDifficulty)
        assertEquals(5, feedback.focus)
        assertEquals(2, feedback.energyBefore)
        assertEquals(4, feedback.energyAfter)
        assertEquals("تمرین عملی مفید بود", feedback.note)
        val stage = restored.payload.learningStageProgress.single()
        assertEquals("chapter-1", stage.chapterId)
        assertEquals("overview", stage.stageId)
        assertEquals(1, stage.stageIndex)
        val session = restored.payload.learningSessions.single()
        assertEquals(1_800_000_000_000L, session.startedAt)
        assertEquals(1_800_001_800_000L, session.endedAt)
        assertEquals(1800L, session.durationSeconds)
    }


    @Test
    fun decodesOlderSchemaV1BackupWhenNewerCollectionsAreAbsent() {
        val root = com.google.gson.JsonParser.parseString(
            UserDataBackupCodec.encode(
                UserDataBackupPayload(
                    mindDump = listOf(MindDumpEntity(content = "یادداشت قدیمی"))
                ),
                1_800_000_000_000L,
                "0.5.8"
            )
        ).asJsonObject
        val payload = root.getAsJsonObject("payload")
        payload.remove("learningSessions")
        payload.remove("labQuestionResponses")
        payload.remove("learningProfile")
        payload.remove("learningSessionFeedback")
        payload.remove("learningStageProgress")
        payload.remove("mindMapNodes")
        payload.remove("mindMapEdges")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(payload.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        root.addProperty("sha256", digest)

        root.addProperty("schemaVersion", 1)
        val restored = UserDataBackupCodec.decode(root.toString())

        assertEquals("یادداشت قدیمی", restored.payload.mindDump.single().content)
        assertEquals(true, restored.payload.learningSessions.isEmpty())
        assertEquals(true, restored.payload.labQuestionResponses.isEmpty())
        assertEquals(true, restored.payload.learningProfile.isEmpty())
    }


    @Test
    fun rejectsInvalidOrDuplicateSessionFeedback() {
        val feedback = LearningSessionFeedbackEntity(sessionId = 1, comprehension = 3, perceivedDifficulty = 3, focus = 3)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.encode(
                UserDataBackupPayload(learningSessionFeedback = listOf(feedback, feedback)),
                1_800_000_000_000L,
                "test"
            ).let { UserDataBackupCodec.decode(it) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.encode(
                UserDataBackupPayload(learningSessionFeedback = listOf(feedback.copy(comprehension = 6))),
                1_800_000_000_000L,
                "test"
            ).let { UserDataBackupCodec.decode(it) }
        }
    }


    @Test
    fun rejectsDuplicateOrInvalidLearningStageProgress() {
        val stage = LearningStageProgressEntity(
            chapterId = "network",
            stageId = "why",
            stageIndex = 0,
            completedAt = 1_800_000_000_000L,
            updatedAt = 1_800_000_000_000L
        )
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(learningStageProgress = listOf(stage, stage)),
                    1_800_000_000_000L,
                    "test"
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(learningStageProgress = listOf(stage.copy(stageIndex = 9))),
                    1_800_000_000_000L,
                    "test"
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(learningStageProgress = listOf(stage.copy(stageId = "overview"))),
                    1_800_000_000_000L,
                    "test"
                )
            )
        }
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
            UserDataBackupCodec.decode(json.replace("\"schemaVersion\":3", "\"schemaVersion\":99"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode("""{"schemaVersion":1,"exportedAt":1800000000000,"appVersion":"test","payload":{},"sha256":"x"}""")
        }
    }

    @Test
    fun encryptedBackupRoundTripsAndDoesNotExposePayloadText() {
        val plain = UserDataBackupCodec.encode(
            UserDataBackupPayload(mindDump = listOf(MindDumpEntity(content = "private-note-xyz"))),
            1_800_000_000_000L,
            "test"
        )
        val encrypted = UserDataBackupCodec.encrypt(plain, "correct horse battery".toCharArray())
        assertEquals(false, encrypted.contains("private-note-xyz"))
        val decrypted = UserDataBackupCodec.decryptIfEncrypted(encrypted, "correct horse battery".toCharArray())
        assertEquals("private-note-xyz", UserDataBackupCodec.decode(decrypted).payload.mindDump.single().content)
    }

    @Test
    fun encryptedBackupRejectsWrongPasswordAndTamperedCiphertext() {
        val plain = UserDataBackupCodec.encode(
            UserDataBackupPayload(mindDump = listOf(MindDumpEntity(content = "private-note"))),
            1_800_000_000_000L,
            "test"
        )
        val encrypted = UserDataBackupCodec.encrypt(plain, "correct horse battery".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decryptIfEncrypted(encrypted, "incorrect password".toCharArray())
        }
        val root = com.google.gson.JsonParser.parseString(encrypted).asJsonObject
        val ciphertext = root.get("ciphertext").asString
        root.addProperty("ciphertext", ciphertext.dropLast(4) + "AAAA")
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decryptIfEncrypted(root.toString(), "correct horse battery".toCharArray())
        }
    }

    @Test
    fun rejectsTooShortEncryptionPasswordAndClearsPasswordBuffer() {
        val password = "short".toCharArray()
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.encrypt("{}", password)
        }
        assertEquals(true, password.all { it == Char.MIN_VALUE })
    }

    @Test
    fun clearsPasswordBufferWhenEncryptedBackupValidationFails() {
        val password = "long-enough-password".toCharArray()
        val malformedEncrypted = """{"format":"mindunlock-encrypted-backup-v1","iterations":1}"""

        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decryptIfEncrypted(malformedEncrypted, password)
        }
        assertEquals(true, password.all { it == Char.MIN_VALUE })
    }

    @Test
    fun clearsPasswordBufferWhenInputIsNotEncrypted() {
        val password = "unused-password".toCharArray()
        val plainJson = """{"schemaVersion":1}"""

        assertEquals(plainJson, UserDataBackupCodec.decryptIfEncrypted(plainJson, password))
        assertEquals(true, password.all { it == Char.MIN_VALUE })
    }

    @Test
    fun writesRestoreSafetyBackupAtomicallyAndLeavesNoTemporaryFile() {
        val directory = java.nio.file.Files.createTempDirectory("mindunlock-safety-test").toFile()
        try {
            val json = """{"schemaVersion":1,"payload":"complete"}"""
            val saved = writeSafetyBackupAtomically(directory, json, 123456L)

            assertEquals(true, saved.isFile)
            assertEquals(json, saved.readText(Charsets.UTF_8))
            assertEquals(1, directory.listFiles()?.count { it.extension == "json" })
            assertEquals(0, directory.listFiles()?.count { it.extension == "tmp" })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun refusesSafetyBackupWhenTargetPathIsNotDirectory() {
        val parent = java.nio.file.Files.createTempDirectory("mindunlock-safety-invalid").toFile()
        val file = java.io.File(parent, "not-a-directory").apply { writeText("preserve") }
        try {
            assertThrows(java.io.IOException::class.java) {
                writeSafetyBackupAtomically(file, """{"payload":"new"}""", 123456L)
            }
            assertEquals("preserve", file.readText())
        } finally {
            parent.deleteRecursively()
        }
    }


    @Test
    fun roundTripsMindMapNodesAndDirectedRelationships() {
        val nodes = listOf(
            MindMapNodeEntity(id = 21, title = "شبکه", nodeType = "یادگیری", createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_100L),
            MindMapNodeEntity(id = 22, title = "DNS", description = "سامانه نام", nodeType = "مفهوم", createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_100L)
        )
        val edge = MindMapEdgeEntity(id = 31, sourceNodeId = 21, targetNodeId = 22, relationType = "شامل", createdAt = 1_800_000_000_200L)
        val decoded = UserDataBackupCodec.decode(
            UserDataBackupCodec.encode(
                UserDataBackupPayload(mindMapNodes = nodes, mindMapEdges = listOf(edge)),
                1_800_000_000_300L,
                "test"
            )
        )
        assertEquals(3, decoded.schemaVersion)
        assertEquals(nodes, decoded.payload.mindMapNodes)
        assertEquals(edge, decoded.payload.mindMapEdges.single())
        assertEquals(3, decoded.payload.totalRecords)
    }

    @Test
    fun rejectsMindMapEdgesWithMissingEndpointsOrSelfLinks() {
        val node = MindMapNodeEntity(id = 41, title = "گره", createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_000L)
        val dangling = MindMapEdgeEntity(id = 51, sourceNodeId = 41, targetNodeId = 99, createdAt = 1_800_000_000_000L)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(UserDataBackupCodec.encode(UserDataBackupPayload(mindMapNodes = listOf(node), mindMapEdges = listOf(dangling)), 1_800_000_000_100L, "test"))
        }
        val selfLink = dangling.copy(targetNodeId = 41)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(UserDataBackupCodec.encode(UserDataBackupPayload(mindMapNodes = listOf(node), mindMapEdges = listOf(selfLink)), 1_800_000_000_100L, "test"))
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

    @Test
    fun rejectsDuplicateMindMapEdgesAndInvalidNodeFields() {
        val first = MindMapNodeEntity(id = 61, title = "شبکه", createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_000L)
        val second = MindMapNodeEntity(id = 62, title = "DNS", createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_000L)
        val edge = MindMapEdgeEntity(id = 71, sourceNodeId = 61, targetNodeId = 62, createdAt = 1_800_000_000_000L)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(UserDataBackupCodec.encode(UserDataBackupPayload(mindMapNodes = listOf(first, second), mindMapEdges = listOf(edge, edge.copy(id = 72))), 1_800_000_000_100L, "test"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(UserDataBackupCodec.encode(UserDataBackupPayload(mindMapNodes = listOf(first.copy(title = " ".repeat(4_001)))), 1_800_000_000_100L, "test"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(UserDataBackupCodec.encode(UserDataBackupPayload(mindMapNodes = listOf(first.copy(nodeType = "x".repeat(41)))), 1_800_000_000_100L, "test"))
        }
    }

    @Test
    fun rejectsDuplicateMindMapNodeIds() {
        val node = MindMapNodeEntity(id = 81, title = "گره", createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_000L)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(UserDataBackupCodec.encode(UserDataBackupPayload(mindMapNodes = listOf(node, node.copy(title = "گره دوم"))), 1_800_000_000_100L, "test"))
        }
    }


    @Test
    fun preservesMindMapSourceReferencesAndRejectsInvalidSourceMetadata() {
        val node = MindMapNodeEntity(
            id = 92, title = "DNS", nodeType = "یادگیری",
            sourceType = "mind_dump", sourceId = 91,
            createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_000_100L
        )
        val decoded = UserDataBackupCodec.decode(
            UserDataBackupCodec.encode(
                UserDataBackupPayload(mindMapNodes = listOf(node)),
                1_800_000_000_200L, "test"
            )
        )
        assertEquals("mind_dump", decoded.payload.mindMapNodes.single().sourceType)
        assertEquals(91L, decoded.payload.mindMapNodes.single().sourceId)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(mindMapNodes = listOf(node.copy(sourceType = "unknown"))),
                    1_800_000_000_200L, "test"
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(mindMapNodes = listOf(node.copy(sourceType = null))),
                    1_800_000_000_200L, "test"
                )
            )
        }
    }


    @Test
    fun preservesReversibleInboxSourceLinksAcrossBackupRestore() {
        val payload = UserDataBackupPayload(
            notes = listOf(NoteEntity(id = 11, title = "یادداشت", body = "متن ویرایش‌شده", sourceMindDumpId = 21)),
            tasks = listOf(StudyTaskEntity(id = 12, title = "کار", details = "جزئیات", sourceMindDumpId = 22)),
            journalEntries = listOf(JournalEntryEntity(id = 13, title = "روزانه", body = "متن", sourceMindDumpId = 23))
        )
        val decoded = UserDataBackupCodec.decode(
            UserDataBackupCodec.encode(payload, 1_800_000_000_300L, "test")
        )
        assertEquals(21L, decoded.payload.notes.single().sourceMindDumpId)
        assertEquals(22L, decoded.payload.tasks.single().sourceMindDumpId)
        assertEquals(23L, decoded.payload.journalEntries.single().sourceMindDumpId)
    }

    @Test
    fun rejectsDuplicateInboxSourceLinksInBackupPayload() {
        val duplicateNotes = listOf(
            NoteEntity(id = 21, title = "اول", body = "متن", sourceMindDumpId = 51),
            NoteEntity(id = 22, title = "دوم", body = "متن", sourceMindDumpId = 51)
        )
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(notes = duplicateNotes),
                    1_800_000_000_300L,
                    "test"
                )
            )
        }
    }

    @Test
    fun rejectsDuplicateMindMapSourceReferences() {
        val first = MindMapNodeEntity(id = 101, title = "گره اول", sourceType = "mind_dump", sourceId = 501)
        val second = MindMapNodeEntity(id = 102, title = "گره دوم", sourceType = "mind_dump", sourceId = 501)
        assertThrows(IllegalArgumentException::class.java) {
            UserDataBackupCodec.decode(
                UserDataBackupCodec.encode(
                    UserDataBackupPayload(mindMapNodes = listOf(first, second)),
                    1_800_000_000_200L,
                    "test"
                )
            )
        }
    }

}
