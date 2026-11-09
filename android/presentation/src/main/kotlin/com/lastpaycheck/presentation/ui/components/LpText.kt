package com.lastpaycheck.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme

enum class LpHeadingLevel { Screen, Section, Card }

@Composable
fun LpHeading(text: String, level: LpHeadingLevel, modifier: Modifier = Modifier) {
    val style = when (level) {
        LpHeadingLevel.Screen -> MaterialTheme.typography.headlineLarge
        LpHeadingLevel.Section -> MaterialTheme.typography.headlineMedium
        LpHeadingLevel.Card -> MaterialTheme.typography.headlineSmall
    }
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = LastPaycheckTheme.colors.textPrimary,
        style = style
    )
}

@Composable
fun LpBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LastPaycheckTheme.colors.textPrimary,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun LpCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LastPaycheckTheme.colors.textSecondary
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}
