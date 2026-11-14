package com.mindunlock.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Job
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.layout.fillMaxWidth

import android.os.Bundle
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.withContext
import com.mindunlock.app.ui.theme.MindUnlockTheme

class MainActivity : ComponentActivity() {
    private lateinit var speech: PersianSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.install(this)

        // Do not repeat a startup crash loop. The recovery Activity uses only
        // platform Views and never opens Room, Compose screens, or speech models.
        if (CrashDiagnostics.beginLaunch(this)) {
            openStartupRecovery()
            return
        }

        try {
            CrashDiagnostics.setCurrentScreen("MainActivity: preparing dependencies")
            enableEdgeToEdge()
            speech = PersianSpeech(this)

            val app = application as MindUnlockApp
            val repository = app.repository
            val preferences = getSharedPreferences("mindunlock_preferences", MODE_PRIVATE)
            val userDatabase = UserDatabase.getInstance(this)

            setContent {
                var theme by remember { mutableStateOf(preferences.getString("theme", "light") ?: "light") }
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
                    CrashDiagnostics.setCurrentScreen("Startup: legacy database migration")
                    try {
                        withContext(Dispatchers.IO) {
                            LegacyDatabaseMigrator.migrate(
                                this@MainActivity,
                                repository.database,
                                userDatabase,
                                onProgress = { phase ->
                                    runOnUiThread { migrationStatus = phase }
                                }
                            )
                        }
                        startupReady = true
                    } catch (error: Throwable) {
                        migrationError = error.message ?: "انتقال اطلاعات قدیمی ناموفق بود."
                        CrashDiagnostics.recordHandled(this@MainActivity, "LegacyDatabaseMigrator", error)
                        android.util.Log.e("MindUnlockStartup", "Legacy database migration failed", error)
                    }
                }

                MindUnlockTheme(theme) {
                    // This runs only after the first successful composition. If
                    // rendering the dashboard crashes, the next launch enters recovery.
                    androidx.compose.runtime.SideEffect {
                        if (startupReady) {
                            CrashDiagnostics.markLaunchHealthy(this@MainActivity)
                        }
                    }
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
                                Text(
                                    if (AppLock.isConfigured(preferences)) {
                                        "برای ورود رمز برنامه را وارد کن."
                                    } else {
                                        "برای محافظت از اطلاعات، رمز اولیه برنامه را تعیین کن (حداقل ۴ کاراکتر)."
                                    }
                                )
                                OutlinedTextField(
                                    lockPassword,
                                    { lockPassword = it; lockError = "" },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("رمز برنامه") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                                if (lockError.isNotBlank()) Text(lockError, color = MaterialTheme.colorScheme.error)
                                Button(
                                    onClick = {
                                        try {
                                            if (AppLock.isConfigured(preferences)) {
                                                if (AppLock.verify(preferences, lockPassword.toCharArray())) {
                                                    unlocked = true
                                                    lockPassword = ""
                                                } else {
                                                    lockError = "رمز برنامه صحیح نیست."
                                                }
                                            } else {
                                                AppLock.setPassword(preferences, lockPassword.toCharArray())
                                                unlocked = true
                                                lockPassword = ""
                                            }
                                        } catch (error: Exception) {
                                            lockError = error.message ?: "تنظیم رمز ناموفق بود."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = lockPassword.length >= 4
                                ) {
                                    Text(if (AppLock.isConfigured(preferences)) "ورود به برنامه" else "فعال‌سازی قفل و ورود")
                                }
                            }
                        } else {
                            MindUnlockRoot(
                                repository = repository,
                                userDatabase = userDatabase,
                                speech = speech,
                                initialTheme = theme,
                                onThemeChange = { theme = it; preferences.edit().putString("theme", it).apply() },
                                onFontScaleChange = { fontScale = it; preferences.edit().putFloat("font_scale", it).apply() },
                                preferences = preferences,
                                onLockApp = { unlocked = false }
                            )
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            CrashDiagnostics.recordStartupFailure(this, "MainActivity.onCreate", error)
            android.util.Log.e("MindUnlockStartup", "MainActivity startup failed", error)
            openStartupRecovery()
        }
    }

    private fun openStartupRecovery() {
        runCatching {
            startActivity(android.content.Intent(this, StartupRecoveryActivity::class.java))
            finish()
        }.onFailure { error ->
            CrashDiagnostics.recordStartupFailure(this, "Opening StartupRecoveryActivity", error)
        }
    }

    override fun onPause() {
        if (::speech.isInitialized) speech.stop()
        super.onPause()
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
    var showTopMenu by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = when (screen) {
        "subject" -> "موضوع‌ها"
        "chapter" -> "فصل‌ها"
        "reader" -> "مطالعه"
        "courses" -> "موضوع‌ها"
        "archive" -> "آرشیو دانش"
        "mindmap" -> "نقشه ذهنی و گراف دانش"
        "flashcards" -> "فلش‌کارت و مرور فاصله‌دار"
        "notes" -> "دفتر یادداشت"
        "inbox" -> "تخلیه ذهن / صندوق ورودی"
        "journal" -> "دفتر روزانه"
        "later" -> "موضوعات برای بعد"
        "quiz" -> "تمرین"
        "exam" -> "آزمون"
        "review" -> "مرور امروز"
        "labs" -> "آزمایشگاه و پروژه‌ها"
        "planner" -> "برنامه‌ریز و یادآورها"
        "progress" -> "گزارش پیشرفت"
        "backup" -> "پشتیبان‌گیری و بازیابی"
        "bookmarks" -> "نشانک‌ها"
        "settings" -> "تنظیمات"
        "about" -> "درباره ما"
        "profile" -> "پروفایل یادگیری"
        "learning" -> "موتور یادگیری"
        "sync" -> "اتصال و دریافت محتوا"
        "more" -> "ابزارهای یادگیری"
        else -> "MindUnlock"
    }
    fun back() {
        screen = when (screen) {
            "reader" -> previousScreen
            "chapter" -> "subject"
            "subject" -> "courses"
            "sync", "settings", "profile", "learning", "mindmap", "more", "quiz", "exam", "review", "labs", "planner", "progress", "bookmarks", "inbox", "journal", "later", "backup", "archive", "about" -> "home"
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
                    PersianVoiceInputButton(compact = true) { spokenText ->
                        scope.launch {
                            userDatabase.mindDumpDao().insert(MindDumpEntity(content = spokenText, category = "thought"))
                            screen = "inbox"
                        }
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "منوی ابزارها")
                        }
                        DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("خواندن عنوان صفحه") },
                                leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                onClick = { showTopMenu = false; speech.speak("صفحه $title") }
                            )
                            DropdownMenuItem(
                                text = { Text("توقف صدا") },
                                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) },
                                onClick = { showTopMenu = false; speech.stop() }
                            )
                            if (screen == "home") DropdownMenuItem(
                                text = { Text("اتصال به ویندوز") },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "sync" }
                            )
                            DropdownMenuItem(
                                text = { Text("صندوق ورودی") },
                                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "inbox" }
                            )
                            DropdownMenuItem(
                                text = { Text("یادداشت‌ها") },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "notes" }
                            )
                            DropdownMenuItem(
                                text = { Text("دفتر روزانه") },
                                leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "journal" }
                            )
                            DropdownMenuItem(
                                text = { Text("موضوعات برای بعد") },
                                leadingIcon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "later" }
                            )
                            DropdownMenuItem(
                                text = { Text("نقشه ذهنی و گراف دانش") },
                                leadingIcon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "mindmap" }
                            )
                            DropdownMenuItem(
                                text = { Text("تنظیمات") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "settings" }
                            )
                            DropdownMenuItem(
                                text = { Text("درباره ما") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "about" }
                            )
                            DropdownMenuItem(
                                text = { Text("قفل برنامه") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = { showTopMenu = false; onLockApp() }
                            )
                        }
                    }
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
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            androidx.compose.runtime.SideEffect { CrashDiagnostics.setCurrentScreen("screen=" + screen + "; item=" + selectedItem) }
        when (screen) {
                "home" -> DashboardScreen(repository.database, userDatabase, speech, onNavigate = { screen = it }, onContinue = { target ->
                    when {
                        target.startsWith("item:") -> {
                            val itemUid = target.removePrefix("item:")
                            selectedItem = itemUid
                            previousScreen = "chapter"
                            screen = "reader"
                            scope.launch {
                                repository.database.contentItemDao().get(itemUid)?.let { selectedChapter = it.chapterId }
                            }
                        }
                        target.startsWith("chapter:") -> {
                            selectedChapter = target.removePrefix("chapter:")
                            screen = "chapter"
                        }
                        else -> {
                            selectedChapter = target
                            screen = "chapter"
                        }
                    }
                })
                "courses" -> SubjectListScreen(repository.database, onSubjectClick = { subject -> selectedSubject = subject; screen = "subject" })
                "subject" -> ChapterListScreen(repository.database, selectedSubject, onChapterClick = { id -> selectedChapter = id; screen = "chapter" })
                "chapter" -> ChapterContentScreen(repository.database, selectedChapter, onItemClick = { uid -> selectedItem = uid; previousScreen = "chapter"; screen = "reader" })
                "archive" -> KnowledgeArchiveScreen(repository.database, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "archive"; screen = "reader" })
                "mindmap" -> MindMapScreen(userDatabase)
                "reader" -> ReaderScreen(repository.database, userDatabase, selectedItem, speech, preferences)
                "flashcards" -> FlashcardsScreen(repository.database, userDatabase, speech, preferences, reviewOnly = false)
                "review" -> FlashcardsScreen(repository.database, userDatabase, speech, preferences, reviewOnly = true)
                "notes" -> NotesScreen(userDatabase, speech)
                "inbox" -> MindDumpScreen(userDatabase, speech)
                "journal" -> JournalScreen(userDatabase, speech)
                "later" -> LaterTopicsScreen(userDatabase, speech)
                "quiz" -> QuizScreen(repository.database, userDatabase, speech, examMode = false)
                "exam" -> QuizScreen(repository.database, userDatabase, speech, examMode = true)
                "labs" -> LabsScreen(repository.database, userDatabase, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "labs"; screen = "reader" })
                "planner" -> PlannerScreen(userDatabase, speech)
                "progress" -> ProgressScreen(repository.database, userDatabase, speech)
                "backup" -> BackupRestoreScreen(userDatabase)
                "bookmarks" -> BookmarksScreen(repository.database, userDatabase, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "bookmarks"; screen = "reader" })
                "settings" -> SettingsScreen(speech, userDatabase, preferences, initialTheme, onThemeChange, onFontScaleChange, onLockApp, { screen = "sync" })
                "about" -> AboutScreen()
                "profile" -> LearningProfileScreen(preferences, userDatabase)
                "learning" -> LearningEngineScreen(repository.database, userDatabase, preferences, onOpenChapter = { id -> selectedChapter = id; screen = "chapter" }, onNavigate = { screen = it })
                "sync" -> SyncScreen(repository, onDone = { screen = "courses" })
                else -> MoreScreen(onNavigate = { screen = it })
            }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("فضای کار MindUnlock", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("ابزارها در چند گروه مشخص چیده شده‌اند تا مسیرهای روزمره سریع‌تر پیدا شوند. داده‌های شخصی روی همین دستگاه نگهداری می‌شوند.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("یادگیری و تمرین", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("تمرین", "quiz", onNavigate, Modifier.weight(1f))
            ActionTile("آزمون", "exam", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("آزمایشگاه عملی", "labs", onNavigate, Modifier.weight(1f))
            ActionTile("مرور هوشمند", "review", onNavigate, Modifier.weight(1f))
        }
        Text("مدیریت ذهن و برنامه‌ریزی", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("برنامه‌ریز", "planner", onNavigate, Modifier.weight(1f))
            ActionTile("گزارش پیشرفت", "progress", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("نشانک‌ها", "bookmarks", onNavigate, Modifier.weight(1f))
            ActionTile("فلش‌کارت‌ها", "flashcards", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("موضوع‌ها", "courses", onNavigate, Modifier.weight(1f))
            ActionTile("آرشیو دانش", "archive", onNavigate, Modifier.weight(1f))
        }
        ActionTile("نقشه ذهنی و گراف دانش", "mindmap", onNavigate, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("تخلیه ذهن / صندوق ورودی", "inbox", onNavigate, Modifier.weight(1f))
            ActionTile("دفتر یادداشت", "notes", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("دفتر روزانه", "journal", onNavigate, Modifier.weight(1f))
            ActionTile("موضوعات برای بعد", "later", onNavigate, Modifier.weight(1f))
        }
        Text("ابزارهای مطالعه", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("پروفایل یادگیری", "profile", onNavigate, Modifier.weight(1f))
            ActionTile("موتور یادگیری", "learning", onNavigate, Modifier.weight(1f))
        }
        Text("تنظیمات و اطلاعات برنامه", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("پشتیبان‌گیری و بازیابی", "backup", onNavigate, Modifier.weight(1f))
            ActionTile("تنظیمات", "settings", onNavigate, Modifier.weight(1f))
        }
        ActionTile("درباره ما و مشخصات سازنده", "about", onNavigate, Modifier.fillMaxWidth())
    }
}


@Composable
fun PersianVoiceInputButton(compact: Boolean = false, onText: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stopSignal = remember { AtomicBoolean(false) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("تشخیص گفتار فارسی کاملاً محلی است؛ دریافت اولیه مدل حدود ۱۱۷ مگابایت است.") }
    var job by remember { mutableStateOf<Job?>(null) }
    val voiceLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(voiceLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                stopSignal.set(true)
                job?.cancel()
            }
        }
        voiceLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            voiceLifecycleOwner.lifecycle.removeObserver(observer)
            stopSignal.set(true)
            job?.cancel()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            stopSignal.set(false)
            recording = true
            busy = true
            job = scope.launch {
                try {
                    val transcript = OfflinePersianSpeech.transcribe(context, stopSignal) { message ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post { status = message }
                    }
                    if (transcript.isNotBlank()) {
                        onText(transcript)
                        status = "متن فارسی به فیلد اضافه شد."
                    } else {
                        status = "گفتاری تشخیص داده نشد؛ دوباره امتحان کن."
                    }
                } catch (error: Exception) {
                    status = "خطای تشخیص آفلاین: ${error.message ?: "نامشخص"}"
                } finally {
                    recording = false
                    busy = false
                }
            }
        } else {
            status = "برای تبدیل گفتار به متن، اجازه میکروفون لازم است."
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val handleVoiceClick: () -> Unit = {
                if (recording) {
                    stopSignal.set(true)
                    status = "در حال پردازش گفتار ضبط‌شده..."
                } else if (!busy) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        stopSignal.set(false)
                        recording = true
                        busy = true
                        job = scope.launch {
                            try {
                                val transcript = OfflinePersianSpeech.transcribe(context, stopSignal) { message ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { status = message }
                                }
                                if (transcript.isNotBlank()) {
                                    onText(transcript)
                                    status = "متن فارسی به فیلد اضافه شد."
                                } else status = "گفتاری تشخیص داده نشد؛ دوباره امتحان کن."
                            } catch (error: Exception) {
                                status = "خطای تشخیص آفلاین: ${error.message ?: "نامشخص"}"
                            } finally {
                                recording = false
                                busy = false
                            }
                        }
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (compact) {
                IconButton(onClick = handleVoiceClick, enabled = !busy || recording) {
                    if (busy && !recording) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    else Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = "تشخیص گفتار فارسی آفلاین")
                }
            } else {
                Button(onClick = handleVoiceClick, enabled = !busy || recording) {
                    if (busy && !recording) CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                    else Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(when { recording -> "پایان ضبط"; busy -> "آماده‌سازی مدل"; else -> "گفتار آفلاین" })
                }
            }
            if (recording) {
                OutlinedButton(onClick = { stopSignal.set(true); status = "در حال پردازش..." }) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("توقف")
                }
            }
        }
        if (!compact) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal object OfflinePersianSpeech {
    private const val SAMPLE_RATE = 16000
    private const val MODEL_URL = "https://huggingface.co/Reza2kn/Shenava-Rizeh-v1.0-sherpa-onnx/resolve/main/model.onnx?download=true"
    private const val TOKENS_URL = "https://huggingface.co/Reza2kn/Shenava-Rizeh-v1.0-sherpa-onnx/resolve/main/tokens.txt?download=true"

    fun isInstalled(context: Context): Boolean {
        val dir = File(context.filesDir, "offline-asr/shenava-rizeh-v1")
        return File(dir, "model.onnx").let { it.exists() && it.length() > 1_000_000L } &&
            File(dir, "tokens.txt").exists()
    }

    fun deleteModel(context: Context): Boolean {
        val dir = File(context.filesDir, "offline-asr/shenava-rizeh-v1")
        return !dir.exists() || dir.deleteRecursively()
    }

    fun modelSizeBytes(context: Context): Long {
        val dir = File(context.filesDir, "offline-asr/shenava-rizeh-v1")
        return if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit = {}): Long =
        withContext(Dispatchers.IO) { ensureModelFiles(context, onProgress) }

    private fun ensureModelFiles(context: Context, onProgress: (Int) -> Unit = {}): Long {
        val modelDir = File(context.filesDir, "offline-asr/shenava-rizeh-v1").apply { mkdirs() }
        val modelFile = File(modelDir, "model.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        if (!modelFile.isFile || modelFile.length() < 1_000_000L) {
            onProgress(0)
            download(context, SpeechMirrorSource.HUGGING_FACE, MODEL_URL, modelFile) { percent -> onProgress((percent * 0.9f).toInt().coerceIn(0, 90)) }
        } else onProgress(90)
        if (!tokensFile.isFile || tokensFile.length() < 100L) {
            download(context, SpeechMirrorSource.HUGGING_FACE, TOKENS_URL, tokensFile) { percent -> onProgress(90 + (percent / 10).coerceIn(0, 10)) }
        }
        check(modelFile.isFile && modelFile.length() >= 1_000_000L) { "فایل مدل تشخیص گفتار ناقص است." }
        check(tokensFile.isFile && tokensFile.length() >= 100L) { "فایل واژگان مدل تشخیص گفتار ناقص است." }
        onProgress(100)
        return modelSizeBytes(context)
    }

    suspend fun transcribe(
        context: Context,
        stopSignal: AtomicBoolean,
        onStatus: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "offline-asr/shenava-rizeh-v1")
        val modelFile = File(modelDir, "model.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        modelDir.mkdirs()

        ensureModelFiles(context) { percent ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onStatus("آماده‌سازی مدل تشخیص گفتار فارسی: $percent٪")
            }
        }

        withContext(Dispatchers.Main) { onStatus("مدل آماده است؛ اکنون صحبت کن و برای پایان دوباره دکمه را بزن.") }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "ضبط صدا روی این دستگاه پشتیبانی نمی‌شود." }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, 8192)
        )
        require(recorder.state == AudioRecord.STATE_INITIALIZED) { "میکروفون قابل راه‌اندازی نیست." }
        val pcm = ArrayList<Short>(SAMPLE_RATE * 12)
        val chunk = ShortArray(2048)
        try {
            recorder.startRecording()
            val startedAt = System.currentTimeMillis()
            while (!stopSignal.get() && System.currentTimeMillis() - startedAt < 45_000L) {
                val count = recorder.read(chunk, 0, chunk.size)
                if (count > 0) for (i in 0 until count) pcm.add(chunk[i])
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        require(pcm.size > SAMPLE_RATE / 3) { "صدای ضبط‌شده خیلی کوتاه است." }
        withContext(Dispatchers.Main) { onStatus("پردازش محلی گفتار فارسی روی گوشی...") }
        val samples = FloatArray(pcm.size) { pcm[it].toFloat() / 32768.0f }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelFile.absolutePath),
                tokens = tokensFile.absolutePath,
                numThreads = if ((context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode) 1 else 2,
                provider = "cpu"
            )
        )
        val recognizer = OfflineRecognizer(config = config)
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    private fun download(
        context: Context,
        source: SpeechMirrorSource,
        originalUrl: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        val partial = File(target.parentFile, target.name + ".part")
        val failures = mutableListOf<String>()
        val mirrors = SpeechDownloadMirrors.candidates(context, source, originalUrl)
        check(mirrors.isNotEmpty()) { "برای دریافت فایل هیچ میروری پیکربندی نشده است." }

        for ((index, mirror) in mirrors.withIndex()) {
            partial.delete()
            val connection = (URL(mirror.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MindUnlock-Android/0.6.28")
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                DownloadAuditLog.record(context, source.name, "attempt=${index + 1}/${mirrors.size} host=${mirror.label} file=${target.name}")
                onProgress(0)
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error("HTTP $responseCode")
                }
                val contentType = connection.contentType.orEmpty().lowercase(java.util.Locale.US)
                if (contentType.contains("text/html")) {
                    error("میرور به‌جای فایل مدل، صفحه HTML برگرداند.")
                }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0L) {
                                val percent = ((copied * 100L) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                if (total > 0L && partial.length() != total) {
                    error("فایل ناقص بود: ${partial.length()} از $total بایت")
                }
                if (partial.length() < 100L) {
                    error("پاسخ سرور فایل مدل معتبر نبود یا بسیار کوچک بود.")
                }

                val backup = File(target.parentFile, target.name + ".backup")
                backup.delete()
                if (target.exists() && !target.renameTo(backup)) {
                    error("فایل قبلی برای جایگزینی قابل انتقال نبود.")
                }
                if (!partial.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target)
                    error("ذخیره فایل دریافت‌شده ناموفق بود.")
                }
                backup.delete()
                onProgress(100)
                DownloadAuditLog.record(context, source.name, "success host=${mirror.label} bytes=${target.length()} file=${target.name}")
                return
            } catch (failure: Throwable) {
                partial.delete()
                val reason = failure.message ?: failure.javaClass.simpleName
                DownloadAuditLog.record(context, source.name, "failure host=${mirror.label} reason=$reason")
                failures += "${index + 1}. ${mirror.label}: $reason"
            } finally {
                connection.disconnect()
            }
        }
        target.parentFile?.let { parent ->
            File(parent, target.name + ".part").delete()
        }
        error("همه میرورهای دریافت ناموفق بودند: ${failures.joinToString(" | ")}")
    }
}


@Composable
private fun AboutScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MindUnlock", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("فضایی برای بازسازی تمرکز، سامان‌دهی ذهن و یادگیری پیوسته", style = MaterialTheme.typography.titleMedium)
                Text("MindUnlock یک ابزار شخصیِ آفلاین‌محور است که یادگیری، ثبت افکار، برنامه‌ریزی و بازبینی پیشرفت را در یک محیط یکپارچه کنار هم قرار می‌دهد.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("درباره سازنده", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Mr Utility", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("متخصص فناوری اطلاعات، شبکه و زیرساخت", style = MaterialTheme.typography.titleMedium)
                Text("بیش از ۱۵ سال سابقه تخصصی در حوزه فناوری اطلاعات و زیرساخت‌های شبکه", style = MaterialTheme.typography.bodyLarge)
                androidx.compose.material3.HorizontalDivider()
                Text("سوابق تحصیلی", style = MaterialTheme.typography.titleMedium)
                Text("• کاردانی اینترنت و فناوری شبکه‌های گسترده", style = MaterialTheme.typography.bodyMedium)
                Text("• کارشناسی مهندسی فناوری شبکه‌های گسترده", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.HorizontalDivider()
                Text("حوزه‌های تخصصی", style = MaterialTheme.typography.titleMedium)
                Text("شبکه و زیرساخت سازمانی، سرویس‌های ویندوز و اکتیو دایرکتوری، لینوکس، امنیت و نگهداری سامانه‌های فناوری اطلاعات", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("اصول محصول", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• حفظ حریم خصوصی و نگهداری محلی اطلاعات شخصی")
                Text("• طراحی فارسی و راست‌چین")
                Text("• امکان استفاده آفلاین پس از دریافت محتوای موردنیاز")
                Text("• توسعه مرحله‌ای، ماژولار و قابل آزمون")
            }
        }
        Text("این برنامه برای کمک به تمرکز، یادگیری عمیق و تبدیل آموخته‌ها به اقدام عملی ساخته می‌شود.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
