package com.lastpaycheck.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpDimensions
import com.lastpaycheck.presentation.ui.theme.LpSpacing

enum class LpButtonStyle { Primary, Secondary, Outline }

@Composable
fun LpButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LpButtonStyle = LpButtonStyle.Primary,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = LastPaycheckTheme.colors
    val shape = MaterialTheme.shapes.small
    val primary = enabled && style == LpButtonStyle.Primary
    val container = when {
        primary && pressed -> colors.primaryPressed
        primary -> colors.primary
        enabled && style == LpButtonStyle.Outline && !pressed -> colors.surface
        else -> colors.surfaceAlt
    }
    val foreground = when {
        !enabled -> colors.textSecondary
        primary -> colors.onPrimary
        else -> colors.textPrimary
    }
    val border = when {
        primary && focused -> BorderStroke(LpDimensions.emphasisBorder, colors.onPrimary)
        primary -> null
        enabled && (focused || (pressed && style == LpButtonStyle.Secondary)) ->
            BorderStroke(LpDimensions.emphasisBorder, colors.primary)
        else -> BorderStroke(
            LpDimensions.border,
            if (enabled) colors.controlBorder else colors.border
        )
    }

    Box(
        modifier = modifier
            .sizeIn(
                minWidth = LpDimensions.buttonMinHeight,
                minHeight = LpDimensions.buttonMinHeight
            )
            .clip(shape)
            .background(container)
            .then(if (border == null) Modifier else Modifier.border(border, shape))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = LpSpacing.base, vertical = LpSpacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}
