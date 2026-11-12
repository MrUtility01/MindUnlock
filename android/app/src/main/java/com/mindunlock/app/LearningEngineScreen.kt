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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.AppDatabase
import com.mindunlock.app.data.local.UserDatabase
import com.mindunlock.app.data.local.LearningSessionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val activeSession by userDb.learningSessionDao().observeActive().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var elapsedSeconds by remember(activeSession?.id, activeSession?.startedAt) {
        mutableLongStateOf(activeSession?.let { ((System.currentTimeMillis() - it.startedAt).coerceAtLeast(0L) / 1000L) } ?: 0L)
    }
    LaunchedEffect(activeSession?.id, activeSession?.startedAt) {
        val session = activeSession
        while (session != null) {
            elapsedSeconds = (System.currentTimeMillis() - session.startedAt).coerceAtLeast(0L) / 1000L
            delay(1000L)
        }
    }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable {
        mutableStateOf(preferences.getString("learning_engine_selected_chapter", null).orEmpty())
    }
    var sessionMinutes by rememberSaveable {
        mutableStateOf(preferences.getInt("learning_session_minutes", 30).toFloat())
    }
    val completedItems = progress.count { it.completed }
    val totalContentItems = chapters.sumOf { it.itemCount }.takeIf { it > 0 } ?: progress.size
    val overallProgress = if (totalContentItems == 0) 0f else (completedItems.toFloat() / totalContentItems).coerceIn(0f, 1f)
    val selected = chapters.firstOrNull { it.id == selectedId }
    val nextChapter = LearningPathPolicy.nextIncompleteChapter(chapters, progress)
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
                    Text("پیشرفت ثبت‌شده: $completedItems مورد تکمیل‌شده از $totalContentItems مورد")
                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ثبت زمان واقعی یادگیری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (activeSession == null) {
                        Text("با شروع دستی جلسه، زمان سپری‌شده ثبت می‌شود. می‌توانی از برنامه خارج شوی و بعداً جلسه را ادامه بدهی.")
                        Button(
                            onClick = {
                                val target = selected ?: nextChapter
                                scope.launch {
                                    userDb.learningSessionDao().insert(
                                        LearningSessionEntity(
                                            chapterId = target?.id,
                                            topicTitle = target?.title ?: "مطالعه آزاد",
                                            source = "learning_engine",
                                            startedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("شروع جلسه یادگیری") }
                    } else {
                        Text("جلسه فعال: ${activeSession?.topicTitle}", fontWeight = FontWeight.SemiBold)
                        Text(formatSessionDuration(elapsedSeconds), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("جلسه تا زمان پایان دستی فعال می‌ماند؛ زمان توقف یا بیکاری دستگاه به‌طور خودکار تشخیص داده نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                val session = activeSession ?: return@Button
                                scope.launch {
                                    val endedAt = System.currentTimeMillis()
                                    val duration = ((endedAt - session.startedAt).coerceAtLeast(0L) / 1000L)
                                    userDb.learningSessionDao().finish(session.id, endedAt, duration)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("پایان جلسه و ثبت زمان") }
                    }
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
                    val sessionPlan = LearningPathPolicy.sessionPlan(sessionMinutes.toInt())
                    Text("تقسیم زمان: ${sessionPlan.learnMinutes} دقیقه یادگیری · ${sessionPlan.practiceMinutes} دقیقه تمرین · ${sessionPlan.reviewMinutes} دقیقه مرور")
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
                val chapterTotal = chapter.itemCount.takeIf { it > 0 } ?: chapterProgress.size
                val ratio = if (chapterTotal == 0) 0f else (done.toFloat() / chapterTotal).coerceIn(0f, 1f)
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
                            Text(if (chapterTotal == 0) "شروع‌نشده" else "$done از $chapterTotal تکمیل", style = MaterialTheme.typography.labelSmall)
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

private fun formatSessionDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
