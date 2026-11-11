package com.mindunlock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MindUnlockTheme(themeName: String = "dark", content: @Composable () -> Unit) {
    val colors = when (themeName) {
        "light" -> lightColorScheme(
            primary = Color(0xFF176D74), onPrimary = Color.White,
            secondary = Color(0xFF5C5AA7), background = Color(0xFFF4F8F8),
            surface = Color.White, surfaceVariant = Color(0xFFE7F0F0),
            onSurface = Color(0xFF173B43), onBackground = Color(0xFF173B43)
        )
        "sepia" -> lightColorScheme(
            primary = Color(0xFF80572F), onPrimary = Color.White,
            secondary = Color(0xFF6F6851), background = Color(0xFFF5EEDC),
            surface = Color(0xFFFFF9EC), surfaceVariant = Color(0xFFECE0C8),
            onSurface = Color(0xFF403626), onBackground = Color(0xFF403626)
        )
        else -> darkColorScheme(
            primary = Color(0xFF42B6B4), onPrimary = Color(0xFF062C31),
            secondary = Color(0xFFA99BE8), background = Color(0xFF09151D),
            surface = Color(0xFF11232D), surfaceVariant = Color(0xFF1A3441),
            onSurface = Color(0xFFE8F2F3), onBackground = Color(0xFFE8F2F3),
            outline = Color(0xFF42616E)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
