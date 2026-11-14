package com.mindunlock.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.mindunlock.app.data.local.LearningProfileEntity
import com.mindunlock.app.data.local.UserDatabase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

private data class ProfileQuestion(val group: String, val prompt: String)

private val profileQuestions = listOf(
    ProfileQuestion("دیداری", "نمودار، دیاگرام و نقشه ذهنی به فهم بهتر من کمک می‌کند."),
    ProfileQuestion("دیداری", "ترجیح می‌دهم ساختار کلی موضوع را قبل از جزئیات ببینم."),
    ProfileQuestion("دیداری", "تصویر و مثال تصویری را بهتر از توضیح صرفاً متنی به خاطر می‌سپارم."),
    ProfileQuestion("دیداری", "رنگ‌بندی و دسته‌بندی بصری به مرتب‌سازی اطلاعات کمک می‌کند."),
    ProfileQuestion("عملی", "با انجام آزمایش و کار عملی بهتر یاد می‌گیرم."),
    ProfileQuestion("عملی", "ترجیح می‌دهم هر مفهوم را با یک سناریوی واقعی محیط کار تمرین کنم."),
    ProfileQuestion("عملی", "اجرای دستورها و مشاهده خروجی برایم از خواندن توضیح طولانی مؤثرتر است."),
    ProfileQuestion("عملی", "عیب‌یابی مرحله‌به‌مرحله باعث تثبیت یادگیری من می‌شود."),
    ProfileQuestion("ساختار", "آموزش مرحله‌ای از مقدماتی تا پیشرفته را ترجیح می‌دهم."),
    ProfileQuestion("ساختار", "قبل از شروع درس، هدف و پیش‌نیازها را بدانم بهتر پیش می‌روم."),
    ProfileQuestion("ساختار", "چک‌لیست و مسیر مشخص به حفظ تمرکز من کمک می‌کند."),
    ProfileQuestion("ساختار", "پس از وقفه، یک اقدام بعدی روشن برای ادامه لازم دارم."),
    ProfileQuestion("تعامل", "پرسش و پاسخ و آزمون کوتاه به یادگیری من کمک می‌کند."),
    ProfileQuestion("تعامل", "دوست دارم ابتدا خودم پاسخ را به یاد بیاورم و بعد پاسخ صحیح را ببینم."),
    ProfileQuestion("تعامل", "بازخورد دقیق درباره اشتباهات برایم مهم است."),
    ProfileQuestion("تعامل", "مرور فاصله‌دار و تکرار برنامه‌ریزی‌شده را مفید می‌دانم."),
    ProfileQuestion("ریتم", "جلسه‌های کوتاه و متمرکز را به مطالعه طولانی ترجیح می‌دهم."),
    ProfileQuestion("ریتم", "بهتر است در هر بازه روی تعداد کمی موضوع فعال تمرکز کنم."),
    ProfileQuestion("ریتم", "ترجیح می‌دهم پس از یادگیری، زمانی برای تمرین و مرور داشته باشم."),
    ProfileQuestion("ریتم", "امکان تغییر سرعت و روش آموزش بر اساس شرایط روزانه برایم مهم است.")
)

@Composable
fun LearningProfileScreen(preferences: android.content.SharedPreferences, userDatabase: UserDatabase) {
    val saved = remember(preferences) {
        preferences.getString("learning_profile_answers", "")
            .orEmpty().split(",").mapNotNull { it.toIntOrNull() }
    }
    val answers = remember {
        mutableStateListOf<Int>().apply {
            repeat(20) { index -> add(saved.getOrNull(index) ?: 0) }
        }
    }
    var sessionMinutes by remember {
        mutableStateOf(preferences.getInt("learning_session_minutes", 30).toFloat())
    }
    var maxTopics by remember {
        mutableStateOf(preferences.getInt("learning_max_topics", 2))
    }
    var guidedMode by remember {
        mutableStateOf(preferences.getString("learning_mode", "guided") ?: "guided")
    }
    var savedMessage by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(userDatabase, preferences) {
        val stored = userDatabase.learningProfileDao().get()
        if (stored != null) {
            val values = stored.answersCsv.split(",").mapNotNull { it.toIntOrNull() }
            if (values.size == 20) values.forEachIndexed { index, value -> answers[index] = value }
            sessionMinutes = stored.sessionMinutes.toFloat()
            maxTopics = stored.maxTopics
            guidedMode = stored.guidedMode
        } else if (preferences.getBoolean("learning_profile_completed", false)) {
            userDatabase.learningProfileDao().upsert(LearningProfileEntity(
                answersCsv = answers.joinToString(","), sessionMinutes = sessionMinutes.toInt(),
                maxTopics = maxTopics, guidedMode = guidedMode, completed = true
            ))
        }
    }
    val answered = answers.count { it in 1..5 }
    val groupScores = profileQuestions.indices.groupBy { profileQuestions[it].group }
        .mapValues { (_, indices) ->
            val values = indices.mapNotNull { answers[it].takeIf { value -> value in 1..5 } }
            if (values.isEmpty()) null else values.average()
        }

    val strategy = LearningPersonalization.build(answers.toList(), sessionMinutes.toInt(), maxTopics, guidedMode)

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پروفایل یادگیری من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("این ارزیابی ترجیحات شخصی تو را ثبت می‌کند؛ نتیجه آن سنجش توانایی یا سطح مهارت نیست.", style = MaterialTheme.typography.bodyMedium)
                    Text("پاسخ داده‌شده: $answered از ۲۰", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { answered / 20f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Text("روش پاسخ‌گویی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("برای هر جمله از ۱ تا ۵ انتخاب کن؛ ۱ یعنی کاملاً مخالفم و ۵ یعنی کاملاً موافقم.", style = MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(profileQuestions) { index, question ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${question.prompt}", fontWeight = FontWeight.Medium)
                    Text(question.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { value ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RadioButton(
                                    selected = answers[index] == value,
                                    onClick = { answers[index] = value }
                                )
                                Text(value.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تنظیمات پیشنهادی یادگیری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("مدت هر جلسه: ${sessionMinutes.toInt()} دقیقه")
                    Slider(value = sessionMinutes, onValueChange = { sessionMinutes = it }, valueRange = 15f..90f, steps = 14)
                    Text("حداکثر موضوع فعال هم‌زمان: $maxTopics")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { value ->
                            FilterChip(selected = maxTopics == value, onClick = { maxTopics = value }, label = { Text(value.toString()) })
                        }
                    }
                    Text("سبک هدایت یادگیری")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = guidedMode == "guided", onClick = { guidedMode = "guided" }, label = { Text("هدایت‌شده") })
                        FilterChip(selected = guidedMode == "flexible", onClick = { guidedMode = "flexible" }, label = { Text("آزاد") })
                    }
                    Text("هدایت‌شده: برنامه و گام بعدی مشخص؛ آزاد: انتخاب مسیر و ترتیب با خودت.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("خلاصه ترجیحات خوداظهاری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    groupScores.forEach { (group, score) ->
                        if (score != null) Text("$group: ${"%.1f".format(java.util.Locale.ROOT, score)} از ۵")
                    }
                    Text("این خلاصه فقط از پاسخ‌های تو محاسبه می‌شود. عملکرد واقعی در آزمون‌ها، تمرین‌ها و مرورها جداگانه ثبت خواهد شد و با این ترجیحات یکی فرض نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (strategy != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("روش پیشنهادی آموزش بر اساس ترجیحات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("گرایش پررنگ‌تر: ${strategy.dominantPreference} (میانگین ${"%.1f".format(java.util.Locale.ROOT, strategy.score)} از ۵)")
                        Text(strategy.teachingEmphasis)
                        Text("ساختار پیشنهادی جلسه", fontWeight = FontWeight.SemiBold)
                        strategy.sessionStructure.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                        Text("مدت جلسه: ${strategy.recommendedSessionMinutes} دقیقه؛ حداکثر موضوع فعال: ${strategy.maxActiveTopics}")
                        Text(strategy.modeDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("این پیشنهاد فقط از ترجیحات خوداظهاری ساخته شده است؛ عملکرد واقعی جداگانه سنجیده می‌شود.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    val profile = LearningProfileEntity(answersCsv = answers.joinToString(","), sessionMinutes = sessionMinutes.toInt(), maxTopics = maxTopics, guidedMode = guidedMode, completed = true)
                    scope.launch {
                        runCatching { userDatabase.learningProfileDao().upsert(profile) }
                            .onSuccess {
                                preferences.edit().putString("learning_profile_answers", profile.answersCsv).putInt("learning_session_minutes", profile.sessionMinutes).putInt("learning_max_topics", profile.maxTopics).putString("learning_mode", profile.guidedMode).putBoolean("learning_profile_completed", true).apply()
                                savedMessage = "پروفایل در پایگاه داده و پشتیبان برنامه ذخیره شد."
                            }
                            .onFailure { savedMessage = "ذخیره پروفایل ناموفق بود: ${it.message ?: "خطای پایگاه داده"}" }
                    }
                },
                enabled = answered == 20,
                modifier = Modifier.fillMaxWidth()
            ) { Text("ذخیره پروفایل") }
            if (savedMessage.isNotBlank()) Text(savedMessage, color = MaterialTheme.colorScheme.primary)
            if (answered < 20) Text("برای ذخیره ارزیابی، به همه ۲۰ سؤال پاسخ بده.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
