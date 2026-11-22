package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.Course
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.SessionStatus

class GetCourseAvailabilityUseCase {
    operator fun invoke(session: GameSession, course: Course): GameError? = when {
        session.status == SessionStatus.FINISHED -> GameError.GameFinished
        session.clock.isPaused -> GameError.Paused
        session.activeActivity != null -> GameError.Busy
        session.player.needs.energyUnits == 0L -> GameError.InsufficientEnergy
        session.player.moneyMinor < course.priceMinor ->
            GameError.InsufficientFunds(course.priceMinor, session.player.moneyMinor)
        else -> null
    }
}
