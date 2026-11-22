package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.engine.GameEngine
import com.lastpaycheck.domain.engine.GameSessionCoordinator
import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.AuthForm
import com.lastpaycheck.domain.model.AuthMode
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FormField
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.OperationKey
import com.lastpaycheck.domain.model.PauseReason
import com.lastpaycheck.domain.model.ValidationIssue
import com.lastpaycheck.domain.repository.CheckpointChoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressUseCasesTest {
    private val name = ValidateCharacterName()
    private val auth = ValidateAuthForm()

    @Test fun namesSupportCyrillicUnicodeAndCanonicalNormalization() {
        assertNull(name("  Анна-Мария О’Нил  "))
        assertNull(name("李 明"))
        assertEquals("Й", name.normalize("И\u0306"))
    }

    @Test fun namesRejectEmptyWhitespaceAndControlCharacters() {
        assertEquals(ValidationIssue.REQUIRED, name(" \n "))
        assertEquals(ValidationIssue.FORMAT, name("Макс\nИмя"))
        assertEquals(ValidationIssue.FORMAT, name("Max123"))
        assertEquals(ValidationIssue.FORMAT, name("🙂"))
    }

    @Test fun nameLengthBoundaryAndSupplementaryLettersUseCodePoints() {
        assertNull(name("я".repeat(40)))
        assertEquals(ValidationIssue.TOO_LONG, name("я".repeat(41)))
        assertNull(name("𐐀".repeat(40)))
        assertEquals(ValidationIssue.TOO_LONG, name("𐐀".repeat(41)))
    }

    @Test fun emailTrimmingAndPasswordAreValidatedSeparately() {
        assertTrue(auth(AuthForm("  max+game@example.com ", "secret"), AuthMode.SIGN_IN).isEmpty())
        assertEquals(
            ValidationIssue.FORMAT,
            auth(AuthForm("a @b.com", "secret"), AuthMode.SIGN_IN)[FormField.EMAIL]
        )
        assertEquals(
            ValidationIssue.FORMAT,
            auth(AuthForm("x@", "secret"), AuthMode.SIGN_IN)[FormField.EMAIL]
        )
        assertEquals(
            ValidationIssue.REQUIRED,
            auth(AuthForm("", ""), AuthMode.SIGN_IN)[FormField.PASSWORD]
        )
    }

    @Test fun registrationChecksPasswordAndConfirmationButLoginDoesNotImposeNewRules() {
        assertEquals(
            ValidationIssue.PASSWORD_SHORT,
            auth(AuthForm("a@b.com", "12345", "12345"), AuthMode.SIGN_UP)[FormField.PASSWORD]
        )
        assertTrue(auth(AuthForm("a@b.com", "123456", "123456"), AuthMode.SIGN_UP).isEmpty())
        assertEquals(
            ValidationIssue.MISMATCH,
            auth(AuthForm("a@b.com", "123456", "654321"), AuthMode.SIGN_UP)[FormField.CONFIRMATION]
        )
        assertTrue(auth(AuthForm("a@b.com", "x"), AuthMode.SIGN_IN).isEmpty())
        assertTrue(auth(AuthForm("a@b.com"), AuthMode.RECOVER).isEmpty())
    }

    @Test fun renamePersistsWhilePausedAndRestoresWithExistingProgress() = runTest {
        val initial = sampleSession().copy(
            clock = sampleSession().clock.copy(pauseReasons = setOf(PauseReason.MANUAL))
        )
        val repository = FakeSessions(initial)
        val coordinator = GameSessionCoordinator(GameEngine(), repository, FakeClock())
        ContinueGameUseCase(coordinator)()
        val rename = RenameCharacterUseCase(coordinator)
        assertTrue(rename(initial.id, "  Максим  ") is DomainResult.Success)
        val saved = (repository.load(CheckpointChoice.LATEST) as DomainResult.Success).value
        assertEquals("Максим", saved.player.name)
        assertEquals(initial.player.moneyMinor, saved.player.moneyMinor)
        assertEquals(initial.clock, saved.clock)
        val reopened = GameSessionCoordinator(GameEngine(), repository, FakeClock())
        assertEquals(
            "Максим",
            (ContinueGameUseCase(reopened)() as DomainResult.Success).value.player.name
        )
    }

    @Test fun renameRejectsInvalidInputWrongSessionAndStorageFailure() = runTest {
        val repository = FakeSessions(sampleSession())
        val coordinator = GameSessionCoordinator(GameEngine(), repository, FakeClock())
        ContinueGameUseCase(coordinator)()
        val rename = RenameCharacterUseCase(coordinator)
        assertEquals(DomainResult.Failure(GameError.InvalidInput("name")), rename("game", " "))
        assertEquals(DomainResult.Failure(GameError.RevisionConflict), rename("other", "Макс"))
        repository.writeError = GameError.StorageFailure
        assertEquals(DomainResult.Failure(GameError.StorageFailure), rename("game", "Макс"))
        assertEquals("", repository.persisted?.player?.name)
    }

    @Test fun financesReadRealLedgerAndHandleEmptyHistoryAndErrors() = runTest {
        val repository = FakeSessions(sampleSession())
        val overview = GetFinancialOverviewUseCase(repository)
        val empty = (overview("game") as DomainResult.Success).value
        assertEquals(150L, empty.session.player.moneyMinor)
        assertTrue(empty.transactions.isEmpty())
        val coordinator = GameSessionCoordinator(GameEngine(), repository, FakeClock())
        ContinueGameUseCase(coordinator)()
        BuyProductUseCase(FakeCatalog(), coordinator)(OperationKey("game", "buy"), "food", 1, 50)
        val data = (overview("game") as DomainResult.Success).value
        assertEquals(100L, data.session.player.moneyMinor)
        assertEquals(50L, data.statistics.expenseMinor)
        assertEquals(-50L, data.transactions.single().amountMinor)
        repository.journalError = GameError.StorageFailure
        assertEquals(DomainResult.Failure(GameError.StorageFailure), overview("game"))
        assertEquals(DomainResult.Failure(GameError.NoSession), overview("wrong"))
    }

    @Test fun courseAvailabilityUsesExistingEconomyAndEnergyRules() {
        val available = GetCourseAvailabilityUseCase()
        val session = sampleSession(sampleCatalog(money = 500))
        val course = sampleCatalog().courses.single()
        assertNull(available(session, course))
        assertTrue(
            available(
                session.copy(player = session.player.copy(moneyMinor = 299)),
                course
            ) is GameError.InsufficientFunds
        )
        assertEquals(
            GameError.InsufficientEnergy,
            available(
                session.copy(
                    player = session.player.copy(needs = session.player.needs.copy(energyUnits = 0))
                ),
                course
            )
        )
        assertEquals(
            GameError.Busy,
            available(session.copy(activeActivity = sampleShift()), course)
        )
        assertEquals(
            GameError.Paused,
            available(
                session.copy(clock = session.clock.copy(pauseReasons = setOf(PauseReason.MANUAL))),
                course
            )
        )
    }

    @Test fun courseCompletionChargesOnceAndSavesExperienceAcrossRestart() = runTest {
        val catalog = FakeCatalog(sampleCatalog(money = 500))
        val repository = FakeSessions(sampleSession(sampleCatalog(money = 500)))
        val clock = FakeClock()
        val coordinator = GameSessionCoordinator(GameEngine(), repository, clock)
        ContinueGameUseCase(coordinator)()
        val start = StartActivityUseCase(catalog, coordinator)
        val key = OperationKey("game", "course")
        assertTrue(start(key, ActivityRequest.Course("logistics")) is DomainResult.Success)
        start(key, ActivityRequest.Course("logistics"))
        assertEquals(200L, repository.persisted?.player?.moneyMinor)
        clock.now = 100_000
        UpdateGameTimeUseCase(coordinator)()
        val restored = (
            ContinueGameUseCase(
                GameSessionCoordinator(GameEngine(), repository, FakeClock())
            )() as DomainResult.Success
            ).value
        assertEquals(100, restored.player.experience)
        assertEquals(1, restored.player.level)
        assertNull(restored.activeActivity)
        assertEquals(1, repository.transactions.size)
    }
}
