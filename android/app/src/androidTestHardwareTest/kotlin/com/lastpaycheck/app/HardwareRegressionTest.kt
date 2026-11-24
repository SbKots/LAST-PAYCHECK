package com.lastpaycheck.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lastpaycheck.presentation.R as PresentationR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HardwareRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun waitText(id: Int) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(
                compose.activity.getString(id)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test fun financeHeroTrainingSettingsThemesAndAccountPreserveTheSavedGame() {
        val ui = HardwareScenarioUi(compose)
        ui.openExistingOrFirstGame()
        if (!ui.exists("pause.notice")) {
            compose.onNodeWithContentDescription(
                compose.activity.getString(PresentationR.string.game_apartment_actions)
            ).performClick()
            text(PresentationR.string.game_pause).performClick()
            ui.waitFor("pause.notice")
        }
        val before = ui.saved()
        ui.click("tab.${PresentationR.string.game_work_section}")
        ui.waitFor("screen.work")
        if (before.lastShiftResult != null) {
            text(PresentationR.string.game_shift_complete).performScrollTo()
            ui.screenshot("tilt-shift-result")
        }
        ui.click("tab.${PresentationR.string.game_finance_section}")
        ui.waitFor("finance.balance")
        ui.screenshot("regression-finances")
        ui.click("tab.${PresentationR.string.game_character_section}")
        waitText(PresentationR.string.progress_save_name)
        text(PresentationR.string.progress_training).performScrollTo().performClick()
        waitText(PresentationR.string.progress_general_skill)
        ui.screenshot("regression-training")
        back()
        waitText(PresentationR.string.progress_save_name)
        text(PresentationR.string.progress_settings).performScrollTo().performClick()
        waitText(PresentationR.string.progress_theme_dark)
        text(PresentationR.string.progress_theme_dark).performScrollTo().performClick()
        ui.screenshot("regression-dark-settings")
        text(PresentationR.string.progress_language_en).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Dark theme").fetchSemanticsNodes().isNotEmpty()
        }
        ui.screenshot("regression-english-settings")
        text(PresentationR.string.progress_language_ru).performScrollTo().performClick()
        waitText(PresentationR.string.progress_theme_light)
        text(PresentationR.string.progress_theme_light).performScrollTo().performClick()
        text(PresentationR.string.progress_account).performScrollTo().performClick()
        waitText(PresentationR.string.progress_account_hint)
        ui.screenshot("regression-account")
        back()
        back()
        ui.click("tab.${PresentationR.string.game_home_section}")
        ui.waitFor("screen.home")
        assertEquals(before.id, ui.saved().id)
        assertEquals(before.player.moneyMinor, ui.saved().player.moneyMinor)
        assertEquals(before.vehicles, ui.saved().vehicles)
        ui.screenshot("regression-home")
    }
}
