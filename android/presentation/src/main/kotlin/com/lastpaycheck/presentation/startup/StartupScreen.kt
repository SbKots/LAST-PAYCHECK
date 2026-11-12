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
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.demo.DesignSystemGallery

@Composable
fun StartupScreen(viewModel: StartupViewModel, onThemeChanged: (Boolean) -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val catalogStatus = when (val result = catalog) {
        null -> stringResource(R.string.catalog_loading)
        is DomainResult.Success -> stringResource(R.string.catalog_version, result.value.version)
        is DomainResult.Failure -> stringResource(catalogErrorMessage(result.error))
    }
    val systemDarkTheme = isSystemInDarkTheme()
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkTheme = darkThemeOverride ?: systemDarkTheme
    SideEffect { onThemeChanged(darkTheme) }

    DesignSystemGallery(
        catalogStatus = catalogStatus,
        darkTheme = darkTheme,
        onThemeToggle = { darkThemeOverride = !darkTheme },
        catalogSource = if (catalog is DomainResult.Success) {
            stringResource(
                R.string.catalog_source
            )
        } else {
            null
        },
        onCatalogRetry = if (catalog is DomainResult.Failure) viewModel::reloadCatalog else null
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
