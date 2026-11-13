package com.mindunlock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MindUnlockShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun MindUnlockTheme(themeName: String = "light", content: @Composable () -> Unit) {
    val colors = when (themeName) {
        "light" -> lightColorScheme(
            primary = Color(0xFF176E72),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD0F2EF),
            onPrimaryContainer = Color(0xFF073B3E),
            secondary = Color(0xFF6258A6),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8E3FF),
            tertiary = Color(0xFF9B5D28),
            background = Color(0xFFF3F6FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE9EFF6),
            onSurface = Color(0xFF172B3A),
            onBackground = Color(0xFF172B3A),
            outline = Color(0xFFB5C4D2)
        )
        "sepia" -> lightColorScheme(
            primary = Color(0xFF80572F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEAD8B9),
            onPrimaryContainer = Color(0xFF3F2C1A),
            secondary = Color(0xFF6F6851),
            secondaryContainer = Color(0xFFE9E1CC),
            tertiary = Color(0xFF7B5846),
            background = Color(0xFFF5EEDC),
            surface = Color(0xFFFFF9EC),
            surfaceVariant = Color(0xFFECE0C8),
            onSurface = Color(0xFF403626),
            onBackground = Color(0xFF403626),
            outline = Color(0xFFC9B99A)
        )
        else -> darkColorScheme(
            primary = Color(0xFF62D8D0),
            onPrimary = Color(0xFF052D31),
            primaryContainer = Color(0xFF164A50),
            onPrimaryContainer = Color(0xFFB9F6F0),
            secondary = Color(0xFFB6A8FF),
            onSecondary = Color(0xFF282052),
            secondaryContainer = Color(0xFF3A326A),
            tertiary = Color(0xFFFFC47D),
            background = Color(0xFF0A1220),
            surface = Color(0xFF111D2D),
            surfaceVariant = Color(0xFF1B2B40),
            onSurface = Color(0xFFEAF2FA),
            onBackground = Color(0xFFEAF2FA),
            outline = Color(0xFF486079)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = MindUnlockShapes,
        content = content
    )
}
