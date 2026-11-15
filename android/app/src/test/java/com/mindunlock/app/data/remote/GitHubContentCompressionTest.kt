package com.mindunlock.app.data.remote

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubContentCompressionTest {
    private fun encodedGzip(value: String): String {
        val bytes = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(value.toByteArray(StandardCharsets.UTF_8)) }
        }.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun decodesBase64GzipUtf8Content() {
        val expected = """{"course":"مسیر پیشرفت فناوری","count":2739}"""
        assertEquals(expected, GitHubContentCompression.decodeBase64Gzip(encodedGzip(expected), 1024))
    }

    @Test
    fun rejectsOutputAboveConfiguredLimit() {
        val encoded = encodedGzip("x".repeat(4096))
        assertThrows(IllegalArgumentException::class.java) {
            GitHubContentCompression.decodeBase64Gzip(encoded, 128)
        }
    }

    @Test
    fun rejectsInvalidBase64() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubContentCompression.decodeBase64Gzip("not-a-gzip-payload!", 1024)
        }
    }
}
