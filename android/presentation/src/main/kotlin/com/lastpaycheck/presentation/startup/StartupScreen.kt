package com.lastpaycheck.presentation.startup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.demo.DesignSystemGallery

@Composable
fun StartupScreen(viewModel: StartupViewModel, onThemeChanged: (Boolean) -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snapshot = (catalog as? DomainResult.Success)?.value
    val catalogStatus = when (val result = catalog) {
        null -> stringResource(R.string.catalog_loading)
        is DomainResult.Success -> {
            stringResource(R.string.catalog_version, result.value.manifest.version)
        }
        is DomainResult.Failure -> stringResource(catalogErrorMessage(result.error))
    }
    val theme = (settings as? DomainResult.Success)?.value?.theme
    val darkTheme = when (theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect { onThemeChanged(darkTheme) }

    val catalogSource = when (snapshot?.fromCache) {
        true -> stringResource(R.string.catalog_cached)
        false -> stringResource(R.string.catalog_source)
        null -> null
    }
    val retry = catalog is DomainResult.Failure || snapshot?.fromCache == true
    DesignSystemGallery(
        catalogStatus = catalogStatus,
        darkTheme = darkTheme,
        onThemeToggle = { viewModel.setTheme(!darkTheme) },
        catalogSource = catalogSource,
        onCatalogRetry = if (retry) viewModel::reloadCatalog else null
    )
}

private fun catalogErrorMessage(error: GameError): Int =
    when ((error as? GameError.Remote)?.reason) {
        RemoteFailure.OFFLINE -> R.string.catalog_offline
        RemoteFailure.TIMEOUT -> R.string.catalog_timeout
        RemoteFailure.DNS,
        RemoteFailure.TLS,
        RemoteFailure.CONNECTION -> R.string.catalog_connection_error
        RemoteFailure.INVALID_RESPONSE -> R.string.catalog_invalid_response
        RemoteFailure.RATE_LIMITED -> R.string.catalog_rate_limited
        RemoteFailure.SERVER -> R.string.catalog_server_error
        else -> R.string.catalog_error
    }
