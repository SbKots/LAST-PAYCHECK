package com.lastpaycheck.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpDimensions
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
fun LpCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LastPaycheckTheme.colors
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = colors.surface,
            contentColor = colors.textPrimary
        ),
        border = BorderStroke(LpDimensions.border, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(LpSpacing.base),
            verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
        ) {
            LpHeading(text = title, level = LpHeadingLevel.Card)
            content()
        }
    }
}
