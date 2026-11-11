package com.mindunlock.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MindDumpScreen(userDatabase: UserDatabase) {
    val scope = rememberCoroutineScope()
    val entries by userDatabase.mindDumpDao().observeAll().collectAsState(initial = emptyList())
    var draft by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("thought") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val visibleEntries = entries.filter { (it.status == "archived") == showArchived }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تخلیه ذهن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "هر فکر، کار نیمه‌تمام، سؤال یا ایده‌ای را سریع ثبت کن؛ لازم نیست همین حالا برایش تصمیم بگیری.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("چه چیزی ذهنت را مشغول کرده؟") },
            placeholder = { Text("ایده، کار، سؤال، نگرانی یا موضوعی برای یادگیری…") },
            minLines = 3,
            maxLines = 6
        )
        Text("نوع ورودی", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("thought" to "فکر", "task" to "کار", "idea" to "ایده", "learning" to "یادگیری").forEach { (value, label) ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(label) })
            }
        }
        Button(
            onClick = {
                val value = draft.trim()
                if (value.isNotEmpty()) {
                    scope.launch {
                        userDatabase.mindDumpDao().insert(MindDumpEntity(content = value, category = category))
                        draft = ""
                        message = "به صندوق ورودی اضافه شد."
                    }
                }
            },
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت در صندوق ورودی") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("ورودی‌ها (" + entries.count { it.status != "archived" } + ")") })
            FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("بایگانی (" + entries.count { it.status == "archived" } + ")") })
        }
        if (visibleEntries.isEmpty()) {
            Text(
                if (showArchived) "هنوز موردی بایگانی نشده است." else "صندوق ورودی خالی است. هرچه در ذهن داری اینجا ثبت کن.",
                modifier = Modifier.padding(vertical = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleEntries, key = { it.id }) { entry ->
                    MindDumpCard(
                        entry = entry,
                        onConvertToTask = {
                            scope.launch {
                                val taskId = userDatabase.studyTaskDao().upsert(
                                    StudyTaskEntity(title = entry.content.lineSequence().first().take(80), details = entry.content, taskType = "task")
                                )
                                userDatabase.mindDumpDao().updateStatus(entry.id, "task", System.currentTimeMillis(), taskId)
                                message = "به برنامه‌ریز منتقل شد."
                            }
                        },
                        onSaveAsNote = {
                            scope.launch {
                                userDatabase.noteDao().upsert(NoteEntity(title = "یادداشت از تخلیه ذهن", body = entry.content))
                                userDatabase.mindDumpDao().updateStatus(entry.id, "note", System.currentTimeMillis(), null)
                                message = "در دفتر یادداشت ذخیره شد."
                            }
                        },
                        onArchive = {
                            scope.launch {
                                userDatabase.mindDumpDao().updateStatus(entry.id, "archived", System.currentTimeMillis(), entry.linkedTaskId)
                                message = "مورد بایگانی شد."
                            }
                        },
                        onRestore = {
                            scope.launch {
                                userDatabase.mindDumpDao().updateStatus(entry.id, "inbox", System.currentTimeMillis(), entry.linkedTaskId)
                                message = "به صندوق ورودی بازگشت."
                            }
                        },
                        onDelete = {
                            scope.launch { userDatabase.mindDumpDao().delete(entry.id) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MindDumpCard(
    entry: MindDumpEntity,
    onConvertToTask: () -> Unit,
    onSaveAsNote: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryLabel = when (entry.category) {
        "task" -> "کار"
        "idea" -> "ایده"
        "learning" -> "یادگیری"
        else -> "فکر"
    }
    val statusLabel = when (entry.status) {
        "task" -> "منتقل‌شده به برنامه‌ریز"
        "note" -> "ذخیره‌شده در یادداشت‌ها"
        "archived" -> "بایگانی‌شده"
        else -> "در صندوق ورودی"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(categoryLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale.getDefault()).format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(entry.content, style = MaterialTheme.typography.bodyLarge)
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.status == "archived") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRestore) { Text("بازگردانی") }
                    OutlinedButton(onClick = onDelete) { Text("حذف") }
                }
            } else if (entry.status == "inbox" || entry.status == "learning" || entry.status == "thought" || entry.status == "idea") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConvertToTask) { Text("تبدیل به کار") }
                    OutlinedButton(onClick = onSaveAsNote) { Text("ذخیره یادداشت") }
                }
                OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text("بایگانی") }
            } else {
                OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text("انتقال به بایگانی") }
            }
        }
    }
}
