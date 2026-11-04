package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = RoyalBlueContainer,
    onPrimaryContainer = RoyalBlueDark,
    secondary = AccentPurple,
    onSecondary = SurfaceWhite,
    secondaryContainer = AccentPurpleBg,
    onSecondaryContainer = AccentPurpleDark,
    tertiary = PresentGreen,
    onTertiary = SurfaceWhite,
    tertiaryContainer = PresentGreenBg,
    onTertiaryContainer = PresentGreenDark,
    error = AbsentRed,
    onError = SurfaceWhite,
    errorContainer = AbsentRedBg,
    onErrorContainer = AbsentRedDark,
    background = AppBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlueDarkTheme,
    onPrimary = AppBackgroundDark,
    primaryContainer = RoyalBlueDark,
    onPrimaryContainer = RoyalBlueLight,
    secondary = AccentPurple,
    onSecondary = SurfaceWhite,
    secondaryContainer = AccentPurpleDark,
    onSecondaryContainer = AccentPurpleBg,
    tertiary = PresentGreen,
    onTertiary = SurfaceWhite,
    tertiaryContainer = PresentGreenDark,
    onTertiaryContainer = PresentGreenBg,
    error = AbsentRed,
    onError = SurfaceWhite,
    errorContainer = AbsentRedDark,
    onErrorContainer = AbsentRedBg,
    background = AppBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartAttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
