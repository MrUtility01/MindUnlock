package com.mindunlock.app

import android.content.Context
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth

import android.os.Bundle
import androidx.compose.material.icons.filled.VolumeUp
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
import com.mindunlock.app.ui.GitHubBrowserScreen

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
        if (::speech.isInitialized) speech.stop(showStatus = false)
        CrashDiagnostics.markLaunchBackgrounded(this)
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
        "growth" -> "مسیر رشد تخصصی IT"
        "sync" -> "اتصال و دریافت محتوا"
        "github" -> "مخزن عمومی GitHub"
        "more" -> "ابزارهای یادگیری"
        else -> "MindUnlock"
    }
    fun back() {
        screen = when (screen) {
            "reader" -> previousScreen
            "chapter" -> "subject"
            "subject" -> "courses"
            "sync", "github", "settings", "profile", "learning", "growth", "mindmap", "more", "quiz", "exam", "review", "labs", "planner", "progress", "bookmarks", "inbox", "journal", "later", "backup", "archive", "about" -> "home"
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
                                text = { Text("مرور مخزن عمومی GitHub") },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                onClick = { showTopMenu = false; screen = "github" }
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
                "growth" -> GrowthPathScreen(preferences)
                "sync" -> SyncScreen(repository, onDone = { screen = "courses" })
                "github" -> GitHubBrowserScreen()
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
        ActionTile("مسیر رشد تخصصی IT", "growth", onNavigate, Modifier.fillMaxWidth())
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
