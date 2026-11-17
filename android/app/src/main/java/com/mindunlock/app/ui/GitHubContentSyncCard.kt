package com.mindunlock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import com.mindunlock.app.data.remote.GitHubContentSyncEntry
import com.mindunlock.app.data.remote.GitHubContentSyncRepository
import com.mindunlock.app.data.remote.GitHubContentSyncStatus
import kotlinx.coroutines.launch

@Composable
fun GitHubContentSyncCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { GitHubContentSyncRepository(context) }
    var entries by remember { mutableStateOf<List<GitHubContentSyncEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("فهرست فقط دوره‌ها را پیدا می‌کند؛ برای افزودن به کتابخانه گوشی باید دوره را انتخاب و دکمه واردسازی را اجرا کنی.") }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var progressLabel by remember { mutableStateOf("") }

    fun inspect() {
        scope.launch {
            busy = true
            error = ""
            try {
                entries = repository.inspect()
                selected = entries.filter {
                    it.status == GitHubContentSyncStatus.NEW ||
                        it.status == GitHubContentSyncStatus.REMOTE_UPDATE
                }.map { it.item.id }.toSet()
                loaded = true
                status = if (entries.isEmpty()) "در Manifest هیچ دوره‌ای ثبت نشده است."
                    else "${entries.size} دوره از Manifest دریافت شد."
            } catch (failure: Exception) {
                error = failure.message ?: "دریافت فهرست دوره‌ها از GitHub ناموفق بود."
                loaded = false
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { inspect() }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("دوره‌های آموزشی مخزن", style = MaterialTheme.typography.titleMedium)
            Text("پیداشدن دوره یا خواندن Manifest به معنی ذخیره‌شدن آن نیست. واردسازی، محتوا را در پایگاه‌داده محلی برنامه ثبت می‌کند.", style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (busy && !loaded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("در حال خواندن فهرست دوره‌ها…")
                }
            }
            if (loaded && entries.isEmpty()) {
                Text("فهرست Manifest خالی است.", style = MaterialTheme.typography.bodyMedium)
            }
            entries.forEach { entry ->
                val eligible = entry.status == GitHubContentSyncStatus.NEW ||
                    entry.status == GitHubContentSyncStatus.REMOTE_UPDATE
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = entry.item.id in selected,
                                enabled = eligible && !busy,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + entry.item.id else selected - entry.item.id
                                }
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.item.title, style = MaterialTheme.typography.titleSmall)
                                Text(entry.item.groupName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${entry.status.label} · ${entry.item.format}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (eligible) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (entry.status.canKeepLocal || entry.status.canTakeRemote) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true
                                        error = ""
                                        try {
                                            repository.keepLocalVersion(entry.item.id)
                                            entries = repository.inspect()
                                            status = "نسخه محلی «${entry.item.title}» حفظ شد."
                                        } catch (failure: Exception) {
                                            error = failure.message ?: "حفظ نسخه محلی ناموفق بود."
                                        } finally { busy = false }
                                    }
                                }) { Text("حفظ نسخه محلی") }
                                if (entry.status.canTakeRemote) {
                                    OutlinedButton(enabled = !busy, onClick = {
                                        scope.launch {
                                            busy = true
                                            error = ""
                                            try {
                                                repository.takeRemoteVersion(entry.item.id)
                                                entries = repository.inspect()
                                                status = "نسخه GitHub برای «${entry.item.title}» جایگزین شد."
                                            } catch (failure: Exception) {
                                                error = failure.message ?: "دریافت نسخه GitHub ناموفق بود."
                                            } finally { busy = false }
                                        }
                                    }) { Text("جایگزینی با نسخه GitHub") }
                                }
                            }
                        }
                    }
                }
            }
            if (progress != null) {
                val (done, total) = progress!!
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(progressLabel.ifBlank { "در حال دریافت و پردازش دوره از GitHub…" }, style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("مرحله $done از $total", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && selected.isNotEmpty(), onClick = {
                    scope.launch {
                        busy = true
                        error = ""
                        progress = 0 to selected.size
                        progressLabel = "شروع دریافت و بررسی فایل‌های انتخاب‌شده از GitHub…"
                        try {
                            val result = repository.importSelected(selected) { done, total, title ->
                                progress = done to total
                                progressLabel = "در حال دریافت و پردازش: $title"
                            }
                            status = "نتیجه واردسازی: ${result.imported} بسته/فصل و ${result.importedItems} آیتم آموزشی ذخیره شد؛ ${result.skipped} مورد رد شد."
                            error = result.blocked.joinToString("\n")
                            entries = repository.inspect()
                            selected = emptySet()
                        } catch (failure: Exception) {
                            error = failure.message ?: "واردسازی محتوا ناموفق بود."
                        } finally {
                            busy = false
                            progress = null
                            progressLabel = ""
                        }
                    }
                }) { Text(if (busy) "در حال انجام…" else "واردسازی دوره‌های انتخاب‌شده") }
                OutlinedButton(enabled = !busy, onClick = { inspect() }) { Text("تازه‌سازی فهرست") }
            }
        }
    }
}
