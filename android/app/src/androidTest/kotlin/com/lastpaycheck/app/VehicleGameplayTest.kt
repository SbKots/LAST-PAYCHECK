package com.lastpaycheck.app

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpaycheck.data.catalog.DemoVehicleCatalogRepository
import com.lastpaycheck.data.catalog.TrainingGameCatalogRepository
import com.lastpaycheck.data.local.LocalPersistence
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.Needs
import com.lastpaycheck.domain.model.OwnedVehicle
import com.lastpaycheck.domain.model.PlayerState
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.VehicleProgress
import com.lastpaycheck.domain.model.VehicleUpgrade
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.domain.repository.CheckpointChoice
import com.lastpaycheck.presentation.R as PresentationR
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.MethodSorters
import org.junit.runners.model.Statement

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class VehicleGameplayTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(
        TestRule { base, description ->
            object : Statement() {
                override fun evaluate() {
                    seed(
                        if (description.methodName.startsWith("insufficient")) 0 else 100_000,
                        description.methodName.startsWith("zHardRace")
                    )
                    base.evaluate()
                }
            }
        }
    ).around(compose)

    private fun seed(money: Long, hardRace: Boolean) = runBlocking {
        val repository = LocalPersistence(context).sessions
        val old = repository.load(CheckpointChoice.LATEST)
        val previous = (old as? DomainResult.Success)?.value
        if (old is DomainResult.Failure && old.error != GameError.NoSession) {
            error("Invalid QA save")
        }
        val catalog = (TrainingGameCatalogRepository().get() as DomainResult.Success).value
        val game = GameSession(
            "vehicles-qa-${UUID.randomUUID()}",
            catalog.version,
            PlayerState(money, Needs.of(100, 100, 100, 100), catalog.jobs.first().id),
            catalog.scenarios.first(),
            catalog.initial.dailyRentMinor,
            vehicles = if (hardRace) {
                val cars = (DemoVehicleCatalogRepository().get() as DomainResult.Success).value
                val car = OwnedVehicle(
                    cars.vehicles.last(),
                    upgrades = mapOf(
                        VehicleUpgrade.ENGINE to 3,
                        VehicleUpgrade.HANDLING to 2,
                        VehicleUpgrade.RELIABILITY to 3,
                        VehicleUpgrade.BRAKES to 1
                    )
                )
                VehicleProgress(listOf(car), car.definition.id, completed = 2, wins = 2)
            } else {
                VehicleProgress()
            }
        )
        check(repository.replace(previous?.identity, game) is DomainResult.Success)
    }

    @Test
    fun insufficientFundsDisablePurchaseAndBackReturnsToTheCity() {
        openDealer()
        compose.onNodeWithTag("vehicle.details.city-hatch").performScrollTo().performClick()
        compose.onNodeWithTag("vehicle.buy.city-hatch").performScrollTo().assertIsNotEnabled()
        text(PresentationR.string.game_insufficient_funds).assertIsDisplayed()
        screenshot("vehicle-insufficient")
        repeat(3) {
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        waitFor("screen.city")
    }

    @Test
    fun purchaseUpgradeDriveWinAndRestoreWithoutDuplicatePrize() {
        openDealer()
        screenshot("vehicle-dealership")
        buy("city-hatch")
        text(PresentationR.string.city_garage).performScrollTo().performClick()
        waitFor("screen.garage")
        text(PresentationR.string.stores_open_dealership).performScrollTo().performClick()
        waitFor("screen.dealership")
        buy("family-wagon")
        compose.onNodeWithTag("vehicle.select").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            val s = runBlocking { LocalPersistence(context).sessions.load(CheckpointChoice.LATEST) }
            (s as? DomainResult.Success)?.value?.vehicles?.activeVehicleId == "family-wagon"
        }
        compose.onNodeWithTag("vehicle.upgrade.ENGINE").performScrollTo().performClick()
        text(PresentationR.string.vehicle_confirm).performClick()
        text(PresentationR.string.city_garage).performScrollTo().performClick()
        waitFor("screen.garage")
        screenshot("vehicle-garage")
        compose.onNodeWithTag("garage.races").performScrollTo().performClick()
        waitFor("screen.races")
        compose.onNodeWithTag("race.prepare.night-standard").performScrollTo().performClick()
        waitFor("screen.race.prepare")
        compose.onNodeWithTag("race.start").performScrollTo().performClick()
        waitFor("screen.race")
        screenshot("vehicle-race")
        val track = runBlocking {
            (DemoVehicleCatalogRepository().get() as DomainResult.Success).value.races.first()
        }
        val deadline = SystemClock.uptimeMillis() + 150_000
        while (!exists("screen.race.result") && SystemClock.uptimeMillis() < deadline) {
            if (!exists("race.track")) {
                SystemClock.sleep(100)
                continue
            }
            try {
                val progress = compose.onNodeWithTag("race.progress").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].joinToString { it.text }
                val distance = Regex("\\d+").find(progress)!!.value.toInt()
                val laneText = compose.onNodeWithTag("race.track").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription]
                val lane = Regex("\\d+").find(laneText)!!.value.toInt() - 1
                val obstacle = track.obstacles.firstOrNull { it.distanceMeters > distance }
                if (obstacle != null && obstacle.distanceMeters - distance < 55 &&
                    obstacle.lane == lane
                ) {
                    compose.onNodeWithTag(
                        if (lane == 2) "race.left" else "race.right"
                    ).performClick()
                }
            } catch (ended: AssertionError) {
                if (!exists("screen.race.result")) throw ended
            }
            SystemClock.sleep(150)
        }
        waitFor("screen.race.result")
        text(PresentationR.string.race_won).assertIsDisplayed()
        screenshot("vehicle-result")
        text(PresentationR.string.city_garage).performClick()
        waitFor("screen.garage")
        val saved = runBlocking {
            val loaded = LocalPersistence(context).sessions.load(CheckpointChoice.LATEST)
            (loaded as DomainResult.Success).value
        }
        assertEquals(2, saved.vehicles.garage.size)
        assertEquals("family-wagon", saved.vehicles.activeVehicleId)
        assertEquals(1, saved.vehicles.activeVehicle!!.level(VehicleUpgrade.ENGINE))
        assertEquals(1L, saved.vehicles.wins)
        assertEquals(83_550L + saved.vehicles.history.single().rewardMinor, saved.player.moneyMinor)
        compose.activityRule.scenario.close()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("start.continue")
            compose.onNodeWithTag("start.continue").performScrollTo().performClick()
            waitFor("screen.home")
            compose.onNodeWithTag("home.city").performScrollTo().performClick()
            waitFor("screen.city")
            text(PresentationR.string.city_garage).performScrollTo().performClick()
            waitFor("screen.garage")
            val repository = LocalPersistence(context).sessions
            val restored = runBlocking {
                (repository.load(CheckpointChoice.LATEST) as DomainResult.Success).value
            }
            assertEquals(saved.vehicles, restored.vehicles)
            val journal = runBlocking {
                (repository.readTransactions(restored.identity) as DomainResult.Success).value
            }
            assertEquals(1, journal.count { row -> row.category == TransactionCategory.RACE_PRIZE })
            screenshot("vehicle-restored")
        }
    }

    @Test
    fun shortShiftShowsMarksFeedbackAndPaysOnce() {
        waitFor("start.continue")
        compose.onNodeWithTag("start.continue").performScrollTo().performClick()
        waitFor("screen.home")
        compose.onNodeWithTag("tab.${PresentationR.string.game_work_section}").performClick()
        waitFor("screen.work")
        text(PresentationR.string.game_shift_requirements).performClick()
        text(PresentationR.string.sorting_intro).performScrollTo().assertIsDisplayed()
        screenshot("sorting-instructions")
        text(PresentationR.string.game_start_shift).performClick()
        waitFor("work.box")
        val started = SystemClock.uptimeMillis()
        repeat(24) { index ->
            val label = nodeText("work.box")
            val correct = when {
                label.contains("A ■") -> BoxCategory.FOOD
                label.contains("B ◆") -> BoxCategory.FRAGILE
                else -> BoxCategory.GENERAL
            }
            val zone = if (index == 0) BoxCategory.entries.first { it != correct } else correct
            compose.onNodeWithTag("work.zone.${zone.name}").performClick()
            compose.waitUntil(10_000) {
                val button = compose.onNodeWithTag("work.place").fetchSemanticsNode()
                SemanticsProperties.Disabled !in button.config
            }
            compose.onNodeWithTag("work.place").performClick()
            compose.waitUntil(10_000) {
                val saved = savedSession()
                val shift = saved.activeActivity as? WorkShift
                shift?.processedBoxIds?.size == index + 1 || saved.lastShiftResult != null
            }
            if (index == 0 ||
                index == 1
            ) {
                val feedback = context.getString(
                    if (index == 0) {
                        PresentationR.string.sorting_wrong
                    } else {
                        PresentationR.string.sorting_correct
                    },
                    boxLabel(correct),
                    boxLabel(zone)
                )
                compose.waitUntil(5000) {
                    exists("work.feedback") && nodeText("work.feedback") == feedback
                }
                screenshot(if (index == 0) "sorting-wrong" else "sorting-correct")
            }
        }
        val result = checkNotNull(savedSession().lastShiftResult)
        assertEquals(23, result.correct)
        assertEquals(1, result.errors)
        assertEquals(475L, result.wageMinor)
        org.junit.Assert.assertTrue(SystemClock.uptimeMillis() - started in 55_000..100_000)
        screenshot("sorting-result")
        val journal = runBlocking {
            (
                LocalPersistence(
                    context
                ).sessions.readTransactions(savedSession().identity) as DomainResult.Success
                ).value
        }
        assertEquals(1, journal.count { it.category == TransactionCategory.WAGE })
    }

    @Test
    fun zHardRaceCollectsLosesAndRebuildsSeriesThenRestoresFinances() {
        waitFor("start.continue")
        compose.onNodeWithTag("start.continue").performScrollTo().performClick()
        waitFor("screen.home")
        compose.onNodeWithTag("home.city").performScrollTo().performClick()
        waitFor("screen.city")
        text(PresentationR.string.city_garage).performScrollTo().performClick()
        waitFor("screen.garage")
        compose.onNodeWithTag("garage.races").performScrollTo().performClick()
        waitFor("screen.races")
        screenshot("race-events")
        compose.onNodeWithTag("race.prepare.industrial-run").performScrollTo().performClick()
        waitFor("screen.race.prepare")
        compose.onNodeWithTag("race.start").performScrollTo().performClick()
        waitFor("screen.race")
        val track = runBlocking {
            (DemoVehicleCatalogRepository().get() as DomainResult.Success).value.races.last()
        }
        var seriesReached = false
        var reset = false
        var recollected = false
        val deadline = SystemClock.uptimeMillis() + 300_000
        while (!exists("screen.race.result") && SystemClock.uptimeMillis() < deadline) {
            if (!exists("race.track")) continue
            try {
                val distance = Regex("\\d+").find(nodeText("race.progress"))!!.value.toInt()
                val laneText = compose.onNodeWithTag("race.track").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription]
                val lane = Regex("\\d+").find(laneText)!!.value.toInt() - 1
                val streak = Regex(
                    "(?:серия|streak) (\\d+)"
                ).find(nodeText("race.combo"))!!.groupValues[1].toInt()
                val collisions = Regex("(?:Столкновения|Collisions) (\\d+)")
                    .find(nodeText("race.metrics"))!!.groupValues[1].toInt()
                if (!seriesReached && streak >= 3) {
                    seriesReached = true
                    screenshot("race-multiplier")
                }
                if (seriesReached && !reset && collisions > 0 && streak == 0) {
                    reset = true
                    screenshot("race-series-reset")
                }
                if (reset && !recollected && streak > 0) {
                    recollected = true
                    screenshot("race-series-rebuilt")
                }
                val hazard = track.obstacles.firstOrNull { it.distanceMeters > distance }
                val coin = track.pickups.firstOrNull { it.distanceMeters > distance }
                var target = lane
                if (coin != null && coin.distanceMeters - distance < 85 &&
                    (hazard == null || coin.distanceMeters < hazard.distanceMeters)
                ) {
                    target = coin.lane
                }
                if (hazard != null && hazard.distanceMeters - distance < 55) {
                    if (seriesReached && !reset) {
                        target = hazard.lane
                    } else if (target == hazard.lane) target = (hazard.lane + 1) % 3
                }
                if (target != lane) {
                    compose.onNodeWithTag(
                        if (target > lane) "race.right" else "race.left"
                    ).performClick()
                }
            } catch (ended: AssertionError) {
                if (!exists("screen.race.result")) throw ended
            }
            SystemClock.sleep(120)
        }
        waitFor("screen.race.result")
        text(PresentationR.string.race_won).assertIsDisplayed()
        org.junit.Assert.assertTrue(seriesReached && reset && recollected)
        screenshot("race-hard-result")
        val saved = savedSession()
        val result = saved.vehicles.history.single()
        assertEquals(2200L, result.baseRewardMinor)
        org.junit.Assert.assertTrue(result.bonusRewardMinor > 0)
        assertEquals(99_700L + result.rewardMinor, saved.player.moneyMinor)
        text(PresentationR.string.city_garage).performClick()
        waitFor("screen.garage")
        compose.onNodeWithTag("tab.${PresentationR.string.game_finance_section}").performClick()
        waitFor("finance.balance")
        screenshot("finance-after-race")
        compose.activityRule.scenario.close()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("start.continue")
            compose.onNodeWithTag("start.continue").performScrollTo().performClick()
            waitFor("screen.home")
            compose.onNodeWithTag("tab.${PresentationR.string.game_finance_section}").performClick()
            waitFor("finance.balance")
            assertEquals(saved.vehicles, savedSession().vehicles)
            assertEquals(saved.player.moneyMinor, savedSession().player.moneyMinor)
            screenshot("finance-restored")
        }
    }

    private fun nodeText(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString { it.text }

    private fun boxLabel(category: BoxCategory) = context.getString(
        when (category) {
            BoxCategory.FOOD -> PresentationR.string.sorting_food
            BoxCategory.FRAGILE -> PresentationR.string.sorting_fragile
            BoxCategory.GENERAL -> PresentationR.string.sorting_general
        }
    )

    private fun savedSession() = runBlocking {
        (
            LocalPersistence(
                context
            ).sessions.load(CheckpointChoice.LATEST) as DomainResult.Success
            ).value
    }

    private fun openDealer() {
        waitFor("start.continue")
        compose.onNodeWithTag("start.continue").performScrollTo().performClick()
        waitFor("screen.home")
        compose.onNodeWithTag("home.city").performScrollTo().performClick()
        waitFor("screen.city")
        text(PresentationR.string.game_grocery).performScrollTo().performClick()
        waitFor("screen.stores")
        compose.onNodeWithTag("stores.dealership").performScrollTo().performClick()
        waitFor("screen.dealership")
    }

    private fun buy(id: String) {
        compose.onNodeWithTag("vehicle.details.$id").performScrollTo().performClick()
        waitFor("screen.vehicle.detail")
        compose.onNodeWithTag("vehicle.buy.$id").performScrollTo().performClick()
        text(PresentationR.string.vehicle_confirm).performClick()
        compose.waitUntil(10_000) { exists("vehicle.select") }
    }

    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun exists(tag: String) =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) {
        compose.waitUntil(20_000) { exists(tag) }
    }
    private fun screenshot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = checkNotNull(automation.takeScreenshot())
        FileOutputStream(File(context.getExternalFilesDir(null), "$name.png"))
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
