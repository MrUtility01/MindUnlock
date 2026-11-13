package com.mindunlock.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.room.withTransaction
import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.MindDumpEntity
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MindDumpScreen(userDatabase: UserDatabase, speech: PersianSpeech) {
    val scope = rememberCoroutineScope()
    val entries by userDatabase.mindDumpDao().observeAll().collectAsState(initial = emptyList())
    var draft by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("thought") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSavingDraft by remember { mutableStateOf(false) }
    var processingEntryIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingEditEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editDraft by rememberSaveable { mutableStateOf("") }
    fun runEntryAction(entryId: Long, successMessage: String, onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (entryId in processingEntryIds) return
        processingEntryIds = processingEntryIds + entryId
        scope.launch {
            try {
                action()
                onSuccess()
                message = successMessage
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "عملیات انجام نشد؛ ورودی اصلی حفظ شده است. دوباره تلاش کن."
            } finally {
                processingEntryIds = processingEntryIds - entryId
            }
        }
    }
    val inboxCount = entries.count { it.status == "inbox" }
    val archivedCount = entries.count { it.status == "archived" }
    val visibleEntries = entries.filter { entry ->
        (if (showArchived) entry.status == "archived" else entry.status == "inbox") &&
            (searchQuery.isBlank() ||
                entry.content.contains(searchQuery.trim(), ignoreCase = true) ||
                entry.category.contains(searchQuery.trim(), ignoreCase = true))
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        PersianVoiceInputButton { spoken -> draft = listOf(draft, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { speech.speak(draft) }, enabled = draft.isNotBlank()) { Text("خواندن متن") }
        }
        Text("نوع ورودی", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "thought" to "فکر",
                "task" to "کار",
                "idea" to "ایده",
                "learning" to "یادگیری",
                "government_job" to "کار دولتی",
                "freelance" to "کار آزاد",
                "private_job" to "کار شرکتی",
                "project" to "پروژه",
                "finance" to "مالی",
                "personal" to "شخصی",
                "family" to "خانواده",
                "other" to "سایر"
            ).forEach { (value, label) ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(label) })
            }
        }
        Button(
            onClick = {
                val value = draft.trim()
                if (value.isNotEmpty()) {
                    if (!isSavingDraft) {
                        isSavingDraft = true
                        scope.launch {
                            try {
                                userDatabase.mindDumpDao().insert(MindDumpEntity(content = value, category = category))
                                if (draft.trim() == value) draft = ""
                                message = "به صندوق ورودی اضافه شد."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                message = "ثبت ورودی انجام نشد؛ متن همچنان در کادر باقی مانده است."
                            } finally {
                                isSavingDraft = false
                            }
                        }
                    }
                }
            },
            enabled = draft.isNotBlank() && !isSavingDraft,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSavingDraft) "در حال ثبت…" else "ثبت در صندوق ورودی") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("صندوق ورودی (" + inboxCount + ")") })
            FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("بایگانی (" + archivedCount + ")") })
        }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (showArchived) "جست‌وجو در بایگانی" else "جست‌وجو در ورودی‌ها") },
            placeholder = { Text("بخشی از متن یا نوع ورودی را بنویس…") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { searchQuery = "" }) { Text("پاک‌کردن") }
                }
            }
        )
        Text(
            "نمایش " + visibleEntries.size + " مورد از " +
                (if (showArchived) archivedCount else inboxCount) + " مورد",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (visibleEntries.isEmpty()) {
            Text(
                if (searchQuery.isNotBlank()) "موردی با این عبارت پیدا نشد."
                else if (showArchived) "هنوز موردی بایگانی نشده است."
                else "صندوق ورودی خالی است. هرچه در ذهن داری اینجا ثبت کن.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleEntries.forEach { entry ->
                    MindDumpCard(
                        entry = entry,
                        speech = speech,
                        busy = entry.id in processingEntryIds,
                        onEdit = {
                            pendingEditEntryId = entry.id
                            editDraft = entry.content
                        },
                        onConvertToLater = {
                            runEntryAction(entry.id, "به موضوعات برای بعد منتقل شد؛ متن اصلی حفظ شده است.") {
                                userDatabase.withTransaction {
                                    userDatabase.laterTopicDao().insert(
                                        LaterTopicEntity(
                                            title = entry.content.lineSequence().first().take(120),
                                            details = entry.content,
                                            sourceMindDumpId = entry.id
                                        )
                                    )
                                    userDatabase.mindDumpDao().updateStatus(
                                        entry.id, "later", System.currentTimeMillis(), entry.linkedTaskId
                                    )
                                }
                            }
                        },
                        onConvertToTask = {
                            runEntryAction(entry.id, "به برنامه‌ریز منتقل شد.") {
                                userDatabase.withTransaction {
                                    val taskId = userDatabase.studyTaskDao().upsert(
                                        StudyTaskEntity(
                                            title = entry.content.lineSequence().first().take(80),
                                            details = entry.content,
                                            taskType = "task"
                                        )
                                    )
                                    userDatabase.mindDumpDao().updateStatus(
                                        entry.id, "task", System.currentTimeMillis(), taskId
                                    )
                                }
                            }
                        },
                        onSaveAsNote = {
                            runEntryAction(entry.id, "در دفتر یادداشت ذخیره شد.") {
                                userDatabase.withTransaction {
                                    userDatabase.noteDao().upsert(
                                        NoteEntity(title = "یادداشت از تخلیه ذهن", body = entry.content)
                                    )
                                    userDatabase.mindDumpDao().updateStatus(
                                        entry.id, "note", System.currentTimeMillis(), null
                                    )
                                }
                            }
                        },
                        onArchive = {
                            runEntryAction(entry.id, "مورد بایگانی شد.") {
                                userDatabase.mindDumpDao().updateStatus(
                                    entry.id, "archived", System.currentTimeMillis(), entry.linkedTaskId
                                )
                            }
                        },
                        onRestore = {
                            runEntryAction(entry.id, "به صندوق ورودی بازگشت.") {
                                userDatabase.mindDumpDao().updateStatus(
                                    entry.id, "inbox", System.currentTimeMillis(), entry.linkedTaskId
                                )
                            }
                        },
                        onDelete = {
                            pendingDeleteEntryId = entry.id
                        }
                    )
                }
            }
        }
    }
    pendingDeleteEntryId?.let { entryId ->
        val pendingEntry = entries.firstOrNull { it.id == entryId }
        if (pendingEntry != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteEntryId = null },
                title = { Text("حذف این ورودی؟") },
                text = { Text(pendingEntry.content.take(180)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteEntryId = null
                            runEntryAction(entryId, "مورد حذف شد.") {
                                userDatabase.mindDumpDao().delete(entryId)
                            }
                        }
                    ) { Text("حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteEntryId = null }) { Text("انصراف") }
                }
            )
        }
    }
    pendingEditEntryId?.let { entryId ->
        val pendingEntry = entries.firstOrNull { it.id == entryId }
        if (pendingEntry != null) {
            AlertDialog(
                onDismissRequest = { if (entryId !in processingEntryIds) pendingEditEntryId = null },
                title = { Text("ویرایش ورودی") },
                text = {
                    OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = entryId !in processingEntryIds,
                        minLines = 4,
                        maxLines = 10,
                        label = { Text("متن ورودی") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val value = editDraft.trim()
                            if (value.isNotEmpty()) {
                                runEntryAction(
                                    entryId,
                                    "ورودی ویرایش شد.",
                                    action = {
                                        userDatabase.mindDumpDao().updateContent(
                                            entryId, value, System.currentTimeMillis()
                                        )
                                    },
                                    onSuccess = {
                                        pendingEditEntryId = null
                                        editDraft = ""
                                    }
                                )
                            }
                        },
                        enabled = entryId !in processingEntryIds && editDraft.isNotBlank() && editDraft.trim() != pendingEntry.content
                    ) { Text(if (entryId in processingEntryIds) "در حال ذخیره…" else "ذخیره تغییرات") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (entryId !in processingEntryIds) pendingEditEntryId = null },
                        enabled = entryId !in processingEntryIds
                    ) { Text("انصراف") }
                }
            )
        }
    }
}

@Composable
private fun MindDumpCard(
    entry: MindDumpEntity,
    speech: PersianSpeech,
    busy: Boolean,
    onEdit: () -> Unit,
    onConvertToLater: () -> Unit,
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
        "government_job" -> "کار دولتی"
        "freelance" -> "کار آزاد"
        "private_job" -> "کار شرکتی"
        "project" -> "پروژه"
        "finance" -> "مالی"
        "personal" -> "شخصی"
        "family" -> "خانواده"
        "other" -> "سایر"
        else -> "فکر"
    }
    val statusLabel = when (entry.status) {
        "task" -> "منتقل‌شده به برنامه‌ریز"
        "note" -> "ذخیره‌شده در یادداشت‌ها"
        "archived" -> "بایگانی‌شده"
        "later" -> "ذخیره‌شده در موضوعات برای بعد"
        else -> "در صندوق ورودی"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(categoryLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    PersianDate.format(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(entry.content, style = MaterialTheme.typography.bodyLarge)
            if (entry.status == "inbox") OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ویرایش متن") }
            OutlinedButton(onClick = { speech.speak(entry.content) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("خواندن با صدا") }
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.status == "archived") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRestore, enabled = !busy) { Text(if (busy) "در حال انجام…" else "بازگردانی") }
                    OutlinedButton(onClick = onDelete, enabled = !busy) { Text("حذف") }
                }
            } else if (entry.status == "inbox" || entry.status == "learning" || entry.status == "thought" || entry.status == "idea") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConvertToTask, enabled = !busy) { Text(if (busy) "در حال انجام…" else "تبدیل به کار") }
                    OutlinedButton(onClick = onConvertToLater, enabled = !busy) { Text("برای بعد") }
                    OutlinedButton(onClick = onSaveAsNote, enabled = !busy) { Text("ذخیره یادداشت") }
                }
                OutlinedButton(onClick = onArchive, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("بایگانی") }
            } else {
                OutlinedButton(onClick = onArchive, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("انتقال به بایگانی") }
            }
        }
    }
}
