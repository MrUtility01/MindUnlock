package com.mindunlock.app

import android.content.Context
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal enum class SpeechMirrorSource(val preferenceKey: String) {
    HUGGING_FACE("speech_mirror_hugging_face"),
    GITHUB_RELEASE("speech_mirror_github_release")
}

internal data class SpeechMirrorEndpoint(
    val label: String,
    val url: String
)

/**
 * Resolves ordered HTTPS-only mirrors for public speech-model files.
 *
 * A configured entry may be:
 *  - a base URL, e.g. https://mirror.example.ir
 *  - a proxy template containing {url} or {encodedUrl}
 *
 * Custom endpoints always run first, followed by maintained public mirrors,
 * and the publisher's original URL is always attempted last.
 */
internal object SpeechDownloadMirrors {
    const val PREF_HF_MIRRORS = "speech_mirror_hugging_face"
    const val PREF_GITHUB_MIRRORS = "speech_mirror_github_release"
    const val SETTINGS_FILE = "mindunlock_preferences"

    fun candidates(
        context: Context,
        source: SpeechMirrorSource,
        originalUrl: String
    ): List<SpeechMirrorEndpoint> {
        val preferences = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        val custom = preferences.getString(source.preferenceKey, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val defaults = when (source) {
            SpeechMirrorSource.HUGGING_FACE -> listOf(
                "https://hf-mirror.com",
                "https://beta.hf-mirror.com"
            )
            SpeechMirrorSource.GITHUB_RELEASE -> listOf(
                "https://ghfast.top",
                "https://gh-proxy.com",
                "https://ghproxy.net"
            )
        }

        val resolved = (custom + defaults).mapNotNull { resolve(it, originalUrl) }
        val official = resolve(originalUrl, originalUrl)
        return (resolved + listOfNotNull(official)).distinctBy { it.url }
    }

    private fun resolve(configured: String, originalUrl: String): SpeechMirrorEndpoint? {
        return runCatching {
            val original = URL(originalUrl)
            require(original.protocol.equals("https", ignoreCase = true))
            val candidate = when {
                configured.contains("{encodedUrl}") -> configured.replace(
                    "{encodedUrl}",
                    URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.name())
                )
                configured.contains("{url}") -> configured.replace("{url}", originalUrl)
                else -> {
                    val base = URL(configured)
                    require(base.protocol.equals("https", ignoreCase = true))
                    require(base.userInfo == null && base.host.isNotBlank())
                    val basePath = base.path.trimEnd('/')
                    val originalPath = original.file
                    URL(base.protocol, base.host, base.port, basePath + originalPath).toExternalForm()
                }
            }
            val parsed = URL(candidate)
            require(parsed.protocol.equals("https", ignoreCase = true))
            require(parsed.host.isNotBlank() && parsed.userInfo == null)
            SpeechMirrorEndpoint(parsed.host, parsed.toExternalForm())
        }.getOrNull()
    }
}
