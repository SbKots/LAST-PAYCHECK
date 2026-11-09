package com.lastpaycheck.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpDimensions
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
fun LpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val colors = LastPaycheckTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val showFocus = enabled && focused
    val activeError = errorMessage.takeIf { enabled }
    val borderColor = when {
        activeError != null -> colors.error
        showFocus -> colors.primary
        enabled -> colors.controlBorder
        else -> colors.border
    }
    val borderWidth = if (activeError != null || showFocus) {
        LpDimensions.emphasisBorder
    } else {
        LpDimensions.border
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                activeError?.let { error(it) }
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) colors.textPrimary else colors.textSecondary
            ),
            decorationBox = { innerTextField ->
                Surface(
                    color = if (enabled) colors.surface else colors.surfaceAlt,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(borderWidth, borderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = LpDimensions.inputMinHeight)
                            .padding(LpSpacing.base),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        innerTextField()
                    }
                }
            }
        )
        (activeError ?: supportingText)?.let {
            LpCaption(
                text = it,
                color = if (activeError == null) colors.textSecondary else colors.error
            )
        }
    }
}
