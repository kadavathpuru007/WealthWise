package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FintechBlue,
    onPrimary = Color.White,
    primaryContainer = FintechBlueBg,
    onPrimaryContainer = FintechBlue,
    secondary = FintechGreen,
    onSecondary = Color.White,
    secondaryContainer = FintechGreenBg,
    onSecondaryContainer = FintechGreen,
    tertiary = FintechOrange,
    onTertiary = Color.White,
    tertiaryContainer = FintechOrangeBg,
    onTertiaryContainer = FintechOrange,
    background = Color.White,
    onBackground = TextNavy,
    surface = CardWhite,
    onSurface = TextNavy,
    surfaceVariant = Color.White,
    onSurfaceVariant = TextBlueGrey,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = FintechRed,
    onError = Color.White
)

@Composable
fun WealthWiseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility with template name
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    WealthWiseTheme(content = content)
}
