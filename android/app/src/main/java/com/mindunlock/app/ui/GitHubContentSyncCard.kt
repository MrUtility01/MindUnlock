package com.mindunlock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    var dialogVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("محتوای آموزشی GitHub را جدا از همگام‌سازی ویندوز بررسی و وارد کن.") }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("همگام‌سازی محتوای GitHub", style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            progress?.let { (done, total) ->
                LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text("واردسازی $done از $total", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                entries = repository.inspect()
                                selected = entries.filter {
                                    it.status == GitHubContentSyncStatus.NEW ||
                                        it.status == GitHubContentSyncStatus.REMOTE_UPDATE
                                }.map { it.item.id }.toSet()
                                dialogVisible = true
                                status = "فهرست بررسی شد: ${entries.size} ورودی."
                            } catch (failure: Exception) {
                                error = failure.message ?: "دریافت Manifest ناموفق بود."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    if (busy) CircularProgressIndicator() else Text("بررسی Manifest و نسخه‌ها")
                }
                OutlinedButton(enabled = !busy, onClick = {
                    entries = emptyList()
                    selected = emptySet()
                    error = ""
                    status = "فهرست محلی پاک شد؛ برای دریافت آخرین Manifest دوباره بررسی کن."
                }) { Text("پاک‌کردن فهرست") }
            }
        }
    }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!busy) dialogVisible = false },
            title = { Text("محتوای قابل واردسازی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "فقط محتوای جدید یا تغییرات راه‌دورِ بدون تعارض قابل انتخاب است. نسخه‌های محلی و تعارض‌ها حفظ می‌شوند.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(entries, key = { it.item.id }) { entry ->
                            val eligible = entry.status == GitHubContentSyncStatus.NEW ||
                                entry.status == GitHubContentSyncStatus.REMOTE_UPDATE
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
                                Column(Modifier.weight(1f)) {
                                    Text(entry.item.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${entry.status.label} · ${entry.item.groupName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (eligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(entry.item.path, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (entries.isEmpty()) item { Text("هیچ ورودی در Manifest وجود ندارد.") }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    progress?.let { (done, total) ->
                        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        Text("واردسازی $done از $total", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && selected.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            progress = 0 to selected.size
                            try {
                                val result = repository.importSelected(selected) { done, total, _ ->
                                    progress = done to total
                                }
                                status = "واردسازی انجام شد: ${result.imported} فصل جدید/به‌روزشده، ${result.skipped} مورد نادیده‌گرفته‌شده."
                                error = result.blocked.joinToString("\n")
                                entries = repository.inspect()
                                selected = emptySet()
                                dialogVisible = false
                            } catch (failure: Exception) {
                                error = failure.message ?: "واردسازی محتوا ناموفق بود."
                            } finally {
                                busy = false
                                progress = null
                            }
                        }
                    }
                ) { Text(if (busy) "در حال واردسازی…" else "واردسازی موارد انتخاب‌شده") }
            },
            dismissButton = {
                OutlinedButton(enabled = !busy, onClick = { dialogVisible = false }) { Text("بستن") }
            }
        )
    }
}
