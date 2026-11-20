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
import kotlin.math.roundToInt

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
                status.value = "آزمون بارگذاری و تولید نمونه موفق بود؛ ${voiceOptions.first { it.first == target }.second} آماده است."

                playbackQueue.take(target)?.let { queued ->
                    generateAndPlay(engine, queued)
                }
            }.onFailure { failure ->
                playbackQueue.cancel()
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
        val validId = voiceOptions.firstOrNull { it.first == id }?.first ?: return false
        val modelSpec = spec(validId)
        val dir = File(appContext.filesDir, "speech/" + modelSpec.directory)
        val model = File(dir, modelSpec.model)
        val tokens = File(dir, "tokens.txt")
        val phonemeData = File(dir, "espeak-ng-data")
        return model.isFile && model.length() > 1_000_000L &&
            tokens.isFile && tokens.length() >= 100L &&
            phonemeData.isDirectory && phonemeData.walkTopDown().any { it.isFile && it.length() > 0L }
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
        val tokens = File(modelDir, "tokens.txt")
        val phonemeData = File(modelDir, "espeak-ng-data")
        check(model.isFile && model.length() > 1_000_000L) { "فایل مدل صوتی کامل نیست." }
        check(tokens.isFile && tokens.length() >= 100L && phonemeData.isDirectory &&
            phonemeData.walkTopDown().any { it.isFile && it.length() > 0L }) {
            "فایل‌های واژگان یا داده‌های آوایی فارسی ناقص هستند."
        }
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
            model = model.absolutePath, tokens = tokens.absolutePath,
            dataDir = phonemeData.absolutePath
        ), numThreads = cpuThreads(), debug = false, provider = "cpu"))
        val engine = OfflineTts(config = config)
        try {
            check(engine.numSpeakers() > 0) { "مدل گوینده‌ای ارائه نکرد." }
            val probe = engine.generate(text = "سلام", sid = 0, speed = 1f)
            check(probe.sampleRate > 0 && probe.samples.isNotEmpty()) {
                "مدل بارگذاری شد اما نتوانست نمونه صدای آزمایشی تولید کند."
            }
            return engine
        } catch (failure: Throwable) {
            runCatching { engine.release() }
            throw IllegalStateException(
                "مدل روی حافظه موجود است، اما آزمون واقعی موتور گفتار ناموفق بود: ${failure.message ?: failure.javaClass.simpleName}",
                failure
            )
        }
    }

    private fun downloadAndExtract(spec: ModelSpec, destination: File) {
        val archive = File(appContext.cacheDir, spec.directory + ".tar.bz2")
        val partial = File(appContext.cacheDir, spec.directory + ".tar.bz2.part")
        if (archive.isFile && archive.length() != spec.archiveBytes) archive.delete()
        if (partial.exists() && partial.length() > spec.archiveBytes) partial.delete()
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
            val extractedModel = File(extracted, spec.model)
            val extractedTokens = File(extracted, "tokens.txt")
            val extractedPhonemeData = File(extracted, "espeak-ng-data")
            check(extractedModel.isFile && extractedModel.length() > 1_000_000L &&
                extractedTokens.isFile && extractedTokens.length() >= 100L &&
                extractedPhonemeData.isDirectory &&
                extractedPhonemeData.walkTopDown().any { it.isFile && it.length() > 0L }) {
                "ساختار بسته ناقص است؛ فایل مدل، واژگان یا داده آوایی معتبر نیست."
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
        if (archive.isFile && archive.length() == spec.archiveBytes) return
        if (partial.isFile && partial.length() == spec.archiveBytes) {
            check(partial.renameTo(archive)) { "تبدیل فایل کامل دریافت‌شده به آرشیو ناموفق بود." }
            return
        }
        val sources = SpeechDownloadMirrors.candidates(appContext, SpeechMirrorSource.GITHUB_RELEASE, spec.archive)
            .flatMap { endpoint -> listOf(endpoint, endpoint) }
        val failures = mutableListOf<String>()
        val skipRetryUrls = mutableSetOf<String>()
        check(sources.isNotEmpty()) { "منبع اصلی یا هیچ میروری برای دریافت مدل گفتار در دسترس نیست." }
        for ((index, source) in sources.withIndex()) {
            if (index % 2 == 1 && source.url in skipRetryUrls) continue
            val offset = partial.length().takeIf { it in 1 until spec.archiveBytes } ?: 0L
            if (offset == 0L && partial.exists()) partial.delete()
            downloadedBytes.value = offset
            downloadProgress.value = (offset.toFloat() / spec.archiveBytes.toFloat()).coerceIn(0f, 1f)
            val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 60_000
                readTimeout = 900_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MindUnlock-Android/0.6.32")
                setRequestProperty("Accept", "application/octet-stream")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                val role = if (index / 2 == 0) "official_github_primary" else "configured_mirror_fallback"
                DownloadAuditLog.record(appContext, "TTS", "attempt=${index + 1}/${sources.size} role=$role host=${source.label} file=${spec.model} resume=$offset")
                status.value = if (index / 2 == 0 && index % 2 == 0) "دریافت مستقیم مدل از GitHub؛ در صورت شکست، تلاش مجدد و سپس میرورها…" else if (index % 2 == 1) "ارتباط با ${source.label} قطع شد؛ تلاش مجدد از همان منبع…" else "منبع قبلی ناموفق بود؛ آزمایش Mirror ${index / 2} از ${sources.size / 2 - 1}: ${source.label}"
                connection.connect()
                val response = connection.responseCode
                check(response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_PARTIAL) { "پاسخ HTTP $response" }
                val contentType = connection.contentType.orEmpty().lowercase(java.util.Locale.US)
                check(!contentType.contains("text/html")) { "سرور به‌جای آرشیو مدل، صفحه HTML برگرداند." }
                val appendOffset = if (offset > 0L && response == HttpURLConnection.HTTP_PARTIAL) {
                    val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                    val range = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(contentRange)
                        ?: throw IllegalStateException("پاسخ ادامه دانلود فاقد Content-Range معتبر است.")
                    val first = range.groupValues[1].toLong()
                    val last = range.groupValues[2].toLong()
                    val total = range.groupValues[3].toLong()
                    check(first == offset && last >= first && total == spec.archiveBytes) { "بازه دریافتی با فایل اصلی مطابقت ندارد: $contentRange" }
                    offset
                } else {
                    if (offset > 0L && response == HttpURLConnection.HTTP_OK) {
                        DownloadAuditLog.record(appContext, "TTS", "range_unsupported host=${source.label}; restarting from byte zero")
                        partial.delete()
                    }
                    0L
                }
                val announcedLength = connection.contentLengthLong
                val expectedResponseBytes = spec.archiveBytes - appendOffset
                val announcedLengthMatches = if (appendOffset == 0L) {
                    announcedLength == spec.archiveBytes
                } else {
                    announcedLength == expectedResponseBytes
                }
                if (announcedLength >= 0L) check(announcedLengthMatches) { "اندازه پاسخ با بازه درخواستی برابر نیست ($announcedLength از $expectedResponseBytes بایت)." }
                status.value = "دریافت مدل از ${source.label}؛ ${if (appendOffset > 0L) "ادامه از ${appendOffset} بایت" else "شروع از ابتدا"}…"
                var received = appendOffset
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(partial, appendOffset > 0L).buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            downloadedBytes.value = received
                            downloadProgress.value = (received.toFloat() / spec.archiveBytes.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                }
                check(received == spec.archiveBytes && partial.length() == spec.archiveBytes) { "دانلود کامل نیست؛ $received از ${spec.archiveBytes} بایت دریافت شد." }
                check(partial.renameTo(archive)) { "انتقال بسته دانلودشده ناموفق بود." }
                DownloadAuditLog.record(appContext, "TTS", "success host=${source.label} bytes=$received file=${spec.model}")
                status.value = "بسته از ${source.label} دریافت شد؛ اعتبارسنجی و استخراج آغاز می‌شود."
                return
            } catch (failure: Throwable) {
                if (partial.length() > spec.archiveBytes) partial.delete()
                val reason = failure.message ?: failure.javaClass.simpleName
                DownloadAuditLog.record(appContext, "TTS", "failure host=${source.label} reason=$reason retained_partial=${partial.length()}")
                failures += "${index + 1}. ${source.label}: $reason"
                val transientIoFailure = generateSequence(failure) { it.cause }
                    .any { it is java.io.IOException } ||
                    reason.startsWith("دانلود کامل نیست") ||
                    Regex("پاسخ HTTP (408|425|429|5\\d{2})").containsMatchIn(reason)
                if (!transientIoFailure && index % 2 == 0) skipRetryUrls += source.url
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("ارتباط با منابع دریافت برقرار نشد یا دانلود کامل نشد؛ فایل ناقص برای ادامه دریافت نگهداری شد: ${failures.joinToString(" | ")}")
    }

    fun deleteDownloadedVoice(id: String) {
        val target = voiceOptions.firstOrNull { it.first == id }?.first ?: return
        if (closed) return
        if (loadingId == target) {
            val message = "تا پایان دریافت یا بارگذاری این گوینده، حذف فایل‌ها امکان‌پذیر نیست."
            error.value = message
            status.value = message
            return
        }

        val folder = File(appContext.filesDir, "speech/${spec(target).directory}")
        val engineToRelease = if (loadedId == target) {
            playbackQueue.cancel()
            releaseActiveTrack()
            val current = tts
            tts = null
            loadedId = null
            ready.value = false
            current
        } else {
            null
        }

        status.value = "حذف مدل در صف امن موتور صوتی قرار گرفت…"
        // Keep deletion behind any active native synthesis call. Removing model
        // files or releasing OfflineTts from the UI thread can race JNI inference.
        runCatching {
            executor.execute {
                runCatching { engineToRelease?.release() }
                val deleted = !folder.exists() || folder.deleteRecursively()
                status.value = if (deleted) {
                    "مدل صوتی انتخاب‌شده از حافظه برنامه حذف شد."
                } else {
                    "حذف مدل صوتی کامل نشد؛ فایل‌ها حفظ شدند."
                }
            }
        }.onFailure { failure ->
            error.value = failure.message ?: "قرار دادن حذف مدل در صف ممکن نشد."
            status.value = error.value ?: "حذف مدل صوتی ناموفق بود."
        }
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
            check(audio.sampleRate > 0 && audio.samples.isNotEmpty()) { "مدل خروجی صوتی معتبری تولید نکرد." }
            if (closed || !playbackQueue.isCurrent(request)) return

            val minBuffer = AudioTrack.getMinBufferSize(
                audio.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "قالب خروجی صوتی روی این دستگاه پشتیبانی نمی‌شود (کد $minBuffer)." }
            val bufferBytes = maxOf(minBuffer, 8 * 1024)

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
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            check(track.state == AudioTrack.STATE_INITIALIZED) { "راه‌اندازی خروجی صوتی گوشی ناموفق بود." }
            synchronized(audioLock) {
                if (closed || !playbackQueue.isCurrent(request)) return
                audioTrack = track
                track.play()
            }
            status.value = "در حال پخش: ${voiceOptions.first { it.first == request.voiceId }.second}"

            var sampleOffset = 0
            while (sampleOffset < audio.samples.size) {
                if (closed || !playbackQueue.isCurrent(request)) return
                val sampleCount = minOf(4096, audio.samples.size - sampleOffset)
                val pcmChunk = SpeechAudioPcm16.convert(audio.samples, sampleOffset, sampleCount)
                var chunkOffset = 0
                while (chunkOffset < pcmChunk.size) {
                    if (closed || !playbackQueue.isCurrent(request)) return
                    val written = track.write(
                        pcmChunk,
                        chunkOffset,
                        pcmChunk.size - chunkOffset,
                        AudioTrack.WRITE_BLOCKING
                    )
                    check(written > 0) { "ارسال صدای تولیدشده به خروجی گوشی ناموفق بود (کد $written)." }
                    chunkOffset += written
                }
                sampleOffset += sampleCount
            }

            while (!closed && playbackQueue.isCurrent(request) &&
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

    fun stop(showStatus: Boolean = true) {
        playbackQueue.cancel()
        releaseActiveTrack()
        if (showStatus) status.value = "پخش متوقف شد."
    }

    fun close() {
        if (closed) return
        closed = true
        stop(showStatus = false)
        voiceGeneration.incrementAndGet()
        loadingId = null
        loading.value = false
        val engine = tts
        tts = null
        loadedId = null
        ready.value = false

        // OfflineTts is backed by native code. Queue release behind any active
        // generation/load operation instead of freeing the native handle while
        // another executor task may still be using it.
        runCatching {
            executor.execute { runCatching { engine?.release() } }
            executor.shutdown()
        }.onFailure {
            executor.shutdown()
            runCatching { engine?.release() }
        }
    }

}

/** Converts sherpa-onnx float output to the broadly supported PCM16 AudioTrack format. */
internal object SpeechAudioPcm16 {
    fun convert(samples: FloatArray): ShortArray = convert(samples, 0, samples.size)

    fun convert(samples: FloatArray, offset: Int, length: Int): ShortArray {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) {
            "بازه نمونه‌های صوتی معتبر نیست."
        }
        return ShortArray(length) { index ->
            val raw = samples[offset + index]
            val sample = if (raw.isFinite()) raw.coerceIn(-1f, 1f) else 0f
            val scale = if (sample < 0f) 32768f else 32767f
            (sample * scale).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
