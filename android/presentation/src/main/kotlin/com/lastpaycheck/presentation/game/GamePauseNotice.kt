package com.lastpaycheck.presentation.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun GamePauseNotice(session: GameSession, onResume: () -> Unit, busy: Boolean = false) {
    if (session.status != SessionStatus.ACTIVE || !session.clock.isPaused) return
    Column(
        Modifier.fillMaxWidth().testTag("pause.notice"),
        verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
    ) {
        LpCaption(stringResource(R.string.progress_unpause_hint))
        if (PauseReason.MANUAL in session.clock.pauseReasons) {
            LpButton(
                stringResource(R.string.progress_resume),
                onResume,
                Modifier.fillMaxWidth().testTag("pause.resume"),
                LpButtonStyle.Secondary,
                enabled = !busy
            )
        }
    }
}
