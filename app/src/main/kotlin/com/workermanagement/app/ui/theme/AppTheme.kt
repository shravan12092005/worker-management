package com.workermanagement.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary          = Color(0xFF82AAFF),   // soft blue
    onPrimary        = Color(0xFF00214D),
    primaryContainer = Color(0xFF1A3A6B),
    secondary        = Color(0xFF89DDFF),
    tertiary         = Color(0xFFC3E88D),
    background       = Color(0xFF0F111A),
    surface          = Color(0xFF1A1C2E),
    surfaceVariant   = Color(0xFF252840),
    onBackground     = Color(0xFFE4E8FF),
    onSurface        = Color(0xFFE4E8FF),
    onSurfaceVariant = Color(0xFFAAB0D0),
    outline          = Color(0xFF3A3F60),
    error            = Color(0xFFFF6B6B),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
