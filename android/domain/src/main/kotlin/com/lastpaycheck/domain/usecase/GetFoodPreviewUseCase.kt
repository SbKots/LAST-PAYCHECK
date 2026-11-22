package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FoodPreview
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.Product
import com.lastpaycheck.domain.model.SessionStatus

class GetFoodPreviewUseCase {
    operator fun invoke(session: GameSession, product: Product): DomainResult<FoodPreview> {
        if (!session.isValid() || !product.isValid()) {
            return DomainResult.Failure(GameError.InvalidInput("foodPreview"))
        }
        val quantity = session.inventory.quantity(product.id)
        val blocked = when {
            session.status == SessionStatus.FINISHED -> GameError.GameFinished
            session.activeActivity != null -> GameError.Busy
            quantity == 0 -> GameError.EmptyInventory
            session.clock.isPaused -> GameError.Paused
            else -> null
        }
        return DomainResult.Success(
            FoodPreview(
                quantity,
                session.player.needs.satiety,
                session.player.needs.afterFood(product.satietyGain).satiety,
                product.durationMinutes,
                blocked
            )
        )
    }
}
