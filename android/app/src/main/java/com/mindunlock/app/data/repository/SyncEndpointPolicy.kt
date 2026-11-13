package com.mindunlock.app.data.repository

import java.net.InetAddress

/**
 * Enforces transport security for sync endpoints.
 *
 * Plain HTTP is retained only for private/local networks to support the
 * existing LAN-only Windows companion. Public endpoints must opt into HTTPS.
 */
internal object SyncEndpointPolicy {
    fun baseUrl(input: String, port: Int): String {
        val raw = input.trim()
        require(raw.isNotBlank()) { "آدرس رایانه را وارد کن." }
        val explicitScheme = when {
            raw.startsWith("https://", ignoreCase = true) -> "https"
            raw.startsWith("http://", ignoreCase = true) -> "http"
            raw.contains("://") -> throw IllegalArgumentException("فقط HTTP یا HTTPS پشتیبانی می‌شود.")
            else -> null
        }
        val clean = cleanHost(raw)
        require(clean.isNotBlank()) { "آدرس رایانه را وارد کن." }
        require(port in 1..65535) { "پورت باید بین ۱ تا ۶۵۵۳۵ باشد." }
        require(!clean.contains('/') && !clean.contains('@') && !clean.contains('?') && !clean.contains('#')) {
            "فقط IP یا نام میزبان را وارد کن؛ بدون مسیر یا اطلاعات ورود."
        }

        val local = isPrivateOrLocalHost(clean)
        val scheme = explicitScheme ?: if (local) "http" else "https"
        require(scheme == "https" || local) {
            "برای همگام‌سازی از طریق اینترنت باید HTTPS فعال باشد؛ HTTP فقط برای شبکه خصوصی یا میزبان محلی مجاز است."
        }
        return "$scheme://$clean:$port"
    }

    fun cleanHost(input: String): String = input.trim()
        .replaceFirst(Regex("(?i)^https?://"), "")
        .trimEnd('/')

    private fun isPrivateOrLocalHost(host: String): Boolean {
        val normalized = host.removePrefix("[").removeSuffix("]").trimEnd('.').lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") ||
            normalized.endsWith(".local") || normalized.endsWith(". 내부")) return true

        val octets = normalized.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val a = octets[0].toInt()
            val b = octets[1].toInt()
            return a == 10 || a == 127 || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) || (a == 192 && b == 168)
        }
        if (normalized.contains(':')) {
            val address = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return false
            val first = address.address.firstOrNull()?.toInt()?.and(0xff) ?: return false
            return address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || (first and 0xfe) == 0xfc
        }
        return false
    }
}
