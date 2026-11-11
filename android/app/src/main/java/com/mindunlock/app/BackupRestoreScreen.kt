package com.mindunlock.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupRestoreScreen(userDb: UserDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("پشتیبان شامل داده‌های شخصی و پیشرفت آموزشی محلی است؛ فایل JSON رمزگذاری‌شده نیست.") }
    var pending by remember { mutableStateOf<UserDataBackupDocument?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && !busy) {
            scope.launch {
                busy = true
                status = "در حال آماده‌سازی پشتیبان..."
                try {
                    val json = UserDataBackupManager.export(context, userDb)
                    val written = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                            output.flush()
                            true
                        } ?: false
                    }
                    status = if (written) "پشتیبان با موفقیت ذخیره شد. حجم فایل: \${json.toByteArray(Charsets.UTF_8).size / 1024} کیلوبایت."
                    else "امکان نوشتن فایل در محل انتخاب‌شده وجود نداشت."
                } catch (error: Exception) {
                    status = "ساخت پشتیبان ناموفق بود: \${error.message ?: "خطای نامشخص"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            scope.launch {
                busy = true
                status = "در حال بررسی فایل پشتیبان..."
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readNBytes(25 * 1024 * 1024 + 1)
                            require(bytes.size <= 25 * 1024 * 1024) { "حداکثر حجم فایل پشتیبان ۲۵ مگابایت است." }
                            String(bytes, Charsets.UTF_8)
                        } ?: throw IllegalStateException("فایل قابل خواندن نیست.")
                    }
                    val decoded = UserDataBackupCodec.decode(json)
                    require(decoded.payload.totalRecords > 0) { "فایل معتبر است اما هیچ رکورد شخصی ندارد؛ برای جلوگیری از پاک‌شدن ناخواسته، بازیابی خالی مجاز نیست." }
                    pending = decoded
                    status = "فایل معتبر است. پیش از بازیابی، اطلاعات فعلی به‌صورت خودکار در پوشه خصوصی برنامه پشتیبان‌گیری می‌شود."
                } catch (error: Exception) {
                    status = "فایل پشتیبان رد شد: \${error.message ?: "خطای نامشخص"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("اطلاعات شخصی روی دستگاه نگهداری می‌شوند. برای انتقال به گوشی دیگر یا جلوگیری از ازدست‌رفتن اطلاعات، یک فایل پشتیبان JSON بساز.", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("موارد داخل پشتیبان", fontWeight = FontWeight.SemiBold)
                Text("• پیشرفت، پاسخ‌ها و زمان‌بندی مرور")
                Text("• یادداشت‌ها و نشانک‌ها")
                Text("• کارها و برنامه‌ریز")
                Text("• صندوق ورودی و دفتر روزانه")
                Text("• موضوعات برای بعد")
                Text("• وضعیت چک‌لیست آزمایشگاه‌ها")
                Text("• تاریخچه جلسه‌های یادگیری")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("موارد خارج از پشتیبان", fontWeight = FontWeight.SemiBold)
                Text("• کتابخانه محتوای آموزشی و فایل فصل‌ها؛ این محتوا از منبع همگام‌سازی دوباره دریافت می‌شود.")
                Text("• مدل‌های گفتار آفلاین و فایل‌های کش.")
                Text("• تنظیمات برنامه، رمز قفل و اطلاعات اتصال.")
                Text("این جداسازی مانع انتقال ناخواسته تنظیمات حساس یا فایل‌های حجیم می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(
            onClick = { exportLauncher.launch("MindUnlock-backup-\${System.currentTimeMillis()}.json") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "لطفاً صبر کن..." else "ساخت و ذخیره فایل پشتیبان") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("انتخاب فایل و بررسی پشتیبان") }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("امنیت فایل: ساختار نسخه‌دار و SHA-256 برای تشخیص تغییر یا خرابی داده‌ها بررسی می‌شود. SHA-256 رمزگذاری نیست؛ فایل را در محل امن نگه‌دار و برای اشتراک‌گذاری عمومی ارسال نکن.", style = MaterialTheme.typography.bodySmall)
    }

    pending?.let { document ->
        val payload = document.payload
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("تأیید بازیابی اطلاعات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("این عملیات داده‌های فعلی را با محتوای فایل جایگزین می‌کند.")
                    Text("تاریخ پشتیبان: \${DateFormat.getDateTimeInstance().format(Date(document.exportedAt))}")
                    Text("نسخه برنامه هنگام ساخت: \${document.appVersion.ifBlank { "نامشخص" }}")
                    Text("مجموع رکوردها: \${payload.totalRecords}")
                    Text("پیشرفت: \${payload.progress.size} · یادداشت: \${payload.notes.size} · نشانک: \${payload.bookmarks.size}")
                    Text("کارها: \${payload.tasks.size} · صندوق ورودی: \${payload.mindDump.size} · دفتر روزانه: \${payload.journalEntries.size}")
                    Text("موضوعات بعدی: \${payload.laterTopics.size} · مراحل آزمایشگاه: \${payload.labStepProgress.size} · جلسه‌ها: \${payload.learningSessions.size}")
                    Text("پیش از جایگزینی، یک نسخه اضطراری از اطلاعات فعلی در فضای خصوصی برنامه ذخیره می‌شود.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                UserDataBackupManager.restore(context, userDb, document)
                                status = "بازیابی با موفقیت انجام شد. اطلاعات قبلی نیز در نسخه اضطراری داخلی نگهداری شده است."
                                pending = null
                            } catch (error: Exception) {
                                status = "بازیابی انجام نشد و تراکنش لغو شد: \${error.message ?: "خطای نامشخص"}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy
                ) { Text("جایگزینی و بازیابی") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, enabled = !busy) { Text("انصراف") }
            }
        )
    }
}
