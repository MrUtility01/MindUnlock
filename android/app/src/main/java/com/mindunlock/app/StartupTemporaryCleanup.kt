package com.mindunlock.app

import android.content.Context
import java.io.File

/**
 * Removes only interrupted-download and extraction scratch files.
 * Installed voice models, the learning database and user data are never deleted.
 */
internal object StartupTemporaryCleanup {
    private val lock = Any()

    fun clear(context: Context): Long = synchronized(lock) {
        var removedBytes = 0L
        removedBytes += clearDirectoryContents(context.cacheDir)
        context.externalCacheDir?.let { removedBytes += clearDirectoryContents(it) }

        // Tar extraction is staged outside cacheDir so that an interrupted install
        // can be resumed safely; stale staging data is never needed after recovery.
        val speechStaging = File(context.filesDir, "speech-staging")
        removedBytes += sizeOf(speechStaging)
        speechStaging.deleteRecursively()

        removedBytes += pruneTemporaryFiles(File(context.filesDir, "speech"))
        removedBytes += pruneTemporaryFiles(File(context.filesDir, "offline-asr"))
        removedBytes
    }

    private fun clearDirectoryContents(directory: File): Long {
        if (!directory.isDirectory) return 0L
        var bytes = 0L
        directory.listFiles()?.forEach { child ->
            bytes += sizeOf(child)
            child.deleteRecursively()
        }
        return bytes
    }

    private fun pruneTemporaryFiles(directory: File): Long {
        if (!directory.isDirectory) return 0L
        var bytes = 0L
        directory.listFiles()?.forEach { child ->
            val isTemporaryDirectory = child.isDirectory &&
                (child.name.endsWith(".installing") || child.name.endsWith(".backup"))
            val isTemporaryFile = child.isFile &&
                (child.name.endsWith(".part") || child.name.endsWith(".tmp") || child.name.endsWith(".backup"))
            when {
                isTemporaryDirectory || isTemporaryFile -> {
                    bytes += sizeOf(child)
                    child.deleteRecursively()
                }
                child.isDirectory -> bytes += pruneTemporaryFiles(child)
            }
        }
        return bytes
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::sizeOf) ?: 0L
    }
}
