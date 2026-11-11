package com.mrutility.mindunlock.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationLabelsTest {
    @Test
    fun navigationContainsRequiredPersianModules() {
        assertEquals(7, navigationLabels.size)
        assertTrue(navigationLabels.contains("یادگیری"))
        assertTrue(navigationLabels.contains("برنامه‌ریزی"))
        assertTrue(navigationLabels.contains("تنظیمات"))
    }
}
