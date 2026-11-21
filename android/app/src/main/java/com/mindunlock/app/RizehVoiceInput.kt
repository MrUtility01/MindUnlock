package com.mindunlock.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

private const val RIZEH_MODEL_SHA256 =
    "2cbebb39be531e04f0f31b15f7053365ac9941080c7f7ad5c98d6e0aa7638770"

private object RizehNative {
    init {
        System.loadLibrary("mindunlock_asr")
    }

    external fun nativeCreate(modelPath: String, tokensPath: String, melPath: String): Long
    external fun nativePush(handle: Long, samples: ShortArray, count: Int): String
    external fun nativeFinish(handle: Long): String
    external fun nativeDestroy(handle: Long)
}

private object RizehAssets {
    data class Paths(val model: File, val tokens: File, val mel: File)

    fun ensureInstalled(context: Context): Paths {
        val root = File(context.filesDir, "offline-asr/rizeh-v1").apply { mkdirs() }
        val model = File(root, "model.int4.onnx")
        val tokens = File(root, "tokens.txt")
        val mel = File(root, "mel_filters.json")
        val license = File(root, "LICENSE")
        val marker = File(root, ".model-sha256")

        val alreadyInstalled =
            model.isFile &&
            model.length() >= 25_000_000L &&
            tokens.isFile && tokens.length() >= 10_000L &&
            mel.isFile && mel.length() >= 50_000L &&
            license.isFile && license.length() >= 1_000L &&
            marker.isFile && marker.readText().trim() == RIZEH_MODEL_SHA256

        if (!alreadyInstalled) {
            copyAsset(context, "rizeh/model.int4.onnx", model, RIZEH_MODEL_SHA256)
            copyAsset(context, "rizeh/tokens.txt", tokens)
            copyAsset(context, "rizeh/mel_filters.json", mel)
            copyAsset(context, "rizeh/LICENSE", license)
            marker.writeText(RIZEH_MODEL_SHA256)
        }
        check(model.isFile && model.length() >= 25_000_000L) {
            "مدل Rizeh در بسته نصب برنامه وجود ندارد یا ناقص است."
        }
        check(tokens.isFile && tokens.length() >= 10_000L) {
            "فایل واژگان مدل Rizeh ناقص است."
        }
        check(mel.isFile && mel.length() >= 50_000L) {
            "فیلترهای صوتی مدل Rizeh ناقص است."
        }
        return Paths(model, tokens, mel)
    }

    private fun copyAsset(
        context: Context,
        assetPath: String,
        destination: File,
        expectedSha256: String? = null
    ) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".part")
        temporary.delete()
        context.assets.open(assetPath).buffered().use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        check(temporary.isFile && temporary.length() > 0L) {
            "استخراج فایل $assetPath ناموفق بود."
        }
        if (expectedSha256 != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            temporary.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(expectedSha256, ignoreCase = true)) {
                "اعتبارسنجی SHA-256 مدل Rizeh ناموفق بود."
            }
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("جایگزینی فایل محلی $assetPath ناموفق بود.")
        }
        check(temporary.renameTo(destination)) {
            temporary.delete()
            "ذخیره فایل محلی $assetPath ناموفق بود."
        }
    }
}

private object OfflinePersianDictation {
    private const val SAMPLE_RATE = 16_000

    suspend fun transcribe(
        context: Context,
        stopSignal: AtomicBoolean,
        onStatus: (String) -> Unit,
        onRecording: (Boolean) -> Unit,
        onPartial: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var handle = 0L
        var recorder: AudioRecord? = null
        try {
            onStatus("آماده‌سازی موتور آفلاین فارسی از فایل‌های داخل برنامه…")
            val paths = RizehAssets.ensureInstalled(context.applicationContext)
            check(!stopSignal.get()) { "ضبط پیش از شروع متوقف شد." }

            onStatus("بارگذاری مدل Rizeh INT4 روی پردازنده…")
            handle = RizehNative.nativeCreate(
                paths.model.absolutePath,
                paths.tokens.absolutePath,
                paths.mel.absolutePath
            )
            check(handle != 0L) { "ساخت نشست تشخیص گفتار ناموفق بود." }

            if (stopSignal.get()) return@withContext ""
            val minimumBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minimumBuffer > 0) { "ضبط صوت ۱۶ کیلوهرتز روی این دستگاه پشتیبانی نمی‌شود." }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimumBuffer * 2, 8_192)
            )
            recorder = audioRecord
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "راه‌اندازی میکروفون ناموفق بود."
            }

            val chunk = ShortArray(2_048) // 128 ms @ 16 kHz; fixed-size reusable buffer.
            var previousText = ""
            audioRecord.startRecording()
            onRecording(true)
            onStatus("در حال شنیدن… صحبت کن؛ متن به‌صورت پیوسته به‌روز می‌شود.")
            while (!stopSignal.get()) {
                val count = audioRecord.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                        error("ارتباط ضبط صدا با میکروفون قطع شد.")
                    }
                    error("خواندن صدا از میکروفون ناموفق بود: $count")
                }
                if (count == 0) continue

                val current = RizehNative.nativePush(handle, chunk, count)
                if (current != previousText) {
                    previousText = current
                    onPartial(current)
                }
            }

            onRecording(false)
            onStatus("در حال نهایی‌سازی آخرین بخش گفتار…")
            PersianTextNormalizer.normalize(RizehNative.nativeFinish(handle))
        } finally {
            onRecording(false)
            recorder?.let { audioRecord ->
                runCatching {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
                }
                audioRecord.release()
            }
            if (handle != 0L) runCatching { RizehNative.nativeDestroy(handle) }
        }
    }
}

@Composable
fun PersianVoiceInputButton(compact: Boolean = false, onText: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val stopSignal = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("تشخیص گفتار فارسی کاملاً آفلاین است؛ مدل از قبل داخل برنامه قرار دارد.") }
    var job by remember { mutableStateOf<Job?>(null) }

    fun startRecording() {
        if (busy) return
        stopSignal.set(false)
        liveText = ""
        status = "آماده‌سازی مدل آفلاین…"
        showDialog = true
        busy = true
        job = scope.launch {
            try {
                val finalText = OfflinePersianDictation.transcribe(
                    context = context,
                    stopSignal = stopSignal,
                    onStatus = { message -> mainHandler.post { status = message } },
                    onRecording = { active -> mainHandler.post { recording = active } },
                    onPartial = { text -> mainHandler.post { liveText = text } }
                )
                if (finalText.isNotBlank()) {
                    liveText = finalText
                    onText(finalText)
                    status = "متن نهایی ثبت شد."
                } else if (stopSignal.get()) {
                    status = "ضبط متوقف شد؛ گفتار قابل تشخیصی ثبت نشد."
                }
            } catch (error: Exception) {
                status = "خطای تشخیص گفتار آفلاین: ${error.message ?: "نامشخص"}"
            } finally {
                recording = false
                busy = false
                if (liveText.isNotBlank()) {
                    showDialog = false
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && busy) {
                stopSignal.set(true)
                mainHandler.post {
                    recording = false
                    status = "با خروج برنامه، ضبط متوقف شد."
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopSignal.set(true)
            job?.cancel()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else status = "برای تبدیل گفتار به متن، اجازه دسترسی به میکروفون لازم است."
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (recording || busy) {
                    stopSignal.set(true)
                    status = "در حال توقف و نهایی‌سازی…"
                } else {
                    showDialog = false
                }
            },
            title = { Text("تبدیل گفتار فارسی به متن") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = liveText,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        minLines = 4,
                        maxLines = 8,
                        label = { Text("متن زنده") }
                    )
                    if (busy && !recording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("پردازش محلی…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (recording) {
                    Button(onClick = {
                        stopSignal.set(true)
                        status = "در حال نهایی‌سازی آخرین بخش گفتار…"
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("توقف و ثبت متن")
                    }
                } else if (!busy) {
                    TextButton(onClick = { showDialog = false }) { Text("بستن") }
                }
            },
            dismissButton = {
                if (busy && !recording) {
                    TextButton(onClick = {
                        stopSignal.set(true)
                        status = "در حال توقف…"
                    }) { Text("لغو") }
                }
            }
        )
    }

    Row(
        modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (compact) {
            IconButton(
                onClick = {
                    if (recording) {
                        stopSignal.set(true)
                        status = "در حال نهایی‌سازی…"
                    } else if (!busy) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !busy || recording
            ) {
                if (busy && !recording) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (recording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (recording) "توقف ضبط" else "شروع گفتار آفلاین"
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    if (recording) {
                        stopSignal.set(true)
                        status = "در حال نهایی‌سازی…"
                    } else if (!busy) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !busy || recording
            ) {
                if (busy && !recording) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Text(if (recording) "توقف و ثبت متن" else "گفتار آفلاین")
            }
        }
        if (!compact) Text(status, style = MaterialTheme.typography.labelSmall)
    }
}
