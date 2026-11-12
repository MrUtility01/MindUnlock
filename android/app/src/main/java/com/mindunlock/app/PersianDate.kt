package com.mindunlock.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

data class JalaliDate(val year: Int, val month: Int, val day: Int)

object PersianDate {
    private data class JalaliYearStart(val gregorianYear: Int, val marchDay: Int, val leap: Int)

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private fun yearStart(jalaliYear: Int): JalaliYearStart {
        require(jalaliYear >= breaks.first() && jalaliYear < breaks.last()) { "سال خارج از محدوده تقویم جلالی است." }
        val gregorianYear = jalaliYear + 621
        var leapJ = -14
        var previousBreak = breaks.first()
        var jump = 0
        var nextBreak = 0
        for (index in 1 until breaks.size) {
            nextBreak = breaks[index]
            jump = nextBreak - previousBreak
            if (jalaliYear < nextBreak) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            previousBreak = nextBreak
        }
        var n = jalaliYear - previousBreak
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gregorianYear / 4 - (((gregorianYear / 100) + 1) * 3) / 4 - 150
        val marchDay = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return JalaliYearStart(gregorianYear, marchDay, leap)
    }

    fun daysInMonth(year: Int, month: Int): Int {
        require(month in 1..12) { "ماه جلالی باید بین ۱ تا ۱۲ باشد." }
        return when {
            month <= 6 -> 31
            month <= 11 -> 30
            runCatching { toGregorian(JalaliDate(year, 12, 30)) }.isSuccess -> 30
            else -> 29
        }
    }

    fun firstWeekdayOffset(year: Int, month: Int): Int {
        val firstGregorianDay = toGregorian(JalaliDate(year, month, 1)).dayOfWeek.value
        return (firstGregorianDay + 1) % 7 // Saturday is the first column.
    }

    fun fromGregorian(date: LocalDate): JalaliDate {
        var jalaliYear = date.year - 621
        var start = yearStart(jalaliYear)
        var newYear = LocalDate.of(start.gregorianYear, 3, start.marchDay)
        if (date.isBefore(newYear)) {
            jalaliYear -= 1
            start = yearStart(jalaliYear)
            newYear = LocalDate.of(start.gregorianYear, 3, start.marchDay)
        }
        val dayOfYear = ChronoUnit.DAYS.between(newYear, date).toInt()
        val month: Int
        val day: Int
        if (dayOfYear < 186) {
            month = dayOfYear / 31 + 1
            day = dayOfYear % 31 + 1
        } else {
            val remainder = dayOfYear - 186
            month = remainder / 30 + 7
            day = remainder % 30 + 1
        }
        return JalaliDate(jalaliYear, month, day)
    }

    fun toGregorian(date: JalaliDate): LocalDate {
        require(date.month in 1..12) { "ماه جلالی باید بین ۱ تا ۱۲ باشد." }
        val start = yearStart(date.year)
        val maxDay = when {
            date.month <= 6 -> 31
            date.month <= 11 -> 30
            start.leap == 0 -> 30
            else -> 29
        }
        require(date.day in 1..maxDay) { "روز برای ماه انتخاب‌شده معتبر نیست." }
        val offset = when {
            date.month <= 6 -> (date.month - 1) * 31 + date.day - 1
            else -> 186 + (date.month - 7) * 30 + date.day - 1
        }
        return LocalDate.of(start.gregorianYear, 3, start.marchDay).plusDays(offset.toLong())
    }

    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), includeTime: Boolean = true): String {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
        val jalali = fromGregorian(dateTime.toLocalDate())
        val date = "%04d/%02d/%02d".format(Locale.ROOT, jalali.year, jalali.month, jalali.day)
        val value = if (includeTime) "$date · %02d:%02d".format(Locale.ROOT, dateTime.hour, dateTime.minute) else date
        return toPersianDigits(value)
    }

    fun format(date: LocalDate): String {
        val jalali = fromGregorian(date)
        return toPersianDigits("%04d/%02d/%02d".format(Locale.ROOT, jalali.year, jalali.month, jalali.day))
    }

    fun formatInput(dateTime: LocalDateTime): String {
        val jalali = fromGregorian(dateTime.toLocalDate())
        return toPersianDigits("%04d/%02d/%02d %02d:%02d".format(
            Locale.ROOT, jalali.year, jalali.month, jalali.day, dateTime.hour, dateTime.minute
        ))
    }

    fun parseDateTime(input: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val normalized = normalizeDigits(input).trim()
        val match = Regex("^(\\d{4})([-/])(\\d{1,2})\\2(\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?$").matchEntire(normalized)
            ?: return null
        return runCatching {
            val year = match.groupValues[1].toInt()
            val separator = match.groupValues[2]
            val month = match.groupValues[3].toInt()
            val day = match.groupValues[4].toInt()
            val hour = match.groupValues[5].takeIf { it.isNotBlank() }?.toInt() ?: 0
            val minute = match.groupValues[6].takeIf { it.isNotBlank() }?.toInt() ?: 0
            require(hour in 0..23 && minute in 0..59)
            val date = if (separator == "/") toGregorian(JalaliDate(year, month, day)) else LocalDate.of(year, month, day)
            date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun normalizeDigits(value: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        return buildString(value.length) {
            value.forEach { char ->
                val p = persian.indexOf(char)
                val a = arabic.indexOf(char)
                append(when {
                    p >= 0 -> ('0'.code + p).toChar()
                    a >= 0 -> ('0'.code + a).toChar()
                    else -> char
                })
            }
        }
    }

    fun toPersianDigits(value: String): String {
        val digits = "۰۱۲۳۴۵۶۷۸۹"
        return buildString(value.length) {
            value.forEach { char ->
                append(if (char in '0'..'9') digits[char - '0'] else char)
            }
        }
    }
}
