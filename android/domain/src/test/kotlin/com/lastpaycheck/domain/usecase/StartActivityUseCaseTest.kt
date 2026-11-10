package com.lastpaycheck.domain.usecase

import com.lastpaycheck.domain.model.ActivityRequest
import com.lastpaycheck.domain.model.GAME_HOUR_MILLIS
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.Inventory
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.TimedActivity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartActivityUseCaseTest {
    @Test
    fun foodReservesPortionAndConsumesItOnlyAtCompletion() = runTest {
        val initial = sampleSession()
        val game = Fixture(
            initial.copy(
                inventory = Inventory(mapOf("food" to 1)),
                player = initial.player.copy(needs = Needs.of(30, 100, 100, 100))
            )
        ).open()
        game.activity(game.key("food"), ActivityRequest.Food("food")).ok()
        assertEquals(1, game.session.inventory.quantity("food"))
        game.tick(4_166).ok()
        assertEquals(1, game.session.inventory.quantity("food"))
        game.tick(1).ok()
        assertEquals(0, game.session.inventory.quantity("food"))
        assertEquals(59.5, game.session.player.needs.satiety, 0.001)
        assertNull(game.session.activeActivity)
    }

    @Test
    fun sleepTakesFiftySecondsAndRestIncludesNormalMoodDecay() = runTest {
        val initial = sampleSession()
        val game = Fixture(
            initial.copy(player = initial.player.copy(needs = Needs.of(100, 20, 50, 100)))
        ).open()
        game.activity(game.key("sleep"), ActivityRequest.Sleep).ok()
        game.tick(49_999).ok()
        assertEquals(4, game.session.speed)
        game.tick(1).ok()
        assertEquals(8 * GAME_HOUR_MILLIS, game.session.clock.gameMillis)
        assertEquals(100.0, game.session.player.needs.energy, 0.0)
        game.activity(game.key("rest"), ActivityRequest.Rest).ok()
        game.tick(25_000).ok()
        assertEquals(56.0, game.session.player.needs.mood, 0.0)
    }

    @Test
    fun chargesCourseAtStartAndGrantsExperienceOnceOnCompletion() = runTest {
        val catalog = sampleCatalog(money = 300)
        val game = Fixture(sampleSession(catalog), FakeCatalog(catalog)).open()
        val request = ActivityRequest.Course("logistics")
        val key = game.key("course")
        val receipt = game.activity(key, request).ok()
        assertEquals(0L, game.session.player.moneyMinor)
        assertEquals(0, game.session.player.experience)
        assertEquals(1, game.store.transactions.size)
        game.tick(100_000).ok()
        assertEquals(1, game.session.player.level)
        assertEquals(receipt, game.activity(key, request).ok())
        assertEquals(100, game.session.player.experience)
        assertEquals(1, game.store.transactions.size)
    }

    @Test
    fun rejectsUnavailableActivitiesAndPropagatesStorageError() = runTest {
        val game = Fixture().open()
        val key = game.key("activity")
        game.activity(key, ActivityRequest.Food(""))
            .failsWith(GameError.InvalidInput("activityItemId"))
        game.activity(key, ActivityRequest.Food("food")).failsWith(GameError.EmptyInventory)
        game.activity(key, ActivityRequest.Course("logistics"))
            .failsWith(GameError.InsufficientFunds(300, 150))
        game.activity(key, ActivityRequest.Sleep).ok()
        game.activity(game.key("busy"), ActivityRequest.Rest).failsWith(GameError.Busy)
        game.tick(50_000).ok()
        game.store.writeError = GameError.StorageFailure
        game.activity(game.key("rest"), ActivityRequest.Rest).failsWith(GameError.StorageFailure)
        assertNull(game.session.activeActivity as? TimedActivity)
    }
}
