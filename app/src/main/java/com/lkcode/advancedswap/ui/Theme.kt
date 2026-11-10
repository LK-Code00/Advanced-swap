package com.lkcode.advancedswap.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6338D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE2FF), onPrimaryContainer = Color(0xFF24104F),
    secondary = Color(0xFF087F5B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F5E6), onSecondaryContainer = Color(0xFF002116),
    tertiary = Color(0xFF7651B8),
    background = Color(0xFFF8F5FF), surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF0ECF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8B5FF), onPrimary = Color(0xFF32176F),
    primaryContainer = Color(0xFF4A2D88), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF83DDB5), onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF14543B), onSecondaryContainer = Color(0xFFBDF3D8),
    tertiary = Color(0xFFE2B9FF),
    background = Color(0xFF15121D), surface = Color(0xFF1C1825),
    surfaceVariant = Color(0xFF322C3D),
)

@Composable
fun AdvancedSwapTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

