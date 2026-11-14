package com.mindunlock.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.mutableStateOf
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PersianSpeech(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var loadedId: String? = null
    @Volatile private var loadingId: String? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var activeId = "gyro"
    @Volatile private var closed = false
    private val playbackQueue = SpeechPlaybackQueue()
    private val voiceGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    private val audioLock = Any()
    val ready = mutableStateOf(false)
    val loading = mutableStateOf(false)
    val downloadProgress = mutableStateOf(0f)
    val downloadedBytes = mutableStateOf(0L)
    val error = mutableStateOf<String?>(null)
    val status = mutableStateOf("مدل گفتار در صورت نیاز دریافت می‌شود.")
    val activeVoice = mutableStateOf("gyro")
    val voiceOptions = listOf("gyro" to "نیما · مرد · Gyro", "amir" to "آریا · مرد · Amir", "haaniye" to "هانیه · زن · Mimic3")

    private data class ModelSpec(val archive: String, val directory: String, val model: String, val archiveBytes: Long)
    private fun spec(id: String) = when (id) {
        "amir" -> ModelSpec("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2", "vits-piper-fa_IR-amir-medium", "fa_IR-amir-medium.onnx", 67180373L)
        "haaniye" -> ModelSpec("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-fa-haaniye_low.tar.bz2", "vits-mimic3-fa-haaniye_low", "fa-haaniye_low.onnx", 67093314L)
        else -> ModelSpec("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-gyro-medium.tar.bz2", "vits-piper-fa_IR-gyro-medium", "fa_IR-gyro-medium.onnx", 67183308L)
    }

    init {
        val saved = appContext.getSharedPreferences("mindunlock_preferences", Context.MODE_PRIVATE)
            .getString("speech_voice", "gyro")
            ?.takeIf { value -> voiceOptions.any { it.first == value } } ?: "gyro"
        activeId = saved
        activeVoice.value = saved
    }

    fun selectVoice(id: String) {
        if (closed) return
        val target = voiceOptions.firstOrNull { it.first == id }?.first ?: "gyro"
        activeId = target
        activeVoice.value = target
        playbackQueue.switchVoice(target)
        releaseActiveTrack()

        if (loadedId == target && tts != null) {
            voiceGeneration.incrementAndGet()
            loadingId = null
            loading.value = false
            ready.value = true
            error.value = null
            status.value = "گویندهٔ ${voiceOptions.first { it.first == target }.second} آماده است."
            return
        }
        if (loadingId == target) return

        val request = voiceGeneration.incrementAndGet()
        loadingId = target
        loading.value = true
        ready.value = false
        downloadProgress.value = 0f
        downloadedBytes.value = 0L
        error.value = null
        status.value = "در حال بررسی یا دریافت مدل ${voiceOptions.first { it.first == target }.second}…"

        executor.execute {
            val result = runCatching { buildEngine(target) }
            if (request != voiceGeneration.get() || closed) {
                result.getOrNull()?.let { runCatching { it.release() } }
                return@execute
            }

            result.onSuccess { engine ->
                val previous = tts
                tts = engine
                loadedId = target
                runCatching { previous?.release() }

                loadingId = null
                loading.value = false
                ready.value = true
                error.value = null
                status.value = "گویندهٔ ${voiceOptions.first { it.first == target }.second} آماده است."

                playbackQueue.take(target)?.let { queued ->
                    generateAndPlay(engine, queued)
                }
            }.onFailure { failure ->
                loadingId = null
                loading.value = false
                ready.value = loadedId == target && tts != null
                val message = "دریافت یا بارگذاری مدل ناموفق بود: ${failure.message ?: failure.javaClass.simpleName}"
                error.value = message
                status.value = message
            }
        }
    }

    fun isVoiceDownloaded(id: String): Boolean {
        val modelSpec = spec(id)
        val dir = File(appContext.filesDir, "speech/" + modelSpec.directory)
        return File(dir, modelSpec.model).let { it.isFile && it.length() > 1_000_000L } &&
            File(dir, "tokens.txt").isFile && File(dir, "espeak-ng-data").isDirectory
    }

    fun expectedArchiveBytes(id: String): Long =
        voiceOptions.firstOrNull { it.first == id }?.let { spec(it.first).archiveBytes } ?: 0L

    fun downloadedVoiceBytes(id: String): Long {
        val validId = voiceOptions.firstOrNull { it.first == id }?.first ?: return 0L
        val dir = File(appContext.filesDir, "speech/" + spec(validId).directory)
        if (!isVoiceDownloaded(validId)) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun cpuThreads(): Int {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (powerManager.isPowerSaveMode) 1 else 2
    }

    private fun buildEngine(id: String): OfflineTts {
        val spec = spec(id)
        val speechRoot = File(appContext.filesDir, "speech").apply { mkdirs() }
        val modelDir = File(speechRoot, spec.directory)
        val model = File(modelDir, spec.model)
        if (!model.isFile || model.length() < 1_000_000L || !File(modelDir, "tokens.txt").isFile || !File(modelDir, "espeak-ng-data").isDirectory) {
            downloadAndExtract(spec, modelDir)
        }
        check(model.isFile && model.length() > 1_000_000L) { "فایل مدل صوتی کامل نیست." }
        check(File(modelDir, "tokens.txt").isFile && File(modelDir, "espeak-ng-data").isDirectory) { "فایل‌های زبان فارسی ناقص هستند." }
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
            model = model.absolutePath, tokens = File(modelDir, "tokens.txt").absolutePath,
            dataDir = File(modelDir, "espeak-ng-data").absolutePath
        ), numThreads = cpuThreads(), debug = false, provider = "cpu"))
        return OfflineTts(config = config).also { check(it.numSpeakers() > 0) { "مدل گوینده‌ای ارائه نکرد." } }
    }

    private fun downloadAndExtract(spec: ModelSpec, destination: File) {
        val archive = File(appContext.cacheDir, spec.directory + ".tar.bz2")
        val partial = File(appContext.cacheDir, spec.directory + ".tar.bz2.part")
        archive.delete()
        partial.delete()
        downloadProgress.value = 0f
        downloadedBytes.value = 0L
        status.value = "در حال اتصال به سرور دریافت مدل…"
        downloadArchiveWithFallback(spec, archive, partial)

        status.value = "در حال بررسی و استخراج مدل گفتار…"
        val root = File(appContext.filesDir, "speech-staging").apply { mkdirs() }.canonicalFile
        val extracted = File(root, spec.directory)
        extracted.deleteRecursively()
        try {
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    val out = File(root, entry.name).canonicalFile
                    check(out.path.startsWith(root.path + File.separator)) { "مسیر ناامن در بستهٔ مدل." }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                    entry = tar.nextTarEntry
                }
            }
            check(File(extracted, spec.model).let { it.isFile && it.length() > 1_000_000L } &&
                File(extracted, "tokens.txt").isFile && File(extracted, "espeak-ng-data").isDirectory) {
                "ساختار بسته معتبر نیست یا فایل‌های مدل و زبان ناقص هستند."
            }

            destination.parentFile?.mkdirs()
            val stagedInstall = File(destination.parentFile, "." + destination.name + ".installing")
            val backup = File(destination.parentFile, "." + destination.name + ".backup")
            stagedInstall.deleteRecursively()
            backup.deleteRecursively()
            check(extracted.renameTo(stagedInstall)) { "آماده‌سازی پوشهٔ نصب مدل ناموفق بود." }
            if (destination.exists()) check(destination.renameTo(backup)) { "امکان جایگزینی مدل قبلی وجود ندارد." }
            if (!stagedInstall.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                throw IllegalStateException("انتقال مدل به پوشهٔ نهایی ناموفق بود.")
            }
            backup.deleteRecursively()
            archive.delete()
            downloadProgress.value = 1f
            status.value = "مدل با موفقیت نصب و اعتبارسنجی شد."
        } catch (e: Throwable) {
            throw IllegalStateException("نصب مدل ناموفق بود: " + (e.message ?: e.javaClass.simpleName), e)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun downloadArchiveWithFallback(spec: ModelSpec, archive: File, partial: File) {
        val mirrors = SpeechDownloadMirrors.candidates(
            appContext,
            SpeechMirrorSource.GITHUB_RELEASE,
            spec.archive
        )
        val failures = mutableListOf<String>()
        check(mirrors.isNotEmpty()) { "میروری برای دریافت مدل گفتار تنظیم نشده است." }

        for ((index, mirror) in mirrors.withIndex()) {
            archive.delete()
            partial.delete()
            downloadedBytes.value = 0L
            downloadProgress.value = 0f
            val connection = (URL(mirror.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 180_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MindUnlock-Android/0.6.28")
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                DownloadAuditLog.record(appContext, "TTS", "attempt=${index + 1}/${mirrors.size} host=${mirror.label} file=${spec.model}")
                status.value = "آزمایش میرور ${index + 1} از ${mirrors.size}: ${mirror.label}"
                connection.connect()
                val response = connection.responseCode
                check(response in 200..299) { "پاسخ HTTP $response" }
                val contentType = connection.contentType.orEmpty().lowercase(java.util.Locale.US)
                check(!contentType.contains("text/html")) {
                    "میرور به‌جای آرشیو مدل، صفحه HTML برگرداند."
                }

                val announcedLength = connection.contentLengthLong
                if (announcedLength > 0L) {
                    check(announcedLength == spec.archiveBytes) {
                        "اندازه اعلام‌شده با مدل اصلی برابر نیست ($announcedLength بایت)."
                    }
                }

                status.value = "دریافت بسته مدل از ${mirror.label}…"
                var received = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            downloadedBytes.value = received
                            downloadProgress.value =
                                (received.toFloat() / spec.archiveBytes.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                }
                check(received == spec.archiveBytes && partial.length() == spec.archiveBytes) {
                    "دانلود کامل نیست؛ $received از ${spec.archiveBytes} بایت دریافت شد."
                }
                check(partial.renameTo(archive)) { "انتقال بسته دانلودشده ناموفق بود." }
                DownloadAuditLog.record(appContext, "TTS", "success host=${mirror.label} bytes=$received file=${spec.model}")
                status.value = "بسته از میرور ${mirror.label} دریافت شد؛ اعتبارسنجی و استخراج آغاز می‌شود."
                return
            } catch (failure: Throwable) {
                partial.delete()
                archive.delete()
                val reason = failure.message ?: failure.javaClass.simpleName
                DownloadAuditLog.record(appContext, "TTS", "failure host=${mirror.label} reason=$reason")
                failures += "${index + 1}. ${mirror.label}: $reason"
            } finally {
                connection.disconnect()
            }
        }

        throw IllegalStateException(
            "تمام میرورهای دریافت مدل ناموفق بودند: ${failures.joinToString(" | ")}"
        )
    }

    fun deleteDownloadedVoice(id: String) {
        val target = voiceOptions.firstOrNull { it.first == id }?.first ?: return
        if (loadingId == target) {
            val message = "تا پایان دریافت یا بارگذاری این گوینده، حذف فایل‌ها امکان‌پذیر نیست."
            error.value = message
            status.value = message
            return
        }
        if (loadedId == target) {
            stop()
            try { tts?.release() } catch (_: Throwable) {}
            tts = null
            loadedId = null
            ready.value = false
        }
        val folder = File(appContext.filesDir, "speech/${spec(target).directory}")
        folder.deleteRecursively()
        status.value = "مدل صوتی انتخاب‌شده از حافظهٔ برنامه حذف شد."
    }

    fun speak(text: String, speed: Float = 1f, voiceName: String? = null) {
        if (closed || text.isBlank()) return
        val id = voiceName?.takeIf { name -> voiceOptions.any { it.first == name } } ?: activeId
        val normalizedSpeed = speed.coerceIn(0.75f, 1.35f)
        val engine = tts?.takeIf { loadedId == id }

        if (engine == null) {
            playbackQueue.enqueue(id, text, normalizedSpeed)
            error.value = null
            status.value = "مدل گوینده آماده نیست؛ دریافت/بارگذاری آغاز شد و متن پس از آماده‌شدن خودکار پخش می‌شود."
            selectVoice(id)
            return
        }

        selectVoice(id)
        val request = playbackQueue.enqueue(id, text, normalizedSpeed)
        ready.value = true
        error.value = null
        status.value = "در حال آماده‌سازی صدای فارسی…"
        executor.execute {
            generateAndPlay(engine, request)
        }
    }

    private fun generateAndPlay(engine: OfflineTts, request: QueuedSpeechRequest) {
        var track: AudioTrack? = null
        try {
            if (closed || !playbackQueue.isCurrent(request)) return
            val audio = engine.generate(text = request.text, sid = 0, speed = request.speed)
            check(audio.samples.isNotEmpty()) { "مدل خروجی صوتی تولید نکرد." }
            if (closed || !playbackQueue.isCurrent(request)) return

            val minBuffer = AudioTrack.getMinBufferSize(
                audio.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            val requiredBytes = audio.samples.size.toLong() * 4L
            val bufferBytes = maxOf(
                if (minBuffer > 0) minBuffer.toLong() else 0L,
                requiredBytes
            ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(4)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(audio.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            check(track.state == AudioTrack.STATE_INITIALIZED) { "راه‌اندازی خروجی صوتی گوشی ناموفق بود." }
            val written = track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == audio.samples.size) { "نوشتن داده صوتی کامل انجام نشد (کد $written)." }
            if (closed || !playbackQueue.isCurrent(request)) return

            synchronized(audioLock) {
                if (closed || !playbackQueue.isCurrent(request)) return
                audioTrack = track
                track.play()
            }
            status.value = "در حال پخش: ${voiceOptions.first { it.first == request.voiceId }.second}"
            while (!closed &&
                playbackQueue.isCurrent(request) &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < audio.samples.size
            ) {
                Thread.sleep(40)
            }
            if (!closed && playbackQueue.isCurrent(request)) {
                status.value = "پخش صدا تمام شد."
            }
        } catch (failure: Throwable) {
            if (!closed && playbackQueue.isCurrent(request)) {
                val message = "خطا در تولید یا پخش صدا: ${failure.message ?: failure.javaClass.simpleName}"
                error.value = message
                status.value = message
            }
        } finally {
            track?.let { completed ->
                synchronized(audioLock) {
                    if (audioTrack === completed) audioTrack = null
                }
                runCatching { completed.pause() }
                runCatching { completed.flush() }
                runCatching { completed.release() }
            }
        }
    }

    private fun releaseActiveTrack() {
        val current = synchronized(audioLock) {
            val value = audioTrack
            audioTrack = null
            value
        }
        current?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
    }

    fun stop() {
        playbackQueue.cancel()
        releaseActiveTrack()
        status.value = "پخش متوقف شد."
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        voiceGeneration.incrementAndGet()
        loadingId = null
        loading.value = false
        executor.shutdownNow()
        val engine = tts
        tts = null
        loadedId = null
        ready.value = false
        runCatching { engine?.release() }
    }

}
