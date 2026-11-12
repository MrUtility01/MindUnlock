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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.ZoneId

@Composable
fun LaterTopicsScreen(userDatabase: UserDatabase, speech: PersianSpeech) {
    val scope = rememberCoroutineScope()
    val topics by userDatabase.laterTopicDao().observeAll().collectAsState(initial = emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var revisitDate by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(2) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val visible = topics.filter { topic ->
        (filter == "all" || topic.status == filter) &&
            (query.isBlank() || topic.title.contains(query, true) || topic.details.contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("موضوعات برای بعد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("سؤال‌ها و ایده‌هایی را که نمی‌خواهی همین حالا دنبال کنی، اینجا نگه‌دار تا تمرکز مسیر فعلی به‌هم نخورد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان یا سؤال") }, singleLine = true)
        PersianVoiceInputButton { spoken -> title = listOf(title, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("توضیحات (اختیاری)") }, minLines = 2)
        PersianVoiceInputButton { spoken -> details = listOf(details, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        OutlinedTextField(revisitDate, { revisitDate = it }, Modifier.fillMaxWidth(), label = { Text("تاریخ یادآوری اختیاری · شمسی: ۱۴۰۵/۰۸/۲۱ یا میلادی: 2026-11-12") }, singleLine = true)
        Text("اولویت", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "زیاد", 2 to "معمولی", 3 to "کم").forEach { (value, label) ->
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
                    scope.launch {
                        userDatabase.laterTopicDao().insert(LaterTopicEntity(title = cleanTitle, details = details.trim(), priority = priority, revisitAt = parsedDate))
                        title = ""; details = ""; revisitDate = ""; priority = 2
                        message = "موضوع برای بعد ذخیره شد."
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره موضوع") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در موضوعات") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "همه", "parked" to "در انتظار", "active" to "در حال پیگیری", "done" to "انجام‌شده").forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }
        if (visible.isEmpty()) {
            Text("موضوعی برای نمایش وجود ندارد.", modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { topic ->
                    LaterTopicCard(
                        topic = topic,
                        onSpeak = { speech.speak(topic.title + ". " + topic.details) },
                        onStatus = { status -> scope.launch { userDatabase.laterTopicDao().updateStatus(topic.id, status, System.currentTimeMillis()) } },
                        onDelete = { scope.launch { userDatabase.laterTopicDao().delete(topic.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun LaterTopicCard(
    topic: LaterTopicEntity,
    onSpeak: () -> Unit,
    onStatus: (String) -> Unit,
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
            Text("اولویت: " + when (topic.priority) { 1 -> "زیاد"; 3 -> "کم"; else -> "معمولی" } + " · " + dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onSpeak, modifier = Modifier.fillMaxWidth()) { Text("خواندن با صدا") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (topic.status != "active") OutlinedButton(onClick = { onStatus("active") }) { Text("پیگیری") }
                if (topic.status != "done") OutlinedButton(onClick = { onStatus("done") }) { Text("انجام شد") }
                if (topic.status != "parked") OutlinedButton(onClick = { onStatus("parked") }) { Text("بعداً") }
                OutlinedButton(onClick = onDelete) { Text("حذف") }
            }
        }
    }
}
