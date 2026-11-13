package com.mindunlock.app.data.repository

/** Validates a sync endpoint and preserves an explicitly selected HTTPS scheme. */
internal object SyncEndpointPolicy {
    fun baseUrl(input: String, port: Int): String {
        val raw = input.trim()
        require(raw.isNotBlank()) { "آدرس رایانه را وارد کن." }
        val scheme = when {
            raw.startsWith("https://", ignoreCase = true) -> "https"
            raw.startsWith("http://", ignoreCase = true) -> "http"
            raw.contains("://") -> throw IllegalArgumentException("فقط HTTP یا HTTPS پشتیبانی می‌شود.")
            else -> "http"
        }
        val clean = cleanHost(raw)
        require(clean.isNotBlank()) { "آدرس رایانه را وارد کن." }
        require(port in 1..65535) { "پورت باید بین ۱ تا ۶۵۵۳۵ باشد." }
        require(!clean.contains('/') && !clean.contains('@') && !clean.contains('?') && !clean.contains('#')) {
            "فقط IP یا نام میزبان را وارد کن؛ بدون مسیر یا اطلاعات ورود."
        }
        return "$scheme://$clean:$port"
    }

    fun cleanHost(input: String): String = input.trim()
        .removePrefix("http://").removePrefix("https://")
        .removePrefix("HTTP://").removePrefix("HTTPS://")
        .trimEnd('/')
}