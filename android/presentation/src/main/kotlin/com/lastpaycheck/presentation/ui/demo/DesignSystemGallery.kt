package com.lastpaycheck.presentation.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpCard
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel
import com.lastpaycheck.presentation.ui.components.LpTextField
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

private const val DEMO_QUANTITY = "2"

@Composable
internal fun DesignSystemGallery(
    catalogStatus: String,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    LastPaycheckTheme(darkTheme = darkTheme) {
        Surface(color = LastPaycheckTheme.colors.background) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LpSpacing.base, vertical = LpSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(LpSpacing.lg)
            ) {
                GalleryHeader(darkTheme, onThemeToggle)
                CatalogCard(catalogStatus)
                QuantityExample()
            }
        }
    }
}

@Composable
private fun GalleryHeader(darkTheme: Boolean, onThemeToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
        LpHeading(
            text = stringResource(R.string.startup_title),
            level = LpHeadingLevel.Screen
        )

        val themeLabel = if (darkTheme) R.string.use_light_theme else R.string.use_dark_theme
        LpButton(
            label = stringResource(themeLabel),
            onClick = onThemeToggle,
            modifier = Modifier.fillMaxWidth(),
            style = LpButtonStyle.Outline
        )
    }
}

@Composable
internal fun CatalogCard(catalogStatus: String) {
    LpCard(
        title = stringResource(R.string.design_system_catalog),
        modifier = Modifier.fillMaxWidth()
    ) {
        LpBody(text = catalogStatus)
        LpCaption(text = stringResource(R.string.design_system_local_data))
    }
}

@Composable
private fun QuantityExample() {
    var quantity by rememberSaveable { mutableStateOf(DEMO_QUANTITY) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val numericQuantity = quantity.toIntOrNull()
    val invalid = (numericQuantity == null || numericQuantity < 1) &&
        (submitted || quantity.isNotEmpty())
    val error = if (invalid) stringResource(R.string.design_system_quantity_error) else null

    Column(verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
        LpHeading(
            text = stringResource(R.string.design_system_controls),
            level = LpHeadingLevel.Section
        )
        LpTextField(
            value = quantity,
            onValueChange = {
                quantity = it
                submitted = false
            },
            label = stringResource(R.string.design_system_quantity),
            supportingText = stringResource(R.string.design_system_quantity_help),
            errorMessage = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        LpButton(
            label = stringResource(R.string.design_system_confirm),
            onClick = { submitted = true },
            modifier = Modifier.fillMaxWidth()
        )
        LpButton(
            label = stringResource(R.string.design_system_reset),
            onClick = {
                quantity = DEMO_QUANTITY
                submitted = false
            },
            modifier = Modifier.fillMaxWidth(),
            style = LpButtonStyle.Secondary
        )
        LpButton(
            label = stringResource(R.string.design_system_unavailable),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        )
        if (submitted && !invalid && numericQuantity != null) {
            LpCaption(
                text = stringResource(R.string.design_system_quantity_confirmed, numericQuantity),
                color = LastPaycheckTheme.colors.success
            )
        }
    }
}
