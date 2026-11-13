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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun LaterTopicsScreen(userDatabase: UserDatabase, speech: PersianSpeech) {
    val scope = rememberCoroutineScope()
    val topics by userDatabase.laterTopicDao().observeAll().collectAsState(initial = emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var revisitDate by rememberSaveable { mutableStateOf("") }
    var showJalaliPicker by rememberSaveable { mutableStateOf(false) }
    var priority by rememberSaveable { mutableStateOf(2) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var classificationFilter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSavingTopic by remember { mutableStateOf(false) }
    var processingTopicIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteTopicId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTopic by remember { mutableStateOf<LaterTopicEntity?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editDetails by rememberSaveable { mutableStateOf("") }
    var editPriority by rememberSaveable { mutableIntStateOf(2) }
    var editRevisitDate by rememberSaveable { mutableStateOf("") }

    fun runTopicAction(topicId: Long, success: String, action: suspend () -> Unit) {
        if (topicId in processingTopicIds) return
        processingTopicIds = processingTopicIds + topicId
        scope.launch {
            try {
                action()
                message = success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "عملیات انجام نشد؛ اطلاعات حفظ شده است. دوباره تلاش کن."
            } finally {
                processingTopicIds = processingTopicIds - topicId
            }
        }
    }

    val visible = topics.filter { topic ->
        (filter == "all" || topic.status == filter) &&
            (classificationFilter == 0 || topic.priority == classificationFilter) &&
            (query.isBlank() || topic.title.contains(query, true) || topic.details.contains(query, true))
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("موضوعات برای بعد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("سؤال‌ها و ایده‌هایی را که نمی‌خواهی همین حالا دنبال کنی، اینجا نگه‌دار تا تمرکز مسیر فعلی به‌هم نخورد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان یا سؤال") }, singleLine = true)
        PersianVoiceInputButton { spoken -> title = listOf(title, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("توضیحات (اختیاری)") }, minLines = 2)
        PersianVoiceInputButton { spoken -> details = listOf(details, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        OutlinedTextField(revisitDate, { revisitDate = it }, Modifier.fillMaxWidth(), label = { Text("تاریخ یادآوری اختیاری · شمسی یا میلادی") }, singleLine = true)
        OutlinedButton(onClick = { showJalaliPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("انتخاب تاریخ یادآوری از تقویم شمسی")
        }
        if (showJalaliPicker) {
            val parsedMillis = if (revisitDate.isBlank()) null else PersianDate.parseDateTime(revisitDate)
            val initialDate = parsedMillis?.let {
                PersianDate.fromGregorian(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
            } ?: PersianDate.fromGregorian(LocalDate.now())
            PersianJalaliDatePicker(
                initialDate = initialDate,
                onDismiss = { showJalaliPicker = false },
                onSelect = { selected ->
                    val selectedDate = PersianDate.toGregorian(selected)
                    revisitDate = PersianDate.formatInput(LocalDateTime.of(selectedDate, LocalTime.MIDNIGHT))
                    showJalaliPicker = false
                }
            )
        }
        Text("دسته‌بندی سؤال یا موضوع", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "ضروری", 2 to "موکول‌شده", 3 to "کنجکاوی").forEach { (value, label) ->
                FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(label) })
            }
        }
        Button(onClick = {
            val cleanTitle = title.trim()
            if (cleanTitle.isBlank()) {
                message = "عنوان موضوع را وارد کن."
            } else {
                val parsedDate = if (revisitDate.isBlank()) null else PersianDate.parseDateTime(revisitDate.trim())
                if (revisitDate.isNotBlank() && parsedDate == null) {
                    message = "تاریخ معتبر نیست؛ شمسی را به شکل ۱۴۰۵/۰۸/۲۱ یا میلادی را به شکل 2026-11-12 وارد کن."
                } else {
                    if (!isSavingTopic) {
                        isSavingTopic = true
                        scope.launch {
                            try {
                                userDatabase.laterTopicDao().insert(
                                    LaterTopicEntity(
                                        title = cleanTitle,
                                        details = details.trim(),
                                        priority = priority,
                                        revisitAt = parsedDate
                                    )
                                )
                                if (title.trim() == cleanTitle) {
                                    title = ""; details = ""; revisitDate = ""; priority = 2
                                }
                                message = "موضوع برای بعد ذخیره شد."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                message = "ذخیره انجام نشد؛ عنوان و توضیحات باقی ماندند."
                            } finally {
                                isSavingTopic = false
                            }
                        }
                    }
                }
            }
        }, enabled = title.isNotBlank() && !isSavingTopic, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSavingTopic) "در حال ذخیره…" else "ذخیره موضوع")
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در موضوعات") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "همه وضعیت‌ها", "parked" to "در انتظار", "active" to "در حال پیگیری", "done" to "انجام‌شده").forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }
        Text("فیلتر دسته‌بندی", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "همه", 1 to "ضروری", 2 to "موکول‌شده", 3 to "کنجکاوی").forEach { (value, label) ->
                FilterChip(selected = classificationFilter == value, onClick = { classificationFilter = value }, label = { Text(label) })
            }
        }
        }
        if (visible.isEmpty()) {
            Text("موضوعی برای نمایش وجود ندارد.", modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { topic ->
                    LaterTopicCard(
                        topic = topic,
                        busy = topic.id in processingTopicIds,
                        onSpeak = { speech.speak(topic.title + ". " + topic.details) },
                        onStatus = { status ->
                            runTopicAction(topic.id, "وضعیت موضوع به‌روزرسانی شد.") {
                                userDatabase.laterTopicDao().updateStatus(topic.id, status, System.currentTimeMillis())
                            }
                        },
                        onEdit = {
                            editingTopic = topic
                            editTitle = topic.title
                            editDetails = topic.details
                            editPriority = topic.priority
                            editRevisitDate = topic.revisitAt?.let { PersianDate.format(it, includeTime = false) }.orEmpty()
                        },
                        onReturnToInbox = {
                            runTopicAction(topic.id, "موضوع به صندوق ورودی بازگردانده شد.") {
                                check(userDatabase.mindDumpDao().returnLaterTopicToInbox(topic.id) != null) {
                                    "Later Topic no longer exists"
                                }
                            }
                        },
                        onDelete = { pendingDeleteTopicId = topic.id }
                    )
                }
            }
        }
    }
    editingTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { editingTopic = null },
            title = { Text("ویرایش موضوع برای بعد") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        editTitle, { editTitle = it },
                        label = { Text("عنوان یا سؤال") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        editDetails, { editDetails = it },
                        label = { Text("توضیحات و زمینه") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("دسته‌بندی سؤال یا موضوع", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "ضروری", 2 to "موکول‌شده", 3 to "کنجکاوی").forEach { (value, label) ->
                            FilterChip(
                                selected = editPriority == value,
                                onClick = { editPriority = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        editRevisitDate, { editRevisitDate = it },
                        label = { Text("تاریخ یادآوری (اختیاری)") },
                        placeholder = { Text("۱۴۰۵/۰۸/۲۱") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editTitle.isNotBlank() && topic.id !in processingTopicIds,
                    onClick = {
                        val cleanTitle = editTitle.trim()
                        val parsedDate = if (editRevisitDate.isBlank()) null
                            else PersianDate.parseDateTime(editRevisitDate.trim())
                        if (cleanTitle.isBlank()) {
                            message = "عنوان موضوع نمی‌تواند خالی باشد."
                        } else if (editRevisitDate.isNotBlank() && parsedDate == null) {
                            message = "تاریخ معتبر نیست؛ تاریخ شمسی یا میلادی وارد کن."
                        } else {
                            runTopicAction(topic.id, "موضوع به‌روزرسانی شد.") {
                                userDatabase.laterTopicDao().update(
                                    topic.copy(
                                        title = cleanTitle,
                                        details = editDetails.trim(),
                                        priority = editPriority,
                                        revisitAt = parsedDate,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                editingTopic = null
                            }
                        }
                    }
                ) { Text("ذخیره تغییرات") }
            },
            dismissButton = {
                TextButton(onClick = { editingTopic = null }) { Text("انصراف") }
            }
        )
    }
    pendingDeleteTopicId?.let { topicId ->
        val pendingTopic = topics.firstOrNull { it.id == topicId }
        if (pendingTopic != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteTopicId = null },
                title = { Text("حذف موضوع برای بعد؟") },
                text = { Text(pendingTopic.title) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteTopicId = null
                            runTopicAction(
                                topicId,
                                if (pendingTopic.sourceMindDumpId == null) "موضوع حذف شد." else "موضوع حذف شد و ورودی اصلی به صندوق بازگشت."
                            ) {
                                if (pendingTopic.sourceMindDumpId != null) {
                                    check(userDatabase.mindDumpDao().returnLaterTopicToInbox(topicId) != null) {
                                        "Later Topic no longer exists"
                                    }
                                } else {
                                    userDatabase.laterTopicDao().delete(topicId)
                                }
                            }
                        }
                    ) { Text("حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteTopicId = null }) { Text("انصراف") }
                }
            )
        }
    }
}

private fun sideQuestionClassificationLabel(value: Int): String = when (value) {
    1 -> "ضروری"
    3 -> "کنجکاوی"
    else -> "موکول‌شده"
}

@Composable
private fun LaterTopicCard(
    topic: LaterTopicEntity,
    busy: Boolean,
    onSpeak: () -> Unit,
    onStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onReturnToInbox: () -> Unit,
    onDelete: () -> Unit
) {
    val statusLabel = when (topic.status) {
        "active" -> "در حال پیگیری"
        "done" -> "انجام‌شده"
        else -> "در انتظار"
    }
    val dateLabel = topic.revisitAt?.let { PersianDate.format(it, includeTime = false) } ?: "بدون تاریخ یادآوری"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(topic.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (topic.details.isNotBlank()) Text(topic.details, style = MaterialTheme.typography.bodyMedium)
            Text("دسته: " + sideQuestionClassificationLabel(topic.priority) + " · " + dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onSpeak, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("خواندن با صدا") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, enabled = !busy) { Text("ویرایش") }
                if (topic.status != "active") OutlinedButton(onClick = { onStatus("active") }, enabled = !busy) { Text("پیگیری") }
                if (topic.status != "done") OutlinedButton(onClick = { onStatus("done") }, enabled = !busy) { Text("انجام شد") }
                if (topic.status != "parked") OutlinedButton(onClick = { onStatus("parked") }, enabled = !busy) { Text("بعداً") }
                OutlinedButton(onClick = onReturnToInbox, enabled = !busy) { Text("بازگردانی به صندوق") }
                OutlinedButton(onClick = onDelete, enabled = !busy) { Text("حذف") }
            }
        }
    }
}
