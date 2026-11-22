package com.lastpaycheck.presentation.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.AppLanguage
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.ThemePreference
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.startup.StartupViewModel
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption

@Composable
internal fun SettingsScreen(startup: StartupViewModel, onAccount: () -> Unit, onBack: () -> Unit) {
    val result by startup.settings.collectAsStateWithLifecycle()
    val busy by startup.saving.collectAsStateWithLifecycle()
    val error by startup.settingsError.collectAsStateWithLifecycle()
    val settings = (result as? DomainResult.Success)?.value
    SectionPage(stringResource(R.string.progress_settings), "", null, onBack, {
        GameBackButton(onBack)
    }) {
        (result as? DomainResult.Failure)?.let {
            GameErrorPanel(it.error, startup::reloadSettings)
        }
        if (settings == null && result == null) GameLoading()
        settings?.let { current ->
            SectionCard(stringResource(R.string.progress_theme)) {
                ThemePreference.entries.forEach { theme ->
                    val label = when (theme) {
                        ThemePreference.SYSTEM -> R.string.progress_theme_system
                        ThemePreference.LIGHT -> R.string.progress_theme_light
                        ThemePreference.DARK -> R.string.progress_theme_dark
                    }
                    SettingsChoice(label, current.theme == theme, !busy) {
                        startup.setSettings(current.copy(theme = theme))
                    }
                }
            }
            SectionCard(stringResource(R.string.progress_language)) {
                AppLanguage.entries.forEach { language ->
                    val label = when (language) {
                        AppLanguage.SYSTEM -> R.string.progress_language_system
                        AppLanguage.RUSSIAN -> R.string.progress_language_ru
                        AppLanguage.ENGLISH -> R.string.progress_language_en
                    }
                    SettingsChoice(label, current.language == language, !busy) {
                        startup.setSettings(current.copy(language = language))
                    }
                }
            }
            error?.let { GameErrorPanel(it) { startup.setSettings(current) } }
        }
        LpButton(
            stringResource(R.string.progress_account),
            onAccount,
            Modifier.fillMaxWidth(),
            LpButtonStyle.Secondary
        )
        LpCaption(stringResource(R.string.progress_about))
    }
}

@Composable
private fun SettingsChoice(label: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    LpButton(
        stringResource(label),
        onClick,
        Modifier.fillMaxWidth(),
        if (selected) LpButtonStyle.Primary else LpButtonStyle.Secondary,
        enabled
    )
}
