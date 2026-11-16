package com.mindunlock.app

import android.content.Context
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StartupTemporaryCleanupTest {

    @Test
    fun restoresModelBackupWhenInterruptedInstallLeftNoDestination() {
        val context = RuntimeEnvironment.getApplication()
        val speechBackup = File(context.filesDir, "speech/.restore-me.backup/model.onnx").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(256))
        }
        val asrBackup = File(context.filesDir, "offline-asr/restored-model.onnx.backup").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(128))
        }

        StartupTemporaryCleanup.clear(context)

        assertFalse(speechBackup.exists())
        assertTrue(File(context.filesDir, "speech/restore-me/model.onnx").isFile)
        assertTrue(File(context.filesDir, "offline-asr/restored-model.onnx").isFile)
    }

    @Test
    fun cleanupRemovesStagingAndPartialFilesButPreservesInstalledModels() {
        val context = RuntimeEnvironment.getApplication()
        val cachePartial = File(context.cacheDir, "voice.tar.bz2.part").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(128))
        }
        val staging = File(context.filesDir, "speech-staging/interrupted/model.onnx").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(256))
        }
        val installedModel = File(context.filesDir, "speech/voice/model.onnx").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(512))
        }
        val installBackup = File(context.filesDir, "speech/.voice.backup/old.onnx").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(32))
        }
        val asrModel = File(context.filesDir, "offline-asr/model.onnx").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(256))
        }
        val asrPartial = File(context.filesDir, "offline-asr/model.onnx.part").apply {
            writeBytes(ByteArray(64))
        }

        val removed = StartupTemporaryCleanup.clear(context)

        assertTrue(removed >= 480L)
        assertFalse(cachePartial.exists())
        assertFalse(staging.exists())
        assertFalse(installBackup.exists())
        assertFalse(asrPartial.exists())
        assertTrue(installedModel.isFile)
        assertTrue(asrModel.isFile)
    }
}
