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

object CrashDiagnostics {
    @Volatile private var currentScreen = "MainActivity"
    @Volatile private var installed = false

    fun setCurrentScreen(value: String) { currentScreen = value.take(160) }

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
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
                    appendLine("Thread: " + thread.name)
                    appendLine("Screen: " + currentScreen)
                    appendLine()
                    append(trace.toString())
                }
                File(folder, "last-crash.txt").writeText(report, Charsets.UTF_8)
            } catch (_: Throwable) { }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

                  fun recordHandled(context: Context, stage: String, error: Throwable) {
        try {
            val folder = File(context.applicationContext.filesDir, "diagnostics")
            folder.mkdirs()
            val trace = StringWriter()
            error.printStackTrace(PrintWriter(trace))
            val entry = buildString {
                appendLine("Handled lesson error")
                appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
                appendLine("Stage: " + stage.take(240))
                appendLine("Error: " + (error.message ?: error.javaClass.name))
                appendLine()
                append(trace.toString())
            }
            val reportFile = File(folder, "last-crash.txt")
            val previous = if (reportFile.isFile) reportFile.readText(Charsets.UTF_8) else ""
            reportFile.writeText((previous + "\n\n" + entry).takeLast(120_000), Charsets.UTF_8)
        } catch (_: Throwable) { }
    }

fun readLastReport(context: Context): String =
        runCatching {
            File(context.filesDir, "diagnostics/last-crash.txt")
                .takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
        }.getOrDefault("")

    fun shareLastReport(context: Context) {
        val report = readLastReport(context)
        val text = if (report.isBlank()) "هنوز گزارش کرشی از MindUnlock ثبت نشده است." else report
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MindUnlock crash diagnostics")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "ارسال گزارش عیب‌یابی").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
