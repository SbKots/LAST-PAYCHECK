package com.lastpaycheck.presentation.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.components.LpHeading
import com.lastpaycheck.presentation.ui.components.LpHeadingLevel
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun CityScreen(
    session: GameSession,
    onWork: () -> Unit,
    onStores: () -> Unit,
    onGarage: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit
) {
    SectionPage(
        stringResource(R.string.city_title),
        stringResource(R.string.city_subtitle),
        session,
        onBack,
        footer = {},
        screenTag = "screen.city"
    ) {
        Image(
            painterResource(R.drawable.street),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f)
        )
        Column(verticalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
            CityDestination(
                R.string.city_warehouse,
                R.string.city_work_hint,
                R.drawable.game_ic_work,
                onWork
            )
            CityDestination(
                R.string.game_grocery,
                R.string.city_shop_hint,
                R.drawable.game_ic_shop,
                onStores
            )
            CityDestination(
                R.string.city_garage,
                R.string.city_garage_hint,
                R.drawable.game_ic_garage,
                onGarage
            )
            CityDestination(
                R.string.city_return_home,
                R.string.city_home_hint,
                R.drawable.game_ic_home,
                onHome
            )
        }
    }
}

@Composable
private fun CityDestination(title: Int, subtitle: Int, icon: Int, onClick: () -> Unit) {
    val colors = LastPaycheckTheme.colors
    Surface(
        Modifier.fillMaxWidth().testTag("city.$title")
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = colors.surface
    ) {
        Row(
            Modifier.padding(LpSpacing.base),
            horizontalArrangement = Arrangement.spacedBy(LpSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameIcon(icon, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LpSpacing.xs)) {
                LpHeading(stringResource(title), LpHeadingLevel.Card)
                LpCaption(stringResource(subtitle))
            }
            Icon(
                painterResource(R.drawable.game_ic_back),
                contentDescription = null,
                modifier = Modifier.rotate(180f),
                tint = colors.primary
            )
        }
    }
}
