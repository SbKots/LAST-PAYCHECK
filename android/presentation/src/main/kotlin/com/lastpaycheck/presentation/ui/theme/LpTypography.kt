package com.lastpaycheck.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.lastpaycheck.presentation.R

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resource: Int, weight: FontWeight) = Font(
    resId = resource,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

private val Manrope = FontFamily(
    variableFont(R.font.manrope, FontWeight.SemiBold),
    variableFont(R.font.manrope, FontWeight.Bold)
)

private val Inter = FontFamily(
    variableFont(R.font.inter, FontWeight.Normal),
    variableFont(R.font.inter, FontWeight.Medium),
    variableFont(R.font.inter, FontWeight.SemiBold)
)

private fun lpStyle(family: FontFamily, weight: FontWeight, size: Int, height: Int) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

private val Display = lpStyle(Manrope, FontWeight.Bold, 30, 38)
private val Screen = lpStyle(Manrope, FontWeight.Bold, 28, 36)
private val Section = lpStyle(Manrope, FontWeight.Bold, 22, 30)
private val Card = lpStyle(Manrope, FontWeight.SemiBold, 18, 26)
private val Body = lpStyle(Inter, FontWeight.Normal, 16, 24)
private val BodyMedium = lpStyle(Inter, FontWeight.Medium, 14, 21)
private val ButtonLabel = lpStyle(Inter, FontWeight.SemiBold, 16, 24)
private val Caption = lpStyle(Inter, FontWeight.Medium, 14, 20)

internal val LpTypography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,
    headlineLarge = Screen,
    headlineMedium = Section,
    headlineSmall = Card,
    titleLarge = Card,
    titleMedium = Card,
    titleSmall = BodyMedium,
    bodyLarge = Body,
    bodyMedium = BodyMedium,
    bodySmall = Caption,
    labelLarge = ButtonLabel,
    labelMedium = Caption,
    labelSmall = Caption
)
