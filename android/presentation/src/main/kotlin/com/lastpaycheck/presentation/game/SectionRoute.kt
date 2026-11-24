package com.lastpaycheck.presentation.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.hardware.WorkTiltViewModel
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel

internal enum class Section(val title: Int) {
    Work(R.string.game_work_section),
    Shop(R.string.game_grocery)
}

@Composable
internal fun SectionRoute(
    game: MainViewModel,
    sessionId: String,
    section: Section,
    tilt: WorkTiltViewModel? = null,
    onHardware: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val state by game.state.collectAsStateWithLifecycle()
    val catalog by game.sectionCatalog.collectAsStateWithLifecycle()
    val effect by game.sectionEffect.collectAsStateWithLifecycle()
    val busy by game.sectionBusy.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { game.restoreHome(sessionId) }
    val session = (state.forSession(sessionId) as? MainUiState.Content)?.session
    LaunchedEffect(sessionId, session?.clock?.day) {
        session?.let { game.loadSection(sessionId, it.clock.day) }
    }

    val back = {
        game.dismissSectionEffect()
        onBack()
    }
    val loaded = (catalog as? DomainResult.Success)?.value
    if (session != null && loaded?.sessionId == sessionId && loaded.day == session.clock.day) {
        when (section) {
            Section.Work -> WorkContent(
                session,
                loaded.gameCatalog.jobs,
                busy,
                effect,
                game,
                checkNotNull(tilt),
                onHardware,
                back
            )
            Section.Shop -> ShopContent(session, loaded.products, busy, effect, game, back)
        }
        return
    }
    SectionPage(stringResource(section.title), "", session, back, { GameBackButton(back) }) {
        when (val current = state.forSession(sessionId)) {
            MainUiState.Empty -> LpBody(stringResource(R.string.game_no_save))
            is MainUiState.Error -> GameErrorPanel(current.reason, game::retry)
            else -> {
                val failure = catalog as? DomainResult.Failure
                if (failure != null && session != null) {
                    GameErrorPanel(failure.error) { game.loadSection(sessionId, session.clock.day) }
                } else {
                    GameLoading()
                }
            }
        }
    }
}

@Composable
internal fun GameConfirmation(
    title: String,
    message: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val cancelLabel = stringResource(R.string.game_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LpHeading(title, LpHeadingLevel.Section) },
        text = { LpBody(message) },
        confirmButton = { LpButton(action, onConfirm) },
        dismissButton = {
            LpButton(cancelLabel, onDismiss, style = LpButtonStyle.Outline)
        }
    )
}

@Composable
internal fun SectionAction(label: Int, enabled: Boolean, onClick: () -> Unit) {
    LpButton(stringResource(label), onClick, Modifier.fillMaxWidth(), enabled = enabled)
}
