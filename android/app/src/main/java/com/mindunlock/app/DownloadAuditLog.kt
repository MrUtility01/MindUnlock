package com.mindunlock.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private, bounded diagnostics for download fallback behavior.
 * Query strings and credentials are deliberately excluded from persisted logs.
 */
internal object DownloadAuditLog {
    private const val TAG = "MindUnlockDownload"
    private const val MAX_BYTES = 512 * 1024
    private val lock = Any()

    fun record(context: Context, source: String, message: String) {
        val safeSource = source.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(40)
        val safeMessage = message
            .replace(Regex("https?://[^\\s?]+\\?[^\\s]+"), "<url-redacted>")
            .replace(Regex("(token|authorization|password)=?[^\\s&]*", RegexOption.IGNORE_CASE), "$1=<redacted>")
            .take(1200)
        val line = "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())} [$safeSource] $safeMessage"
        Log.i(TAG, line)
        synchronized(lock) {
            runCatching {
                val directory = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
                val file = File(directory, "downloads.log")
                if (file.exists() && file.length() > MAX_BYTES) {
                    val rotated = File(directory, "downloads.previous.log")
                    rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(line + "\n", Charsets.UTF_8)
            }.onFailure { Log.w(TAG, "Could not persist download diagnostic", it) }
        }
    }
}
