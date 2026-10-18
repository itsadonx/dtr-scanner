package com.dtr.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SecureAttendColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = NavyDeep,
    secondary = SkyAccent,
    onSecondary = NavyDeep,
    background = ScreenBackground,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE8EEF9),
    onSurfaceVariant = TextMuted,
    outline = Color(0xFFCBD5E1),
)

@Composable
fun DtrScannerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SecureAttendColors,
        content = content,
    )
}
