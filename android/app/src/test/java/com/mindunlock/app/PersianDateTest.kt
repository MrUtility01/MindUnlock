package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PersianDateTest {
    @Test
    fun convertsNowruzBoundariesForRecentYears() {
        assertEquals(JalaliDate(1403, 1, 1), PersianDate.fromGregorian(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1403, 12, 30), PersianDate.fromGregorian(LocalDate.of(2025, 3, 20)))
        assertEquals(JalaliDate(1404, 1, 1), PersianDate.fromGregorian(LocalDate.of(2025, 3, 21)))
        assertEquals(JalaliDate(1405, 1, 1), PersianDate.fromGregorian(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun convertsAcrossMonthBoundaries() {
        assertEquals(JalaliDate(1405, 1, 31), PersianDate.fromGregorian(LocalDate.of(2026, 4, 20)))
        assertEquals(JalaliDate(1405, 7, 1), PersianDate.fromGregorian(LocalDate.of(2026, 9, 23)))
        assertEquals(JalaliDate(1405, 8, 21), PersianDate.fromGregorian(LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun calculatesJalaliMonthLengthsIncludingLeapEsfand() {
        assertEquals(31, PersianDate.daysInMonth(1405, 1))
        assertEquals(30, PersianDate.daysInMonth(1405, 7))
        assertEquals(30, PersianDate.daysInMonth(1403, 12))
        assertEquals(29, PersianDate.daysInMonth(1404, 12))
        assertEquals(29, PersianDate.daysInMonth(1405, 12))
    }

    @Test
    fun alignsCalendarWeeksWithSaturdayAsFirstDay() {
        // 1405/01/01 was Saturday; 1405/08/01 was Friday.
        assertEquals(0, PersianDate.firstWeekdayOffset(1405, 1))
        assertEquals(6, PersianDate.firstWeekdayOffset(1405, 8))
    }

    @Test
    fun convertsJalaliDatesBackToGregorian() {
        assertEquals(LocalDate.of(2024, 3, 20), PersianDate.toGregorian(JalaliDate(1403, 1, 1)))
        assertEquals(LocalDate.of(2025, 3, 20), PersianDate.toGregorian(JalaliDate(1403, 12, 30)))
        assertEquals(LocalDate.of(2026, 11, 12), PersianDate.toGregorian(JalaliDate(1405, 8, 21)))
    }

    @Test
    fun parsesPersianDigitsAndKeepsIsoGregorianCompatibility() {
        val zone = ZoneId.of("UTC")
        assertEquals(
            LocalDate.of(2026, 11, 12).atTime(9, 5).atZone(zone).toInstant().toEpochMilli(),
            PersianDate.parseDateTime("۱۴۰۵/۰۸/۲۱ ۰۹:۰۵", zone)
        )
        assertEquals(
            LocalDate.of(2026, 11, 12).atTime(9, 5).atZone(zone).toInstant().toEpochMilli(),
            PersianDate.parseDateTime("2026-11-12 09:05", zone)
        )
    }

    @Test
    fun roundTripsRepresentativeDatesAcrossJalaliYearsAndMonths() {
        val dates = listOf(
            JalaliDate(1399, 1, 1), JalaliDate(1400, 6, 31),
            JalaliDate(1401, 7, 1), JalaliDate(1402, 11, 30),
            JalaliDate(1403, 12, 30), JalaliDate(1404, 12, 29),
            JalaliDate(1405, 8, 21), JalaliDate(1406, 1, 1)
        )
        dates.forEach { jalali ->
            assertEquals(jalali, PersianDate.fromGregorian(PersianDate.toGregorian(jalali)))
        }
    }

    @Test
    fun acceptsArabicIndicDigitsAndFormatsPersianDigits() {
        val zone = ZoneId.of("UTC")
        assertEquals(
            LocalDate.of(2026, 11, 12).atTime(9, 5).atZone(zone).toInstant().toEpochMilli(),
            PersianDate.parseDateTime("١٤٠٥/٠٨/٢١ ٠٩:٠٥", zone)
        )
        assertEquals("۱۴۰۵/۰۸/۲۱", PersianDate.format(LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun keepsCalendarDateStableAcrossExplicitTimeZones() {
        val instant = LocalDate.of(2026, 3, 20).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("۱۴۰۴/۱۲/۲۹", PersianDate.format(instant, ZoneId.of("UTC"), includeTime = false))
        assertEquals("۱۴۰۴/۱۲/۲۹", PersianDate.format(instant, ZoneId.of("Europe/Berlin"), includeTime = false))
    }

    @Test
    fun rejectsInvalidJalaliDatesAndTimes() {
        assertNull(PersianDate.parseDateTime("1404/12/30 10:00", ZoneId.of("UTC")))
        assertNull(PersianDate.parseDateTime("1405/13/01 10:00", ZoneId.of("UTC")))
        assertNull(PersianDate.parseDateTime("1405/08/21 25:00", ZoneId.of("UTC")))
    }
}
