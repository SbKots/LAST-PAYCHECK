package com.lastpaycheck.presentation.game

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun SectionPage(
    title: String,
    subtitle: String,
    session: GameSession?,
    onBack: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LastPaycheckTheme.colors
    val back = stringResource(R.string.game_back)
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.safeDrawingPadding()) {
            Surface(color = colors.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(
                        horizontal = LpSpacing.base,
                        vertical = LpSpacing.sm
                    ),
                    horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LpButton(
                        "‹",
                        onBack,
                        Modifier.semantics { contentDescription = back },
                        LpButtonStyle.Secondary
                    )
                    Column(Modifier.weight(1f)) {
                        LpHeading(title, LpHeadingLevel.Card)
                        if (subtitle.isNotEmpty()) LpCaption(subtitle)
                    }
                    session?.let {
                        LpCaption(gameMoney(it.player.moneyMinor), color = colors.accent)
                    }
                }
            }
            Column(
                Modifier.weight(1f).align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState()).padding(LpSpacing.base),
                verticalArrangement = Arrangement.spacedBy(LpSpacing.base),
                content = content
            )
            Surface(color = colors.background) {
                Column(
                    Modifier.fillMaxWidth().padding(
                        horizontal = LpSpacing.base,
                        vertical = LpSpacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
                ) {
                    footer()
                }
            }
        }
    }
}

@Composable
internal fun SectionFooter(
    back: (() -> Unit)? = null,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
        if (back != null) {
            LpButton(
                stringResource(R.string.game_back),
                back,
                Modifier.weight(1f),
                LpButtonStyle.Secondary
            )
        }
        LpButton(action, onAction, Modifier.weight(1f), enabled = enabled)
    }
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = LastPaycheckTheme.colors.surface
    ) {
        Column(
            Modifier.padding(LpSpacing.base),
            verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)
        ) {
            LpHeading(title, LpHeadingLevel.Card)
            content()
        }
    }
}

@Composable
internal fun SectionArtwork(@DrawableRes image: Int) {
    Image(
        painterResource(image),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f)
    )
}

@Composable
internal fun SectionScene(@DrawableRes image: Int, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.base)) {
                Box(Modifier.weight(1.15f)) { SectionArtwork(image) }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LpSpacing.base),
                    content = content
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LpSpacing.base)) {
                SectionArtwork(image)
                content()
            }
        }
    }
}
