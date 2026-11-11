package com.mindunlock.app
import androidx.compose.foundation.layout.fillMaxWidth

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mindunlock.app.data.local.MindDumpEntity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.mindunlock.app.data.repository.SyncRepository
import com.mindunlock.app.data.local.UserDatabase
import com.mindunlock.app.data.local.LegacyDatabaseMigrator
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.withContext
import com.mindunlock.app.ui.theme.MindUnlockTheme

class MainActivity : ComponentActivity() {
    private lateinit var speech: PersianSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.install(this)
        enableEdgeToEdge()
        speech = PersianSpeech(this)
        val app = application as MindUnlockApp
        val preferences = getSharedPreferences("mindunlock_preferences", MODE_PRIVATE)
        val userDatabase = UserDatabase.getInstance(this)
        
        setContent {
            var theme by remember { mutableStateOf(preferences.getString("theme", "dark") ?: "dark") }
            var fontScale by remember { mutableStateOf(preferences.getFloat("font_scale", 1f)) }
            val density = LocalDensity.current
            var unlocked by rememberSaveable { mutableStateOf(false) }
            var lockPassword by rememberSaveable { mutableStateOf("") }
            var lockError by rememberSaveable { mutableStateOf("") }
            var startupReady by rememberSaveable { mutableStateOf(false) }
            var migrationError by rememberSaveable { mutableStateOf("") }
                      var migrationStatus by rememberSaveable { mutableStateOf("شروع آماده‌سازی...") }
            var migrationAttempt by rememberSaveable { mutableStateOf(0) }

            LaunchedEffect(migrationAttempt) {
                migrationError = ""
                try {
                    withContext(Dispatchers.IO) {
                        LegacyDatabaseMigrator.migrate(this@MainActivity, app.repository.database, userDatabase, onProgress = { phase -> runOnUiThread { migrationStatus = phase } })
                    }
                    startupReady = true
                } catch (error: Exception) {
                    migrationError = error.message ?: "انتقال اطلاعات قدیمی ناموفق بود."
                    android.util.Log.e("MindUnlockStartup", "Legacy database migration failed", error)
                }
            }
            MindUnlockTheme(theme) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides Density(density.density, density.fontScale * fontScale)
                ) {
                    if (!startupReady) {
                    Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("MindUnlock · آماده‌سازی برنامه", style = MaterialTheme.typography.headlineSmall)
                        if (migrationError.isBlank()) {
                            CircularProgressIndicator()
                            Text(migrationStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("آماده‌سازی کامل نشد: $migrationError", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { migrationAttempt += 1 }, modifier = Modifier.fillMaxWidth()) {
                                Text("تلاش مجدد")
                            }
                        }
                    }
                } else if (!unlocked) {
                        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                            Text("MindUnlock · قفل برنامه", style = MaterialTheme.typography.headlineSmall)
                            Text(if (AppLock.isConfigured(preferences)) "برای ورود رمز برنامه را وارد کن." else "برای محافظت از اطلاعات، رمز اولیه برنامه را تعیین کن (حداقل ۴ کاراکتر).")
                            OutlinedTextField(lockPassword, { lockPassword = it; lockError = "" }, Modifier.fillMaxWidth(), label = { Text("رمز برنامه") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            if (lockError.isNotBlank()) Text(lockError, color = MaterialTheme.colorScheme.error)
                            Button(onClick = {
                                try {
                                    if (AppLock.isConfigured(preferences)) {
                                        if (AppLock.verify(preferences, lockPassword.toCharArray())) { unlocked = true; lockPassword = "" }
                                        else lockError = "رمز برنامه صحیح نیست."
                                    } else {
                                        AppLock.setPassword(preferences, lockPassword.toCharArray()); unlocked = true; lockPassword = ""
                                    }
                                } catch (e: Exception) { lockError = e.message ?: "تنظیم رمز ناموفق بود." }
                            }, modifier = Modifier.fillMaxWidth(), enabled = lockPassword.length >= 4) { Text(if (AppLock.isConfigured(preferences)) "ورود به برنامه" else "فعال‌سازی قفل و ورود") }
                        }
                    } else {
                        MindUnlockRoot(
                            repository = app.repository, userDatabase = userDatabase, speech = speech, initialTheme = theme,
                            onThemeChange = { theme = it; preferences.edit().putString("theme", it).apply() },
                            onFontScaleChange = { fontScale = it; preferences.edit().putFloat("font_scale", it).apply() },
                            preferences = preferences, onLockApp = { unlocked = false }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::speech.isInitialized) speech.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindUnlockRoot(
    repository: SyncRepository,
    userDatabase: UserDatabase,
    speech: PersianSpeech,
    initialTheme: String,
    onThemeChange: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    preferences: android.content.SharedPreferences,
    onLockApp: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var previousScreen by rememberSaveable { mutableStateOf("home") }
    var selectedSubject by rememberSaveable { mutableStateOf("") }
    var selectedChapter by rememberSaveable { mutableStateOf("") }
    var selectedItem by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val voiceCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotBlank()) {
                scope.launch {
                    userDatabase.mindDumpDao().insert(MindDumpEntity(content = spokenText, category = "thought"))
                    screen = "inbox"
                }
            }
        }
    }
    val title = when (screen) {
        "subject" -> "موضوع‌ها"
        "chapter" -> "فصل‌ها"
        "reader" -> "مطالعه"
        "courses" -> "موضوع‌ها"
        "flashcards" -> "فلش‌کارت و مرور فاصله‌دار"
        "notes" -> "دفتر یادداشت"
        "inbox" -> "تخلیه ذهن / صندوق ورودی"
        "journal" -> "دفتر روزانه"
        "quiz" -> "تمرین"
        "exam" -> "آزمون"
        "review" -> "مرور امروز"
        "labs" -> "آزمایشگاه و پروژه‌ها"
        "planner" -> "برنامه‌ریز و یادآورها"
        "progress" -> "گزارش پیشرفت"
        "bookmarks" -> "نشانک‌ها"
        "settings" -> "تنظیمات"
        "sync" -> "اتصال و دریافت محتوا"
        "more" -> "ابزارهای یادگیری"
        else -> "MindUnlock"
    }
    fun back() {
        screen = when (screen) {
            "reader" -> previousScreen
            "chapter" -> "subject"
            "subject" -> "courses"
            "sync", "settings", "more", "quiz", "exam", "review", "labs", "planner", "progress", "bookmarks", "inbox", "journal" -> "home"
            else -> "home"
        }
    }
    BackHandler(enabled = screen != "home" && screen !in listOf("courses", "flashcards", "notes")) { back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    if (screen !in listOf("home", "courses", "flashcards", "notes")) {
                        IconButton(onClick = { back() }) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") }
                    }
                },
                actions = {
                    IconButton(onClick = { speech.speak("صفحه $title") }) { Icon(Icons.Default.VolumeUp, contentDescription = "خواندن عنوان صفحه") }
                    IconButton(onClick = { speech.stop() }) { Icon(Icons.Default.Stop, contentDescription = "توقف صدا") }
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "صحبت کن؛ متن در صندوق ورودی ذخیره می‌شود")
                        }
                        runCatching { voiceCaptureLauncher.launch(intent) }
                    }) { Icon(Icons.Default.Mic, contentDescription = "ثبت صوتی در صندوق ورودی") }
                    if (screen == "home") IconButton(onClick = { screen = "sync" }) { Icon(Icons.Default.CloudDownload, contentDescription = "اتصال به ویندوز") }
                    IconButton(onClick = onLockApp) { Icon(Icons.Default.Lock, contentDescription = "قفل برنامه") }
                    IconButton(onClick = { screen = "settings" }) { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("خانه") })
                NavigationBarItem(selected = screen in listOf("courses", "subject", "chapter", "reader"), onClick = { screen = "courses" }, icon = { Icon(Icons.Default.Book, null) }, label = { Text("مطالعه") })
                NavigationBarItem(selected = screen in listOf("flashcards", "review"), onClick = { screen = "flashcards" }, icon = { Icon(Icons.Default.Bookmark, null) }, label = { Text("مرور") })
                NavigationBarItem(selected = screen == "notes", onClick = { screen = "notes" }, icon = { Icon(Icons.Default.Notes, null) }, label = { Text("یادداشت") })
                NavigationBarItem(selected = screen == "more", onClick = { screen = "more" }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text("بیشتر") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.runtime.SideEffect { CrashDiagnostics.setCurrentScreen("screen=" + screen + "; item=" + selectedItem) }
        when (screen) {
                "home" -> DashboardScreen(repository.database, userDatabase, speech, onNavigate = { screen = it }, onContinue = { id -> selectedChapter = id; screen = "chapter" })
                "courses" -> SubjectListScreen(repository.database, onSubjectClick = { subject -> selectedSubject = subject; screen = "subject" })
                "subject" -> ChapterListScreen(repository.database, selectedSubject, onChapterClick = { id -> selectedChapter = id; screen = "chapter" })
                "chapter" -> ChapterContentScreen(repository.database, selectedChapter, onItemClick = { uid -> selectedItem = uid; previousScreen = "chapter"; screen = "reader" })
                "reader" -> ReaderScreen(repository.database, userDatabase, selectedItem, speech, preferences)
                "flashcards" -> FlashcardsScreen(repository.database, userDatabase, speech, preferences, reviewOnly = false)
                "review" -> FlashcardsScreen(repository.database, userDatabase, speech, preferences, reviewOnly = true)
                "notes" -> NotesScreen(userDatabase, speech)
                "inbox" -> MindDumpScreen(userDatabase, speech)
                "journal" -> JournalScreen(userDatabase, speech)
                "quiz" -> QuizScreen(repository.database, userDatabase, speech, examMode = false)
                "exam" -> QuizScreen(repository.database, userDatabase, speech, examMode = true)
                "labs" -> LabsScreen(repository.database, userDatabase, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "labs"; screen = "reader" })
                "planner" -> PlannerScreen(userDatabase, speech)
                "progress" -> ProgressScreen(repository.database, userDatabase, speech)
                "bookmarks" -> BookmarksScreen(repository.database, userDatabase, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "bookmarks"; screen = "reader" })
                "settings" -> SettingsScreen(speech, preferences, initialTheme, onThemeChange, onFontScaleChange, onLockApp, { screen = "sync" })
                "sync" -> SyncScreen(repository, onDone = { screen = "courses" })
                else -> MoreScreen(onNavigate = { screen = it })
            }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ابزارهای آموزشی روی گوشی", style = MaterialTheme.typography.titleLarge)
        Text("همهٔ داده‌های این بخش‌ها روی همین گوشی ذخیره می‌شوند و پس از دانلود فصل‌ها بدون اینترنت در دسترس‌اند.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("تمرین", "quiz", onNavigate, Modifier.weight(1f))
            ActionTile("آزمون", "exam", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("برنامه‌ریز", "planner", onNavigate, Modifier.weight(1f))
            ActionTile("گزارش پیشرفت", "progress", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("نشانک‌ها", "bookmarks", onNavigate, Modifier.weight(1f))
            ActionTile("تنظیمات", "settings", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("موضوع‌ها", "courses", onNavigate, Modifier.weight(1f))
            ActionTile("مرور امروز", "review", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("تخلیه ذهن / صندوق ورودی", "inbox", onNavigate, Modifier.weight(1f))
            ActionTile("دفتر یادداشت", "notes", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("دفتر روزانه", "journal", onNavigate, Modifier.weight(1f))
            ActionTile("دفتر یادداشت آموزشی", "notes", onNavigate, Modifier.weight(1f))
        }
    }
}


@Composable
fun PersianVoiceInputButton(onText: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (recognized.isNotBlank()) onText(recognized)
        }
    }
    Button(onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "متن را به فارسی بیان کن")
        }
        runCatching { launcher.launch(intent) }
    }) {
        Icon(Icons.Default.Mic, contentDescription = null)
        Text("ورود با صدا")
    }
}
