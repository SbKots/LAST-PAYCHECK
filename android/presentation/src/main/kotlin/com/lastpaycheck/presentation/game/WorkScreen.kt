package com.lastpaycheck.presentation.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.GAME_MINUTE_MILLIS
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.ShiftResult
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun WorkContent(
    session: GameSession,
    jobs: List<Job>,
    busy: Boolean,
    effect: GameEffect?,
    game: MainViewModel,
    onHardware: (() -> Unit)?,
    onBack: () -> Unit
) {
    val shift = session.activeActivity as? WorkShift
    var selectedId by rememberSaveable(session.id) { mutableStateOf(session.player.selectedJobId) }
    var preparing by rememberSaveable(session.id) { mutableStateOf(false) }
    val job = jobs.find { it.id == selectedId }
    val backToJobs = { preparing = false }
    BackHandler(preparing && shift == null && job != null, backToJobs)
    when {
        shift != null -> ActiveShift(session, shift, busy, effect, game, onBack)
        preparing && job != null -> ShiftPreparation(
            session,
            job,
            busy,
            game::togglePause,
            backToJobs
        ) {
            preparing = false
            game.startShift(session.id, job.id)
        }
        else -> SectionPage(
            stringResource(R.string.game_find_shift),
            stringResource(R.string.game_career_level, session.player.level),
            session,
            onBack,
            screenTag = "screen.work",
            footer = {
                SectionFooter(
                    action = stringResource(R.string.game_shift_requirements),
                    enabled = job != null && !busy
                ) { preparing = true }
            }
        ) {
            GamePauseNotice(session, game::togglePause, busy)
            onHardware?.let {
                LpButton(stringResource(R.string.hardware_title), it, Modifier.fillMaxWidth())
            }
            if (jobs.isEmpty()) LpBody(stringResource(R.string.game_no_jobs))
            if (effect is GameEffect.ShiftFinished) ShiftSummary(effect.result)
            Column(verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
                jobs.forEach { entry ->
                    JobListCard(entry, session.player.level) {
                        selectedId = entry.id
                        preparing = true
                    }
                }
            }
            if (effect !is GameEffect.ShiftFinished) {
                session.lastShiftResult?.let {
                    ShiftSummary(
                        it
                    )
                }
            }
        }
    }
}

@Composable
private fun JobListCard(job: Job, playerLevel: Int, onClick: () -> Unit) {
    val colors = LastPaycheckTheme.colors
    val shape = MaterialTheme.shapes.medium
    val available = playerLevel >= job.requiredLevel
    val status = if (available) {
        stringResource(R.string.game_job_available)
    } else {
        stringResource(R.string.game_job_level_badge, job.requiredLevel)
    }
    val marker = if (job.requiredLevel == 0) R.drawable.game_ic_work else R.drawable.game_ic_box
    Surface(
        Modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button, onClick = onClick),
        color = colors.surface,
        shape = shape
    ) {
        Column(
            Modifier.padding(LpSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LpSpacing.xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameIcon(marker, colors.primary)
                LpHeading(gameJob(job.id), LpHeadingLevel.Card)
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = colors.surfaceAlt
            ) {
                LpCaption(
                    status,
                    Modifier.padding(horizontal = LpSpacing.sm, vertical = LpSpacing.xs),
                    if (available) colors.primary else colors.accent
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LpCaption(stringResource(R.string.game_pay_and_bonus), Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.game_job_rates,
                        gameAmount(job.basePayMinor),
                        gameAmount(job.bonusCapMinor)
                    ),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            LpCaption(stringResource(R.string.game_job_duration, job.durationHours))
            LpCaption(stringResource(R.string.game_job_plan, minOf(24, job.plan)))
            LpCaption(stringResource(R.string.game_job_energy, job.extraEnergyPerHour))
        }
    }
}

@Composable
private fun ShiftPreparation(
    session: GameSession,
    job: Job,
    busy: Boolean,
    onResume: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    val blocked = blockedJob(session, job)
    SectionPage(
        stringResource(R.string.game_before_shift),
        gameJob(job.id),
        session,
        onBack,
        footer = {
            SectionFooter(
                onBack,
                stringResource(R.string.game_start_shift),
                !busy && blocked == null,
                onStart
            )
        }
    ) {
        GamePauseNotice(session, onResume, busy)
        SectionCard(stringResource(R.string.sorting_title)) {
            LpBody(stringResource(R.string.sorting_intro))
            LpCaption(stringResource(R.string.sorting_round))
            BoxCategory.entries.forEach { LpCaption(stringResource(sortLabel(it))) }
        }
        SectionScene(R.drawable.warehouse) {
            SectionCard(stringResource(R.string.game_shift_requirements)) {
                JobDetails(job)
                Need(
                    R.string.game_energy,
                    session.player.needs.energy,
                    Modifier.fillMaxWidth(),
                    accent = true
                )
                blocked?.let { LpCaption(stringResource(it)) }
                LpCaption(stringResource(R.string.game_sort_hint))
            }
        }
    }
}

private fun blockedJob(session: GameSession, job: Job): Int? = when {
    session.status == SessionStatus.FINISHED -> R.string.game_finished
    session.clock.isPaused -> R.string.progress_unpause_hint
    session.activeActivity != null -> R.string.game_busy
    session.player.needs.energyUnits == 0L -> R.string.game_no_energy
    job.requiredLevel > session.player.level -> R.string.game_job_locked
    else -> null
}

@Composable
private fun JobDetails(job: Job) {
    LpCaption(
        stringResource(
            R.string.game_base_pay,
            gameMoney(job.basePayMinor),
            gameMoney(job.bonusCapMinor)
        ),
        color = LastPaycheckTheme.colors.accent
    )
    LpCaption(
        stringResource(
            R.string.game_shift_conditions,
            job.durationHours,
            minOf(24, job.plan),
            job.extraEnergyPerHour
        )
    )
    LpCaption(stringResource(R.string.game_level_required, job.requiredLevel))
}

@Composable
private fun ActiveShift(
    session: GameSession,
    shift: WorkShift,
    busy: Boolean,
    effect: GameEffect?,
    game: MainViewModel,
    onBack: () -> Unit
) {
    var finishing by rememberSaveable(shift.id) { mutableStateOf(false) }
    val boxId = shift.selectedBox.id
    var selectedZone by rememberSaveable(boxId) { mutableStateOf<BoxCategory?>(null) }
    val enabled = !busy && !session.clock.isPaused
    val ready = session.clock.gameMillis >= shift.boxReadyAtGameMillis
    SectionPage(
        stringResource(R.string.game_warehouse),
        gameTime(session.clock),
        session,
        onBack,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.xs)) {
                BoxCategory.entries.forEach { zone ->
                    LpButton(
                        stringResource(sortLabel(zone)),
                        { selectedZone = zone },
                        Modifier.weight(1f).testTag("work.zone.${zone.name}"),
                        style = if (zone == selectedZone) {
                            LpButtonStyle.Primary
                        } else {
                            LpButtonStyle.Secondary
                        },
                        enabled = enabled
                    )
                }
            }
            LpButton(
                stringResource(if (ready) R.string.game_place_box else R.string.sorting_transit),
                { selectedZone?.let { game.placeBox(session.id, boxId, it) } },
                Modifier.fillMaxWidth().testTag("work.place"),
                enabled = enabled && ready && selectedZone != null
            )
        }
    ) {
        GamePauseNotice(session, game::togglePause, busy)
        Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
            ShiftMetric(
                R.string.game_shift_correct,
                "${shift.correct} / ${shift.targetBoxes}",
                Modifier.weight(1f)
            )
            ShiftMetric(R.string.game_shift_errors, shift.errors.toString(), Modifier.weight(1f))
            ShiftMetric(
                R.string.game_shift_remaining,
                if (shift.compact) {
                    stringResource(
                        R.string.sorting_seconds,
                        (
                            shift.remainingGameMillis * 90 /
                                (shift.job.durationHours * 3_600_000L)
                            ).toInt()
                    )
                } else {
                    durationLabel(shift.remainingGameMillis)
                },
                Modifier.weight(1.4f)
            )
        }
        LpCaption(
            stringResource(
                R.string.sorting_progress,
                shift.processedBoxIds.size,
                shift.targetBoxes
            )
        )
        LinearProgressIndicator(
            progress = {
                (shift.processedBoxIds.size.toFloat() / shift.targetBoxes).coerceIn(
                    0f,
                    1f
                )
            },
            modifier = Modifier.fillMaxWidth(),
            color = LastPaycheckTheme.colors.primary
        )
        SectionCard(stringResource(R.string.game_current_box)) {
            val category = shift.selectedBox.category
            Text(
                text = when (category) {
                    BoxCategory.FOOD -> "■ A"
                    BoxCategory.FRAGILE -> "◆ B"
                    BoxCategory.GENERAL -> "● C"
                },
                style = MaterialTheme.typography.displayLarge,
                color = when (category) {
                    BoxCategory.FOOD -> LastPaycheckTheme.colors.primary
                    BoxCategory.FRAGILE -> LastPaycheckTheme.colors.accent
                    BoxCategory.GENERAL -> MaterialTheme.colorScheme.onSurface
                }
            )
            LpBody(
                stringResource(R.string.sorting_box, stringResource(sortLabel(category))),
                Modifier.testTag("work.box")
            )
            LpCaption(stringResource(R.string.sorting_choose))
            if (effect is GameEffect.Placement) {
                val expected = effect.expected ?: category
                val message = if (effect.correct) {
                    stringResource(
                        R.string.sorting_correct,
                        stringResource(sortLabel(expected))
                    )
                } else {
                    stringResource(
                        R.string.sorting_wrong,
                        stringResource(sortLabel(expected)),
                        stringResource(sortLabel(effect.actual ?: category))
                    )
                }
                LpCaption(message, Modifier.testTag("work.feedback"))
            }
            Need(
                R.string.game_energy,
                session.player.needs.energy,
                Modifier.fillMaxWidth(),
                accent = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
            LpButton(
                stringResource(
                    if (session.clock.isPaused) R.string.game_unpause else R.string.game_pause
                ),
                game::togglePause,
                Modifier.weight(1f),
                LpButtonStyle.Secondary,
                !busy
            )
            LpButton(
                stringResource(R.string.game_finish_shift),
                { finishing = true },
                Modifier.weight(1f),
                LpButtonStyle.Secondary,
                enabled
            )
        }
    }
    if (finishing && enabled) {
        GameConfirmation(
            title = stringResource(R.string.game_finish_shift),
            message = stringResource(R.string.game_finish_shift_confirm),
            action = stringResource(R.string.game_finish_shift),
            onDismiss = { finishing = false },
            onConfirm = {
                finishing = false
                game.finishShift(session.id)
            }
        )
    }
}

@Composable
private fun ShiftMetric(label: Int, value: String, modifier: Modifier) {
    SectionCard(value, modifier) { LpCaption(stringResource(label)) }
}

@Composable
private fun ShiftSummary(result: ShiftResult) {
    SectionCard(stringResource(R.string.game_shift_complete)) {
        LpCaption(
            stringResource(R.string.game_shift_wage, gameMoney(result.wageMinor)),
            color = LastPaycheckTheme.colors.primary
        )
        LpBody(
            stringResource(
                R.string.game_shift_summary,
                result.correct,
                result.errors,
                durationLabel(result.workedGameMillis),
                result.experienceGained
            )
        )
        if (result.debtPaidMinor > 0) {
            LpCaption(stringResource(R.string.game_shift_debt, gameMoney(result.debtPaidMinor)))
        }
    }
}

@Composable
private fun durationLabel(millis: Long): String {
    val minutes = millis / GAME_MINUTE_MILLIS
    return stringResource(R.string.game_duration, minutes / 60, minutes % 60)
}

private fun boxLabel(category: BoxCategory): Int = when (category) {
    BoxCategory.FOOD -> R.string.game_box_food
    BoxCategory.FRAGILE -> R.string.game_box_fragile
    BoxCategory.GENERAL -> R.string.game_box_general
}

private fun sortLabel(category: BoxCategory): Int = when (category) {
    BoxCategory.FOOD -> R.string.sorting_food
    BoxCategory.FRAGILE -> R.string.sorting_fragile
    BoxCategory.GENERAL -> R.string.sorting_general
}
