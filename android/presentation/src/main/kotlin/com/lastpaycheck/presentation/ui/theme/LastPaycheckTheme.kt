package com.lastpaycheck.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalLpColors = staticCompositionLocalOf { LightColors }

object LastPaycheckTheme {
    val colors: LpColors
        @Composable get() = LocalLpColors.current
}

@Composable
fun LastPaycheckTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalLpColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = LpTypography,
            shapes = LpShapes,
            content = content
        )
    }
}
