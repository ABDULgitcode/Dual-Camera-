package com.dualframe.prototype.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LandscapeGold = Color(0xFFE8C547)
private val PortraitCyan = Color(0xFF5EC8E8)
private val Ink = Color(0xFF121820)
private val Paper = Color(0xFFF3F0E8)
private val Slate = Color(0xFF2A3444)

private val DarkColors = darkColorScheme(
    primary = LandscapeGold,
    secondary = PortraitCyan,
    background = Ink,
    surface = Slate,
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = Paper,
    onSurface = Paper,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A10),
    secondary = Color(0xFF0E6F86),
    background = Paper,
    surface = Color(0xFFE8E4D8),
    onPrimary = Paper,
    onSecondary = Paper,
    onBackground = Ink,
    onSurface = Ink,
)

val DualFrameLandscapeStroke = LandscapeGold
val DualFramePortraitStroke = PortraitCyan

@Composable
fun DualFrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
