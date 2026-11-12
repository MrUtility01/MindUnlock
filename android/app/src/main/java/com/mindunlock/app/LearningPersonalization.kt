package com.mindunlock.app

/**
 * Converts self-reported learning preferences into an explainable session strategy.
 * These scores describe preferences only; they never represent ability or measured performance.
 */
data class LearningStrategy(
    val dominantPreference: String,
    val score: Double,
    val teachingEmphasis: String,
    val sessionStructure: List<String>,
    val modeDescription: String,
    val recommendedSessionMinutes: Int,
    val maxActiveTopics: Int
)

object LearningPersonalization {
    private val groups = listOf("دیداری", "عملی", "ساختار", "تعامل", "ریتم")

    fun build(answers: List<Int>, sessionMinutes: Int, maxTopics: Int, guidedMode: String): LearningStrategy? {
        if (answers.size != 20 || answers.any { it !in 1..5 }) return null
        val strongest = groups.mapIndexed { index, group ->
            group to answers.subList(index * 4, index * 4 + 4).average()
        }.maxByOrNull { it.second } ?: return null
        val emphasis = when (strongest.first) {
            "دیداری" -> "شروع با نقشه مفهومی، دیاگرام و نمایش ارتباط اجزا؛ سپس توضیح متنی کوتاه."
            "عملی" -> "شروع با سناریوی واقعی، اجرای مرحله‌ای و مشاهده خروجی؛ سپس جمع‌بندی مفهوم."
            "ساختار" -> "شروع با هدف، پیش‌نیاز، چک‌لیست و مسیر مرحله‌به‌مرحله."
            "تعامل" -> "شروع با پرسش بازیابی، آزمون کوتاه و بازخورد توضیحی پیش از نمایش پاسخ."
            else -> "تقسیم جلسه به بازه‌های کوتاه، تمرین میان‌بازه‌ای و مکث برای مرور."
        }
        val structure = when (strongest.first) {
            "دیداری" -> listOf("نمای کلی و نقشه مفهومی", "توضیح بخش‌ها با دیاگرام", "تمرین تشخیص ارتباط‌ها", "خلاصه تصویری")
            "عملی" -> listOf("سناریوی واقعی", "آماده‌سازی محیط و پیش‌نیاز", "اجرای گام‌ها و ثبت خروجی", "عیب‌یابی و نتیجه‌گیری")
            "ساختار" -> listOf("هدف و معیار پذیرش", "پیش‌نیازها", "آموزش مرحله‌ای", "چک‌لیست و مرور")
            "تعامل" -> listOf("پرسش آغازین", "یادآوری بدون نگاه به پاسخ", "بازخورد و اصلاح", "آزمون پایانی")
            else -> listOf("تمرکز کوتاه اول", "تمرین فعال", "وقفه کوتاه", "مرور و تعیین قدم بعدی")
        }
        return LearningStrategy(
            dominantPreference = strongest.first,
            score = strongest.second,
            teachingEmphasis = emphasis,
            sessionStructure = structure,
            modeDescription = if (guidedMode == "flexible") "آزاد: ترتیب مراحل قابل جابه‌جایی است؛ هدف و معیار پذیرش حفظ می‌شوند." else "هدایت‌شده: مراحل به ترتیب پیشنهادی اجرا می‌شوند و قدم بعدی مشخص است.",
            recommendedSessionMinutes = sessionMinutes.coerceIn(15, 90),
            maxActiveTopics = maxTopics.coerceIn(1, 3)
        )
    }
}
