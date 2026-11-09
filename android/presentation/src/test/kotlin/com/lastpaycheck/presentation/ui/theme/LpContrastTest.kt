package com.lastpaycheck.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class LpContrastTest {
    @Test
    fun lightPaletteMeetsContrastTargets() = verifyContrast(LightColors)

    @Test
    fun darkPaletteMeetsContrastTargets() = verifyContrast(DarkColors)

    private fun verifyContrast(colors: LpColors) = with(colors) {
        val textPairs = mapOf(
            "body on background" to (textPrimary to background),
            "body on card" to (textPrimary to surface),
            "caption on card" to (textSecondary to surface),
            "caption on alternative surface" to (textSecondary to surfaceAlt),
            "primary button" to (onPrimary to primary),
            "pressed primary button" to (onPrimary to primaryPressed),
            "accent on card" to (accent to surface),
            "success on card" to (success to surface),
            "warning on card" to (warning to surface),
            "error on card" to (error to surface),
            "secondary button" to (textPrimary to surfaceAlt),
            "error help on background" to (error to background),
            "error help on alternative surface" to (error to surfaceAlt),
            "success message on background" to (success to background)
        )
        val controlPairs = mapOf(
            "border on card" to (border to surface),
            "border on background" to (border to background),
            "progress indicator" to (primary to surfaceAlt),
            "critical progress indicator" to (error to surfaceAlt)
        )
        val controlSurfaces = mapOf(
            "background" to background,
            "surface" to surface,
            "alternative surface" to surfaceAlt
        )
        textPairs.forEach { (label, colors) -> assertContrast(label, colors, 4.5) }
        controlPairs.forEach { (label, colors) -> assertContrast(label, colors, 3.0) }
        controlSurfaces.forEach { (name, adjacent) ->
            assertContrast("control border on $name", controlBorder to adjacent, 3.0)
            assertContrast("active or focused control on $name", primary to adjacent, 3.0)
            assertContrast("pressed primary button on $name", primaryPressed to adjacent, 3.0)
            assertContrast("invalid input border on $name", error to adjacent, 3.0)
        }
    }

    private fun assertContrast(label: String, colors: Pair<Color, Color>, threshold: Double) {
        val first = colors.first.luminance()
        val second = colors.second.luminance()
        val ratio = (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
        assertTrue("$label has contrast $ratio; expected at least $threshold", ratio >= threshold)
    }
}
