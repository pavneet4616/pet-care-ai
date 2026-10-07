package com.petcare.companionlite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.petcare.companionlite.data.model.ThemeMode

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Sage,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2C4638),
    onPrimaryContainer = Cream,
    secondary = Honey,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3A3424),
    onSecondaryContainer = Cream,
    tertiary = AccentPurple,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnMuted,
    error = Coral,
    onError = Color.White,
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E8DC),
    onPrimaryContainer = Forest,
    secondary = DeepSage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F0EA),
    onSecondaryContainer = Forest,
    tertiary = AccentPurple,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnMuted,
    error = Coral,
    onError = Color.White,
)

@Composable
fun PetCareTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PetTypography,
        content = content,
    )
}
