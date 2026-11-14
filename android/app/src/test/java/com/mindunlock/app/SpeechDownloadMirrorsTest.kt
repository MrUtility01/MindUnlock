package com.mindunlock.app

import org.robolectric.RuntimeEnvironment
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechDownloadMirrorsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(SpeechDownloadMirrors.SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(SpeechDownloadMirrors.SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun shippedDefaultsAreVisibleAsEditableSettingsValues() {
        assertEquals(
            listOf("https://hf-mirror.com", "https://beta.hf-mirror.com"),
            SpeechDownloadMirrors.defaultAddresses(SpeechMirrorSource.HUGGING_FACE)
        )
        assertEquals(
            listOf(
                "https://ghfast.top/{url}",
                "https://gh-proxy.com/{url}",
                "https://ghproxy.net/{url}"
            ),
            SpeechDownloadMirrors.defaultAddresses(SpeechMirrorSource.GITHUB_RELEASE)
        )
        assertTrue(
            SpeechDownloadMirrors.defaultEditableText(SpeechMirrorSource.GITHUB_RELEASE)
                .contains("\\n")
        )
    }

    @Test
    fun blankSavedMirrorListFallsBackToShippedDefaults() {
        context.getSharedPreferences(SpeechDownloadMirrors.SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(SpeechDownloadMirrors.PREF_GITHUB_MIRRORS, "")
            .commit()

        val original = "https://github.com/owner/repo/releases/download/v1/model.tar.bz2"
        val candidates = SpeechDownloadMirrors.candidates(
            context,
            SpeechMirrorSource.GITHUB_RELEASE,
            original
        )

        assertEquals("ghfast.top", candidates.first().label)
        assertEquals(original, candidates.last().url)
    }

    @Test
    fun customSavedListReplacesBuiltInDefaultsAndKeepsOfficialLast() {
        context.getSharedPreferences(SpeechDownloadMirrors.SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(SpeechDownloadMirrors.PREF_GITHUB_MIRRORS, "https://custom.example.ir/{url}")
            .commit()

        val original = "https://github.com/owner/repo/releases/download/v1/model.tar.bz2"
        val candidates = SpeechDownloadMirrors.candidates(
            context,
            SpeechMirrorSource.GITHUB_RELEASE,
            original
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.first().url.startsWith("https://custom.example.ir/"))
        assertEquals(original, candidates.last().url)
    }

    @Test
    fun customIranianEndpointIsTriedFirstAndOfficialHostLast() {
        context.getSharedPreferences(SpeechDownloadMirrors.SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(
                SpeechDownloadMirrors.PREF_HF_MIRRORS,
                "https://iran-mirror.example.ir\nhttp://insecure.example.ir\nhttps://proxy.example.ir/fetch?url={url}"
            )
            .commit()

        val original = "https://huggingface.co/org/model/resolve/main/model.onnx?download=true"
        val candidates = SpeechDownloadMirrors.candidates(
            context,
            SpeechMirrorSource.HUGGING_FACE,
            original
        )

        assertEquals(
            "https://iran-mirror.example.ir/org/model/resolve/main/model.onnx?download=true",
            candidates.first().url
        )
        assertTrue(candidates[1].url.startsWith("https://proxy.example.ir/fetch?url=https://huggingface.co/"))
        assertEquals(original, candidates.last().url)
        assertTrue(candidates.all { it.url.startsWith("https://") })
    }

    @Test
    fun githubReleaseHasSeveralFallbacksBeforeOfficialUrl() {
        val original = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1/model.tar.bz2"
        val candidates = SpeechDownloadMirrors.candidates(
            context,
            SpeechMirrorSource.GITHUB_RELEASE,
            original
        )

        assertEquals("ghfast.top", candidates.first().label)
        assertTrue(candidates.size >= 4)
        assertEquals(original, candidates.last().url)
        assertTrue(candidates.dropLast(1).all { it.url.startsWith("https://") })
    }

    @Test
    fun encodedUrlTemplateIsExpanded() {
        context.getSharedPreferences(SpeechDownloadMirrors.SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(SpeechDownloadMirrors.PREF_GITHUB_MIRRORS, "https://proxy.example.ir/get?target={encodedUrl}")
            .commit()

        val original = "https://github.com/owner/repo/releases/download/v1/model.tar.bz2"
        val first = SpeechDownloadMirrors.candidates(
            context,
            SpeechMirrorSource.GITHUB_RELEASE,
            original
        ).first()

        assertTrue(first.url.startsWith("https://proxy.example.ir/get?target=https%3A%2F%2Fgithub.com%2F"))
    }
}
