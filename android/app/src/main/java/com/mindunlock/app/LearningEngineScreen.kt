package com.mindunlock.app

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.mindunlock.app.data.local.AppDatabase
import com.mindunlock.app.data.local.UserDatabase

@Composable
fun LearningEngineScreen(
    db: AppDatabase,
    userDb: UserDatabase,
    preferences: SharedPreferences,
    onOpenChapter: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val chapters by db.chapterDao().observeAll().collectAsState(initial = emptyList())
    val progress by userDb.progressDao().observeAll().collectAsState(initial = emptyList())
    var search by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable {
        mutableStateOf(preferences.getString("learning_engine_selected_chapter", null).orEmpty())
    }
    var sessionMinutes by rememberSaveable {
        mutableStateOf(preferences.getInt("learning_session_minutes", 30).toFloat())
    }
    val completedItems = progress.count { it.completed }
    val selected = chapters.firstOrNull { it.id == selectedId }
    val nextChapter = chapters.firstOrNull { chapter ->
        val chapterItems = progress.filter { it.chapterId == chapter.id }
        chapterItems.isEmpty() || (chapter.itemCount > 0 && chapterItems.count { it.completed } < chapter.itemCount)
    } ?: chapters.firstOrNull()
    val visibleChapters = chapters.filter {
        search.isBlank() || it.title.contains(search, ignoreCase = true) ||
            (it.groupName?.contains(search, ignoreCase = true) == true)
    }.sortedBy { it.orderIndex }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("موتور یادگیری", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("مسیر یادگیری را از فصل‌های دریافت‌شده انتخاب کن؛ محتوای دانلودشده بدون اینترنت قابل مطالعه است.")
                    Text("پیشرفت ثبت‌شده: $completedItems مورد تکمیل‌شده از ${progress.size} مورد")
                    LinearProgressIndicator(
                        progress = { if (progress.isEmpty()) 0f else completedItems.toFloat() / progress.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("جلسه پیشنهادی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("مدت جلسه: ${sessionMinutes.toInt()} دقیقه")
                    Slider(
                        value = sessionMinutes,
                        onValueChange = {
                            sessionMinutes = it
                            preferences.edit().putInt("learning_session_minutes", it.toInt()).apply()
                        },
                        valueRange = 15f..90f,
                        steps = 14
                    )
                    val learn = (sessionMinutes / 3f).toInt().coerceAtLeast(5)
                    val practice = (sessionMinutes / 2f).toInt().coerceAtLeast(5)
                    val review = (sessionMinutes.toInt() - learn - practice).coerceAtLeast(3)
                    Text("تقسیم زمان: $learn دقیقه یادگیری · $practice دقیقه تمرین · $review دقیقه مرور")
                    Text("این برنامه زمان‌بندی پیشنهادی است؛ با زدن هر مرحله، بخش مربوطه باز می‌شود.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            val target = selected?.id ?: nextChapter?.id
                            if (target != null) {
                                preferences.edit()
                                    .putString("learning_engine_selected_chapter", target)
                                    .putString("learning_engine_last_action", "learn")
                                    .apply()
                                onOpenChapter(target)
                            }
                        },
                        enabled = selected != null || nextChapter != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selected != null) "ادامه فصل انتخاب‌شده" else "شروع از فصل بعدی") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = { preferences.edit().putString("learning_engine_last_action", "practice").apply(); onNavigate("quiz") },
                            label = { Text("مرحله تمرین") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { preferences.edit().putString("learning_engine_last_action", "review").apply(); onNavigate("review") },
                            label = { Text("مرحله مرور") }
                        )
                    }
                }
            }
        }
        item {
            Text("مسیرها و فصل‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("جست‌وجوی فصل یا موضوع") },
                singleLine = true
            )
        }
        if (visibleChapters.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (chapters.isEmpty()) "هنوز محتوایی دریافت نشده است." else "نتیجه‌ای برای جست‌وجو پیدا نشد.")
                        if (chapters.isEmpty()) Button(onClick = { onNavigate("sync") }) { Text("دریافت محتوای آموزشی") }
                    }
                }
            }
        } else {
            items(visibleChapters, key = { it.id }) { chapter ->
                val chapterProgress = progress.filter { it.chapterId == chapter.id }
                val done = chapterProgress.count { it.completed }
                val ratio = if (chapterProgress.isEmpty()) 0f else done.toFloat() / chapterProgress.size
                Card(
                    Modifier.fillMaxWidth().clickable {
                        selectedId = chapter.id
                        preferences.edit().putString("learning_engine_selected_chapter", chapter.id).apply()
                    }
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.SemiBold)
                        Text(chapter.groupName ?: "بدون دسته‌بندی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (chapterProgress.isEmpty()) "شروع‌نشده" else "$done از ${chapterProgress.size} تکمیل", style = MaterialTheme.typography.labelSmall)
                            Text(if (selectedId == chapter.id) "انتخاب‌شده" else "انتخاب فصل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(onClick = {
                            selectedId = chapter.id
                            preferences.edit()
                                .putString("learning_engine_selected_chapter", chapter.id)
                                .putString("learning_engine_last_action", "learn")
                                .apply()
                            onOpenChapter(chapter.id)
                        }, modifier = Modifier.fillMaxWidth()) { Text("باز کردن فصل") }
                    }
                }
            }
        }
        item {
            Text(
                "نشانک‌ها، یادداشت‌های درس و وضعیت تکمیل از ماژول‌های مطالعه و پایگاه داده محلی موجود استفاده می‌کنند. مدیریت دانلود و جست‌وجوی سراسری محتوا در گام‌های بعدی موتور یادگیری تکمیل می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
