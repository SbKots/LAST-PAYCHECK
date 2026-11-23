package com.lastpaycheck.presentation.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
    onBack: () -> Unit,
    onCity: (() -> Unit)?,
    onTraining: () -> Unit,
    onFridge: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        Modifier.fillMaxSize().testTag("screen.home"),
        color = LastPaycheckTheme.colors.background
    ) {
        Column(Modifier.safeDrawingPadding()) {
            val session = when (state) {
                is MainUiState.Content -> state.session
                is MainUiState.Error -> state.session
                else -> null
            }
            session?.let {
                GameStatusHeader(it) { HomeMenu(it, onPause, onSave, onBack, onSettings) }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState()).padding(LpSpacing.base),
                verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
            ) {
                session?.let { GamePauseNotice(it, onPause) }
                when (state) {
                    MainUiState.Loading -> GameLoading()
                    MainUiState.Empty -> LpBody(stringResource(R.string.game_no_save))
                    is MainUiState.Content -> HomeContent(
                        state.session,
                        onAction,
                        onCity,
                        onTraining,
                        onFridge
                    )
                    is MainUiState.Error -> {
                        state.session?.let { NeedsPanel(it.player.needs, compact = true) }
                        GameErrorPanel(state.reason, onRetry)
                    }
                }
                if (state !is MainUiState.Content) GameBackButton(onBack)
            }
        }
    }
}

@Composable
internal fun HomeSections(
    onWork: (() -> Unit)?,
    onShop: (() -> Unit)?,
    onHome: () -> Unit,
    onFinance: () -> Unit,
    onHero: () -> Unit,
    selected: Int
) {
    val colors = LastPaycheckTheme.colors
    Surface(color = colors.surface) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SectionLink(
                R.string.game_home_section,
                R.drawable.game_ic_home,
                Modifier.weight(1f),
                onHome,
                selected = selected == R.string.game_home_section
            )
            SectionLink(
                R.string.game_work_section,
                R.drawable.game_ic_work,
                Modifier.weight(1f),
                onWork,
                selected = selected == R.string.game_work_section
            )
            SectionLink(
                R.string.game_shop_section,
                R.drawable.game_ic_shop,
                Modifier.weight(1f),
                onShop,
                selected = selected == R.string.game_shop_section
            )
            SectionLink(
                R.string.game_finance_section,
                R.drawable.game_ic_finance,
                Modifier.weight(1f),
                onFinance,
                selected = selected == R.string.game_finance_section
            )
            SectionLink(
                R.string.game_character_section,
                R.drawable.game_ic_hero,
                Modifier.weight(1f),
                onHero,
                selected = selected == R.string.game_character_section
            )
        }
    }
}

@Composable
private fun SectionLink(
    label: Int,
    icon: Int,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    val colors = LastPaycheckTheme.colors
    val tint = if (selected) colors.primary else colors.textSecondary
    val click = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClick = onClick
        )
    }
    Column(
        modifier.then(click).testTag("tab.$label").heightIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
    ) {
        Box(
            Modifier.fillMaxWidth(
                0.7f
            ).height(2.dp).background(if (selected) tint else Color.Transparent)
        )
        GameIcon(icon, tint)
        LpCaption(stringResource(label), color = tint)
    }
}

@Composable
private fun HomeContent(
    session: GameSession,
    onAction: (ActivityRequest) -> Unit,
    onCity: (() -> Unit)?,
    onTraining: () -> Unit,
    onFridge: () -> Unit
) {
    var confirmation by rememberSaveable { mutableStateOf(false) }
    var sleeping by rememberSaveable { mutableStateOf(false) }
    NeedsPanel(session.player.needs, compact = true)
    Box(
        Modifier.padding(top = LpSpacing.base, bottom = LpSpacing.md)
            .background(Color(0xFF435D58))
    ) {
        ApartmentArtwork(aspectRatio = 1.56f)
        Surface(
            Modifier.padding(LpSpacing.md),
            shape = MaterialTheme.shapes.small,
            color = LastPaycheckTheme.colors.surfaceAlt
        ) {
            LpCaption(
                stringResource(R.string.game_at_home),
                Modifier.padding(horizontal = LpSpacing.md, vertical = LpSpacing.xs),
                LastPaycheckTheme.colors.textPrimary
            )
        }
    }
    val activity = session.activeActivity
    val canAct = session.status == SessionStatus.ACTIVE && !session.clock.isPaused
    val ready = canAct && activity == null
    Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
        HomeAction(
            R.string.game_training_action,
            R.drawable.game_ic_study,
            Modifier.weight(1f),
            onClick = onTraining
        )
        HomeAction(R.string.game_rest, R.drawable.game_ic_mood, Modifier.weight(1f), ready) {
            sleeping = false
            confirmation = true
        }
    }
    LpCaption(
        stringResource(R.string.game_home_description),
        Modifier.padding(vertical = LpSpacing.md)
    )
    val food = session.inventory.quantities.keys.firstOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
        HomeAction(
            R.string.game_fridge,
            R.drawable.game_ic_food,
            Modifier.weight(1f),
            onClick = onFridge
        )
        HomeAction(R.string.game_sleep, R.drawable.game_ic_sleep, Modifier.weight(1f), ready) {
            sleeping = true
            confirmation = true
        }
    }
    CityAction(onCity)
    val activityLabel = when {
        session.status == SessionStatus.FINISHED -> R.string.game_finished
        activity is TimedActivity.Rest -> R.string.game_resting
        activity is TimedActivity.Sleep -> R.string.game_sleeping
        activity is TimedActivity.Food -> R.string.game_eating
        activity != null -> R.string.game_busy
        else -> null
    }
    activityLabel?.let { LpBody(stringResource(it)) }
    if (food != null) {
        LpCaption(stringResource(R.string.game_in_stock, session.inventory.quantity(food)))
    }
    if (confirmation) {
        GameConfirmation(
            stringResource(if (sleeping) R.string.game_sleep else R.string.game_rest),
            stringResource(
                if (sleeping) R.string.progress_sleep_details else R.string.progress_rest_details
            ),
            stringResource(if (sleeping) R.string.game_sleep else R.string.game_rest),
            { confirmation = false },
            {
                confirmation = false
                onAction(if (sleeping) ActivityRequest.Sleep else ActivityRequest.Rest)
            }
        )
    }
}

@Composable
private fun CityAction(onCity: (() -> Unit)?) {
    LpButton(
        stringResource(R.string.game_go_to_city),
        { onCity?.invoke() },
        Modifier.fillMaxWidth().testTag("home.city"),
        enabled = onCity != null,
        pinLeadingIcon = true
    ) { tint ->
        Icon(
            painterResource(R.drawable.game_ic_back),
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(180f),
            tint = tint
        )
    }
}

@Composable
private fun HomeMenu(
    session: GameSession,
    onPause: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val paused = PauseReason.MANUAL in session.clock.pauseReasons
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.game_ic_more),
                stringResource(R.string.game_apartment_actions),
                tint = LastPaycheckTheme.colors.textSecondary
            )
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            LpCaption(
                stringResource(R.string.game_job, gameJob(session.player.selectedJobId)),
                Modifier.padding(horizontal = LpSpacing.base, vertical = LpSpacing.sm)
            )
            HomeMenuItem(
                if (paused) R.string.game_unpause else R.string.game_pause,
                if (paused) R.drawable.game_ic_play else R.drawable.game_ic_pause,
                enabled = session.status == SessionStatus.ACTIVE
            ) {
                expanded = false
                onPause()
            }
            HomeMenuItem(R.string.game_save, R.drawable.game_ic_save) {
                expanded = false
                onSave()
            }
            HomeMenuItem(R.string.game_back, R.drawable.game_ic_back) {
                expanded = false
                onBack()
            }
            HomeMenuItem(R.string.progress_settings, R.drawable.game_ic_more) {
                expanded = false
                onSettings()
            }
        }
    }
}

@Composable
private fun HomeMenuItem(label: Int, icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { LpBody(stringResource(label)) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = { GameIcon(icon, LastPaycheckTheme.colors.textSecondary) }
    )
}

@Composable
private fun GameStatusHeader(session: GameSession, actions: @Composable () -> Unit) =
    Surface(color = LastPaycheckTheme.colors.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = LpSpacing.lg,
                vertical = LpSpacing.md
            ),
            horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                LpHeading(stringResource(R.string.game_apartment), LpHeadingLevel.Card)
                LpCaption(gameTime(session.clock))
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = LastPaycheckTheme.colors.surfaceAlt
            ) {
                Box(Modifier.padding(horizontal = LpSpacing.md, vertical = LpSpacing.sm)) {
                    LpCaption(
                        gameMoney(session.player.moneyMinor),
                        color = LastPaycheckTheme.colors.accent
                    )
                }
            }
            actions()
        }
    }

@Composable
internal fun GameBackButton(onBack: () -> Unit) {
    LpButton(
        stringResource(R.string.game_back),
        onBack,
        Modifier.fillMaxWidth(),
        style = LpButtonStyle.Outline
    )
}

@Composable
internal fun NeedsPanel(needs: Needs, compact: Boolean = false) {
    val gap = if (compact) LpSpacing.sm else LpSpacing.md
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Need(R.string.game_satiety, needs.satiety, Modifier.weight(1f), compact = compact)
            Need(
                R.string.game_energy,
                needs.energy,
                Modifier.weight(1f),
                accent = true,
                compact = compact
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Need(R.string.game_mood, needs.mood, Modifier.weight(1f), compact = compact)
            Need(R.string.game_health, needs.health, Modifier.weight(1f), compact = compact)
        }
    }
}

@Composable
internal fun Need(
    label: Int,
    value: Double,
    modifier: Modifier,
    accent: Boolean = false,
    compact: Boolean = false
) {
    val colors = LastPaycheckTheme.colors
    val tint = if (accent) colors.accent else colors.primary
    Surface(modifier, shape = MaterialTheme.shapes.small, color = colors.surface) {
        Column(
            Modifier.padding(LpSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LpSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (compact) {
                    val icon = when (label) {
                        R.string.game_satiety -> R.drawable.game_ic_food
                        R.string.game_energy -> R.drawable.game_ic_energy
                        R.string.game_mood -> R.drawable.game_ic_mood
                        else -> R.drawable.game_ic_health
                    }
                    GameIcon(icon, tint)
                }
                LpCaption(stringResource(label), Modifier.weight(1f))
                LpCaption(
                    value.roundToInt().toString(),
                    color = tint
                )
            }
            LinearProgressIndicator(
                progress = { (value / 100).toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = tint,
                trackColor = colors.surfaceAlt,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun HomeAction(
    label: Int,
    icon: Int,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val actionModifier = if (label == R.string.game_fridge) {
        modifier.testTag("home.fridge")
    } else {
        modifier
    }
    LpButton(
        stringResource(label),
        onClick,
        actionModifier,
        LpButtonStyle.Secondary,
        enabled,
        pinLeadingIcon = true
    ) {
        GameIcon(icon, it)
    }
}

@Composable
internal fun GameIcon(icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp), tint)
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
internal fun ApartmentArtwork(aspectRatio: Float = 16f / 9) {
    Image(
        painterResource(R.drawable.apartment),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)
    )
}

@Composable
internal fun GameLoading() {
    LpBody(stringResource(R.string.game_loading))
}

@Composable
internal fun GameErrorPanel(reason: GameError, onRetry: () -> Unit) {
    if (reason is GameError.Vehicle) {
        LpBody(vehicleError(reason))
        return
    }
    val label = when (reason) {
        is GameError.InvalidSnapshot -> R.string.game_invalid_save
        GameError.CatalogUnavailable, GameError.InvalidCatalog -> R.string.game_catalog_error
        GameError.Busy -> R.string.game_busy
        GameError.Paused -> R.string.progress_unpause_hint
        is GameError.InsufficientFunds -> R.string.game_insufficient_funds
        is GameError.PriceChanged -> R.string.game_price_changed
        GameError.InsufficientEnergy -> R.string.game_no_energy
        GameError.UnavailableJob -> R.string.game_job_locked
        is GameError.NotFound -> R.string.game_item_missing
        GameError.GameFinished -> R.string.game_finished
        GameError.NoActiveShift, GameError.BoxAlreadyPlaced -> R.string.game_turn_changed
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
internal fun gameMoney(amount: Long): String =
    stringResource(R.string.game_money, gameAmount(amount))

@Composable
internal fun gameAmount(amount: Long): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val format = NumberFormat.getNumberInstance(locale)
    return format.format(BigDecimal.valueOf(amount, 2))
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
