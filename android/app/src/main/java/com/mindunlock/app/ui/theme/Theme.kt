package com.mindunlock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2696FF),
    onPrimary = Color.White,
    secondary = Color(0xFF7855E8),
    onSecondary = Color.White,
    background = Color(0xFF071321),
    onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF0C1D31),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF102641),
    onSurfaceVariant = Color(0xFF93A9C5),
    error = Color(0xFFEF526B),
    outline = Color(0xFF223A57)
)

@Composable
fun MindUnlockTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
