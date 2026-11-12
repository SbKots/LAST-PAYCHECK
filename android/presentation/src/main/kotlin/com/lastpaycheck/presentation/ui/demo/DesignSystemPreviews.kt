package com.lastpaycheck.presentation.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpTextField
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Preview(name = "Gallery · Light", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
fun LightGalleryPreview() {
    GalleryPreview(darkTheme = false)
}

@Preview(name = "Gallery · Dark", widthDp = 360, heightDp = 800, locale = "ru")
@Composable
fun DarkGalleryPreview() {
    GalleryPreview(darkTheme = true)
}

@Preview(
    name = "Gallery · Large text",
    widthDp = 320,
    heightDp = 800,
    fontScale = 2f,
    locale = "ru"
)
@Composable
fun LargeTextGalleryPreview() {
    GalleryPreview(darkTheme = false)
}

@Preview(name = "Components · Light", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
fun LightComponentsPreview() {
    ComponentsPreview(darkTheme = false)
}

@Preview(name = "Components · Dark", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
fun DarkComponentsPreview() {
    ComponentsPreview(darkTheme = true)
}

@Composable
private fun GalleryPreview(darkTheme: Boolean) {
    DesignSystemGallery(
        catalogStatus = stringResource(R.string.demo_catalog_version, "demo-1"),
        darkTheme = darkTheme,
        onThemeToggle = {},
        catalogSource = stringResource(R.string.design_system_local_data)
    )
}

@Composable
private fun ComponentsPreview(darkTheme: Boolean) {
    LastPaycheckTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LastPaycheckTheme.colors.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(LpSpacing.base),
                verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
            ) {
                LpButton(
                    label = stringResource(R.string.design_system_confirm),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                LpButton(
                    label = stringResource(R.string.design_system_reset),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    style = LpButtonStyle.Secondary
                )
                LpButton(
                    label = stringResource(R.string.design_system_unavailable),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
                LpTextField(
                    value = "0",
                    onValueChange = {},
                    label = stringResource(R.string.design_system_quantity),
                    errorMessage = stringResource(R.string.design_system_quantity_error)
                )
                LpTextField(
                    value = "2",
                    onValueChange = {},
                    label = stringResource(R.string.design_system_quantity),
                    supportingText = stringResource(R.string.design_system_unavailable),
                    enabled = false
                )
                CatalogCard(
                    stringResource(R.string.demo_catalog_version, "demo-1"),
                    stringResource(R.string.design_system_local_data)
                )
            }
        }
    }
}
