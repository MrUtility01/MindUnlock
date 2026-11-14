package com.mindunlock.app

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Crash reporting that is installed from Application.onCreate, before repositories,
 * databases, Compose, or speech engines are created.
 */
object CrashDiagnostics {
    private const val BOOT_PREFS = "mindunlock_boot_diagnostics"
    private const val KEY_LAUNCH_IN_PROGRESS = "launch_in_progress"
    private const val KEY_RECOVERY_PENDING = "recovery_pending"

    @Volatile private var currentScreen = "Application"
    @Volatile private var installed = false

    fun setCurrentScreen(value: String) {
        currentScreen = value.take(160)
    }

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                recordCrash(app, "Uncaught exception on ${thread.name}", error, markRecovery = true)
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    /**
     * Returns true after a previous launch crashed or was killed before its first
     * successful frame. In that case MainActivity must route to the lightweight
     * recovery Activity instead of reopening the failing startup path.
     */
    fun beginLaunch(context: Context): Boolean {
        val preferences = context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
        val recoveryRequired = preferences.getBoolean(KEY_RECOVERY_PENDING, false) ||
            preferences.getBoolean(KEY_LAUNCH_IN_PROGRESS, false)
        if (!recoveryRequired) {
            preferences.edit().putBoolean(KEY_LAUNCH_IN_PROGRESS, true).apply()
        }
        return recoveryRequired
    }

    fun markLaunchHealthy(context: Context) {
        context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LAUNCH_IN_PROGRESS, false)
            .putBoolean(KEY_RECOVERY_PENDING, false)
            .putLong("last_healthy_at", System.currentTimeMillis())
            .apply()
    }

    fun clearRecoveryState(context: Context) {
        context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LAUNCH_IN_PROGRESS, false)
            .putBoolean(KEY_RECOVERY_PENDING, false)
            .apply()
    }

    fun recordStartupFailure(context: Context, stage: String, error: Throwable) {
        recordCrash(context.applicationContext, stage, error, markRecovery = true)
    }

    private fun recordCrash(context: Context, stage: String, error: Throwable, markRecovery: Boolean) {
        try {
            val app = context.applicationContext
            val folder = File(app.filesDir, "diagnostics")
            folder.mkdirs()
            val version = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")
            val trace = StringWriter()
            error.printStackTrace(PrintWriter(trace))
            val report = buildString {
                appendLine("MindUnlock crash report")
                appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
                appendLine("App version: " + version)
                appendLine("Package: " + app.packageName)
                appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                appendLine("Device: " + Build.MANUFACTURER + " " + Build.MODEL)
                appendLine("Thread: " + Thread.currentThread().name)
                appendLine("Stage: " + stage.take(240))
                appendLine("Screen: " + currentScreen)
                appendLine()
                append(trace.toString())
            }
            File(folder, "last-crash.txt").writeText(report.takeLast(160_000), Charsets.UTF_8)
            if (markRecovery) {
                app.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_LAUNCH_IN_PROGRESS, false)
                    .putBoolean(KEY_RECOVERY_PENDING, true)
                    .apply()
            }
        } catch (_: Throwable) {
            // Crash reporting must never replace the original failure.
        }
    }

    fun recordHandled(context: Context, stage: String, error: Throwable) {
        try {
            val folder = File(context.applicationContext.filesDir, "diagnostics")
            folder.mkdirs()
            val trace = StringWriter()
            error.printStackTrace(PrintWriter(trace))
            val entry = buildString {
                appendLine("Handled application error")
                appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
                appendLine("Stage: " + stage.take(240))
                appendLine("Error: " + (error.message ?: error.javaClass.name))
                appendLine()
                append(trace.toString())
            }
            val reportFile = File(folder, "last-crash.txt")
            val previous = if (reportFile.isFile) reportFile.readText(Charsets.UTF_8) else ""
            reportFile.writeText((previous + "\n\n" + entry).takeLast(160_000), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }

    fun readLastReport(context: Context): String =
        runCatching {
            File(context.filesDir, "diagnostics/last-crash.txt")
                .takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
        }.getOrDefault("")

    fun shareLastReport(context: Context) {
        val report = readLastReport(context)
        val text = if (report.isBlank()) {
            "هنوز گزارش کرشی از MindUnlock ثبت نشده است."
        } else {
            report
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MindUnlock crash diagnostics")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "ارسال گزارش عیب‌یابی").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
