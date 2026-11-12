package com.lastpaycheck.domain.engine

import com.lastpaycheck.domain.model.Activity
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.FinishReason
import com.lastpaycheck.domain.model.GAME_DAY_MILLIS
import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameEffect
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.Job
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.SessionStatus
import com.lastpaycheck.domain.model.TimedActivity
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.WarehouseBox
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.domain.model.checkedArithmetic
import com.lastpaycheck.domain.model.failure
import com.lastpaycheck.domain.model.flatMap
import com.lastpaycheck.domain.repository.RandomSource

class GameEngine(
    private val random: RandomSource = SeededRandomSource,
    private val events: EventPolicy = NoEventsPolicy
) {
    private val prices = PricePolicy()
    private val wages = WagePolicy()
    private val needs = NeedsPolicy()
    private val rent = RentPolicy(prices)

    internal fun handle(
        session: GameSession,
        key: OperationKey,
        command: GameCommand
    ): DomainResult<GameTransition> = checkedArithmetic {
        if (session.status == SessionStatus.FINISHED) return failure(GameError.GameFinished)
        if (session.clock.isPaused) return failure(GameError.Paused)
        val ledger = Ledger(key)
        when (command) {
            is GameCommand.Buy -> buy(session, command, ledger)
            is GameCommand.SelectJob -> selectJob(session, command.jobId, command.catalog.jobs)
            is GameCommand.StartShift -> startShift(
                session,
                key.id,
                command.jobId,
                command.catalog.jobs
            )
            is GameCommand.MoveBox -> moveBox(session, command.direction)
            is GameCommand.PlaceBox -> placeBox(session, command)
            GameCommand.FinishShift -> withShift(session) { shift ->
                DomainResult.Success(finishShift(session, shift, ledger))
            }
            is GameCommand.StartActivity -> startActivity(session, key.id, command, ledger)
        }
    }

    internal fun advance(
        session: GameSession,
        key: OperationKey,
        activeMillis: Long
    ): DomainResult<GameTransition> = checkedArithmetic {
        if (activeMillis < 0) return failure(GameError.InvalidInput("activeMillis"))
        if (session.status == SessionStatus.FINISHED) return failure(GameError.GameFinished)
        if (session.clock.isPaused || activeMillis == 0L) return transition(session)

        var budget = Math.addExact(
            Math.multiplyExact(activeMillis, 576L),
            session.clock.fractionalRemainder.toLong()
        )
        var current = session
        val ledger = Ledger(key)
        var effect: GameEffect = GameEffect.Applied
        while (current.status == SessionStatus.ACTIVE) {
            val unitCost = 4 / current.speed
            val available = budget / unitCost
            if (available == 0L) break
            val active = current.activeActivity
            val toMidnight = GAME_DAY_MILLIS - current.clock.gameMillis % GAME_DAY_MILLIS
            val event = current.lastEvent
            val eventRemaining = event?.remainingAt(current.clock.gameMillis) ?: 0L
            val toEventEnd = if (eventRemaining > 0) eventRemaining else Long.MAX_VALUE
            val activityRemaining = active?.remainingGameMillis ?: Long.MAX_VALUE
            val chunk = minOf(available, toMidnight, activityRemaining, toEventEnd)
            val eventLoad = if (eventRemaining > 0) {
                checkNotNull(event).extraWorkEnergyPerHour
            } else {
                0
            }
            val advanced = needs.advance(current.player.needs, active, chunk, eventLoad)
            val elapsed = advanced.elapsedGameMillis
            current = current.copy(
                clock = current.clock.copy(
                    gameMillis = Math.addExact(current.clock.gameMillis, elapsed)
                ),
                player = current.player.copy(needs = advanced.needs),
                activeActivity = active?.withRemaining(active.remainingGameMillis - elapsed),
                workMillis = if (active is WorkShift) {
                    Math.addExact(current.workMillis, elapsed)
                } else {
                    current.workMillis
                }
            ).finishIfDead()
            budget -= elapsed * unitCost

            if (current.clock.day > current.lastRentDay) {
                val (charged, paid) = rent.charge(current)
                current = charged
                ledger.record(current, TransactionCategory.RENT, -paid)
                if (current.status == SessionStatus.ACTIVE) {
                    when (val eventResult = applyEvent(current, ledger)) {
                        is DomainResult.Success -> current = eventResult.value
                        is DomainResult.Failure -> return eventResult
                    }
                }
            }
            val ending = current.activeActivity
            if (ending?.remainingGameMillis == 0L) {
                val completed = completeActivity(current, ending, ledger)
                current = completed.session
                if (completed.effect != GameEffect.Applied) effect = completed.effect
            }
        }
        val remainder = if (current.status == SessionStatus.FINISHED) 0 else budget.toInt()
        DomainResult.Success(
            GameTransition(
                current.copy(clock = current.clock.copy(fractionalRemainder = remainder)),
                ledger.transactions,
                effect
            )
        )
    }

    private fun buy(
        session: GameSession,
        command: GameCommand.Buy,
        ledger: Ledger
    ): DomainResult<GameTransition> {
        if (session.activeActivity != null) return failure(GameError.Busy)
        val product = command.catalog.products.find { it.id == command.productId }
            ?: return failure(GameError.NotFound(command.productId))
        val price = prices.price(
            product.basePriceMinor,
            session.scenario,
            session.clock.day - 1
        )
        if (price != command.quotedUnitPriceMinor) return failure(GameError.PriceChanged(price))
        val cost = Math.multiplyExact(price, command.quantity.toLong())
        return debit(session, cost).mapSession { next ->
            ledger.record(next, TransactionCategory.PURCHASE, -cost)
            GameTransition(
                next.copy(inventory = next.inventory.add(product.id, command.quantity)),
                ledger.transactions,
                GameEffect.Purchase(product.id, command.quantity, cost)
            )
        }
    }

    private fun selectJob(
        session: GameSession,
        jobId: String,
        jobs: List<Job>
    ): DomainResult<GameTransition> {
        if (session.activeActivity is WorkShift) return failure(GameError.Busy)
        return availableJob(session, jobId, jobs).flatMap { job ->
            transition(session.copy(player = session.player.copy(selectedJobId = job.id)))
        }
    }

    private fun startShift(
        session: GameSession,
        shiftId: String,
        jobId: String,
        jobs: List<Job>
    ): DomainResult<GameTransition> {
        if (session.activeActivity != null) return failure(GameError.Busy)
        if (session.player.needs.energyUnits == 0L) return failure(GameError.InsufficientEnergy)
        return availableJob(session, jobId, jobs).flatMap { job ->
            val (box, state) = nextBox(session.randomState, shiftId, 0)
            transition(
                session.copy(
                    player = session.player.copy(selectedJobId = job.id),
                    activeActivity = WorkShift(shiftId, job, box),
                    randomState = state
                )
            )
        }
    }

    private fun availableJob(
        session: GameSession,
        jobId: String,
        jobs: List<Job>
    ): DomainResult<Job> {
        val job = jobs.find { it.id == jobId } ?: return failure(GameError.NotFound(jobId))
        if (job.requiredLevel > session.player.level) return failure(GameError.UnavailableJob)
        return DomainResult.Success(job)
    }

    private fun moveBox(session: GameSession, direction: Double) = withShift(session) { shift ->
        val position = (shift.boxPosition + direction).coerceIn(-1.0, 1.0)
        transition(session.copy(activeActivity = shift.copy(boxPosition = position)))
    }

    private fun placeBox(session: GameSession, command: GameCommand.PlaceBox) =
        withShift(session) { shift ->
            if (command.boxId in shift.processedBoxIds) {
                return@withShift failure(
                    GameError.BoxAlreadyPlaced
                )
            }
            if (command.boxId != shift.selectedBox.id) {
                return@withShift failure(
                    GameError.NotFound(command.boxId)
                )
            }
            val correct = shift.selectedBox.category == command.zone
            val processed = shift.processedBoxIds + command.boxId
            val (box, state) = nextBox(session.randomState, shift.id, processed.size)
            val next = shift.copy(
                selectedBox = box,
                correct = shift.correct + if (correct) 1 else 0,
                errors = shift.errors + if (correct) 0 else 1,
                processedBoxIds = processed,
                boxPosition = 0.0
            )
            DomainResult.Success(
                GameTransition(
                    session.copy(activeActivity = next, randomState = state),
                    effect = GameEffect.Placement(correct)
                )
            )
        }

    private fun nextBox(state: Long, shiftId: String, index: Int): Pair<WarehouseBox, Long> {
        val draw = random.next(state, BoxCategory.entries.size)
        return WarehouseBox("$shiftId:$index", BoxCategory.entries[draw.value]) to draw.nextState
    }

    private fun finishShift(
        session: GameSession,
        shift: WorkShift,
        ledger: Ledger
    ): GameTransition {
        val (player, result) = wages.settle(shift, session.player)
        ledger.record(session, TransactionCategory.WAGE, result.wageMinor)
        ledger.record(session, TransactionCategory.DEBT_PAYMENT, -result.debtPaidMinor)
        return GameTransition(
            session.copy(player = player, activeActivity = null, lastShiftResult = result),
            ledger.transactions,
            GameEffect.ShiftFinished(result)
        )
    }

    private fun startActivity(
        session: GameSession,
        activityId: String,
        command: GameCommand.StartActivity,
        ledger: Ledger
    ): DomainResult<GameTransition> {
        if (session.activeActivity != null) return failure(GameError.Busy)
        return when (val request = command.request) {
            ActivityRequest.Sleep -> transition(
                session.copy(activeActivity = TimedActivity.Sleep(activityId))
            )
            ActivityRequest.Rest -> transition(
                session.copy(activeActivity = TimedActivity.Rest(activityId))
            )
            is ActivityRequest.Food -> {
                val product = command.catalog?.products?.find { it.id == request.productId }
                    ?: return failure(GameError.NotFound(request.productId))
                if (session.inventory.quantity(product.id) == 0) {
                    return failure(
                        GameError.EmptyInventory
                    )
                }
                transition(session.copy(activeActivity = TimedActivity.Food(activityId, product)))
            }
            is ActivityRequest.Course -> {
                val course = command.catalog?.courses?.find { it.id == request.courseId }
                    ?: return failure(GameError.NotFound(request.courseId))
                debit(session, course.priceMinor).mapSession { next ->
                    ledger.record(next, TransactionCategory.COURSE, -course.priceMinor)
                    GameTransition(
                        next.copy(activeActivity = TimedActivity.Course(activityId, course)),
                        ledger.transactions
                    )
                }
            }
        }
    }

    private fun completeActivity(
        session: GameSession,
        activity: Activity,
        ledger: Ledger
    ): GameTransition {
        if (activity is WorkShift) return finishShift(session, activity, ledger)
        var next = session.copy(activeActivity = null)
        when (activity) {
            is TimedActivity.Food -> {
                val player = next.player
                val satiety = player.needs.satietyUnits +
                    activity.product.satietyGain * GAME_HOUR_MILLIS
                next = next.copy(
                    inventory = next.inventory.consume(activity.product.id),
                    player = player.copy(
                        needs = player.needs.copy(
                            satietyUnits = minOf(satiety, Needs.MAX_UNITS)
                        )
                    )
                )
            }
            is TimedActivity.Course -> {
                val player = next.player
                next = next.copy(
                    player = player.copy(
                        experience = Math.addExact(
                            player.experience,
                            activity.course.experienceGain
                        )
                    )
                )
            }
            else -> Unit
        }
        return GameTransition(next, ledger.transactions)
    }

    private fun applyEvent(session: GameSession, ledger: Ledger): DomainResult<GameSession> {
        val decision = events.evaluate(session)
        val next = session.copy(randomState = decision.nextRandomState)
        val event = decision.event ?: return DomainResult.Success(next)
        val previous = session.lastEvent
        val lastEventTime = previous?.occurredAt ?: 0
        val spacing = maxOf(3 * GAME_DAY_MILLIS, previous?.durationGameMillis ?: 0)
        if (!event.isValid() || event.occurredAt != session.clock.gameMillis ||
            event.occurredAt - lastEventTime < spacing ||
            event.moneyLostMinor > session.player.moneyMinor / 10
        ) {
            return failure(GameError.InvalidEvent)
        }
        val player = next.player
        val health = (
            player.needs.healthUnits -
                event.healthLost * GAME_HOUR_MILLIS
            ).coerceAtLeast(0)
        ledger.record(next, TransactionCategory.EVENT, -event.moneyLostMinor)
        return DomainResult.Success(
            next.copy(
                events = next.events + event,
                player = player.copy(
                    moneyMinor = player.moneyMinor - event.moneyLostMinor,
                    needs = player.needs.copy(healthUnits = health)
                )
            ).finishIfDead()
        )
    }

    private fun GameSession.finishIfDead() = if (player.needs.healthUnits == 0L) {
        copy(
            activeActivity = null,
            status = SessionStatus.FINISHED,
            finishReason = FinishReason.HEALTH_DEPLETED
        )
    } else {
        this
    }

    private fun Activity.withRemaining(millis: Long): Activity = when (this) {
        is WorkShift -> copy(remainingGameMillis = millis)
        is TimedActivity.Food -> copy(remainingGameMillis = millis)
        is TimedActivity.Sleep -> copy(remainingGameMillis = millis)
        is TimedActivity.Rest -> copy(remainingGameMillis = millis)
        is TimedActivity.Course -> copy(remainingGameMillis = millis)
    }

    private fun debit(session: GameSession, cost: Long): DomainResult<GameSession> {
        val player = session.player
        if (cost > player.moneyMinor) {
            return failure(
                GameError.InsufficientFunds(cost, player.moneyMinor)
            )
        }
        return DomainResult.Success(
            session.copy(player = player.copy(moneyMinor = player.moneyMinor - cost))
        )
    }

    private inline fun withShift(
        session: GameSession,
        action: (WorkShift) -> DomainResult<GameTransition>
    ): DomainResult<GameTransition> =
        (session.activeActivity as? WorkShift)?.let(action) ?: failure(GameError.NoActiveShift)

    private fun transition(session: GameSession) = DomainResult.Success(GameTransition(session))

    private inline fun DomainResult<GameSession>.mapSession(
        action: (GameSession) -> GameTransition
    ) = flatMap { DomainResult.Success(action(it)) }
}

private class Ledger(private val key: OperationKey) {
    private val entries = mutableListOf<FinancialTransaction>()
    val transactions: List<FinancialTransaction> get() = entries.toList()

    fun record(session: GameSession, category: TransactionCategory, amount: Long) {
        if (amount == 0L) return
        val prefix = "${key.sessionId.length}:${key.sessionId}:${key.id.length}:${key.id}"
        val id = "$prefix:${entries.size}"
        entries += FinancialTransaction(
            id,
            key.id,
            key.sessionId,
            session.clock.gameMillis,
            category,
            amount
        )
    }
}
