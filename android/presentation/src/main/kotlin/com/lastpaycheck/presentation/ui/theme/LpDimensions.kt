package com.lastpaycheck.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object LpSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object LpDimensions {
    val buttonMinHeight = 48.dp
    val inputMinHeight = 56.dp
    val controlRadius = 12.dp
    val cardRadius = 16.dp
    val border = 1.dp
    val emphasisBorder = 2.dp
}

internal val LpShapes = Shapes(
    extraSmall = RoundedCornerShape(LpDimensions.controlRadius),
    small = RoundedCornerShape(LpDimensions.controlRadius),
    medium = RoundedCornerShape(LpDimensions.cardRadius),
    large = RoundedCornerShape(LpDimensions.cardRadius),
    extraLarge = RoundedCornerShape(LpDimensions.cardRadius)
)
