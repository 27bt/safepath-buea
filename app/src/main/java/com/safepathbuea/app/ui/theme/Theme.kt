package com.safepathbuea.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SafePathColorScheme = darkColorScheme(
    background = SafePathBackground,
    surface = SafePathSurface,
    onBackground = SafePathOnSurface,
    onSurface = SafePathOnSurface,
    primary = SafePathPrimary,
    onPrimary = SafePathOnPrimary,
    secondary = SafePathSecondary,
    error = SafePathError,
    outline = SafePathOutline,
)

@Composable
fun SafePathBueaTheme(content: @Composable () -> Unit) {
    // Always dark/high-contrast regardless of system setting: this is a
    // deliberate accessibility choice, not a follow-system default.
    MaterialTheme(
        colorScheme = SafePathColorScheme,
        typography = SafePathTypography,
        content = content,
    )
}
