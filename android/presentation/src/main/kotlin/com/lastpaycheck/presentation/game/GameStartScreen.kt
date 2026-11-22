package com.lastpaycheck.presentation.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.NewGamePreview
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpCard
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun GameStartScreen(
    state: MainUiState,
    dark: Boolean,
    onContinue: () -> Unit,
    onRestore: () -> Unit,
    onNew: () -> Unit,
    onRetry: () -> Unit,
    onTheme: () -> Unit,
    onSettings: () -> Unit
) {
    GamePage {
        Column {
            LpCaption(stringResource(R.string.game_simulation))
            LpHeading("LAST\nPAYCHECK", LpHeadingLevel.Screen)
        }
        Image(
            painter = painterResource(R.drawable.street),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9)
        )
        LpHeading(stringResource(R.string.game_tagline), LpHeadingLevel.Card)
        when (state) {
            MainUiState.Loading -> GameLoading()
            MainUiState.Empty -> LpCaption(stringResource(R.string.game_no_save))
            is MainUiState.Content -> {
                LpCard(stringResource(R.string.game_your_run), Modifier.fillMaxWidth()) {
                    LpBody(gameTime(state.session.clock))
                }
                LpButton(
                    stringResource(R.string.game_continue),
                    onContinue,
                    Modifier.fillMaxWidth().testTag("start.continue")
                )
            }
            is MainUiState.Error -> {
                GameErrorPanel(state.reason, onRetry)
                if (state.reason is GameError.InvalidSnapshot && state.reason.previousAvailable) {
                    LpButton(
                        stringResource(R.string.game_restore_previous),
                        onRestore,
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (state !is MainUiState.Error) {
            LpButton(
                stringResource(R.string.game_new),
                onNew,
                Modifier.fillMaxWidth().testTag("start.new"),
                enabled = state != MainUiState.Loading,
                style = if (state == MainUiState.Empty) {
                    LpButtonStyle.Primary
                } else {
                    LpButtonStyle.Secondary
                }
            )
        }
        LpButton(
            themeToggleLabel(dark),
            onTheme,
            Modifier.fillMaxWidth(),
            style = LpButtonStyle.Outline
        )
        LpButton(
            stringResource(R.string.progress_settings),
            onSettings,
            Modifier.fillMaxWidth(),
            LpButtonStyle.Secondary
        )
        LpCaption(stringResource(R.string.game_local), Modifier.padding(bottom = LpSpacing.sm))
    }
}

@Composable
internal fun NewGameScreen(
    preview: DomainResult<NewGamePreview>?,
    replacing: Boolean,
    creating: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    GamePage {
        LpHeading(stringResource(R.string.game_new_life), LpHeadingLevel.Screen)
        ApartmentArtwork()
        when (preview) {
            null -> GameLoading()
            is DomainResult.Failure -> GameErrorPanel(preview.error, onRetry)
            is DomainResult.Success -> {
                val (initial, scenario) = preview.value
                LpCard(stringResource(R.string.game_new_summary), Modifier.fillMaxWidth()) {
                    LpBody(
                        stringResource(R.string.game_initial_balance, gameMoney(initial.moneyMinor))
                    )
                    LpBody(stringResource(R.string.game_rent, gameMoney(initial.dailyRentMinor)))
                    LpCaption(
                        stringResource(R.string.game_training, scenario.annualInflationPercent)
                    )
                    LpCaption(stringResource(R.string.game_job, gameJob(initial.jobId)))
                }
                NeedsPanel(initial.needs)
                if (replacing) LpBody(stringResource(R.string.game_replace_warning))
                val label = if (replacing) R.string.game_replace else R.string.game_start_day
                LpButton(
                    stringResource(label),
                    onStart,
                    Modifier.fillMaxWidth().testTag("newgame.start"),
                    enabled = !creating
                )
            }
        }
        LpButton(
            stringResource(R.string.game_back),
            onBack,
            Modifier.fillMaxWidth(),
            enabled = !creating,
            style = LpButtonStyle.Outline
        )
    }
}
