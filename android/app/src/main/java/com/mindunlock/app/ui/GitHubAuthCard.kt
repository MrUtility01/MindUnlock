package com.mindunlock.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.remote.GitHubDeviceAuth
import com.mindunlock.app.data.remote.GitHubDeviceCode
import com.mindunlock.app.data.remote.GitHubTokenVault
import com.mindunlock.app.data.remote.GitHubAccount
import com.mindunlock.app.data.remote.GitHubWriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitHubAuthCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { GitHubDeviceAuth() }
    val writer = remember { GitHubWriteRepository(auth) }
    var account by remember { mutableStateOf<GitHubAccount?>(null) }
    var tokenPresent by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<GitHubDeviceCode?>(null) }
    var status by remember { mutableStateOf("برای مرور مخزن عمومی نیازی به ورود نیست؛ ورود فقط برای قابلیت‌های نوشتن استفاده می‌شود.") }
    var busy by remember { mutableStateOf(false) }
    var authJob by remember { mutableStateOf<Job?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedText by remember { mutableStateOf("") }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadBusy by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var pullRequestUrl by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uploadBusy = true
                uploadStatus = "در حال خواندن فایل انتخاب‌شده…"
                pullRequestUrl = null
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 1_000_000) { "حداکثر اندازه فایل ۱ مگابایت است." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray().toString(Charsets.UTF_8)
                        } ?: error("خواندن فایل انتخاب‌شده ممکن نشد.")
                    }
                    require(loaded.isNotBlank()) { "فایل خالی است." }
                    require(!loaded.contains(0.toChar())) { "این فایل متنی نیست." }
                    selectedText = loaded
                    selectedFileName = (uri.lastPathSegment ?: "content.txt").substringAfterLast('/')
                    uploadTitle = selectedFileName
                    uploadStatus = "فایل آماده ارسال است (${loaded.toByteArray(Charsets.UTF_8).size} بایت)."
                } catch (failure: Exception) {
                    uploadStatus = failure.message ?: "خواندن فایل ناموفق بود."
                    selectedText = ""
                    selectedFileName = ""
                } finally {
                    uploadBusy = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        tokenPresent = GitHubTokenVault.read(context) != null
        if (tokenPresent) status = "توکن GitHub به‌صورت رمزگذاری‌شده روی این دستگاه ذخیره شده است."
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("اتصال امن به GitHub", style = MaterialTheme.typography.titleMedium)
            if (account != null) {
                Text("متصل به حساب: ${account?.login}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    authJob?.cancel()
                    GitHubTokenVault.clear(context)
                    account = null
                    tokenPresent = false
                    deviceCode = null
                    selectedText = ""
                    uploadStatus = ""
                    status = "توکن محلی پاک شد. برای لغو مجوز سمت GitHub باید از تنظیمات حساب GitHub اقدام کنی."
                }) { Text("پاک‌کردن توکن از این دستگاه") }
            } else if (tokenPresent) {
                Text("یک توکن رمزگذاری‌شده از ورود قبلی روی این دستگاه وجود دارد.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    GitHubTokenVault.clear(context)
                    tokenPresent = false
                    selectedText = ""
                    uploadStatus = ""
                    status = "توکن ذخیره‌شده پاک شد."
                }) { Text("پاک‌کردن توکن ذخیره‌شده") }
            } else {
                Button(enabled = !busy, onClick = {
                    authJob?.cancel()
                    authJob = scope.launch {
                        busy = true
                        status = "در حال دریافت کد ورود از GitHub…"
                        deviceCode = null
                        try {
                            val code = auth.requestDeviceCode()
                            deviceCode = code
                            status = "کد را در صفحه GitHub وارد و دسترسی را تأیید کن."
                            val result = auth.authorize(context, code) { message -> status = message }
                            account = result
                            tokenPresent = true
                            deviceCode = null
                            status = "ورود موفق بود. توکن با Android Keystore رمزگذاری و ذخیره شد."
                        } catch (failure: Exception) {
                            if (failure !is kotlinx.coroutines.CancellationException) {
                                status = failure.message ?: "ورود به GitHub ناموفق بود."
                                deviceCode = null
                            }
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "در حال انتظار برای تأیید…" else "ورود امن با GitHub") }
                deviceCode?.let { code ->
                    Text("کد یک‌بارمصرف:", style = MaterialTheme.typography.labelMedium)
                    Text(code.userCode, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", code.userCode))
                            status = "کد ورود کپی شد."
                        }) { Text("کپی کد") }
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) }
                                .onFailure { status = "باز کردن مرورگر ناموفق بود؛ نشانی را دستی باز کن." }
                        }) { Text("باز کردن صفحه تأیید") }
                        OutlinedButton(onClick = {
                            authJob?.cancel()
                            authJob = null
                            deviceCode = null
                            busy = false
                            status = "فرایند ورود لغو شد."
                        }) { Text("لغو") }
                    }
                    Text("کد پس از ${code.expiresInSeconds / 60} دقیقه منقضی می‌شود.", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (tokenPresent) {
                Text("ارسال فایل به مخزن", style = MaterialTheme.typography.titleSmall)
                Text("فایل در content/inbox شاخه جداگانه ثبت می‌شود و یک Pull Request برای بازبینی می‌سازد؛ برنامه مستقیماً main را تغییر نمی‌دهد.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !uploadBusy && !busy, onClick = {
                    filePicker.launch(arrayOf("text/*", "application/json", "application/yaml", "application/xml"))
                }) { Text("انتخاب فایل متنی از گوشی") }
                if (selectedFileName.isNotBlank()) {
                    Text("فایل انتخاب‌شده: $selectedFileName", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = selectedFileName, onValueChange = { selectedFileName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام فایل مقصد") })
                    OutlinedTextField(value = uploadTitle, onValueChange = { uploadTitle = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان درخواست بازبینی") })
                    Text("محتوای انتخاب‌شده: ${selectedText.toByteArray(Charsets.UTF_8).size} بایت", style = MaterialTheme.typography.labelSmall)
                    Button(enabled = !uploadBusy && selectedText.isNotBlank() && selectedFileName.isNotBlank(), onClick = {
                        scope.launch {
                            uploadBusy = true
                            uploadStatus = "در حال ساخت شاخه و ارسال فایل به GitHub…"
                            pullRequestUrl = null
                            try {
                                val result = writer.submitTextFile(context, selectedFileName, selectedText, uploadTitle)
                                pullRequestUrl = result.pullRequestUrl
                                uploadStatus = "فایل در شاخه ${result.branch} ثبت شد؛ Pull Request شماره ${result.pullRequestNumber} ساخته شد."
                            } catch (failure: Exception) {
                                uploadStatus = failure.message ?: "ارسال فایل ناموفق بود."
                            } finally { uploadBusy = false }
                        }
                    }) { Text(if (uploadBusy) "در حال ارسال…" else "ارسال و ساخت Pull Request") }
                }
                if (uploadStatus.isNotBlank()) Text(uploadStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                pullRequestUrl?.let { url ->
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                        Text("مشاهده Pull Request در GitHub")
                    }
                }
            }

            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("رمز عبور GitHub در برنامه دریافت نمی‌شود. توکن در Android Keystore محافظت می‌شود و در گزارش‌ها ثبت نمی‌شود. پاک‌کردن محلی، مجوز را از حساب GitHub لغو نمی‌کند.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
