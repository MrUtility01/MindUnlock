package com.mindunlock.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun GitHubAuthCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { GitHubDeviceAuth() }
    var account by remember { mutableStateOf<GitHubAccount?>(null) }
    var tokenPresent by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<GitHubDeviceCode?>(null) }
    var status by remember { mutableStateOf("برای مرور مخزن عمومی نیازی به ورود نیست؛ ورود فقط برای قابلیت‌های نوشتن استفاده می‌شود.") }
    var busy by remember { mutableStateOf(false) }
    var authJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        tokenPresent = GitHubTokenVault.read(context) != null
        if (tokenPresent) status = "توکن GitHub به‌صورت رمزگذاری‌شده روی این دستگاه ذخیره شده است."
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("اتصال امن به GitHub", style = MaterialTheme.typography.titleMedium)
            if (account != null) {
                Text("متصل به حساب: ${account?.login}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    authJob?.cancel()
                    GitHubTokenVault.clear(context)
                    account = null
                    tokenPresent = false
                    deviceCode = null
                    status = "اتصال GitHub از این دستگاه پاک شد."
                }) { Text("پاک‌کردن توکن از این دستگاه") }
            } else if (tokenPresent) {
                Text("یک توکن رمزگذاری‌شده از ورود قبلی روی این دستگاه وجود دارد.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    GitHubTokenVault.clear(context)
                    tokenPresent = false
                    status = "توکن ذخیره‌شده پاک شد."
                }) { Text("پاک‌کردن توکن ذخیره‌شده") }
            } else {
                Button(
                    enabled = !busy,
                    onClick = {
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
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "در حال انتظار برای تأیید…" else "ورود امن با GitHub") }
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
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)))
                            }.onFailure { status = "باز کردن مرورگر ناموفق بود؛ نشانی را دستی باز کن." }
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
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "رمز عبور GitHub در برنامه دریافت نمی‌شود. توکن در Android Keystore محافظت می‌شود و در گزارش‌ها ثبت نمی‌شود. پاک‌کردن محلی، مجوز را از حساب GitHub لغو نمی‌کند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
