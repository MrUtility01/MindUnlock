package com.mindunlock.app

/**
 * Conservative Persian text cleanup for offline dictation.
 * This deliberately avoids broad autocorrection so command names and IP/network terms survive.
 */
internal object PersianTextNormalizer {
    private val unitNumbers = mapOf(
        "صفر" to 0L, "یک" to 1L, "دو" to 2L, "سه" to 3L, "چهار" to 4L,
        "پنج" to 5L, "شش" to 6L, "شیش" to 6L, "هفت" to 7L, "هشت" to 8L, "نه" to 9L,
        "ده" to 10L, "یازده" to 11L, "دوازده" to 12L, "سیزده" to 13L,
        "چهارده" to 14L, "پانزده" to 15L, "پونزده" to 15L, "شانزده" to 16L,
        "شونزده" to 16L, "هفده" to 17L, "هیفده" to 17L, "هجده" to 18L,
        "هیجده" to 18L, "نوزده" to 19L
    )
    private val tens = mapOf(
        "بیست" to 20L, "سی" to 30L, "چهل" to 40L, "پنجاه" to 50L,
        "شصت" to 60L, "هفتاد" to 70L, "هشتاد" to 80L, "نود" to 90L
    )
    private val hundreds = mapOf(
        "صد" to 100L, "یکصد" to 100L, "دویست" to 200L, "سیصد" to 300L,
        "چهارصد" to 400L, "پانصد" to 500L, "پونصد" to 500L, "ششصد" to 600L,
        "شیشصد" to 600L, "هفتصد" to 700L, "هشتصد" to 800L, "نهصد" to 900L
    )
    private val scales = mapOf("هزار" to 1_000L, "میلیون" to 1_000_000L, "میلیارد" to 1_000_000_000L)

    private val spokenTerms = listOf(
        "میکرو تیک" to "MikroTik",
        "میکروتیک" to "MikroTik",
        "فورتی گیت" to "FortiGate",
        "فورتیگیت" to "FortiGate",
        "ایزابل" to "Issabel",
        "استریسک" to "Asterisk",
        "دی اچ سی پی" to "DHCP",
        "دی‌اچ‌سی‌پی" to "DHCP",
        "دی ان اس" to "DNS",
        "دی‌ان‌اس" to "DNS",
        "وی لن" to "VLAN",
        "وی‌لن" to "VLAN",
        "اکتیو دایرکتوری" to "Active Directory",
        "میکرو‌تیک" to "MikroTik",
        "فورتی‌گیت" to "FortiGate"
    )

    private data class WordToken(val prefix: String, val core: String, val suffix: String)

    private val tokenPattern = Regex("^([^\\p{L}\\p{N}]*)([\\p{L}\\p{N}]+)([^\\p{L}\\p{N}]*)$")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        var text = input
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('\u0660', '۰').replace('\u0661', '۱').replace('\u0662', '۲')
            .replace('\u0663', '۳').replace('\u0664', '۴').replace('\u0665', '۵')
            .replace('\u0666', '۶').replace('\u0667', '۷').replace('\u0668', '۸')
            .replace('\u0669', '۹')
            .replace(Regex("[ \\t\\r\\n]+"), " ")
            .trim()

        text = normalizePersianHalfSpaces(text)
        // Protect spoken letter names inside infrastructure abbreviations (for example,
        // "سی" in "دی اچ سی پی") before number ITN turns them into numeric values.
        for ((spoken, technical) in spokenTerms) {
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(spoken) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            )
            text = pattern.replace(text, technical)
        }
        text = normalizeSpokenNumbers(text)

        return text
            .replace(Regex(" +([،؛:,.!?؟])"), "$1")
            .replace(Regex("([،؛؟!])(?=\\S)"), "$1 ")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    private fun normalizePersianHalfSpaces(text: String): String =
        text
            // Persian verbal prefixes: می‌رود، نمی‌شود
            .replace(Regex("(?<![آ-ی])(نمی|می)\\s+([آ-ی]+)"), "$1‌$2")
            // Common Persian suffixes: کتاب‌ها، فایل‌های، سریع‌تر، دقیق‌ترین
            .replace(Regex("([آ-ی]+)\\s+(ها|های|تر|ترین)(?![آ-ی])"), "$1‌$2")

    private fun normalizeSpokenNumbers(text: String): String {
        val tokens = text.split(Regex("\\s+"))
        if (tokens.isEmpty()) return text
        val result = ArrayList<String>(tokens.size)
        var index = 0

        while (index < tokens.size) {
            val first = parseToken(tokens[index])
            if (first == null || numberValue(first.core) == null) {
                result.add(tokens[index])
                index++
                continue
            }

            var total = 0L
            var current = 0L
            var cursor = index
            var lastSuffix = ""
            while (cursor < tokens.size) {
                val part = parseToken(tokens[cursor])
                if (part == null) break
                val value = numberValue(part.core)
                if (value != null) {
                    when (val scale = scales[part.core]) {
                        null -> current = (current + value).coerceAtMost(Long.MAX_VALUE / 2)
                        else -> {
                            val multiplier = if (current == 0L) 1L else current
                            total = (total + multiplier * scale).coerceAtMost(Long.MAX_VALUE / 2)
                            current = 0L
                        }
                    }
                    lastSuffix = part.suffix
                    cursor++
                } else if (
                    part.core == "و" &&
                    cursor + 1 < tokens.size &&
                    parseToken(tokens[cursor + 1])?.core?.let { numberValue(it) != null } == true
                ) {
                    cursor++
                } else {
                    break
                }
            }

            val value = (total + current).coerceAtLeast(0L)
            result.add(first.prefix + toPersianDigits(value) + lastSuffix)
            index = cursor
        }
        return result.joinToString(" ")
    }

    private fun parseToken(token: String): WordToken? {
        val match = tokenPattern.matchEntire(token) ?: return null
        return WordToken(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }

    private fun numberValue(word: String): Long? =
        unitNumbers[word] ?: tens[word] ?: hundreds[word] ?: scales[word]

    private fun toPersianDigits(value: Long): String =
        value.toString().map { char ->
            if (char in '0'..'9') ('۰'.code + (char.code - '0'.code)).toChar() else char
        }.joinToString("")
}
