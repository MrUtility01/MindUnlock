package com.mindunlock.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianTextNormalizerTest {
    @Test
    fun normalizesPersianCharactersAndArabicDigits() {
        assertEquals("کیبورد ۱۲۳", PersianTextNormalizer.normalize("كيبورد ١٢٣"))
    }

    @Test
    fun convertsSpokenPersianNumbers() {
        assertEquals("۱۲۵ دستگاه", PersianTextNormalizer.normalize("صد و بیست و پنج دستگاه"))
        assertEquals("۲۳۴۵ کاربر", PersianTextNormalizer.normalize("دو هزار و سیصد و چهل و پنج کاربر"))
        assertEquals("۱۲۰۰۰۰۰", PersianTextNormalizer.normalize("یک میلیون و دویست هزار"))
    }

    @Test
    fun preservesIpAddressesAndTechnicalTerms() {
        assertEquals(
            "MikroTik و FortiGate و DHCP و 192.168.1.10",
            PersianTextNormalizer.normalize("میکروتیک و فورتی گیت و دی اچ سی پی و 192.168.1.10")
        )
    }
}
