package com.lastpaycheck.presentation.game

import androidx.lifecycle.viewModelScope
import com.lastpaycheck.domain.model.CatalogSource
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.EconomicScenario
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.GameTransition
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.SessionRevision
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.usecase.GetFinancialOverviewUseCase
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModelTest {
    @Test fun retryActuallyReadsAgainAndRecoversWithoutWritingTheSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ReadOnlySessions()
        val model = FinanceViewModel(GetFinancialOverviewUseCase(repository))
        try {
            repository.error = GameError.StorageFailure
            model.load(repository.session.id)
            runCurrent()
            assertEquals(DomainResult.Failure(GameError.StorageFailure), model.state.value)
            repository.error = null
            model.load(repository.session.id)
            runCurrent()
            assertTrue(model.state.value is DomainResult.Success)
            assertEquals(2, repository.loads)
            assertEquals(
                repository.session,
                (model.state.value as DomainResult.Success).value.session
            )
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class ReadOnlySessions : GameSessionRepository {
        val session = GameSession(
            "finance",
            "test",
            PlayerState(12602, Needs.of(80, 80, 80, 100), "helper"),
            EconomicScenario(
                "test",
                "test",
                "RUS",
                2026,
                BigDecimal.ZERO,
                CatalogSource.TRAINING
            ),
            100
        )
        var error: GameError? = null
        var loads = 0
        override suspend fun load(choice: CheckpointChoice): DomainResult<GameSession> {
            loads++
            return error?.let { DomainResult.Failure(it) } ?: DomainResult.Success(session)
        }
        override suspend fun readTransactions(revision: SessionRevision) =
            DomainResult.Success(emptyList<FinancialTransaction>())
        override suspend fun restorePrevious(checkpoint: SessionRevision) =
            error("Unexpected write")
        override suspend fun replace(expected: SessionRevision?, session: GameSession) =
            error("Unexpected write")
        override suspend fun findReceipt(key: OperationKey) = error("Unexpected receipt read")
        override suspend fun commit(
            expected: SessionRevision,
            key: OperationKey,
            transition: GameTransition
        ) = error("Unexpected write")
        override suspend fun checkpoint(expected: SessionRevision, session: GameSession) =
            error("Unexpected write")
    }
}
