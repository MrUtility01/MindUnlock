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
    val title = when (screen) {
        "subject" -> "موضوع‌ها"
        "chapter" -> "فصل‌ها"
        "reader" -> "مطالعه"
        "courses" -> "موضوع‌ها"
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
            "sync", "settings", "profile", "learning", "more", "quiz", "exam", "review", "labs", "planner", "progress", "bookmarks", "inbox", "journal", "later", "backup" -> "home"
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
                    PersianVoiceInputButton(compact = true) { spokenText ->
                        scope.launch {
                            userDatabase.mindDumpDao().insert(MindDumpEntity(content = spokenText, category = "thought"))
                            screen = "inbox"
                        }
                    }
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
                "later" -> LaterTopicsScreen(userDatabase, speech)
                "quiz" -> QuizScreen(repository.database, userDatabase, speech, examMode = false)
                "exam" -> QuizScreen(repository.database, userDatabase, speech, examMode = true)
                "labs" -> LabsScreen(repository.database, userDatabase, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "labs"; screen = "reader" })
                "planner" -> PlannerScreen(userDatabase, speech)
                "progress" -> ProgressScreen(repository.database, userDatabase, speech)
                "backup" -> BackupRestoreScreen(userDatabase)
                "bookmarks" -> BookmarksScreen(repository.database, userDatabase, speech, onOpenItem = { uid -> selectedItem = uid; previousScreen = "bookmarks"; screen = "reader" })
                "settings" -> SettingsScreen(speech, preferences, initialTheme, onThemeChange, onFontScaleChange, onLockApp, { screen = "sync" })
                "profile" -> LearningProfileScreen(preferences)
                "learning" -> LearningEngineScreen(repository.database, userDatabase, preferences, onOpenChapter = { id -> selectedChapter = id; screen = "chapter" }, onNavigate = { screen = it })
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
            ActionTile("موضوعات برای بعد", "later", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("پروفایل یادگیری", "profile", onNavigate, Modifier.weight(1f))
            ActionTile("موتور یادگیری", "learning", onNavigate, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("پشتیبان‌گیری و بازیابی", "backup", onNavigate, Modifier.weight(1f))
        }
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

    suspend fun transcribe(
        context: Context,
        stopSignal: AtomicBoolean,
        onStatus: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "offline-asr/shenava-rizeh-v1")
        val modelFile = File(modelDir, "model.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        modelDir.mkdirs()

        if (!modelFile.exists() || modelFile.length() < 1_000_000L || !tokensFile.exists()) {
            withContext(Dispatchers.Main) { onStatus("دریافت یک‌باره مدل فارسی؛ پس از آن تشخیص بدون اینترنت انجام می‌شود.") }
            if (!modelFile.exists() || modelFile.length() < 1_000_000L) {
                download(MODEL_URL, modelFile) { percent ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onStatus("دریافت مدل فارسی: $percent٪")
                    }
                }
            }
            if (!tokensFile.exists() || tokensFile.length() < 100L) {
                download(TOKENS_URL, tokensFile) { }
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
                numThreads = 4,
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

    private fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val partial = File(target.parentFile, target.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MindUnlock-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("دریافت مدل ناموفق بود: HTTP ${connection.responseCode}")
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
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (partial.length() < 1024L) error("فایل مدل ناقص دریافت شد.")
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) error("ذخیره فایل مدل انجام نشد.")
        } finally {
            connection.disconnect()
        }
    }
}
