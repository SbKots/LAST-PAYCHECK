package com.lastpaycheck.presentation.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.presentation.R
import com.lastpaycheck.presentation.ui.components.LpBody
import com.lastpaycheck.presentation.ui.components.LpButton
import com.lastpaycheck.presentation.ui.components.LpButtonStyle
import com.lastpaycheck.presentation.ui.components.LpCaption
import com.lastpaycheck.presentation.ui.theme.LastPaycheckTheme
import java.util.UUID

@Composable
internal fun ShopContent(
    session: GameSession,
    products: List<StoreProduct>,
    busy: Boolean,
    effect: GameEffect?,
    game: MainViewModel,
    onBack: () -> Unit
) {
    var selectedId by rememberSaveable(session.id) {
        mutableStateOf(products.firstOrNull()?.product?.id)
    }
    val item = products.find { it.product.id == selectedId } ?: products.firstOrNull()
    var quantity by rememberSaveable(item?.product?.id) { mutableIntStateOf(1) }
    var confirmationId by rememberSaveable(item?.quote) { mutableStateOf<String?>(null) }
    val total = item?.totalMinor(quantity)
    val balance = session.player.moneyMinor
    val canAfford = total != null && total <= balance
    val active = session.status == SessionStatus.ACTIVE && !session.clock.isPaused
    val canBuy = canAfford && active && !busy
    val colors = LastPaycheckTheme.colors
    SectionPage(
        stringResource(R.string.game_grocery),
        stringResource(R.string.game_grocery_subtitle),
        session,
        onBack,
        screenTag = "screen.groceries",
        footer = {
            if (item == null) {
                GameBackButton(onBack)
            } else {
                SectionFooter(
                    onBack,
                    pluralStringResource(R.plurals.game_buy_portions, quantity, quantity),
                    canBuy
                ) {
                    confirmationId = UUID.randomUUID().toString()
                }
            }
        }
    ) {
        GamePauseNotice(session, game::togglePause, busy)
        SectionScene(R.drawable.grocery) {
            if (effect is GameEffect.Purchase) {
                SectionCard(stringResource(R.string.game_purchase_done)) {
                    LpBody(
                        stringResource(
                            R.string.game_purchase_receipt,
                            productName(effect.productId),
                            effect.quantity,
                            gameMoney(effect.costMinor)
                        )
                    )
                    LpButton(
                        stringResource(R.string.game_close),
                        game::dismissSectionEffect,
                        style = LpButtonStyle.Outline
                    )
                }
            }
            if (item == null) {
                LpBody(stringResource(R.string.game_no_products))
            } else {
                val product = item.product
                SectionCard(productName(product.id)) {
                    LpCaption(
                        stringResource(
                            R.string.game_product_price,
                            gameMoney(item.quote.unitPriceMinor)
                        ),
                        color = colors.accent
                    )
                    LpCaption(
                        stringResource(
                            R.string.game_product_effect,
                            product.satietyGain,
                            product.durationMinutes
                        ),
                        color = colors.primary
                    )
                    LpCaption(
                        stringResource(
                            R.string.game_in_stock,
                            session.inventory.quantity(product.id)
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LpButton(
                            "−",
                            { quantity-- },
                            style = LpButtonStyle.Secondary,
                            enabled = quantity > 1 && !busy
                        )
                        LpBody(
                            pluralStringResource(
                                R.plurals.game_quantity_portions,
                                quantity,
                                quantity
                            )
                        )
                        LpButton(
                            "+",
                            { quantity++ },
                            style = LpButtonStyle.Secondary,
                            enabled = quantity < Int.MAX_VALUE && !busy &&
                                item.canAfford(quantity + 1, balance)
                        )
                    }
                }
                total?.let {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LpCaption(stringResource(R.string.game_in_basket))
                        LpCaption(gameMoney(it), color = colors.accent)
                    }
                    if (canAfford) {
                        LpCaption(
                            stringResource(R.string.game_after_purchase, gameMoney(balance - it))
                        )
                    }
                }
                if (!canAfford) LpBody(stringResource(R.string.game_insufficient_funds))
                if (session.status == SessionStatus.FINISHED) {
                    LpCaption(stringResource(R.string.game_finished))
                }
                products.filterNot { it.product.id == product.id }.forEach { other ->
                    SectionCard(productName(other.product.id)) {
                        LpCaption(
                            stringResource(
                                R.string.game_product_price,
                                gameMoney(other.quote.unitPriceMinor)
                            )
                        )
                        SectionAction(
                            R.string.game_choose_product,
                            !busy
                        ) { selectedId = other.product.id }
                    }
                }
            }
        }
    }
    val pending = confirmationId
    if (item != null && pending != null && total != null && canBuy) {
        GameConfirmation(
            title = stringResource(R.string.game_purchase_confirm),
            message = stringResource(
                R.string.game_purchase_receipt,
                productName(item.product.id),
                quantity,
                gameMoney(total)
            ),
            action = stringResource(R.string.game_buy),
            onDismiss = { confirmationId = null },
            onConfirm = {
                if (confirmationId != null) {
                    confirmationId = null
                    game.buy(session.id, item, quantity, pending)
                }
            }
        )
    }
}

@Composable
internal fun productName(id: String): String =
    if (id == "meal") stringResource(R.string.game_meal) else id
