package com.lastpaycheck.presentation.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpCard
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
internal fun GameHomeScreen(
    state: MainUiState,
    onAction: (ActivityRequest) -> Unit,
    onPause: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = LastPaycheckTheme.colors.background) {
        Column(Modifier.safeDrawingPadding()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState()).padding(LpSpacing.base),
                verticalArrangement = Arrangement.spacedBy(LpSpacing.md)
            ) {
                when (state) {
                    MainUiState.Loading -> GameLoading()
                    MainUiState.Empty -> LpBody(stringResource(R.string.game_no_save))
                    is MainUiState.Content -> HomeContent(state.session, onAction, onPause, onSave)
                    is MainUiState.Error -> {
                        state.session?.let { HomeStatus(it) }
                        GameErrorPanel(state.reason, onRetry)
                    }
                }
                LpButton(
                    stringResource(R.string.game_back),
                    onBack,
                    Modifier.fillMaxWidth(),
                    style = LpButtonStyle.Outline
                )
            }
            HomeSections()
        }
    }
}

@Composable
private fun HomeSections() {
    val colors = LastPaycheckTheme.colors
    Surface(color = colors.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LpCaption(stringResource(R.string.game_home_section), color = colors.primary)
            LpCaption(stringResource(R.string.game_work_section))
            LpCaption(stringResource(R.string.game_shop_section))
            LpCaption(stringResource(R.string.game_finance_section))
            LpCaption(stringResource(R.string.game_character_section))
        }
    }
}

@Composable
private fun HomeContent(
    session: GameSession,
    onAction: (ActivityRequest) -> Unit,
    onPause: () -> Unit,
    onSave: () -> Unit
) {
    HomeStatus(session)
    ApartmentArtwork()
    val activity = session.activeActivity
    val canAct = session.status == SessionStatus.ACTIVE && !session.clock.isPaused
    val ready = canAct && activity == null
    HomeActionRow(
        left = stringResource(R.string.game_training_action),
        onLeft = {},
        leftEnabled = false,
        right = stringResource(R.string.game_rest),
        onRight = { onAction(ActivityRequest.Rest) },
        rightEnabled = ready
    )
    LpCaption(stringResource(R.string.game_home_description))
    val food = session.inventory.quantities.keys.firstOrNull()
    HomeActionRow(
        left = stringResource(R.string.game_fridge),
        onLeft = { food?.let { onAction(ActivityRequest.Food(it)) } },
        leftEnabled = ready && food != null,
        right = stringResource(R.string.game_sleep),
        onRight = { onAction(ActivityRequest.Sleep) },
        rightEnabled = ready
    )
    val activityLabel = when {
        session.status == SessionStatus.FINISHED -> R.string.game_finished
        activity is TimedActivity.Rest -> R.string.game_resting
        activity is TimedActivity.Sleep -> R.string.game_sleeping
        activity is TimedActivity.Food -> R.string.game_eating
        activity != null -> R.string.game_busy
        else -> null
    }
    activityLabel?.let { LpBody(stringResource(it)) }
    val pauseLabel = if (PauseReason.MANUAL in session.clock.pauseReasons) {
        R.string.game_unpause
    } else {
        R.string.game_pause
    }
    HomeActionRow(
        left = stringResource(pauseLabel),
        onLeft = onPause,
        leftEnabled = session.status == SessionStatus.ACTIVE,
        right = stringResource(R.string.game_save),
        onRight = onSave,
        rightEnabled = true
    )
}

@Composable
private fun HomeStatus(session: GameSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            LpHeading(stringResource(R.string.game_apartment), LpHeadingLevel.Section)
            LpCaption(gameTime(session.clock))
        }
        Surface(shape = MaterialTheme.shapes.small, color = LastPaycheckTheme.colors.surfaceAlt) {
            Box(Modifier.padding(horizontal = LpSpacing.md, vertical = LpSpacing.sm)) {
                LpCaption(
                    gameMoney(session.player.moneyMinor),
                    color = LastPaycheckTheme.colors.accent
                )
            }
        }
    }
    NeedsPanel(session.player.needs)
    LpCaption(stringResource(R.string.game_job, gameJob(session.player.selectedJobId)))
}

@Composable
internal fun NeedsPanel(needs: Needs) {
    Column(verticalArrangement = Arrangement.spacedBy(LpSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.md)) {
            Need(R.string.game_satiety, needs.satiety, Modifier.weight(1f))
            Need(R.string.game_energy, needs.energy, Modifier.weight(1f), accent = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.md)) {
            Need(R.string.game_mood, needs.mood, Modifier.weight(1f))
            Need(R.string.game_health, needs.health, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Need(label: Int, value: Double, modifier: Modifier, accent: Boolean = false) {
    val colors = LastPaycheckTheme.colors
    Surface(modifier, shape = MaterialTheme.shapes.small, color = colors.surface) {
        Column(
            Modifier.padding(LpSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.xs)) {
                LpCaption(stringResource(label), Modifier.weight(1f))
                LpCaption(
                    value.roundToInt().toString(),
                    color = if (accent) colors.accent else colors.primary
                )
            }
            LinearProgressIndicator(
                progress = { (value / 100).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (accent) colors.accent else colors.primary,
                trackColor = colors.surfaceAlt
            )
        }
    }
}

@Composable
private fun HomeActionRow(
    left: String,
    onLeft: () -> Unit,
    leftEnabled: Boolean,
    right: String,
    onRight: () -> Unit,
    rightEnabled: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.md)) {
        LpButton(left, onLeft, Modifier.weight(1f), LpButtonStyle.Secondary, leftEnabled)
        LpButton(right, onRight, Modifier.weight(1f), LpButtonStyle.Secondary, rightEnabled)
    }
}

@Composable
internal fun GamePage(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = LastPaycheckTheme.colors.background) {
        Column(
            modifier = Modifier.safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(LpSpacing.base),
            verticalArrangement = Arrangement.spacedBy(LpSpacing.md),
            content = content
        )
    }
}

@Composable
internal fun ApartmentArtwork() {
    Image(
        painterResource(R.drawable.apartment),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9)
    )
}

@Composable
internal fun GameLoading() {
    LpBody(stringResource(R.string.game_loading))
}

@Composable
internal fun GameErrorPanel(reason: GameError, onRetry: () -> Unit) {
    val label = when (reason) {
        is GameError.InvalidSnapshot -> R.string.game_invalid_save
        GameError.CatalogUnavailable, GameError.InvalidCatalog -> R.string.game_catalog_error
        GameError.Busy, GameError.Paused -> R.string.game_busy
        else -> R.string.game_storage_error
    }
    LpCard(stringResource(R.string.game_error), Modifier.fillMaxWidth()) {
        LpBody(stringResource(label))
        LpButton(stringResource(R.string.catalog_retry), onRetry, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun gameTime(clock: GameClock): String =
    stringResource(R.string.game_time, clock.day, clock.minuteOfDay / 60, clock.minuteOfDay % 60)

@Composable
internal fun gameMoney(amount: Long): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val format = NumberFormat.getNumberInstance(locale)
    return stringResource(R.string.game_money, format.format(BigDecimal.valueOf(amount, 2)))
}

@Composable
internal fun gameJob(id: String): String = stringResource(
    when (id) {
        "warehouse-helper" -> R.string.game_warehouse_helper
        "warehouse-picker" -> R.string.game_warehouse_picker
        "warehouse-senior" -> R.string.game_warehouse_senior
        else -> R.string.game_unknown_job
    }
)

@Composable
internal fun themeToggleLabel(dark: Boolean) =
    stringResource(if (dark) R.string.use_light_theme else R.string.use_dark_theme)
