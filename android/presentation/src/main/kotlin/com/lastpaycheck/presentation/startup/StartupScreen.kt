package com.lastpaycheck.presentation.startup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.demo.DesignSystemGallery

@Composable
fun StartupScreen(viewModel: StartupViewModel, onThemeChanged: (Boolean) -> Unit) {
    val version by viewModel.catalogVersion.collectAsStateWithLifecycle()
    val catalogStatus = version?.let { stringResource(R.string.demo_catalog_version, it) }
        ?: stringResource(R.string.catalog_loading)
    val systemDarkTheme = isSystemInDarkTheme()
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkTheme = darkThemeOverride ?: systemDarkTheme
    SideEffect { onThemeChanged(darkTheme) }

    DesignSystemGallery(
        catalogStatus = catalogStatus,
        darkTheme = darkTheme,
        onThemeToggle = { darkThemeOverride = !darkTheme }
    )
}
