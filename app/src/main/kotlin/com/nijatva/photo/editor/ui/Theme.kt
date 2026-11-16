package com.nijatva.photo.editor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NijatvaTeal = Color(0xFF00E0B2)
val NijatvaBlue = Color(0xFF3B82F6)
val NijatvaViolet = Color(0xFF8B5CF6)
val NijatvaPink = Color(0xFFEC4899)

val AmoledBlack = Color(0xFF000000)
val Surface1 = Color(0xFF121212)
val Surface2 = Color(0xFF1C1C1E)
val TextPrimary = Color(0xFFF2F2F2)
val TextSecondary = Color(0xFF9A9A9E)

private val NijatvaColors = darkColorScheme(
    primary = NijatvaTeal,
    onPrimary = Color.Black,
    secondary = NijatvaBlue,
    onSecondary = Color.Black,
    tertiary = NijatvaViolet,
    onTertiary = Color.Black,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2A2A2E),
    error = NijatvaPink
)

@Composable
fun NijatvaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NijatvaColors, content = content)
}
