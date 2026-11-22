package com.lastpaycheck.presentation.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GAME_MINUTE_MILLIS
import com.lastpaycheck.domain.model.GameClock
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.ValidationIssue
import com.lastpaycheck.domain.usecase.GetCourseAvailabilityUseCase
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpTextField
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun FinanceScreen(
    sessionId: String,
    viewModel: FinanceViewModel,
    game: MainViewModel,
    onBack: () -> Unit
) {
    val result by viewModel.state.collectAsStateWithLifecycle()
    val gameState by game.state.collectAsStateWithLifecycle()
    val live = (gameState.forSession(sessionId) as? MainUiState.Content)?.session
    LaunchedEffect(sessionId, live?.player?.moneyMinor, live?.clock?.day) {
        viewModel.load(sessionId)
    }
    var filter by rememberSaveable { mutableStateOf(0) }
    val overview = (result as? DomainResult.Success)?.value?.takeIf { it.session.id == sessionId }
    SectionPage(
        stringResource(R.string.progress_finances),
        overview?.session?.let { gameTime(it.clock) }.orEmpty(),
        overview?.session,
        onBack,
        { GameBackButton(onBack) }
    ) {
        when (val current = result) {
            null -> GameLoading()
            is DomainResult.Failure -> GameErrorPanel(current.error) { viewModel.load(sessionId) }
            is DomainResult.Success -> overview?.let { data ->
                SectionCard(stringResource(R.string.progress_balance)) {
                    MoneyValue(data.session.player.moneyMinor)
                    LpCaption(
                        stringResource(
                            R.string.progress_debt,
                            gameMoney(data.session.player.rentDebtMinor)
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
                    SectionCard(stringResource(R.string.progress_income), Modifier.weight(1f)) {
                        MoneyValue(data.statistics.incomeMinor)
                    }
                    SectionCard(stringResource(R.string.progress_expense), Modifier.weight(1f)) {
                        MoneyValue(data.statistics.expenseMinor)
                    }
                }
                BalanceHistory(data.dailyBalances)
                SectionCard(
                    stringResource(
                        R.string.progress_economy,
                        data.session.scenario.country,
                        data.session.scenario.year,
                        data.session.scenario.annualInflationPercent.toPlainString()
                    )
                ) {
                    data.statistics.byCategoryMinor.forEach { (category, amount) ->
                        LpCaption(
                            stringResource(transactionLabel(category)) + " · " + gameMoney(amount)
                        )
                    }
                }
                val labels =
                    listOf(
                        R.string.progress_filter_all,
                        R.string.progress_filter_income,
                        R.string.progress_filter_expense
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.xs)) {
                    labels.forEachIndexed { index, label ->
                        LpButton(
                            stringResource(label),
                            { filter = index },
                            Modifier.weight(1f),
                            if (filter == index) LpButtonStyle.Primary else LpButtonStyle.Secondary
                        )
                    }
                }
                SectionCard(stringResource(R.string.progress_journal)) {
                    val records = data.transactions.filter {
                        filter == 0 || (filter == 1 && it.amountMinor > 0) ||
                            (filter == 2 && it.amountMinor < 0)
                    }
                    if (records.isEmpty()) {
                        LpCaption(
                            stringResource(R.string.progress_empty_journal)
                        )
                    }
                    records.forEach { transaction ->
                        Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
                            Column(Modifier.weight(1f)) {
                                LpBody(stringResource(transactionLabel(transaction.category)))
                                LpCaption(gameTime(GameClock(transaction.gameMillis)))
                            }
                            MoneyValue(transaction.amountMinor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyValue(amount: Long) {
    LpBody(gameMoney(amount), color = LastPaycheckTheme.colors.accent)
}

private fun transactionLabel(category: TransactionCategory): Int = when (category) {
    TransactionCategory.PURCHASE -> R.string.progress_purchase
    TransactionCategory.COURSE -> R.string.progress_course
    TransactionCategory.WAGE -> R.string.progress_wage
    TransactionCategory.RENT -> R.string.progress_rent
    TransactionCategory.DEBT_PAYMENT -> R.string.progress_debt_payment
    TransactionCategory.EVENT -> R.string.progress_event
}

@Composable
internal fun HeroScreen(
    session: GameSession,
    game: MainViewModel,
    onTraining: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable(session.id) { mutableStateOf(session.player.name) }
    val error by game.nameError.collectAsStateWithLifecycle()
    val saved by game.nameSaved.collectAsStateWithLifecycle()
    val busy by game.sectionBusy.collectAsStateWithLifecycle()
    val catalog by game.sectionCatalog.collectAsStateWithLifecycle()
    LaunchedEffect(session.id) {
        game.clearNameFeedback()
        game.loadSection(session.id, session.clock.day)
    }
    SectionPage(stringResource(R.string.progress_hero), gameTime(session.clock), session, onBack, {
        GameBackButton(onBack)
    }) {
        SectionCard(
            session.player.name.ifEmpty { stringResource(R.string.progress_default_name) }
        ) {
            LpBody(stringResource(R.string.progress_level, session.player.level))
            LpCaption(
                stringResource(
                    R.string.progress_xp,
                    session.player.experience,
                    100 - session.player.experience % 100
                )
            )
            LinearProgressIndicator(
                progress = { (session.player.experience % 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = LastPaycheckTheme.colors.primary,
                trackColor = LastPaycheckTheme.colors.surfaceAlt
            )
            LpCaption(stringResource(R.string.game_job, gameJob(session.player.selectedJobId)))
        }
        NeedsPanel(session.player.needs, compact = true)
        SectionCard(stringResource(R.string.progress_character_name)) {
            LpTextField(
                name,
                {
                    name = it
                    game.clearNameFeedback()
                },
                stringResource(R.string.progress_character_name),
                Modifier.fillMaxWidth(),
                supportingText = stringResource(R.string.progress_name_rules),
                errorMessage = error?.let { validationMessage(it) },
                enabled = !busy
            )
            LpButton(stringResource(R.string.progress_save_name), {
                game.rename(session.id, name)
            }, Modifier.fillMaxWidth(), enabled = !busy)
            if (saved) LpCaption(stringResource(R.string.progress_saved))
        }
        SectionCard(stringResource(R.string.progress_skills)) {
            LpBody(stringResource(R.string.progress_general_skill))
            val jobs = (catalog as? DomainResult.Success)?.value?.gameCatalog?.jobs.orEmpty()
            jobs.forEach {
                LpCaption(
                    gameJob(it.id) + " · " + stringResource(
                        if (session.player.level >= it.requiredLevel) {
                            R.string.progress_career_open
                        } else {
                            R.string.progress_career_requirement
                        },
                        it.requiredLevel
                    )
                )
            }
            LpButton(
                stringResource(R.string.progress_training),
                onTraining,
                Modifier.fillMaxWidth()
            )
        }
        EventsPanel(session)
        LpButton(
            stringResource(R.string.progress_settings),
            onSettings,
            Modifier.fillMaxWidth(),
            LpButtonStyle.Secondary
        )
        LpButton(
            stringResource(R.string.progress_account),
            onAccount,
            Modifier.fillMaxWidth(),
            LpButtonStyle.Secondary
        )
    }
}

@Composable
internal fun TrainingScreen(session: GameSession, game: MainViewModel, onBack: () -> Unit) {
    val result by game.sectionCatalog.collectAsStateWithLifecycle()
    val busy by game.sectionBusy.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var observedCourse by rememberSaveable(session.id) { mutableStateOf(false) }
    var previousExperience by rememberSaveable(session.id) {
        mutableStateOf(session.player.experience)
    }
    LaunchedEffect(
        session.id,
        session.clock.day
    ) { game.loadSection(session.id, session.clock.day) }
    val catalog = (result as? DomainResult.Success)?.value?.gameCatalog
    SectionPage(
        stringResource(
            R.string.progress_training
        ),
        gameTime(session.clock),
        session,
        onBack,
        {
            GameBackButton(onBack)
        }
    ) {
        ApartmentArtwork()
        SectionCard(stringResource(R.string.progress_general_skill)) {
            LpBody(stringResource(R.string.progress_level, session.player.level))
            LpCaption(
                stringResource(
                    R.string.progress_xp,
                    session.player.experience,
                    100 - session.player.experience % 100
                )
            )
        }
        val active = session.activeActivity as? TimedActivity.Course
        LaunchedEffect(active?.id) {
            if (active != null) {
                observedCourse = true
                previousExperience = session.player.experience
            }
        }
        if (observedCourse && active == null && session.player.experience > previousExperience) {
            SectionCard(stringResource(R.string.progress_course_complete)) {
                LpBody(stringResource(R.string.progress_level, session.player.level))
                LpCaption(
                    stringResource(
                        R.string.progress_xp,
                        session.player.experience,
                        100 - session.player.experience % 100
                    )
                )
            }
        }
        if (active != null) {
            SectionCard(
                stringResource(
                    R.string.progress_studying,
                    active.remainingGameMillis / GAME_MINUTE_MILLIS
                )
            ) {
                LpCaption(
                    stringResource(
                        R.string.progress_course_details,
                        gameMoney(active.course.priceMinor),
                        active.course.durationHours,
                        active.course.experienceGain
                    )
                )
            }
        }
        GamePauseNotice(session, game::togglePause, busy)
        when (val current = result) {
            null -> GameLoading()
            is DomainResult.Failure -> GameErrorPanel(current.error) {
                game.loadSection(session.id, session.clock.day)
            }
            is DomainResult.Success -> {
                if (catalog?.courses.isNullOrEmpty()) {
                    LpCaption(
                        stringResource(R.string.progress_no_courses)
                    )
                }
                catalog?.courses?.forEach { course ->
                    val blocked = GetCourseAvailabilityUseCase()(session, course)
                    SectionCard(stringResource(R.string.progress_general_skill)) {
                        LpBody(
                            stringResource(
                                R.string.progress_course_details,
                                gameMoney(course.priceMinor),
                                course.durationHours,
                                course.experienceGain
                            )
                        )
                        LpCaption(stringResource(R.string.progress_course_energy))
                        blocked?.let { LpCaption(courseBlockMessage(it)) }
                        LpButton(stringResource(R.string.progress_study), {
                            selected = course.id
                        }, Modifier.fillMaxWidth(), enabled = !busy && blocked == null)
                    }
                }
            }
        }
    }
    catalog?.courses?.find { it.id == selected }?.let { course ->
        GameConfirmation(
            stringResource(R.string.progress_training),
            stringResource(
                R.string.progress_course_details,
                gameMoney(course.priceMinor),
                course.durationHours,
                course.experienceGain
            ),
            stringResource(R.string.progress_study),
            { selected = null },
            {
                selected = null
                game.startCourse(session.id, course.id)
            }
        )
    }
}

@Composable
private fun courseBlockMessage(error: GameError): String = stringResource(
    when (error) {
        GameError.Paused -> R.string.progress_unpause_hint
        GameError.InsufficientEnergy -> R.string.game_no_energy
        is GameError.InsufficientFunds -> R.string.game_insufficient_funds
        GameError.GameFinished -> R.string.game_finished
        else -> R.string.progress_busy
    }
)

@Composable
private fun BalanceHistory(balances: List<Pair<Long, Long>>) {
    SectionCard(stringResource(R.string.progress_balance_history)) {
        val maximum = balances.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            balances.forEach { (day, balance) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height((80f * balance / maximum).coerceAtLeast(2f).dp)
                            .background(LastPaycheckTheme.colors.primary)
                    )
                    LpCaption(
                        stringResource(R.string.progress_day_number, day),
                        Modifier.padding(top = LpSpacing.xs)
                    )
                }
            }
        }
        LpCaption(stringResource(R.string.progress_balance_history_hint))
    }
}

@Composable
private fun EventsPanel(session: GameSession) {
    SectionCard(stringResource(R.string.progress_events)) {
        val active = session.events.filter { it.remainingAt(session.clock.gameMillis) > 0 }
        if (active.isEmpty()) LpCaption(stringResource(R.string.progress_no_events))
        active.forEach {
            LpBody(it.description)
            LpCaption(
                stringResource(
                    R.string.progress_event_effect,
                    gameMoney(it.moneyLostMinor),
                    it.healthLost,
                    it.remainingAt(session.clock.gameMillis) / GAME_MINUTE_MILLIS
                )
            )
        }
    }
}

@Composable
internal fun ResultsScreen(
    session: GameSession,
    finances: FinanceViewModel,
    onNew: () -> Unit,
    onBack: () -> Unit
) {
    val result by finances.state.collectAsStateWithLifecycle()
    LaunchedEffect(session.id) { finances.load(session.id) }
    SectionPage(
        stringResource(
            R.string.progress_results
        ),
        gameTime(session.clock),
        session,
        onBack,
        {
            LpButton(stringResource(R.string.game_new), onNew, Modifier.fillMaxWidth())
        }
    ) {
        LpBody(stringResource(R.string.progress_finish_reason))
        LpBody(stringResource(R.string.progress_days, session.clock.day))
        LpCaption(
            stringResource(R.string.progress_work_time, session.workMillis / GAME_HOUR_MILLIS)
        )
        LpCaption(
            stringResource(
                R.string.progress_xp,
                session.player.experience,
                100 - session.player.experience % 100
            )
        )
        NeedsPanel(session.player.needs)
        when (val current = result) {
            null -> GameLoading()
            is DomainResult.Failure -> GameErrorPanel(current.error) { finances.load(session.id) }
            is DomainResult.Success -> {
                SectionCard(stringResource(R.string.progress_income)) {
                    MoneyValue(current.value.statistics.incomeMinor)
                }
                SectionCard(stringResource(R.string.progress_expense)) {
                    MoneyValue(current.value.statistics.expenseMinor)
                }
            }
        }
        GameBackButton(onBack)
    }
}

@Composable
internal fun validationMessage(issue: ValidationIssue, email: Boolean = false): String =
    stringResource(
        when (issue) {
            ValidationIssue.REQUIRED -> R.string.progress_required
            ValidationIssue.TOO_LONG -> R.string.progress_too_long
            ValidationIssue.FORMAT -> if (email) {
                R.string.progress_email_format
            } else {
                R.string.progress_name_format
            }
            ValidationIssue.PASSWORD_SHORT -> R.string.progress_password_short
            ValidationIssue.MISMATCH -> R.string.progress_password_mismatch
        }
    )
