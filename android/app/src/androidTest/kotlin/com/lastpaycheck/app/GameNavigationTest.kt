package com.lastpaycheck.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lastpaycheck.presentation.R as PresentationR
import org.junit.Rule
import org.junit.Test

class GameNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun cityStoreSelectionAndFridgeKeepTheExpectedBackStack() {
        compose.waitUntil(20_000) { exists("start.continue") || exists("start.new") }
        if (exists("start.continue")) {
            compose.onNodeWithTag("start.continue").performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("start.new").performScrollTo().performClick()
            waitFor("newgame.start")
            compose.onNodeWithTag("newgame.start").performScrollTo().performClick()
        }
        waitFor("screen.home")
        compose.onNodeWithTag("home.city").performScrollTo().performClick()
        waitFor("screen.city")

        cityCard(PresentationR.string.city_warehouse)
        waitFor("screen.work")
        back()
        waitFor("screen.city")

        cityCard(PresentationR.string.city_garage)
        waitFor("screen.garage")
        back()
        waitFor("screen.city")

        cityCard(PresentationR.string.game_grocery)
        waitFor("screen.stores")
        compose.onNodeWithTag("stores.groceries").performScrollTo().performClick()
        waitFor("screen.groceries")
        back()
        waitFor("screen.stores")
        compose.onNodeWithTag("stores.dealership").performScrollTo().performClick()
        waitFor("screen.dealership")
        compose.onNodeWithTag("vehicle.details.city-hatch").performScrollTo().assertIsDisplayed()
        back()
        waitFor("screen.stores")
        back()
        waitFor("screen.city")
        cityCard(PresentationR.string.city_return_home)
        waitFor("screen.home")

        repeat(3) {
            compose.onNodeWithTag("tab.${PresentationR.string.game_shop_section}").performClick()
            waitFor("screen.stores")
        }
        back()
        waitFor("screen.home")

        compose.onNodeWithTag("home.fridge").performScrollTo().performClick()
        waitFor("screen.fridge")
        compose.onNodeWithTag("fridge.eat").assertIsDisplayed()
        back()
        waitFor("screen.home")
    }

    private fun cityCard(title: Int) {
        compose.onNodeWithTag("city.$title").performScrollTo().performClick()
    }

    @Test
    fun pausedActionsExplainTheirStateAndResumeThroughTheExistingControl() {
        compose.waitUntil(20_000) { exists("start.continue") || exists("start.new") }
        if (exists("start.continue")) {
            compose.onNodeWithTag("start.continue").performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("start.new").performScrollTo().performClick()
            waitFor("newgame.start")
            compose.onNodeWithTag("newgame.start").performScrollTo().performClick()
        }
        waitFor("screen.home")
        pauseAtHome()
        compose.onNodeWithTag("tab.${PresentationR.string.game_work_section}").performClick()
        waitFor("screen.work")
        text(PresentationR.string.game_shift_requirements).performClick()
        compose.onNodeWithTag("pause.resume").performScrollTo().assertIsDisplayed()
        text(PresentationR.string.game_start_shift).assertIsNotEnabled()
        resume()
        val energyBlocked = compose.onAllNodesWithText(
            compose.activity.getString(PresentationR.string.game_no_energy)
        ).fetchSemanticsNodes().isNotEmpty()
        if (energyBlocked) {
            text(PresentationR.string.game_start_shift).assertIsNotEnabled()
        } else {
            text(PresentationR.string.game_start_shift).assertIsEnabled()
        }
        back()
        home()

        pauseAtHome()
        compose.onNodeWithTag("tab.${PresentationR.string.game_shop_section}").performClick()
        waitFor("screen.stores")
        compose.onNodeWithTag("stores.groceries").performScrollTo().performClick()
        waitFor("screen.groceries")
        val buy = compose.activity.resources.getQuantityString(
            PresentationR.plurals.game_buy_portions,
            1,
            1
        )
        compose.onNodeWithText(buy).assertIsNotEnabled()
        resume()
        compose.onNodeWithText(buy).assertIsEnabled()
        home()

        pauseAtHome()
        compose.onNodeWithTag("home.fridge").performScrollTo().performClick()
        waitFor("screen.fridge")
        compose.onNodeWithTag("fridge.eat").assertIsNotEnabled()
        resume()
        compose.onNodeWithTag("fridge.eat").assertIsNotEnabled()
        home()

        pauseAtHome()
        text(PresentationR.string.game_training_action).performScrollTo().performClick()
        compose.onNodeWithTag("pause.resume").performScrollTo().assertIsDisplayed()
        resume()
        back()
        waitFor("screen.home")
    }

    private fun home() {
        compose.onNodeWithTag("tab.${PresentationR.string.game_home_section}").performClick()
        waitFor("screen.home")
    }

    private fun pauseAtHome() {
        if (!exists("pause.notice")) {
            compose.onNodeWithContentDescription(
                compose.activity.getString(PresentationR.string.game_apartment_actions)
            ).performClick()
            text(PresentationR.string.game_pause).performClick()
        }
        compose.waitUntil(5_000) { exists("pause.notice") }
        compose.onNodeWithTag("pause.resume").performScrollTo().assertIsDisplayed()
    }

    private fun resume() {
        compose.onNodeWithTag("pause.resume").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("pause.notice") }
    }

    private fun text(resource: Int) = compose.onNodeWithText(compose.activity.getString(resource))

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(
        tag
    ).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String) {
        compose.waitUntil(20_000) { exists(tag) }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
