package com.mindunlock.app

import kotlinx.coroutines.flow.catch
import com.mindunlock.app.CrashDiagnostics
import java.io.File

import kotlinx.coroutines.withContext

import kotlinx.coroutines.Dispatchers

import androidx.compose.runtime.produceState

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.Image

import android.net.Uri as AndroidUri

import android.graphics.BitmapFactory

import android.graphics.Bitmap

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.google.gson.JsonParser
import com.mindunlock.app.PersianDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.mindunlock.app.JalaliDate
import com.mindunlock.app.PersianSpeech
import com.mindunlock.app.StudyReminderReceiver
import com.mindunlock.app.data.local.AppDatabase
import com.mindunlock.app.data.local.UserDatabase
import com.mindunlock.app.data.local.BookmarkEntity
import com.mindunlock.app.data.local.ContentItemEntity
import com.mindunlock.app.data.local.LabStepProgressEntity
import com.mindunlock.app.data.local.ContentItemListEntry
import com.mindunlock.app.data.local.NoteEntity
import com.mindunlock.app.data.local.ProgressEntity
import com.mindunlock.app.data.local.StudyTaskEntity
import com.mindunlock.app.data.repository.ManifestChapter
import com.mindunlock.app.data.repository.SyncRepository
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ActionTile(label: String, destination: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val icon = when (destination) {
        "courses", "subject", "chapter" -> Icons.Default.Bookmark
        "flashcards", "review" -> Icons.Default.PlayArrow
        "quiz", "exam" -> Icons.Default.Check
        "labs" -> Icons.Default.Download
        "notes" -> Icons.Default.Bookmark
        else -> Icons.Default.Add
    }
    Card(
        onClick = { onNavigate(destination) },
        modifier = modifier.height(120.dp),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun DashboardScreen(
    db: AppDatabase,
    userDb: UserDatabase,
    speech: PersianSpeech,
    onNavigate: (String) -> Unit,
    onContinue: (String) -> Unit
) {
    val chapters by db.chapterDao().observeAll().collectAsState(initial = emptyList())
    val itemCount by db.contentItemDao().observeItemCount().collectAsState(initial = 0)
    val flashcardCount by db.contentItemDao().observeFlashcardCount().collectAsState(initial = 0)
    val progress by userDb.progressDao().observeAll().collectAsState(initial = emptyList())
    val completed = progress.count { it.completed }
    val last = chapters.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("MindUnlock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("ادامه بده؛ یادگیری پیوسته نتیجه می‌دهد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("فصل‌ها", chapters.size.toString(), Modifier.weight(1f))
            MetricCard("مطالب", itemCount.toString(), Modifier.weight(1f))
            MetricCard("تکمیل‌شده", completed.toString(), Modifier.weight(1f))
        }
        Text("شروع یادگیری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile("موضوع‌ها و فصل‌ها", "courses", onNavigate, Modifier.weight(1f))
            ActionTile("فلش‌کارت‌ها", "flashcards", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile("مرور امروز", "review", onNavigate, Modifier.weight(1f))
            ActionTile("تمرین", "quiz", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile("آزمون", "exam", onNavigate, Modifier.weight(1f))
            ActionTile("آزمایشگاه", "labs", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile("دفتر یادداشت", "notes", onNavigate, Modifier.weight(1f))
            ActionTile("برنامه‌ریز", "planner", onNavigate, Modifier.weight(1f))
        }
        Text("ادامهٔ مطالعه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (last != null) {
            Card(onClick = { onContinue(last.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(last.title, fontWeight = FontWeight.SemiBold)
                        Text(last.groupName ?: "بدون موضوع", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "ادامه")
                }
            }
        } else EmptyMessage("هنوز محتوایی دریافت نشده است.")
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubjectListScreen(db: AppDatabase, onSubjectClick: (String) -> Unit) {
    val chapters by db.chapterDao().observeAll().collectAsState(initial = emptyList())
    val allItemsCount by db.contentItemDao().observeItemCount().collectAsState(initial = 0)
    var query by rememberSaveable { mutableStateOf("") }
    val subjects = chapters.groupBy { it.groupName?.trim()?.takeIf(String::isNotEmpty) ?: "بدون موضوع" }
        .entries.sortedBy { entry -> entry.value.minOfOrNull { it.orderIndex } ?: Int.MAX_VALUE }
        .filter { it.key.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجوی موضوع") }, singleLine = true)
        Text("${subjects.size} موضوع · ${chapters.size} فصل · ${allItemsCount} مطلب", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(subjects, key = { it.key }) { entry ->
                val chapterCount = entry.value.size
                val itemCount = entry.value.sumOf { it.itemCount }
                Card(onClick = { onSubjectClick(entry.key) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Bookmark, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.key, fontWeight = FontWeight.SemiBold)
                            Text("$chapterCount فصل · $itemCount مطلب", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterListScreen(db: AppDatabase, subject: String, onChapterClick: (String) -> Unit) {
    val chapters by db.chapterDao().observeAll().collectAsState(initial = emptyList())
    var query by rememberSaveable(subject) { mutableStateOf("") }
    val filtered = chapters.filter { (it.groupName?.trim()?.ifBlank { "بدون موضوع" } ?: "بدون موضوع") == subject }
        .filter { query.isBlank() || it.title.contains(query, true) }
        .sortedWith(compareBy({ it.orderIndex }, { it.title }))
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(subject, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در فصل‌ها") }, singleLine = true)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(filtered, key = { it.id }) { chapter ->
                Card(onClick = { onChapterClick(chapter.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("فصل ${chapter.orderIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(chapter.title, fontWeight = FontWeight.SemiBold)
                            if (!chapter.description.isNullOrBlank()) Text(chapter.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text("${chapter.itemCount} مطلب", style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "باز کردن فصل")
                    }
                }
            }
            if (filtered.isEmpty()) item { EmptyMessage("در این موضوع فصلی پیدا نشد.") }
        }
    }
}

@Composable
fun ChapterContentScreen(db: AppDatabase, chapterId: String, onItemClick: (String) -> Unit) {
    val chapters by db.chapterDao().observeAll().collectAsState(initial = emptyList())
    val chapter = chapters.firstOrNull { it.id == chapterId }
    var query by rememberSaveable(chapterId) { mutableStateOf("") }
    var kind by rememberSaveable(chapterId) { mutableStateOf("همه") }
    val kindOptions = listOf("همه", "درس", "سؤال", "فلش‌کارت", "آزمایشگاه", "دستور", "مثال", "یادداشت")
    val selectedType = when (kind) {
        "درس" -> "lesson"
        "سؤال" -> "question"
        "فلش‌کارت" -> "flashcard"
        "آزمایشگاه" -> "lab"
        "دستور" -> "command"
        "مثال" -> "example"
        "یادداشت" -> "note"
        else -> ""
    }
    val loadedItems = remember(chapterId) { mutableStateListOf<ContentItemListEntry>() }
    var totalCount by remember(chapterId) { mutableIntStateOf(0) }
    var loading by remember(chapterId) { mutableStateOf(false) }
    var lessonLoadError by remember(chapterId) { mutableStateOf("") }
    var lessonLoadAttempt by remember(chapterId) { mutableIntStateOf(0) }
    val lessonContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageSize = 30

    LaunchedEffect(chapterId, selectedType, query, lessonLoadAttempt) {
        loading = true
        loadedItems.clear()
        lessonLoadError = ""
        try {
            val normalizedQuery = query.trim()
            totalCount = db.contentItemDao().getChapterFilteredCount(chapterId, selectedType, normalizedQuery)
            val firstPage = db.contentItemDao().getChapterPage(chapterId, selectedType, normalizedQuery, pageSize, 0)
            loadedItems.addAll(firstPage)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            lessonLoadError = error.message ?: "خطا در بارگذاری فصل رخ داد."
            android.util.Log.e("MindUnlockLesson", "Chapter load failed: " + chapterId, error)
            CrashDiagnostics.recordHandled(lessonContext, "ChapterContentScreen:" + chapterId, error)
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(chapter?.title ?: "فصل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!chapter?.description.isNullOrBlank()) Text(chapter?.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در مطالب") }, singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            kindOptions.forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option) }) }
        }
        Text(loadedItems.size.toString() + " از " + totalCount + " مورد", style = MaterialTheme.typography.labelSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (lessonLoadError.isNotBlank()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بارگذاری درس با خطا روبه‌رو شد", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Text(lessonLoadError, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { lessonLoadAttempt += 1 }) { Text("تلاش مجدد") }
                    }
                }
            }
            if (loadedItems.isEmpty() && !loading) item { EmptyMessage("مطلبی با این فیلتر پیدا نشد.") }
            items(loadedItems, key = { it.uid }) { item ->
                ContentItemCard(item, onClick = { onItemClick(item.uid) })
            }
            if (loadedItems.size < totalCount) item {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        if (!loading) {
                            loading = true
                            scope.launch {
                                try {
                                    val normalizedQuery = query.trim()
                                    val nextPage = db.contentItemDao().getChapterPage(
                                        chapterId, selectedType, normalizedQuery, pageSize, loadedItems.size
                                    )
                                    loadedItems.addAll(nextPage)
                                } catch (error: kotlinx.coroutines.CancellationException) {
                                  throw error
                              } catch (error: Exception) {
                                  lessonLoadError = error.message ?: "بارگذاری مطالب بعدی ناموفق بود."
                                  android.util.Log.e("MindUnlockLesson", "Chapter pagination failed: " + chapterId, error)
                                  CrashDiagnostics.recordHandled(lessonContext, "ChapterPagination:" + chapterId, error)
                              } finally {
                                  loading = false
                              }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "در حال بارگذاری…" else "بارگذاری ۳۰ مطلب بعدی") }
            }
            if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ContentItemCard(item: ContentItemListEntry, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kindLabel(item.type), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                if (item.sourceStatus != "verified") Text("غیرقابل مطالعه", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.title ?: "بدون عنوان", fontWeight = FontWeight.SemiBold)
            if (!item.summary.isNullOrBlank()) Text(item.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ContentItemCard(item: ContentItemEntity, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kindLabel(item.type), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                if (item.sourceStatus != "verified") Text("غیرقابل مطالعه", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.title ?: "بدون عنوان", fontWeight = FontWeight.SemiBold)
            if (!item.summary.isNullOrBlank()) Text(item.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ReaderScreen(db: AppDatabase, userDb: UserDatabase, itemUid: String, speech: PersianSpeech, preferences: android.content.SharedPreferences) {
    val keepAwakeContext = androidx.compose.ui.platform.LocalContext.current
    val keepAwakeActivity = androidx.compose.runtime.remember(keepAwakeContext) {
        var node: android.content.Context? = keepAwakeContext
        while (node != null && node !is android.app.Activity) {
            node = (node as? android.content.ContextWrapper)?.baseContext
        }
        node as? android.app.Activity
    }
    val keepAwakeEnabled = preferences.getBoolean("keep_screen_on_while_reading", true)
    val readingLineSpacing = preferences.getFloat("reading_line_spacing", 1.45f).coerceIn(1.1f, 2.2f)
    val readingHorizontalMargin = preferences.getFloat("reading_horizontal_margin", 16f).coerceIn(8f, 32f)
    androidx.compose.runtime.DisposableEffect(keepAwakeActivity, keepAwakeEnabled) {
        val window = keepAwakeActivity?.window
        if (keepAwakeEnabled) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val context = LocalContext.current
    var readerLoadError by remember(itemUid) { mutableStateOf("") }
    var readerLoadAttempt by remember(itemUid) { mutableIntStateOf(0) }
    val metadataFlow = remember(db, itemUid, readerLoadAttempt, context) {
        db.contentItemDao().observeMetadata(itemUid).catch { error ->
            readerLoadError = error.message ?: "خواندن اطلاعات این مطلب ناموفق بود."
            android.util.Log.e("MindUnlockLesson", "Reader metadata failed: " + itemUid, error)
            CrashDiagnostics.recordHandled(context, "ReaderMetadata:" + itemUid, error)
            emit(null)
        }
    }
    val contentFlow = remember(db, itemUid, readerLoadAttempt, context) {
        db.contentItemDao().observeContent(itemUid).catch { error ->
            readerLoadError = error.message ?: "خواندن متن این مطلب ناموفق بود."
            android.util.Log.e("MindUnlockLesson", "Reader content failed: " + itemUid, error)
            CrashDiagnostics.recordHandled(context, "ReaderContent:" + itemUid, error)
            emit(null)
        }
    }
    val itemMetadata by metadataFlow.collectAsState(initial = null)
    val itemContent by contentFlow.collectAsState(initial = null)
    val item = itemMetadata?.copy(content = itemContent)
    val scope = rememberCoroutineScope()
    val bookmarks by userDb.bookmarkDao().observeAll().collectAsState(initial = emptyList())
    val media by db.mediaCacheDao().observeAll().collectAsState(initial = emptyList())
    var selectedOption by remember(itemUid) { mutableIntStateOf(-1) }
    var answerMessage by remember(itemUid) { mutableStateOf("") }
    var answerVisible by remember(itemUid) { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    if (item == null) {
        if (readerLoadError.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("باز کردن مطلب با خطا روبه‌رو شد", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Text(readerLoadError, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { readerLoadError = ""; readerLoadAttempt += 1 }) { Text("تلاش مجدد") }
            }
        } else {
            EmptyMessage("در حال بارگذاری مطلب…")
        }
        return
    }
    val current = item!!
    val metadata = runCatching { JsonParser.parseString(current.metadataJson ?: "{}").asJsonObject }.getOrNull()
    val options = readStringArray(current.optionsJson ?: metadata?.get("options")?.toString())
    val correctIndex = metadata?.get("correct_index")?.let { runCatching { it.asInt }.getOrNull() }
    val marked = bookmarks.any { it.itemUid == current.uid }
    val bodyText = listOfNotNull(current.summary, current.content).filter { it.isNotBlank() }.joinToString("\n\n")
    val labDetails = remember(current.uid, current.metadataJson) {
        if (current.type in setOf("lab", "exercise", "practice", "project")) LabScenarioParser.parse(current.metadataJson)
        else LabScenarioMetadata()
    }
    val imageRefs = extractImageReferences(listOfNotNull(current.content, current.summary, current.metadataJson, current.rawJson).joinToString("\n"))
    val localImages = media.filter { cached ->
        val rel = cached.relativePath.replace('\\', '/')
        val base = rel.substringAfterLast('/')
        imageRefs.any { ref -> ref == rel || rel.endsWith(ref) || ref.endsWith(rel) || ref.substringAfterLast('/') == base }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(kindLabel(current.type), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text(current.title ?: "بدون عنوان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!current.summary.isNullOrBlank()) Text(cleanHtml(current.summary), style = MaterialTheme.typography.titleSmall)
        localImages.distinctBy { it.localPath }.forEach { cached -> SafeLocalCachedImage(cached.localPath) }
        if (labDetails.hasStructuredDetails) {
            LabScenarioReaderPanel(item = current, details = labDetails, userDb = userDb, speech = speech, preferences = preferences)
        }
        if (!current.content.isNullOrBlank()) {
                Text(
                    cleanHtml(current.content),
                    modifier = Modifier.padding(horizontal = readingHorizontalMargin.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * readingLineSpacing
                    )
                )
            }
        if (imageRefs.isNotEmpty() && localImages.isEmpty()) Text("تصاویر این مطلب در همگام‌سازی موجود نبودند؛ فصل را دوباره از ویندوز دریافت کن.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        when (current.type?.lowercase(Locale.ROOT)) {
            "question" -> {
                if (options.isNotEmpty()) {
                    Text("گزینه‌ها", fontWeight = FontWeight.Bold)
                    options.forEachIndexed { index, option ->
                        Row(Modifier.fillMaxWidth().clickable { selectedOption = index }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedOption == index, onClick = { selectedOption = index })
                            Text(option)
                        }
                    }
                    Button(onClick = {
                        if (selectedOption < 0) answerMessage = "ابتدا یک گزینه را انتخاب کن."
                        else if (correctIndex == null) answerMessage = "کلید پاسخ برای این سؤال در بانک ذخیره نشده است."
                        else {
                            val correct = selectedOption == correctIndex
                            answerMessage = if (correct) "پاسخ درست است." else "پاسخ صحیح: گزینهٔ ${correctIndex + 1}"
                            scope.launch {
                                val timestamp = System.currentTimeMillis()
                                val old = userDb.progressDao().get(current.uid) ?: ProgressEntity(current.uid, current.chapterId)
                                val schedule = ReviewScheduler.schedule(if (correct) 4 else 0, old.intervalDays, timestamp)
                                userDb.progressDao().upsert(old.copy(
                                    attempts = old.attempts + 1,
                                    correct = old.correct + if (correct) 1 else 0,
                                    wrong = old.wrong + if (correct) 0 else 1,
                                    quality = if (correct) 4 else 0,
                                    intervalDays = schedule.intervalDays,
                                    dueAt = schedule.dueAt,
                                    lastResult = if (correct) "correct" else "wrong",
                                    updatedAt = timestamp
                                ))
                            }
                        }
                    }) { Text("بررسی پاسخ") }
                    if (answerMessage.isNotBlank()) Text(answerMessage, color = MaterialTheme.colorScheme.primary)
                    if (answerVisible || answerMessage.isNotBlank()) metadata?.get("explanation")?.let { Text("توضیح: ${it.asString}") }
                } else Text("گزینه‌ای برای این سؤال ثبت نشده است.")
            }
            "flashcard" -> {
                val back = metadata?.get("back")?.let { runCatching { it.asString }.getOrNull() }.orEmpty().ifBlank { current.summary ?: current.content.orEmpty() }
                OutlinedButton(onClick = { answerVisible = !answerVisible }) { Text(if (answerVisible) "پنهان‌کردن پاسخ" else "نمایش پاسخ") }
                if (answerVisible) Text(cleanHtml(back), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                val text = "${current.title}. $bodyText"
                speech.speak(cleanHtml(text), preferences.getFloat("speech_rate", 1f), preferences.getString("speech_voice", null))
            }, enabled = speech.ready.value) { Icon(Icons.Default.PlayArrow, null); Text("خواندن فارسی") }
            OutlinedButton(onClick = { speech.stop() }) { Icon(Icons.Default.Stop, null); Text("توقف صدا") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    if (marked) userDb.bookmarkDao().delete(current.uid)
                    else userDb.bookmarkDao().upsert(BookmarkEntity(current.uid, current.chapterId))
                }
            }) { Icon(Icons.Default.Bookmark, null); Text(if (marked) "حذف نشانک" else "نشانک") }
            OutlinedButton(onClick = { noteDialog = true }) { Text("یادداشت مرتبط") }
        }
        Button(onClick = {
            scope.launch {
                val old = userDb.progressDao().get(current.uid) ?: ProgressEntity(current.uid, current.chapterId)
                userDb.progressDao().upsert(old.copy(completed = true, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            }
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Text("خواندم؛ ثبت در پیشرفت") }
        if (current.sourceStatus != "verified") Text("این محتوا تأییدشده نیست و برای مطالعه در اپ اندروید قابل استفاده نیست.", color = MaterialTheme.colorScheme.error)
    }
    if (noteDialog) {
        AlertDialog(onDismissRequest = { noteDialog = false }, title = { Text("یادداشت برای این مطلب") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(noteText, { noteText = it }, label = { Text("متن یادداشت") }, minLines = 3); PersianVoiceInputButton { spoken -> noteText = listOf(noteText, spoken).filter { it.isNotBlank() }.joinToString("\n") } } },
            confirmButton = { TextButton(onClick = {
                if (noteText.isNotBlank()) scope.launch { userDb.noteDao().upsert(NoteEntity(itemUid = current.uid, chapterId = current.chapterId, title = current.title ?: "یادداشت", body = noteText.trim())) }
                noteText = ""; noteDialog = false
            }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("انصراف") } })
    }
}

@Composable
private fun LabScenarioReaderPanel(
    item: ContentItemEntity,
    details: LabScenarioMetadata,
    userDb: UserDatabase,
    speech: PersianSpeech,
    preferences: android.content.SharedPreferences
) {
    val scope = rememberCoroutineScope()
    val savedSteps by userDb.labStepProgressDao().observeByItem(item.uid).collectAsState(initial = emptyList())
    val completedIndexes = remember(savedSteps) { savedSteps.filter { it.completed }.map { it.stepIndex }.toSet() }
    val progress by userDb.progressDao().observeAll().collectAsState(initial = emptyList())
    val saved = progress.firstOrNull { it.itemUid == item.uid }
    val finishedSteps = completedIndexes.count { it in details.commands.indices }
    val allStepsDone = details.commands.isEmpty() || finishedSteps == details.commands.size
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text("راهنمای اجرای آزمایشگاه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (details.goal.isNotBlank()) LabDetailLine("هدف آزمایش", details.goal)
        if (details.topology.isNotBlank()) LabDetailLine("توپولوژی", details.topology)
        if (details.prerequisites.isNotEmpty()) LabDetailLine("پیش‌نیازها", details.prerequisites.joinToString("، "))
        if (details.equipment.isNotEmpty()) LabDetailLine("تجهیزات موردنیاز", details.equipment.joinToString("، "))
        if (details.services.isNotEmpty()) LabDetailLine("سرویس‌ها و فناوری‌ها", details.services.joinToString("، "))
        if (details.commands.isNotEmpty()) {
            Text("چک‌لیست مراحل اجرا · ${finishedSteps} از ${details.commands.size}", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { finishedSteps.toFloat() / details.commands.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth()
            )
            details.commands.forEachIndexed { index, command ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = index in completedIndexes,
                        onCheckedChange = { checked ->
                            scope.launch {
                                if (checked) {
                                    userDb.labStepProgressDao().upsert(LabStepProgressEntity(item.uid, index, true))
                                } else {
                                    userDb.labStepProgressDao().delete(item.uid, index)
                                    val old = userDb.progressDao().get(item.uid)
                                    if (old?.completed == true && old.lastResult == "lab_completed") {
                                        userDb.progressDao().upsert(old.copy(completed = false, completedAt = null, lastResult = "lab_in_progress", updatedAt = System.currentTimeMillis()))
                                    }
                                }
                            }
                        }
                    )
                    Text("${index + 1}. $command", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (details.expectedOutput.isNotEmpty()) {
            Text("معیار پذیرش / خروجی مورد انتظار", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            details.expectedOutput.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (details.troubleshooting.isNotEmpty()) {
            Text("چک‌لیست عیب‌یابی", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            details.troubleshooting.forEachIndexed { index, issue -> Text("${index + 1}. $issue", style = MaterialTheme.typography.bodyMedium) }
        }
        if (details.questions.isNotEmpty()) {
            Text("سؤالات کنترل یادگیری", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            details.questions.forEachIndexed { index, question -> Text("${index + 1}. $question", style = MaterialTheme.typography.bodyMedium) }
        }
        val metadataLine = listOfNotNull(
            details.difficulty.takeIf(String::isNotBlank),
            details.estimatedMinutes?.let { "زمان تخمینی: $it دقیقه" }
        ).joinToString(" · ")
        if (metadataLine.isNotBlank()) Text(metadataLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val script = buildString {
                        append(details.goal)
                        details.commands.forEachIndexed { index, value -> append(". مرحله ${index + 1}: $value") }
                        details.expectedOutput.forEach { append(". خروجی مورد انتظار: $it") }
                    }
                    speech.speak(script, preferences.getFloat("speech_rate", 1f), preferences.getString("speech_voice", null))
                },
                enabled = speech.ready.value,
                modifier = Modifier.weight(1f)
            ) { Text("خواندن مراحل") }
            Button(
                onClick = {
                    scope.launch {
                        val timestamp = System.currentTimeMillis()
                        val old = userDb.progressDao().get(item.uid) ?: ProgressEntity(item.uid, item.chapterId)
                        userDb.progressDao().upsert(old.copy(
                            completed = true,
                            completedAt = timestamp,
                            lastResult = "lab_completed",
                            updatedAt = timestamp
                        ))
                    }
                },
                enabled = allStepsDone && saved?.completed != true,
                modifier = Modifier.weight(1f)
            ) { Text(if (saved?.completed == true) "آزمایش ثبت شده" else "ثبت تکمیل آزمایش") }
        }
        if (saved?.completed == true) {
            Text("این آزمایش به‌عنوان تکمیل‌شده در پیشرفت محلی ثبت شده است.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        } else if (!allStepsDone) {
            Text("برای ثبت تکمیل، همه مراحل بالا را علامت بزن. نتیجهٔ واقعی تجهیزات باید جداگانه بررسی شود.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FlashcardsScreen(db: AppDatabase, userDb: UserDatabase, speech: PersianSpeech, preferences: android.content.SharedPreferences, reviewOnly: Boolean = false) {
    val all by db.contentItemDao().observeByTypes(listOf("flashcard", "question", "quiz", "exercise", "practice", "exam")).collectAsState(initial = emptyList())
    val progress by userDb.progressDao().observeAll().collectAsState(initial = emptyList())
    val flashcards = all.filter { it.type == "flashcard" && it.sourceStatus == "verified" }
    val reviewCandidates = all.filter { item ->
        if (item.sourceStatus != "verified") return@filter false
        if (item.type == "flashcard") return@filter true
        val meta = runCatching { JsonParser.parseString(item.metadataJson ?: "{}").asJsonObject }.getOrNull()
        val options = readStringArray(item.optionsJson ?: meta?.get("options")?.toString())
        val correct = meta?.get("correct_index")?.let { runCatching { it.asInt }.getOrNull() }
        item.type?.let { it in setOf("question", "quiz", "exercise", "practice", "exam") } == true &&
            options.isNotEmpty() && correct != null && correct in options.indices
    }
    val now = System.currentTimeMillis()
    val dueCandidates = reviewCandidates.filter { item ->
        progress.firstOrNull { it.itemUid == item.uid }?.dueAt?.let { it <= now } ?: true
    }
    val weakCount = reviewCandidates.count { item ->
        val record = progress.firstOrNull { it.itemUid == item.uid }
        record != null && (record.wrong > record.correct || record.quality < 3)
    }
    val deck = if (reviewOnly) dueCandidates else flashcards
    var index by rememberSaveable(reviewOnly) { mutableIntStateOf(0) }
    var flipped by rememberSaveable(reviewOnly) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = deck.getOrNull(index)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (reviewOnly) "مرور هوشمند روزانه" else "دستهٔ فلش‌کارت‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(if (reviewOnly) "موارد سررسیدشده" else "فلش‌کارت‌ها", if (reviewOnly) dueCandidates.size.toString() else flashcards.size.toString(), Modifier.weight(1f))
            MetricCard("نیازمند تقویت", weakCount.toString(), Modifier.weight(1f))
        }
        if (reviewOnly) {
            Text("پرسش‌های دارای کلید پاسخ و فلش‌کارت‌ها در یک صف مرور قرار می‌گیرند. پاسخ اشتباه پس از ۱۰ دقیقه دوباره سررسید می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current == null) {
            EmptyMessage(
                if (reviewOnly && reviewCandidates.isNotEmpty()) "مرورهای سررسیدشده تمام شدند. موارد بعدی طبق فاصله‌گذاری در زمان مقرر نمایش داده می‌شوند."
                else if (reviewOnly) "هنوز فلش‌کارت یا پرسش چهارگزینه‌ای معتبر برای مرور وجود ندارد."
                else "هنوز فلش‌کارتی دریافت نشده است."
            )
        } else {
            Text("${index + 1} از ${deck.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { (index + 1f) / deck.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            val meta = runCatching { JsonParser.parseString(current.metadataJson ?: "{}").asJsonObject }.getOrNull()
            val isFlashcard = current.type == "flashcard"
            val options = readStringArray(current.optionsJson ?: meta?.get("options")?.toString())
            val correctIndex = meta?.get("correct_index")?.let { runCatching { it.asInt }.getOrNull() }
            val explanation = meta?.get("explanation")?.let { runCatching { it.asString }.getOrNull() }.orEmpty()
            val back = if (isFlashcard) {
                meta?.get("back")?.let { runCatching { it.asString }.getOrNull() }.orEmpty()
                    .ifBlank { current.summary ?: current.content.orEmpty() }
            } else {
                val answerText = correctIndex?.let { options.getOrNull(it) }.orEmpty()
                buildString {
                    append("پاسخ صحیح: ")
                    append(answerText)
                    if (explanation.isNotBlank()) {
                        append("\n\nتوضیح: ")
                        append(explanation)
                    }
                }
            }
            Card(onClick = { flipped = !flipped }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (flipped) "پاسخ" else "یادآوری فعال · پرسش", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text(cleanHtml(if (flipped) back else current.title ?: current.summary ?: current.content.orEmpty()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (flipped) "برای بازگشت به روی کارت لمس کن" else "ابتدا پاسخ را در ذهن بازیابی کن؛ سپس کارت را برگردان.", style = MaterialTheme.typography.bodySmall)
                }
            }
            val old = progress.firstOrNull { it.itemUid == current.uid }
            Text("تلاش‌ها: ${old?.attempts ?: 0} · درست: ${old?.correct ?: 0} · اشتباه: ${old?.wrong ?: 0} · فاصله: ${old?.intervalDays ?: 0} روز", style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = { speech.speak(cleanHtml(current.title.orEmpty() + ". " + current.content.orEmpty()), preferences.getFloat("speech_rate", 1f), preferences.getString("speech_voice", "gyro")) }, enabled = speech.ready.value) { Icon(Icons.Default.PlayArrow, null); Text("شنیدن کارت") }
            if (flipped) {
                Text("کیفیت یادآوری را صادقانه ارزیابی کن", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("دوباره" to 0, "سخت" to 3, "خوب" to 4, "آسان" to 5).forEach { (label, quality) ->
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val timestamp = System.currentTimeMillis()
                                val previous = userDb.progressDao().get(current.uid) ?: ProgressEntity(current.uid, current.chapterId)
                                val schedule = ReviewScheduler.schedule(quality, previous.intervalDays, timestamp)
                                userDb.progressDao().upsert(previous.copy(
                                    attempts = previous.attempts + 1,
                                    correct = previous.correct + if (quality >= 3) 1 else 0,
                                    wrong = previous.wrong + if (quality < 3) 1 else 0,
                                    quality = quality,
                                    intervalDays = schedule.intervalDays,
                                    dueAt = schedule.dueAt,
                                    lastResult = when (quality) {
                                        0 -> "review_again"
                                        3 -> "review_hard"
                                        4 -> "review_good"
                                        else -> "review_easy"
                                    },
                                    updatedAt = timestamp
                                ))
                                if (!reviewOnly) index = (index + 1).coerceAtMost(deck.size)
                                flipped = false
                            }
                        }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
                Text("دوباره: ۱۰ دقیقه · سخت: فاصله کوتاه · خوب: فاصله استاندارد · آسان: فاصله طولانی‌تر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!reviewOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { index = (index - 1).coerceAtLeast(0); flipped = false }, enabled = index > 0) { Text("قبلی") }
                OutlinedButton(onClick = { index = (index + 1).coerceAtMost(deck.lastIndex); flipped = false }, enabled = index < deck.lastIndex) { Text("بعدی") }
            }
        }
    }
}

@Composable
fun QuizScreen(db: AppDatabase, userDb: UserDatabase, speech: PersianSpeech, examMode: Boolean = false) {
    val questions by db.contentItemDao().observeByTypes(listOf("question", "quiz", "exercise", "practice", "exam")).collectAsState(initial = emptyList())
    val valid = questions.filter { it.sourceStatus == "verified" && readStringArray(it.optionsJson ?: runCatching { JsonParser.parseString(it.metadataJson ?: "{}").asJsonObject.get("options")?.toString() }.getOrNull() ).isNotEmpty() }
    val scope = rememberCoroutineScope()
    var index by rememberSaveable(examMode) { mutableIntStateOf(0) }
    var answer by rememberSaveable(examMode) { mutableIntStateOf(-1) }
    var checked by rememberSaveable(examMode) { mutableStateOf(false) }
    var finished by rememberSaveable(examMode) { mutableStateOf(false) }
    var score by rememberSaveable(examMode) { mutableIntStateOf(0) }
    var scoredCount by rememberSaveable(examMode) { mutableIntStateOf(0) }
    val item = valid.getOrNull(index)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (examMode) "آزمون" else "تمرین", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (finished) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("نتیجهٔ آزمون", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$score از $scoredCount", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("سؤال‌های دارای کلید پاسخ در نمره محاسبه شده‌اند.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { index = 0; answer = -1; checked = false; finished = false; score = 0; scoredCount = 0 }, modifier = Modifier.fillMaxWidth()) { Text("شروع دوباره") }
        } else if (item == null) EmptyMessage("سؤال چهارگزینه‌ای تأییدشده‌ای برای این بخش دریافت نشده است.") else {
            Text("سؤال ${index + 1} از ${valid.size}", color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (index + 1f) / valid.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Text(item.title.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(cleanHtml(item.content.orEmpty()), style = MaterialTheme.typography.bodyLarge)
            val meta = runCatching { JsonParser.parseString(item.metadataJson ?: "{}").asJsonObject }.getOrNull()
            val options = readStringArray(item.optionsJson ?: meta?.get("options")?.toString())
            options.forEachIndexed { i, option ->
                Card(
                    onClick = { if (!checked) answer = i },
                    colors = CardDefaults.cardColors(containerColor = if (answer == i) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = answer == i, onClick = { if (!checked) answer = i }, enabled = !checked)
                        Text(option)
                    }
                }
            }
            val correct = meta?.get("correct_index")?.let { runCatching { it.asInt }.getOrNull() }
            if (checked && !examMode) {
                Text(if (correct != null && answer == correct) "پاسخ درست است." else if (correct != null) "پاسخ صحیح: گزینهٔ ${correct + 1}" else "کلید پاسخ برای این سؤال ثبت نشده است؛ نمره‌ای محاسبه نشد.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                meta?.get("explanation")?.let { Text("نکته: ${it.asString}") }
            }
            Button(enabled = answer >= 0 && !checked, onClick = {
                checked = true
                if (correct != null) {
                    val isCorrect = correct == answer
                    if (isCorrect) score++
                    scoredCount++
                    scope.launch {
                        val timestamp = System.currentTimeMillis()
                        val old = userDb.progressDao().get(item.uid) ?: ProgressEntity(item.uid, item.chapterId)
                        val schedule = ReviewScheduler.schedule(if (isCorrect) 4 else 0, old.intervalDays, timestamp)
                        userDb.progressDao().upsert(old.copy(
                            attempts = old.attempts + 1,
                            correct = old.correct + if (isCorrect) 1 else 0,
                            wrong = old.wrong + if (isCorrect) 0 else 1,
                            quality = if (isCorrect) 4 else 0,
                            intervalDays = schedule.intervalDays,
                            dueAt = schedule.dueAt,
                            lastResult = if (isCorrect) "correct" else "wrong",
                            updatedAt = timestamp
                        ))
                    }
                }
                if (examMode) {
                    if (index < valid.lastIndex) { index++; answer = -1; checked = false }
                    else finished = true
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (examMode) "ثبت پاسخ و ادامه" else "بررسی پاسخ") }
            if (!examMode && checked) {
                OutlinedButton(onClick = { if (index < valid.lastIndex) { index++; answer = -1; checked = false } else finished = true }, modifier = Modifier.fillMaxWidth()) { Text(if (index < valid.lastIndex) "سؤال بعدی" else "پایان تمرین") }
            }
            if (examMode) Text("در حالت آزمون، پاسخ صحیح تا پایان نمایش داده نمی‌شود.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NotesScreen(userDb: UserDatabase, speech: PersianSpeech) {
    val notes by userDb.noteDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var exportMessage by remember { mutableStateOf("") }
    val visible = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val snapshot = notes.sortedByDescending { it.updatedAt }
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("امکان باز کردن فایل مقصد وجود ندارد.")
                        output.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.appendLine("یادداشت‌های MindUnlock")
                            writer.appendLine("تعداد: " + snapshot.size)
                            writer.appendLine()
                            snapshot.forEachIndexed { index, note ->
                                writer.appendLine((index + 1).toString() + ". " + note.title)
                                writer.appendLine(note.body)
                                if (!note.chapterId.isNullOrBlank()) writer.appendLine("شناسه فصل: " + note.chapterId)
                                if (!note.itemUid.isNullOrBlank()) writer.appendLine("شناسه مطلب: " + note.itemUid)
                                writer.appendLine()
                                writer.appendLine("----------------------------------------")
                                writer.appendLine()
                            }
                        }
                    }
                }
                exportMessage = result.fold(
                    { "خروجی " + snapshot.size + " یادداشت ذخیره شد." },
                    { "ذخیرهٔ خروجی ناموفق بود: " + (it.message ?: "خطای ناشناخته") }
                )
            }
        } else {
            exportMessage = "ذخیرهٔ خروجی لغو شد."
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("یادداشت‌ها · " + notes.size, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { editingId = null; title = ""; body = ""; showDialog = true },
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.Add, null); Text("یادداشت جدید") }
            OutlinedButton(
                onClick = { exportLauncher.launch("MindUnlock-Notes.txt") },
                enabled = notes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.Download, null); Text("خروجی همه") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در یادداشت‌ها") }, singleLine = true)
        if (exportMessage.isNotBlank()) Text(exportMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (visible.isEmpty()) {
                item { EmptyMessage(if (notes.isEmpty()) "هنوز یادداشتی ذخیره نشده است." else "یادداشتی با این عبارت پیدا نشد.") }
            }
            items(visible, key = { it.id }) { note ->
                Card(onClick = { editingId = note.id; title = note.title; body = note.body; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { userDb.noteDao().delete(note.id) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                            }
                        }
                        Text(note.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (!note.chapterId.isNullOrBlank()) Text("متصل به فصل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showDialog) AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(if (editingId == null) "یادداشت جدید" else "ویرایش یادداشت") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, singleLine = true)
                PersianVoiceInputButton { spoken -> title = listOf(title, spoken).filter { it.isNotBlank() }.joinToString(" ") }
                OutlinedTextField(body, { body = it }, label = { Text("متن") }, minLines = 4)
                PersianVoiceInputButton { spoken -> body = listOf(body, spoken).filter { it.isNotBlank() }.joinToString("\n") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (body.isNotBlank()) scope.launch {
                    val old = notes.firstOrNull { it.id == editingId }
                    userDb.noteDao().upsert(
                        NoteEntity(
                            id = editingId ?: 0L,
                            itemUid = old?.itemUid,
                            chapterId = old?.chapterId,
                            title = title.ifBlank { "یادداشت" },
                            body = body.trim(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                showDialog = false
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = { showDialog = false }) { Text("انصراف") } }
    )
}

@Composable
fun LabsScreen(db: AppDatabase, userDb: UserDatabase, speech: PersianSpeech, onOpenItem: (String) -> Unit) {
    val content by db.contentItemDao().observeByTypes(listOf("lab", "command", "example", "exercise", "practice", "project")).collectAsState(initial = emptyList())
    val tasks by userDb.studyTaskDao().observeAll().collectAsState(initial = emptyList())
    val progress by userDb.progressDao().observeAll().collectAsState(initial = emptyList())
    var selected by rememberSaveable { mutableStateOf("همه") }
    var query by rememberSaveable { mutableStateOf("") }
    val verified = content.filter { it.sourceStatus == "verified" }
    val progressByUid = remember(progress) { progress.associateBy { it.itemUid } }
    val visible = verified.filter { item ->
        (selected == "همه" || kindLabel(item.type) == selected) &&
            (query.isBlank() || listOf(item.title, item.summary, item.content, item.chapterId)
                .any { it.orEmpty().contains(query, ignoreCase = true) })
    }
    val projectTasks = tasks.filter { it.taskType == "project" }
    val completed = verified.count { progressByUid[it.uid]?.completed == true }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("آزمایشگاه و تمرین عملی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("سناریوهای مرحله‌ای، تجهیزات، دستورات، خروجی مورد انتظار و عیب‌یابی را در کنار هم دنبال کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("سناریوها", verified.size.toString(), Modifier.weight(1f))
            MetricCard("تکمیل‌شده", completed.toString(), Modifier.weight(1f))
            MetricCard("پروژه‌ها", projectTasks.size.toString(), Modifier.weight(1f))
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجوی سناریو، دستور یا موضوع") }, singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("همه", "آزمایشگاه", "تمرین", "دستور", "مثال", "پروژه").forEach { label ->
                FilterChip(selected = selected == label, onClick = { selected = label }, label = { Text(label) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.uid }) { item ->
                LabScenarioCard(
                    item = item,
                    progress = progressByUid[item.uid],
                    onOpen = { onOpenItem(item.uid) },
                    onSpeak = { speech.speak(listOfNotNull(item.title, item.summary).joinToString(". ")) }
                )
            }
            items(projectTasks, key = { "project-${it.id}" }) { task ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("پروژه شخصی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        if (task.details.isNotBlank()) Text(task.details, style = MaterialTheme.typography.bodySmall)
                        Text(if (task.completed) "تکمیل‌شده" else "در حال انجام", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (visible.isEmpty() && projectTasks.isEmpty()) {
                item { EmptyMessage(if (verified.isEmpty()) "مورد عملی تأییدشده‌ای دریافت نشده است." else "با این فیلتر موردی پیدا نشد.") }
            }
        }
    }
}

@Composable
private fun LabScenarioCard(
    item: ContentItemEntity,
    progress: ProgressEntity?,
    onOpen: () -> Unit,
    onSpeak: () -> Unit
) {
    val details = remember(item.metadataJson) { LabScenarioParser.parse(item.metadataJson) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(kindLabel(item.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(if (progress?.completed == true) "تکمیل‌شده" else if ((progress?.attempts ?: 0) > 0) "در حال تمرین" else "شروع‌نشده", style = MaterialTheme.typography.labelSmall)
            }
            Text(item.title ?: "سناریوی بدون عنوان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!item.summary.isNullOrBlank()) Text(cleanHtml(item.summary), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (details.goal.isNotBlank()) LabDetailLine("هدف", details.goal)
            if (details.topology.isNotBlank()) LabDetailLine("توپولوژی", details.topology)
            if (details.prerequisites.isNotEmpty()) LabDetailLine("پیش‌نیازها", details.prerequisites.joinToString("، "))
            if (details.equipment.isNotEmpty()) LabDetailLine("تجهیزات", details.equipment.joinToString("، "))
            if (details.services.isNotEmpty()) LabDetailLine("سرویس‌ها", details.services.joinToString("، "))
            if (details.commands.isNotEmpty()) LabDetailLine("مراحل / دستورات", details.commands.take(3).joinToString("  •  "))
            if (details.expectedOutput.isNotEmpty()) LabDetailLine("خروجی مورد انتظار", details.expectedOutput.joinToString("؛ "))
            if (details.troubleshooting.isNotEmpty()) LabDetailLine("عیب‌یابی", details.troubleshooting.joinToString("؛ "))
            if (details.questions.isNotEmpty()) LabDetailLine("سؤالات کنترل", details.questions.joinToString("؛ "))
            val metaLine = listOfNotNull(
                details.difficulty.takeIf(String::isNotBlank),
                details.estimatedMinutes?.let { "$it دقیقه" }
            ).joinToString(" · ")
            if (metaLine.isNotBlank()) Text(metaLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!details.hasStructuredDetails) Text("جزئیات سناریو در متن کامل مطلب قرار دارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSpeak, modifier = Modifier.weight(1f)) { Text("خواندن راهنمای آزمایشگاه") }
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("شروع سناریو") }
            }
        }
    }
}

@Composable
private fun LabDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}


@Composable
fun PlannerScreen(userDb: UserDatabase, speech: PersianSpeech) {
    val tasks by userDb.studyTaskDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var whenText by rememberSaveable { mutableStateOf(PersianDate.formatInput(LocalDateTime.now().plusHours(2))) }
    var dateError by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("task") }
    var reminder by rememberSaveable { mutableStateOf(true) }
    var showJalaliPicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("برنامهٔ روزانه، تکلیف و پروژهٔ شخصی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") }, singleLine = true)
        PersianVoiceInputButton { spoken -> title = listOf(title, spoken).filter { it.isNotBlank() }.joinToString(" ") }
        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("توضیحات") }, minLines = 2)
        PersianVoiceInputButton { spoken -> details = listOf(details, spoken).filter { it.isNotBlank() }.joinToString("\n") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "task", onClick = { type = "task" }, label = { Text("کار مطالعاتی") })
            FilterChip(selected = type == "project", onClick = { type = "project" }, label = { Text("پروژه") })
        }
        OutlinedTextField(whenText, { whenText = it; dateError = "" }, Modifier.fillMaxWidth(), label = { Text("تاریخ و ساعت شمسی: ۱۴۰۵/۰۸/۲۱ ۰۹:۳۰ (میلادی ISO نیز پذیرفته می‌شود)") }, isError = dateError.isNotBlank(), singleLine = true)
        OutlinedButton(onClick = { showJalaliPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("انتخاب تاریخ از تقویم شمسی")
        }
        if (showJalaliPicker) {
            val parsedMillis = PersianDate.parseDateTime(whenText)
            val initialDate = parsedMillis?.let {
                PersianDate.fromGregorian(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
            } ?: PersianDate.fromGregorian(LocalDate.now())
            PersianJalaliDatePicker(
                initialDate = initialDate,
                onDismiss = { showJalaliPicker = false },
                onSelect = { selected ->
                    val currentTime = parsedMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
                    } ?: java.time.LocalTime.now()
                    val selectedDate = PersianDate.toGregorian(selected)
                    whenText = PersianDate.formatInput(LocalDateTime.of(selectedDate, currentTime))
                    dateError = ""
                    showJalaliPicker = false
                }
            )
        }
        if (dateError.isNotBlank()) Text(dateError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = reminder, onCheckedChange = { reminder = it }); Text("یادآور اعلان‌دار") }
        Button(enabled = title.isNotBlank(), onClick = {
            val due = PersianDate.parseDateTime(whenText)
            if (due == null) {
                dateError = "تاریخ یا ساعت معتبر نیست؛ نمونه: ۱۴۰۵/۰۸/۲۱ ۰۹:۳۰"
            } else {
                scope.launch {
                    val id = userDb.studyTaskDao().upsert(StudyTaskEntity(title = title.trim(), details = details.trim(), taskType = type, dueAt = due, remindAt = if (reminder) due else null))
                    if (reminder) scheduleReminder(context, id, title.trim(), due)
                    title = ""; details = ""; dateError = ""
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("افزودن به برنامه") }
        HorizontalDivider()
        Text("برنامه‌های ثبت‌شده (${tasks.size})", style = MaterialTheme.typography.titleMedium)
        tasks.forEach { task ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text(if (task.taskType == "project") "پروژه" else "کار مطالعاتی", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        if (task.dueAt != null) Text(PersianDate.format(task.dueAt), style = MaterialTheme.typography.bodySmall)
                        if (task.details.isNotBlank()) Text(task.details, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(task.completed, onCheckedChange = { checked -> scope.launch { userDb.studyTaskDao().upsert(task.copy(completed = checked)) } })
                    IconButton(onClick = { scope.launch { userDb.studyTaskDao().delete(task.id) } }) { Icon(Icons.Default.Delete, null) }
                }
            }
        }
    }
}


@Composable
fun PersianJalaliDatePicker(
    initialDate: JalaliDate,
    onDismiss: () -> Unit,
    onSelect: (JalaliDate) -> Unit
) {
    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    var year by rememberSaveable(initialDate.year) { mutableIntStateOf(initialDate.year) }
    var month by rememberSaveable(initialDate.month) { mutableIntStateOf(initialDate.month) }
    val firstCell = remember(year, month) { PersianDate.firstWeekdayOffset(year, month) }
    val monthDays = remember(year, month) { PersianDate.daysInMonth(year, month) }
    val cellCount = ((firstCell + monthDays + 6) / 7) * 7

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                    }) { Text("ماه قبل") }
                    Text("${monthNames[month - 1]} ${PersianDate.toPersianDigits(year.toString())}", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                    }) { Text("ماه بعد") }
                }
                Row {
                    weekDays.forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(day, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                for (week in 0 until (cellCount / 7)) {
                    Row {
                        for (column in 0..6) {
                            val day = week * 7 + column - firstCell + 1
                            Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                                if (day in 1..monthDays) {
                                    Box(
                                        Modifier.fillMaxWidth().height(40.dp).clickable {
                                            onSelect(JalaliDate(year, month, day))
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(PersianDate.toPersianDigits(day.toString()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
fun ProgressScreen(db: AppDatabase, userDb: UserDatabase, speech: PersianSpeech) {
    val items by db.contentItemDao().observeAll().collectAsState(initial = emptyList())
    val chapters by db.chapterDao().observeAll().collectAsState(initial = emptyList())
    val progress by userDb.progressDao().observeAll().collectAsState(initial = emptyList())
    val tasks by userDb.studyTaskDao().observeAll().collectAsState(initial = emptyList())
    val sessions by userDb.learningSessionDao().observeAll().collectAsState(initial = emptyList())
    val snapshot = remember(chapters, items, progress, tasks, sessions) {
        LearningAnalytics.build(chapters, items, progress, tasks, sessions, System.currentTimeMillis())
    }
    val completion = if (snapshot.totalItems == 0) 0f else snapshot.completedItems.toFloat() / snapshot.totalItems
    val maxActivity = snapshot.recentActivity.maxOfOrNull { it.sessionMinutes }?.coerceAtLeast(1L) ?: 1L

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("گزارش پیشرفت و تحلیل یادگیری", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("آمار از محتوای تأییدشده و پیشرفت آموزشی همین دستگاه محاسبه می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("پیشرفت محتوای آموزشی", fontWeight = FontWeight.SemiBold)
                    Text("${if (snapshot.totalItems == 0) 0 else (completion * 100).toInt()}٪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth())
                Text("${snapshot.completedItems} تکمیل‌شده از ${snapshot.totalItems} مطلب تأییدشده", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("عملکرد تمرین و مرور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("کل تلاش‌ها", snapshot.attempts.toString(), Modifier.weight(1f))
            MetricCard("دقت پاسخ‌ها", "${snapshot.accuracyPercent}٪", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("پاسخ درست", snapshot.correct.toString(), Modifier.weight(1f))
            MetricCard("پاسخ اشتباه", snapshot.wrong.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("مرورهای سررسیدشده", snapshot.scheduledReviewsDue.toString(), Modifier.weight(1f))
            MetricCard("موارد نیازمند تقویت", snapshot.weakItems.toString(), Modifier.weight(1f))
        }
        Text("زمان ثبت‌شدهٔ یادگیری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("زمان کل", "${snapshot.totalStudyMinutes} دقیقه", Modifier.weight(1f))
            MetricCard("جلسه‌های پایان‌یافته", snapshot.completedSessions.toString(), Modifier.weight(1f))
        }
        MetricCard("میانگین هر جلسه", "${snapshot.averageSessionMinutes} دقیقه", Modifier.fillMaxWidth())
        Text("زمان‌ها با شروع و پایان دستی جلسه در موتور یادگیری محاسبه می‌شوند؛ زمان بیکاری یا توقف دستگاه به‌صورت خودکار تشخیص داده نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("فعالیت هفت روز اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                snapshot.recentActivity.forEach { day ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.width(76.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(day.label, style = MaterialTheme.typography.labelSmall)
                            Text(day.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { day.sessionMinutes.toFloat() / maxActivity.toFloat() },
                            modifier = Modifier.weight(1f)
                        )
                        Text("${day.sessionMinutes} د", modifier = Modifier.size(width = 38.dp, height = 24.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("نمودار بر اساس مدت جلسه‌هایی است که در همان روز شروع و به‌صورت دستی پایان داده شده‌اند. تعداد آیتم‌های آموزشی به‌روزشده در همان روز: ${snapshot.recentActivity.sumOf { it.updatedItems }} مورد.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("وضعیت برنامه‌ریزی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("کارهای باز", snapshot.openTasks.toString(), Modifier.weight(1f))
            MetricCard("کارهای عقب‌افتاده", snapshot.overdueTasks.toString(), Modifier.weight(1f))
            MetricCard("کارهای تکمیل‌شده", snapshot.completedTasks.toString(), Modifier.weight(1f))
        }
        Text("تحلیل فصل‌ها و نقاط نیازمند تمرین", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (snapshot.chapters.isEmpty()) {
            EmptyMessage("هنوز محتوای آموزشی تأییدشده‌ای برای تحلیل فصل‌ها وجود ندارد.")
        } else {
            snapshot.chapters.forEach { chapter ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(chapter.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (chapter.needsPractice) Text("نیازمند تمرین", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        val chapterCompletion = if (chapter.totalItems == 0) 0f else chapter.completedItems.toFloat() / chapter.totalItems
                        LinearProgressIndicator(progress = { chapterCompletion }, modifier = Modifier.fillMaxWidth())
                        Text("${chapter.completedItems} از ${chapter.totalItems} مطلب · دقت ${chapter.accuracyPercent}٪ · ${chapter.attempts} تلاش", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (chapter.needsPractice) Text("پیشنهاد: این فصل را با تمرین و مرور فعال دوباره بررسی کن.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text("حریم خصوصی: این گزارش فقط از محتوای آموزشی، پیشرفت و کارهای برنامه‌ریز استفاده می‌کند؛ متن دفتر روزانه و صندوق ورودی وارد گزارش نمی‌شود و داده‌ای به سرور ارسال نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BookmarksScreen(db: AppDatabase, userDb: UserDatabase, speech: PersianSpeech, onOpenItem: (String) -> Unit) {
    val bookmarks by userDb.bookmarkDao().observeAll().collectAsState(initial = emptyList())
    val bookmarkUids = remember(bookmarks) { bookmarks.map { it.itemUid }.distinct() }
    val saved by db.contentItemDao().observeByUids(bookmarkUids).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    
    val visible = saved.filter { item ->
        query.isBlank() ||
            item.title.orEmpty().contains(query, true) ||
            item.summary.orEmpty().contains(query, true) ||
            item.content.orEmpty().contains(query, true) ||
            item.chapterId.contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("نشانک‌ها · " + bookmarks.size, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجو در نشانک‌ها") }, singleLine = true)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) {
                item { EmptyMessage(if (saved.isEmpty()) "هنوز نشانکی ذخیره نشده است." else "نشانکی با این عبارت پیدا نشد.") }
            }
            items(visible, key = { it.uid }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { onOpenItem(item.uid) },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.title ?: "بدون عنوان", fontWeight = FontWeight.SemiBold)
                            Text(kindLabel(item.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (!item.summary.isNullOrBlank()) {
                                Text(cleanHtml(item.summary), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { pendingRemoval = item.uid }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف نشانک")
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { uid ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("حذف نشانک") },
            text = { Text("این مطلب از فهرست نشانک‌ها حذف شود؟ محتوای اصلی و یادداشت‌های آن پاک نمی‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { userDb.bookmarkDao().delete(uid) }
                    pendingRemoval = null
                }) { Text("حذف نشانک") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("انصراف") } }
        )
    }
}

@Composable
fun SettingsScreen(
    speech: PersianSpeech,
    preferences: android.content.SharedPreferences,
    theme: String,
    onThemeChange: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLockApp: () -> Unit,
    onOpenSync: () -> Unit
) {
    var crashReportPreview by remember { mutableStateOf("") }
    val diagnosticContext = LocalContext.current
    val context = LocalContext.current
    val voices = speech.voiceOptions
    var speed by remember { mutableStateOf(preferences.getFloat("speech_rate", 1f).coerceIn(0.75f, 1.35f)) }
    var voiceName by remember { mutableStateOf(preferences.getString("speech_voice", "gyro") ?: "gyro") }
    var fontScale by remember { mutableStateOf(preferences.getFloat("font_scale", 1f).coerceIn(0.85f, 1.35f)) }
    var readingLineSpacing by remember { mutableStateOf(preferences.getFloat("reading_line_spacing", 1.45f).coerceIn(1.1f, 2.2f)) }
    var readingHorizontalMargin by remember { mutableStateOf(preferences.getFloat("reading_horizontal_margin", 16f).coerceIn(8f, 32f)) }
    var keepScreenOnWhileReading by remember { mutableStateOf(preferences.getBoolean("keep_screen_on_while_reading", true)) }
    var newAppPassword by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf("") }
    var settingsMessage by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }
    var asrModelPresent by remember { mutableStateOf(OfflinePersianSpeech.isInstalled(context)) }
    var showDeleteAsrDialog by remember { mutableStateOf(false) }

    fun folderSize(file: java.io.File): Long =
        if (!file.exists()) 0L else if (file.isFile) file.length()
        else file.listFiles()?.sumOf { folderSize(it) } ?: 0L

    fun privateDataSize(): Long {
        val databaseFolder = context.getDatabasePath("mindunlock.db").parentFile
        return folderSize(context.filesDir) + folderSize(databaseFolder ?: context.filesDir)
    }

    var cacheBytes by remember { mutableStateOf(folderSize(context.cacheDir)) }
    var storageBytes by remember { mutableStateOf(privateDataSize()) }
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "نامشخص"
        }.getOrDefault("نامشخص")
    }
    val selectedVoice = voiceName.ifBlank { "gyro" }
    val selectedVoiceLabel = voices.firstOrNull { it.first == selectedVoice }?.second ?: "نیما · مرد · Gyro"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("مرکز تنظیمات MindUnlock", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("تنظیمات ظاهری، مطالعه، گفتار فارسی، امنیت، فضای ذخیره‌سازی و اتصال را از این بخش مدیریت کن.", style = MaterialTheme.typography.bodyMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۱. ظاهر و نمایش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("قالب رنگی برنامه")
                listOf("dark" to "تیره", "light" to "روشن", "sepia" to "سپیا · مناسب مطالعه").forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onThemeChange(option.first) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = theme == option.first, onClick = { onThemeChange(option.first) })
                        Text(option.second)
                    }
                }
                HorizontalDivider()
                Text("اندازه نوشته‌های برنامه: " + String.format(java.util.Locale.US, "%.0f", fontScale * 100f) + "٪")
                Slider(
                    value = fontScale,
                    onValueChange = {
                        fontScale = it
                        onFontScaleChange(it)
                    },
                    valueRange = 0.85f..1.35f
                )
                Text("این گزینه اندازه متن بخش‌های مختلف برنامه را تغییر می‌دهد.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۲. تنظیمات مطالعه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("فاصله خطوط: " + String.format(java.util.Locale.US, "%.2f", readingLineSpacing) + " برابر")
                Slider(
                    value = readingLineSpacing,
                    onValueChange = {
                        readingLineSpacing = it
                        preferences.edit().putFloat("reading_line_spacing", it).apply()
                    },
                    valueRange = 1.1f..2.2f
                )
                Text("حاشیه افقی متن: " + String.format(java.util.Locale.US, "%.0f", readingHorizontalMargin) + " dp")
                Slider(
                    value = readingHorizontalMargin,
                    onValueChange = {
                        readingHorizontalMargin = it
                        preferences.edit().putFloat("reading_horizontal_margin", it).apply()
                    },
                    valueRange = 8f..32f
                )
                OutlinedButton(
                    onClick = {
                        keepScreenOnWhileReading = !keepScreenOnWhileReading
                        preferences.edit().putBoolean("keep_screen_on_while_reading", keepScreenOnWhileReading).apply()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (keepScreenOnWhileReading) "روشن ماندن صفحه هنگام مطالعه: فعال" else "روشن ماندن صفحه هنگام مطالعه: غیرفعال")
                }
                Text("تنظیمات فاصله خطوط، حاشیه و روشن‌ماندن صفحه بلافاصله ذخیره می‌شوند.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۳. موتور گفتار فارسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("گوینده انتخاب‌شده: " + selectedVoiceLabel, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (speech.ready.value) "وضعیت موتور: آماده"
                    else if (speech.loading.value) "وضعیت موتور: در حال دریافت یا بارگذاری"
                    else "وضعیت موتور: آماده نیست",
                    color = if (speech.ready.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("سرعت خواندن: " + String.format(java.util.Locale.US, "%.2f", speed) + " برابر")
                Slider(
                    value = speed,
                    onValueChange = {
                        speed = it
                        preferences.edit().putFloat("speech_rate", it).apply()
                    },
                    valueRange = 0.75f..1.35f
                )
                Text("مدل‌های گفتار فارسی", fontWeight = FontWeight.SemiBold)
                voices.forEach { voice ->
                    val isSelected = selectedVoice == voice.first
                    Card(
                        onClick = {
                            voiceName = voice.first
                            preferences.edit().putString("speech_voice", voice.first).apply()
                            speech.selectVoice(voice.first)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    voiceName = voice.first
                                    preferences.edit().putString("speech_voice", voice.first).apply()
                                    speech.selectVoice(voice.first)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(voice.second, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (voice.first == "haaniye") "صدای زن · مدل Mimic3" else "صدای مرد · مدل Piper",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    if (speech.isVoiceDownloaded(voice.first)) "مدل نصب شده روی دستگاه" else "مدل دریافت نشده · با انتخاب، دانلود می‌شود",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                Text(speech.status.value, style = MaterialTheme.typography.bodySmall)
                if (speech.loading.value) {
                    LinearProgressIndicator(
                        progress = speech.downloadProgress.value,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "دریافت‌شده: " + String.format(java.util.Locale.US, "%.1f", speech.downloadedBytes.value / (1024f * 1024f)) + " مگابایت",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                speech.error.value?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        speech.speak(
                            "سلام. این یک آزمایش صدای فارسی در برنامه مایندآنلاک است. کیفیت و سرعت گفتار را بررسی کن.",
                            speed,
                            selectedVoice
                        )
                    },
                    enabled = !speech.loading.value,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("آزمایش صدای انتخاب‌شده") }
                OutlinedButton(onClick = { speech.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text("توقف پخش صدا")
                }
                OutlinedButton(onClick = { showDeleteVoiceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("حذف مدل صوتی انتخاب‌شده از حافظه")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تشخیص گفتار فارسی آفلاین", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (asrModelPresent) "مدل Shenava روی همین گوشی نصب است؛ تبدیل گفتار بدون اینترنت و بدون ارسال صدا انجام می‌شود."
                    else "مدل فارسی هنوز دریافت نشده است. با اولین استفاده از میکروفون، حدود ۱۱۷ مگابایت دریافت می‌شود؛ پس از آن تشخیص گفتار کاملاً محلی است.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("موتور: sherpa-onnx · مدل فارسی Shenava Rizeh · مجوز Apache 2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (asrModelPresent) {
                    OutlinedButton(onClick = { showDeleteAsrDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("حذف مدل تشخیص گفتار و آزادسازی فضا")
                    }
                } else {
                    Text("برای دریافت مدل، از دکمه «گفتار آفلاین» کنار یکی از فیلدهای متنی استفاده کن.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (showDeleteAsrDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAsrDialog = false },
                title = { Text("حذف مدل گفتار فارسی؟") },
                text = { Text("فایل مدل حدود ۱۱۷ مگابایت است. با حذف آن، تشخیص گفتار آفلاین تا دریافت دوباره مدل غیرفعال می‌شود؛ نوشته‌ها و داده‌های برنامه دست‌نخورده می‌مانند.") },
                confirmButton = {
                    TextButton(onClick = {
                        OfflinePersianSpeech.deleteModel(context)
                        asrModelPresent = OfflinePersianSpeech.isInstalled(context)
                        storageBytes = privateDataSize()
                        showDeleteAsrDialog = false
                    }) { Text("حذف مدل") }
                },
                dismissButton = { TextButton(onClick = { showDeleteAsrDialog = false }) { Text("انصراف") } }
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۴. امنیت و قفل برنامه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (com.mindunlock.app.AppLock.isConfigured(preferences)) "قفل برنامه فعال است؛ رمز به‌صورت هش‌شده نگهداری می‌شود."
                    else "هنوز رمز قفل تنظیم نشده است.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = newAppPassword,
                    onValueChange = {
                        newAppPassword = it
                        passwordMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رمز جدید (حداقل ۴ کاراکتر)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Button(
                    enabled = newAppPassword.length >= 4,
                    onClick = {
                        runCatching {
                            com.mindunlock.app.AppLock.setPassword(preferences, newAppPassword.toCharArray())
                        }.onSuccess {
                            newAppPassword = ""
                            passwordMessage = "رمز قفل با موفقیت تغییر کرد."
                        }.onFailure {
                            passwordMessage = it.message ?: "تغییر رمز ناموفق بود."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ذخیره رمز جدید") }
                if (passwordMessage.isNotBlank()) {
                    Text(passwordMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onLockApp, modifier = Modifier.fillMaxWidth()) {
                    Text("قفل‌کردن برنامه اکنون")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۵. فضای ذخیره‌سازی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "داده‌های خصوصی برنامه: " + String.format(java.util.Locale.US, "%.1f", storageBytes / (1024.0 * 1024.0)) + " مگابایت"
                )
                Text(
                    "حافظه موقت: " + String.format(java.util.Locale.US, "%.1f", cacheBytes / (1024.0 * 1024.0)) + " مگابایت"
                )
                Text("پاک‌سازی حافظه موقت، یادداشت‌ها، پیشرفت، نشانک‌ها و مدل‌های صوتی نصب‌شده را حذف نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            cacheBytes = folderSize(context.cacheDir)
                            storageBytes = privateDataSize()
                            settingsMessage = "حافظه موقت پاک‌سازی شد؛ اطلاعات مطالعه حفظ شده‌اند."
                        }.onFailure {
                            settingsMessage = "پاک‌سازی ناموفق بود: " + (it.message ?: "خطای ناشناخته")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("پاک‌سازی امن حافظه موقت") }
                TextButton(
                    onClick = {
                        cacheBytes = folderSize(context.cacheDir)
                        storageBytes = privateDataSize()
                        settingsMessage = "اطلاعات فضای ذخیره‌سازی به‌روزرسانی شد."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("به‌روزرسانی اطلاعات حافظه") }
                if (settingsMessage.isNotBlank()) {
                    Text(settingsMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("۶. اتصال، همگام‌سازی و حریم خصوصی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("این نسخه محتوای آموزشی را از سرور ویندوز دریافت می‌کند. یادداشت‌ها، نشانک‌ها و پیشرفت کاربر در پایگاه‌داده خصوصی همین دستگاه نگهداری می‌شوند و در قرارداد فعلی به سرور ارسال نمی‌شوند.", style = MaterialTheme.typography.bodySmall)
                Text("اتصال HTTP را فقط در شبکه محلی مورداعتماد استفاده کن و پورت سرور ویندوز را در اینترنت عمومی منتشر نکن.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenSync, modifier = Modifier.fillMaxWidth()) {
                    Text("بازکردن تنظیمات اتصال به سرور ویندوز")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("گزارش آخرین کرش", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("خطاهای برنامه به‌صورت محلی ذخیره می‌شوند؛ گزارش شامل زمان، نسخه اندروید، دستگاه و Stack trace است.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { crashReportPreview = CrashDiagnostics.readLastReport(diagnosticContext) }, modifier = Modifier.weight(1f)) { Text("بررسی گزارش ذخیره‌شده") }
                    OutlinedButton(onClick = { CrashDiagnostics.shareLastReport(diagnosticContext) }, modifier = Modifier.weight(1f)) { Text("ارسال / اشتراک گزارش") }
                }
                if (crashReportPreview.isNotBlank()) {
                    Text(crashReportPreview.takeLast(5000), style = MaterialTheme.typography.bodySmall, maxLines = 14, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("۷. درباره برنامه و عیب‌یابی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("MindUnlock · نسخه " + appVersion)
                Text("Android " + android.os.Build.VERSION.RELEASE + " · API " + android.os.Build.VERSION.SDK_INT, style = MaterialTheme.typography.bodySmall)
                Text("شناسه بسته: " + context.packageName, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        val report = "MindUnlock " + appVersion +
                            "\nAndroid " + android.os.Build.VERSION.RELEASE +
                            " (API " + android.os.Build.VERSION.SDK_INT + ")" +
                            "\nPackage: " + context.packageName +
                            "\nTheme: " + theme +
                            "\nFont scale: " + fontScale +
                            "\nSpeech voice: " + selectedVoice +
                            "\nSpeech status: " + speech.status.value +
                            "\nCache bytes: " + cacheBytes +
                            "\nPrivate data bytes: " + storageBytes
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MindUnlock diagnostics", report))
                            settingsMessage = "گزارش فنی در کلیپ‌بورد کپی شد."
                        } else {
                            settingsMessage = "دسترسی به کلیپ‌بورد ممکن نشد."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("کپی گزارش فنی برای پشتیبانی") }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:" + context.packageName)
                                }
                            )
                        }.onFailure {
                            settingsMessage = "بازکردن تنظیمات اندروید ناموفق بود."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("بازکردن تنظیمات سیستمی برنامه") }
                OutlinedButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("بازنشانی تنظیمات برنامه")
                }
            }
        }
    }

    if (showDeleteVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteVoiceDialog = false },
            title = { Text("حذف مدل گفتار؟") },
            text = { Text("فقط فایل‌های مدل گوینده انتخاب‌شده حذف می‌شوند. برای استفاده دوباره باید آن مدل را مجدداً دریافت کنی.") },
            confirmButton = {
                Button(onClick = {
                    speech.deleteDownloadedVoice(selectedVoice)
                    showDeleteVoiceDialog = false
                }) { Text("حذف مدل") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVoiceDialog = false }) { Text("انصراف") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("بازنشانی همه تنظیمات؟") },
            text = { Text("قالب، اندازه متن، فاصله خطوط، حاشیه مطالعه، روشن‌ماندن صفحه، سرعت و گوینده گفتار به حالت پیش‌فرض بازمی‌گردند. رمز قفل، یادداشت‌ها، نشانک‌ها، پیشرفت و فایل‌های صوتی حذف نمی‌شوند.") },
            confirmButton = {
                Button(onClick = {
                    onThemeChange("dark")
                    fontScale = 1f
                    onFontScaleChange(1f)
                    readingLineSpacing = 1.45f
                    readingHorizontalMargin = 16f
                    keepScreenOnWhileReading = true
                    speed = 1f
                    voiceName = "gyro"
                    preferences.edit()
                        .putString("theme", "dark")
                        .putFloat("font_scale", 1f)
                        .putFloat("reading_line_spacing", 1.45f)
                        .putFloat("reading_horizontal_margin", 16f)
                        .putBoolean("keep_screen_on_while_reading", true)
                        .putFloat("speech_rate", 1f)
                        .putString("speech_voice", "gyro")
                        .apply()
                    speech.stop()
                    settingsMessage = "تنظیمات بازنشانی شد؛ اطلاعات شخصی و رمز قفل حفظ شده‌اند."
                    showResetDialog = false
                }) { Text("تأیید بازنشانی") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("انصراف") }
            }
        )
    }
}


@Composable
fun SyncScreen(repository: SyncRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(repository.savedHost()) }
    var port by remember { mutableStateOf(repository.savedPort().toString()) }
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("") }
    var manifest by remember { mutableStateOf<List<ManifestChapter>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var syncJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var message by remember { mutableStateOf("آدرس IP رایانه‌ای را وارد کن که برنامهٔ ویندوز روی آن اجراست.") }
    var progress by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var diagnostic by remember { mutableStateOf<com.mindunlock.app.data.repository.ConnectionDiagnostics?>(null) }
    var diagnosticBusy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("اتصال به سرور ویندوز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("IP یا نام میزبان ویندوز") }, singleLine = true, placeholder = { Text("مثلاً 192.168.1.10") })
        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("پورت") }, singleLine = true)
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("نام کاربری ویندوز") }, singleLine = true)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("رمز برنامهٔ ویندوز") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Text("گوشی و رایانه باید در یک شبکهٔ محلی باشند. پورت پیش‌فرض ۸۲ است.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            enabled = !busy && !diagnosticBusy && host.isNotBlank() && ((port.toIntOrNull() ?: 0) in 1..65535),
            onClick = {
                diagnosticBusy = true; diagnostic = null
                scope.launch {
                    diagnostic = repository.diagnoseConnection(host, port.toIntOrNull() ?: 82, username, password)
                    diagnosticBusy = false
                }
            }, modifier = Modifier.fillMaxWidth()
        ) { if (diagnosticBusy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("عیب‌یابی مرحله‌ای اتصال") }
        diagnostic?.let { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("گزارش عیب‌یابی", fontWeight = FontWeight.Bold)
                    Text("۱. دسترسی IP و پورت (TCP): ${if (d.tcpReachable) "موفق" else "ناموفق"}")
                    Text("۲. پاسخ سرویس HTTP: ${if (d.httpReachable) "موفق" else "ناموفق"}")
                    Text("۳. احراز هویت: ${if (d.authenticated) "موفق" else "ناموفق / انجام‌نشده"}")
                    Text("۴. دریافت فهرست محتوا: ${if (d.manifestAvailable) "موفق" else "ناموفق / انجام‌نشده"}")
                    Text(d.message, color = if (d.manifestAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(enabled = !busy && host.isNotBlank() && ((port.toIntOrNull() ?: 0) in 1..65535), onClick = {
            busy = true; message = "در حال اتصال و دریافت فهرست فصل‌ها…"; manifest = emptyList(); selected.clear()
            scope.launch {
                runCatching { repository.fetchManifest(host, port.toIntOrNull() ?: 82, username, password) }
                    .onSuccess { data -> manifest = data; selected.addAll(data.map { it.id }); message = "${data.size} فصل پیدا شد؛ فصل‌های موردنیاز را تیک بزن و دانلود کن." }
                    .onFailure { message = it.message ?: "اتصال ناموفق بود." }
                busy = false
            }
        }, modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("بررسی اتصال و نمایش محتوا") }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = if (message.contains("ناموفق") || message.contains("لازم") || message.contains("رمز")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (manifest.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("انتخاب فصل‌ها (${selected.size}/${manifest.size})", fontWeight = FontWeight.Bold)
                TextButton(onClick = { if (selected.size == manifest.size) selected.clear() else { selected.clear(); selected.addAll(manifest.map { it.id }) } }) { Text(if (selected.size == manifest.size) "لغو همه" else "انتخاب همه") }
            }
            manifest.forEach { ch ->
                Card {
                    Row(Modifier.fillMaxWidth().clickable {
                        if (selected.contains(ch.id)) selected.remove(ch.id) else selected.add(ch.id)
                    }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected.contains(ch.id), onCheckedChange = { checked -> if (checked) selected.add(ch.id) else selected.remove(ch.id) })
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, fontWeight = FontWeight.SemiBold)
                            Text("${ch.groupName ?: "بدون گروه"} · ${ch.itemCount} مطلب", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Button(enabled = !busy && selected.isNotEmpty(), onClick = {
                busy = true; progress = 0f; progressText = "شروع دانلود…"
                val chosen = manifest.filter { selected.contains(it.id) }
                syncJob = scope.launch {
                    val result = runCatching {
                        repository.syncSelected(host, port.toIntOrNull() ?: 82, chosen) { current, total, title ->
                            progress = if (total == 0) 1f else current.toFloat() / total
                            progressText = "$current از $total · $title"
                        }
                    }
                    result.onSuccess { r ->
                        message = "دانلود شد: ${r.downloaded} فصل · بدون تغییر: ${r.skipped} فصل · خطا: ${r.failures.size}"
                        if (r.failures.isNotEmpty()) progressText = r.failures.joinToString("\n")
                        password = ""
                    }.onFailure { message = it.message ?: "دانلود ناموفق بود." }
                    busy = false
                    syncJob = null
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("دانلود فصل‌های انتخاب‌شده") }
            if (busy || progress > 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(progressText, style = MaterialTheme.typography.bodySmall)
            }
            if (busy && syncJob != null) OutlinedButton(onClick = { syncJob?.cancel(); message = "درخواست لغو همگام‌سازی ارسال شد." }, modifier = Modifier.fillMaxWidth()) { Text("لغو همگام‌سازی") }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("رفتن به کتابخانهٔ آفلاین") }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
}

private fun kindLabel(kind: String?): String = when (kind?.lowercase(Locale.ROOT)) {
    "lesson" -> "درس"
    "question" -> "سؤال"
    "flashcard" -> "فلش‌کارت"
    "command" -> "دستور"
    "lab", "experiment" -> "آزمایشگاه"
    "exercise", "practice" -> "تمرین"
    "exam", "quiz" -> "آزمون"
    "project" -> "پروژه"
    "example" -> "مثال"
    "note" -> "یادداشت"
    "asset" -> "رسانه"
    else -> kind ?: "مطلب"
}

private fun cleanHtml(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString()
    .replace(Regex("(?i)(?:https?://|file:/{0,3})?[^\\s<>\"]+\\.(?:png|jpe?g|webp|gif)(?:\\?[^\\s<>\"]*)?"), "")
    .replace(Regex("(?m)^\\s*(?:مسیر تصویر|image path)\\s*:?\\s*$"), "")
    .trim()

@Composable
private fun LocalCachedImage(path: String) {
    val bitmap = produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }.value
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "تصویر آموزشی", modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
    }
}

private fun extractImageReferences(source: String): Set<String> {
    val values = linkedSetOf<String>()
    val patterns = listOf(
        Regex("(?i)(?:src|image|image_path|media|url|path)\\s*[\"']?\\s*[:=]\\s*[\"']([^\"']+)") ,
        Regex("!\\[[^]]*]\\(([^)]+)\\)"),
        Regex("(?i)(?:/|[a-z0-9_.-]+/)[a-z0-9_./% -]+\\.(?:png|jpe?g|webp|gif)(?:\\?[^\\s\"']*)?")
    )
    patterns.forEach { regex -> regex.findAll(source).forEach { match ->
        val raw = (1..match.groupValues.lastIndex).asSequence().map { match.groupValues[it] }.firstOrNull { it.isNotBlank() } ?: match.value
        val normalized = runCatching { AndroidUri.decode(raw.trim().trim('\"', '\'', '(', ')')) }.getOrDefault(raw.trim())
            .substringBefore('?').substringBefore('#').replace('\\', '/')
        if (normalized.contains('.') && normalized.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "webp", "gif")) values += normalized.trimStart('/')
    } }
    return values
}

private fun readStringArray(value: String?): List<String> = runCatching {
    val json = JsonParser.parseString(value ?: "[]").asJsonArray
    json.mapNotNull { if (it.isJsonNull) null else runCatching { it.asString }.getOrNull() }
}.getOrDefault(emptyList())

private fun scheduleReminder(context: Context, id: Long, title: String, remindAt: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, StudyReminderReceiver::class.java).putExtra("title", title).putExtra("task_id", id)
    val pending = PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(remindAt, System.currentTimeMillis() + 5_000L), pending)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        context is Activity) {
        ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 902)
    }
}


@Composable
private fun SafeLocalCachedImage(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.isFile || file.length() <= 0L) {
                    null
                } else {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        null
                    } else {
                        var sample = 1
                        while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1920) {
                            sample *= 2
                        }
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("MindUnlockImage", "Could not decode cached lesson image: " + path, error)
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "تصویر مطلب",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
    } else {
        Text("تصویر این مطلب قابل نمایش نیست.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
