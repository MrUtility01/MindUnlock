package com.mindunlock.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Minimal crash-loop recovery UI. It intentionally avoids Compose, Room,
 * SyncRepository, and all speech model initialization.
 */
class StartupRecoveryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = CrashDiagnostics.readLastReport(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val title = TextView(this).apply {
            text = "MindUnlock · بازیابی راه‌اندازی"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        content.addView(title, matchWrap())

        val explanation = TextView(this).apply {
            text = if (report.isBlank()) {
                "اجرای قبلی برنامه کامل نشد. گزارش فنی ثبت‌شده‌ای در دسترس نیست؛ ممکن است سیستم‌عامل برنامه را متوقف کرده باشد. می‌توانی گزارش موجود را بررسی کنی یا راه‌اندازی عادی را دوباره امتحان کنی."
            } else {
                "اجرای قبلی برنامه با خطا متوقف شد. برای جلوگیری از بسته‌شدن پیاپی، این صفحه بدون بارگذاری دیتابیس، صفحه‌های اصلی یا موتور صوتی باز شده است. اطلاعات شخصی و پایگاه‌داده‌ها حذف نشده‌اند."
            }
            textSize = 15f
            setPadding(0, dp(12), 0, dp(18))
        }
        content.addView(explanation, matchWrap())

        val send = Button(this).apply {
            text = "اشتراک گزارش فنی"
            setOnClickListener { shareReport(report) }
        }
        content.addView(send, matchWrap())

        val copy = Button(this).apply {
            text = "کپی گزارش فنی"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard == null) {
                    Toast.makeText(this@StartupRecoveryActivity, "کلیپ‌بورد در دسترس نیست.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("MindUnlock crash report", report.ifBlank { "No crash report was recorded." }))
                    Toast.makeText(this@StartupRecoveryActivity, "گزارش کپی شد.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(copy, matchWrap())

        val cache = Button(this).apply {
            text = "پاک‌کردن فایل‌های موقت و تلاش دوباره"
            setOnClickListener {
                runCatching {
                    cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                }.onSuccess {
                    Toast.makeText(this@StartupRecoveryActivity, "فایل‌های موقت پاک شدند؛ داده‌های شخصی حفظ شدند.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this@StartupRecoveryActivity, "پاک‌کردن فایل‌های موقت کامل نشد.", Toast.LENGTH_LONG).show()
                }
                retryMainActivity()
            }
        }
        content.addView(cache, matchWrap())

        val retry = Button(this).apply {
            text = "تلاش دوباره برای اجرای برنامه"
            setOnClickListener { retryMainActivity() }
        }
        content.addView(retry, matchWrap())

        val reportTitle = TextView(this).apply {
            text = "گزارش آخرین اجرا"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        }
        content.addView(reportTitle, matchWrap())

        val reportView = TextView(this).apply {
            text = report.ifBlank { "گزارش خطا ثبت نشده است." }
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        content.addView(reportView, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(scroll)
    }

    private fun shareReport(report: String) {
        val text = report.ifBlank { "MindUnlock closed before a crash report could be recorded." }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MindUnlock startup crash report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(send, "ارسال گزارش خطای MindUnlock"))
        }.onFailure {
            Toast.makeText(this, "بازکردن ابزار اشتراک‌گذاری ممکن نشد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun retryMainActivity() {
        CrashDiagnostics.clearRecoveryState(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
