package com.lastpaycheck.presentation.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GAME_MINUTE_MILLIS
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import com.lastpaycheck.presentation.ui.theme.LpSpacing
import kotlin.math.roundToInt

@Composable
internal fun FridgeScreen(
    session: GameSession,
    game: MainViewModel,
    onStores: () -> Unit,
    onBack: () -> Unit
) {
    val result by game.sectionCatalog.collectAsStateWithLifecycle()
    val busy by game.sectionBusy.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable(session.id) { mutableStateOf("meal") }
    var observedFood by rememberSaveable(session.id) { mutableStateOf(false) }
    var quantityBefore by rememberSaveable(session.id) { mutableStateOf(0) }
    LaunchedEffect(
        session.id,
        session.clock.day
    ) { game.loadSection(session.id, session.clock.day) }
    val catalog = (result as? DomainResult.Success)?.value
        ?.takeIf { it.sessionId == session.id && it.day == session.clock.day }?.gameCatalog
    val product = catalog?.products?.find { it.id == selectedId }
        ?: catalog?.products?.firstOrNull()
    val previewResult = product?.let { game.foodPreview(session.id, it) }
    val preview = (previewResult as? DomainResult.Success)?.value
    val active = session.activeActivity as? TimedActivity.Food
    LaunchedEffect(active?.id, product?.id) {
        if (active != null && product?.id == active.product.id) {
            observedFood = true
            quantityBefore = session.inventory.quantity(active.product.id)
        }
    }
    SectionPage(
        stringResource(R.string.game_fridge),
        stringResource(R.string.fridge_subtitle),
        session,
        onBack,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(LpSpacing.sm)) {
                LpButton(
                    stringResource(R.string.game_cancel),
                    onBack,
                    Modifier.weight(1f),
                    LpButtonStyle.Secondary
                )
                LpButton(
                    stringResource(R.string.fridge_eat),
                    {
                        product?.let {
                            observedFood = false
                            game.start(ActivityRequest.Food(it.id))
                        }
                    },
                    Modifier.weight(1f).testTag("fridge.eat"),
                    enabled = !busy && preview != null && preview.blockedReason == null
                )
            }
        },
        screenTag = "screen.fridge"
    ) {
        GamePauseNotice(session, game::togglePause, busy)
        SectionScene(R.drawable.apartment) {
            when {
                result is DomainResult.Failure -> GameErrorPanel(
                    (result as DomainResult.Failure).error
                ) { game.loadSection(session.id, session.clock.day) }
                previewResult is DomainResult.Failure -> GameErrorPanel(
                    previewResult.error,
                    game::retry
                )
                product == null || preview == null -> GameLoading()
                else -> {
                    SectionCard(
                        if (product.id == "meal") {
                            stringResource(R.string.fridge_meal)
                        } else {
                            productName(product.id)
                        }
                    ) {
                        LpBody(stringResource(R.string.game_in_stock, preview.quantity))
                        LpCaption(
                            stringResource(
                                R.string.fridge_satiety,
                                preview.satietyBefore.roundToInt(),
                                preview.satietyAfter.roundToInt()
                            ),
                            color = LastPaycheckTheme.colors.primary
                        )
                        LpCaption(stringResource(R.string.fridge_time, preview.durationMinutes))
                    }
                    SectionCard(stringResource(R.string.fridge_result)) {
                        LpCaption(stringResource(R.string.fridge_result_hint))
                        if (active != null && active.product.id == product.id) {
                            LpBody(
                                stringResource(
                                    R.string.fridge_eating,
                                    (active.remainingGameMillis + GAME_MINUTE_MILLIS - 1) /
                                        GAME_MINUTE_MILLIS
                                )
                            )
                        } else if (observedFood && preview.quantity < quantityBefore) {
                            LpBody(
                                stringResource(
                                    R.string.fridge_eaten,
                                    preview.satietyBefore.roundToInt()
                                )
                            )
                        }
                        when (preview.blockedReason) {
                            GameError.EmptyInventory -> {
                                LpCaption(stringResource(R.string.progress_fridge_empty))
                                LpButton(
                                    stringResource(R.string.fridge_buy_food),
                                    onStores,
                                    Modifier.fillMaxWidth(),
                                    LpButtonStyle.Secondary
                                )
                            }
                            GameError.Busy -> if (active == null) {
                                LpCaption(stringResource(R.string.progress_busy))
                            }
                            GameError.GameFinished -> LpCaption(
                                stringResource(R.string.game_finished)
                            )
                            else -> Unit
                        }
                    }
                    catalog?.products.orEmpty().filterNot { it.id == product.id }.forEach { other ->
                        LpButton(
                            productName(other.id),
                            { selectedId = other.id },
                            Modifier.fillMaxWidth(),
                            LpButtonStyle.Secondary
                        )
                    }
                }
            }
        }
    }
}
