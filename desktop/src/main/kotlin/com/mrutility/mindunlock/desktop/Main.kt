package com.mrutility.mindunlock.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection

internal val navigationLabels = listOf(
    "خانه",
    "Inbox",
    "یادگیری",
    "برنامه‌ریزی",
    "ژورنال",
    "دانش",
    "تنظیمات"
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MindUnlock"
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MindUnlockWindow()
        }
    }
}

@Composable
private fun MindUnlockWindow() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MindUnlock", style = MaterialTheme.typography.titleLarge)
                    navigationLabels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("به MindUnlock خوش آمدید", style = MaterialTheme.typography.headlineMedium)
                    Text("نسخه آزمایشی ویندوز؛ محیط محلی و بدون نیاز به اینترنت")
                    Text("وضعیت ذخیره‌سازی: در مرحله پیاده‌سازی")
                }
            }
        }
    }
}
