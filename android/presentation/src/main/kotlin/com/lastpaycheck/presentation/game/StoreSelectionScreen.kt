package com.lastpaycheck.presentation.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing

@Composable
internal fun StoreSelectionScreen(
    session: GameSession,
    game: MainViewModel,
    onGroceries: () -> Unit,
    onDealership: () -> Unit,
    onBack: () -> Unit
) {
    val result by game.sectionCatalog.collectAsStateWithLifecycle()
    val cars by game.vehicleCatalog.collectAsStateWithLifecycle()
    LaunchedEffect(
        session.id,
        session.clock.day
    ) {
        game.loadSection(session.id, session.clock.day)
        game.loadVehicles()
    }
    SectionPage(
        stringResource(R.string.stores_title),
        stringResource(R.string.stores_subtitle),
        session,
        onBack,
        footer = {},
        screenTag = "screen.stores"
    ) {
        SectionCard(stringResource(R.string.game_grocery)) {
            Image(
                painterResource(R.drawable.grocery),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(112.dp),
                contentScale = ContentScale.Crop
            )
            LpBody(stringResource(R.string.game_meal))
            val catalog = (result as? DomainResult.Success)?.value
                ?.takeIf { it.sessionId == session.id && it.day == session.clock.day }
            val meal = catalog?.products?.firstOrNull { it.product.id == "meal" }
                ?: catalog?.products?.firstOrNull()
            meal?.let {
                LpCaption(
                    stringResource(R.string.game_product_price, gameMoney(it.quote.unitPriceMinor)),
                    color = LastPaycheckTheme.colors.accent
                )
            }
            (result as? DomainResult.Failure)?.let {
                GameErrorPanel(it.error) { game.loadSection(session.id, session.clock.day) }
            }
            if (result == null) GameLoading()
            LpButton(
                stringResource(R.string.stores_buy_food),
                onGroceries,
                Modifier.fillMaxWidth().testTag("stores.groceries")
            )
        }
        SectionCard(stringResource(R.string.stores_motors)) {
            Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
                GameIcon(R.drawable.game_ic_car, LastPaycheckTheme.colors.accent)
                LpBody(stringResource(R.string.stores_car_description))
            }
            LpCaption(
                stringResource(
                    R.string.vehicle_from_price,
                    gameMoney(
                        (cars as? DomainResult.Success)?.value?.vehicles
                            ?.minOfOrNull { it.priceMinor } ?: 6000
                    )
                ),
                color = LastPaycheckTheme.colors.accent
            )
            LpCaption(stringResource(R.string.vehicles_unavailable))
            LpButton(
                stringResource(R.string.stores_open_dealership),
                onDealership,
                Modifier.fillMaxWidth().testTag("stores.dealership")
            )
        }
    }
}

@Composable
internal fun VehicleStatusScreen(session: GameSession, garage: Boolean, onBack: () -> Unit) {
    SectionPage(
        stringResource(if (garage) R.string.city_garage else R.string.stores_motors),
        stringResource(R.string.stores_car_description),
        session,
        onBack,
        { GameBackButton(onBack) },
        screenTag = if (garage) "screen.garage" else "screen.dealership"
    ) {
        SectionArtwork(R.drawable.street)
        SectionCard(stringResource(R.string.vehicles_unavailable)) {
            GameIcon(
                if (garage) R.drawable.game_ic_garage else R.drawable.game_ic_car,
                LastPaycheckTheme.colors.accent
            )
            val message = if (garage) R.string.vehicles_garage_empty else R.string.vehicles_status
            LpBody(stringResource(message))
            if (!garage) {
                LpCaption(stringResource(R.string.stores_car_price))
                LpButton(
                    stringResource(R.string.vehicles_buy),
                    {},
                    Modifier.fillMaxWidth().testTag("dealership.buy"),
                    enabled = false
                )
            }
        }
    }
}
