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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
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
import com.mindunlock.app.data.local.JournalEntryEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun JournalScreen(userDatabase: UserDatabase, speech: PersianSpeech) {
    val scope = rememberCoroutineScope()
    val entries by userDatabase.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editBody by rememberSaveable { mutableStateOf("") }
    var editMood by rememberSaveable { mutableStateOf("") }
    var editTags by rememberSaveable { mutableStateOf("") }
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filtered = entries.filter {
        query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || it.tags.contains(query, true)
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("دفتر روزانه", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("فضایی خصوصی برای ثبت اتفاق‌ها، فکرها و تجربه‌های روزانه؛ اطلاعات فقط در پایگاه داده محلی برنامه ذخیره می‌شود.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان (اختیاری)") }, singleLine = true)
        PersianVoiceInputButton { spoken -> title = listOf(title, spoken).filter { it.isNotBlank() }.joinToString(" ") }
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("امروز چه گذشت؟") }, minLines = 4, maxLines = 8)
        PersianVoiceInputButton { spoken -> body = listOf(body, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { speech.speak(body.ifBlank { title }) }, enabled = body.isNotBlank() || title.isNotBlank()) {
                androidx.compose.material3.Icon(Icons.Default.VolumeUp, contentDescription = "خواندن پیش‌نویس")
            }
        }
        Text("حال‌وهوای امروز (اختیاری)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to "بدون ثبت", "calm" to "آرام", "good" to "خوب", "tired" to "خسته", "stressed" to "پرفشار").forEach { (value, label) ->
                FilterChip(selected = mood == value, onClick = { mood = value }, label = { Text(label) })
            }
        }
        OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("برچسب‌ها (با ویرگول جدا کن)") }, singleLine = true)
        Button(
            onClick = {
                val text = body.trim()
                if (text.isNotEmpty() && !isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            val now = System.currentTimeMillis()
                            userDatabase.journalEntryDao().insert(
                                JournalEntryEntity(
                                    title = title.trim(), body = text,
                                    mood = mood.ifBlank { null }, tags = tags.trim(),
                                    createdAt = now, updatedAt = now
                                )
                            )
                            if (body.trim() == text) {
                                title = ""; body = ""; mood = ""; tags = ""
                            }
                            message = "یادداشت روزانه ذخیره شد."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            message = "ذخیره انجام نشد؛ متن پیش‌نویس حفظ شده است."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
            enabled = body.isNotBlank() && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSaving) "در حال ذخیره…" else "ذخیره در دفتر روزانه") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در دفتر روزانه") }, singleLine = true)
        Text("نوشته‌ها: ${filtered.size}", style = MaterialTheme.typography.titleSmall)
        }
        if (filtered.isEmpty()) {
            Text("هنوز نوشته‌ای ثبت نشده است.", modifier = Modifier.fillMaxWidth().padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEach { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.title.ifBlank { "بدون عنوان" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(PersianDate.format(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.body, style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { speech.speak((entry.title + ". " + entry.body).trim()) }, modifier = Modifier.fillMaxWidth()) { Text("خواندن نوشته با صدا") }
                            if (entry.mood != null) Text("حال‌وهوا: " + when(entry.mood) { "calm" -> "آرام"; "good" -> "خوب"; "tired" -> "خسته"; "stressed" -> "پرفشار"; else -> entry.mood }, style = MaterialTheme.typography.labelSmall)
                            if (entry.tags.isNotBlank()) Text("برچسب‌ها: ${entry.tags}", style = MaterialTheme.typography.labelSmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        editingEntry = entry
                                        editTitle = entry.title
                                        editBody = entry.body
                                        editMood = entry.mood.orEmpty()
                                        editTags = entry.tags
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ویرایش") }
                                OutlinedButton(
                                    onClick = { pendingDeleteEntryId = entry.id },
                                    modifier = Modifier.weight(1f)
                                ) { Text("حذف نوشته") }
                            }
                        }
                    }
                }
            }
        }
    }
    editingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("ویرایش نوشته") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editTitle, { editTitle = it }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editBody, { editBody = it }, label = { Text("متن نوشته") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editTags, { editTags = it }, label = { Text("برچسب‌ها") }, modifier = Modifier.fillMaxWidth())
                    Text("حال‌وهوا", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("" to "بدون ثبت", "calm" to "آرام", "good" to "خوب", "tired" to "خسته", "stressed" to "پرفشار").forEach { (value, label) ->
                            FilterChip(selected = editMood == value, onClick = { editMood = value }, label = { Text(label) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editBody.isNotBlank() && !isSaving,
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            scope.launch {
                                try {
                                    userDatabase.journalEntryDao().update(
                                        entry.copy(
                                            title = editTitle.trim(),
                                            body = editBody.trim(),
                                            mood = editMood.ifBlank { null },
                                            tags = editTags.trim(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    editingEntry = null
                                    message = "نوشته به‌روزرسانی شد."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    message = "ویرایش ذخیره نشد؛ اطلاعات قبلی حفظ شده است."
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                ) { Text(if (isSaving) "در حال ذخیره…" else "ذخیره تغییرات") }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("انصراف") }
            }
        )
    }
    pendingDeleteEntryId?.let { entryId ->
        val pendingEntry = entries.firstOrNull { it.id == entryId }
        if (pendingEntry != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteEntryId = null },
                title = { Text("حذف نوشته؟") },
                text = { Text(pendingEntry.title.ifBlank { pendingEntry.body.take(160) }) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteEntryId = null
                            scope.launch {
                                try {
                                    userDatabase.journalEntryDao().delete(entryId)
                                    message = "نوشته حذف شد."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    message = "حذف انجام نشد؛ نوشته حفظ شده است."
                                }
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

}
