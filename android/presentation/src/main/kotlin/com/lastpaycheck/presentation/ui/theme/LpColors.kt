package com.lastpaycheck.presentation.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class LpColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val controlBorder: Color,
    val primary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val onPrimary: Color,
    val primaryPressed: Color
)

internal val LightColors = LpColors(
    background = Color(0xFFF7F5F1),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFECE8DF),
    textPrimary = Color(0xFF20242A),
    textSecondary = Color(0xFF525B66),
    border = Color(0xFF818990),
    controlBorder = Color(0xFF7E868D),
    primary = Color(0xFF176D60),
    accent = Color(0xFF905000),
    success = Color(0xFF126744),
    warning = Color(0xFF8A4F00),
    error = Color(0xFFB42336),
    onPrimary = Color(0xFFFFFFFF),
    primaryPressed = Color(0xFF12594F)
)

internal val DarkColors = LpColors(
    background = Color(0xFF12171C),
    surface = Color(0xFF1D252B),
    surfaceAlt = Color(0xFF28323A),
    textPrimary = Color(0xFFF5F3ED),
    textSecondary = Color(0xFFBEC8CF),
    border = Color(0xFF647079),
    controlBorder = Color(0xFF6F7B84),
    primary = Color(0xFF67D5B5),
    accent = Color(0xFFF7C777),
    success = Color(0xFF72D7AA),
    warning = Color(0xFFF3C26E),
    error = Color(0xFFFF99A3),
    onPrimary = Color(0xFF12171C),
    primaryPressed = Color(0xFF52BFA1)
)

internal val LightColorScheme = LightColors.toMaterialScheme(dark = false)
internal val DarkColorScheme = DarkColors.toMaterialScheme(dark = true)

private fun LpColors.toMaterialScheme(dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = surfaceAlt,
        onPrimaryContainer = textPrimary,
        inversePrimary = if (dark) LightColors.primary else DarkColors.primary,
        secondary = accent,
        onSecondary = onPrimary,
        secondaryContainer = surfaceAlt,
        onSecondaryContainer = textPrimary,
        tertiary = success,
        onTertiary = onPrimary,
        tertiaryContainer = surfaceAlt,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textSecondary,
        surfaceTint = primary,
        inverseSurface = if (dark) LightColors.surface else DarkColors.surface,
        inverseOnSurface = if (dark) LightColors.textPrimary else DarkColors.textPrimary,
        error = error,
        onError = onPrimary,
        errorContainer = surfaceAlt,
        onErrorContainer = error,
        outline = controlBorder,
        outlineVariant = border,
        scrim = DarkColors.background,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
