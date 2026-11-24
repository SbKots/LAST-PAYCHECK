package com.lastpaycheck.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.lastpaycheck.domain.model.BoxCategory
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.model.WorkShift
import com.lastpaycheck.presentation.R as PresentationR
import com.lastpaycheck.presentation.hardware.MotionStatus
import com.lastpaycheck.presentation.hardware.WorkTiltViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TiltWarehouseScenarioTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model(): WorkTiltViewModel {
        lateinit var result: WorkTiltViewModel
        compose.activityRule.scenario.onActivity {
            result = ViewModelProvider(it)[WorkTiltViewModel::class.java]
        }
        return result
    }

    @Test fun aTiltHoldModesBackgroundAndRotationPreserveTheActiveShift() {
        val ui = HardwareScenarioUi(compose)
        ui.openExistingOrFirstGame()
        ui.motion()
        ui.click("tab.${PresentationR.string.game_work_section}")
        ui.waitFor("screen.work")
        if (ui.exists("pause.resume")) ui.click("pause.resume")
        if (ui.saved().activeActivity !is WorkShift) {
            compose.onNodeWithText(
                ui.context.getString(PresentationR.string.game_shift_requirements)
            ).performClick()
            compose.onNodeWithText(
                ui.context.getString(PresentationR.string.game_start_shift)
            ).performClick()
        }
        ui.waitFor("work.box")
        if (ui.exists("pause.resume")) ui.click("pause.resume")
        ui.click("work.mode.TILT")
        ui.calibrate()
        ui.screenshot("tilt-calibrated")
        val before = ui.saved().activeActivity as WorkShift
        ui.motion(4.0)
        ui.await {
            val current = ui.saved().activeActivity as? WorkShift
            current?.processedBoxIds?.size == before.processedBoxIds.size + 1
        }
        val held = ui.saved().activeActivity as WorkShift
        SystemClock.sleep(5000)
        assertEquals(held.selectedBox.id, (ui.saved().activeActivity as WorkShift).selectedBox.id)
        assertEquals(held.processedBoxIds, (ui.saved().activeActivity as WorkShift).processedBoxIds)
        ui.motion()
        ui.readyForTilt()
        ui.click("work.mode.TOUCH")
        ui.await { model().state.value.motion == MotionStatus.STOPPED }
        assertEquals(held.selectedBox.id, (ui.saved().activeActivity as WorkShift).selectedBox.id)
        ui.click("work.zone.${held.selectedBox.category.name}")
        ui.click("work.place")
        ui.await {
            val current = ui.saved().activeActivity as? WorkShift
            current?.processedBoxIds?.size == held.processedBoxIds.size + 1
        }
        ui.motion()
        ui.click("work.mode.TILT")
        ui.readyForTilt()
        val beforeBackground = ui.saved().activeActivity as WorkShift
        val workModel = model()
        ui.automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        ui.await { workModel.state.value.motion == MotionStatus.STOPPED }
        ui.motion(-4.0)
        SystemClock.sleep(2000)
        assertEquals(
            beforeBackground.processedBoxIds,
            (ui.saved().activeActivity as WorkShift).processedBoxIds
        )
        ui.context.startActivity(
            Intent(ui.context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        ui.waitFor("work.box")
        SystemClock.sleep(600)
        assertEquals(
            beforeBackground.selectedBox.id,
            (ui.saved().activeActivity as WorkShift).selectedBox.id
        )
        ui.motion()
        ui.readyForTilt()
        compose.onNodeWithText(
            ui.context.getString(PresentationR.string.game_pause)
        ).performScrollTo().performClick()
        var rotated = ui.saved().activeActivity as WorkShift
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        ui.waitFor("work.box")
        ui.screenshot("tilt-landscape")
        assertEquals(
            rotated.selectedBox.id,
            (ui.saved().activeActivity as WorkShift).selectedBox.id
        )
        if (ui.exists("pause.resume")) ui.click("pause.resume")
        ui.motion()
        ui.await { !workModel.state.value.calibrated }
        ui.calibrate()
        assertEquals(
            rotated.processedBoxIds,
            (ui.saved().activeActivity as WorkShift).processedBoxIds
        )
        val rotation = compose.activity.display?.rotation ?: error("Display absent")
        ui.motion(y = if (rotation == 1) -4.0 else 4.0)
        ui.await {
            (ui.saved().activeActivity as? WorkShift)?.processedBoxIds?.size ==
                rotated.processedBoxIds.size + 1
        }
        rotated = ui.saved().activeActivity as WorkShift
        ui.screenshot("tilt-landscape-placement")
        compose.onNodeWithText(
            ui.context.getString(PresentationR.string.game_pause)
        ).performScrollTo().performClick()
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        ui.waitFor("work.box")
        if (ui.exists("pause.resume")) ui.click("pause.resume")
        ui.motion()
        ui.calibrate()
        compose.activityRule.scenario.recreate()
        ui.waitFor("work.box")
        assertEquals(
            rotated.selectedBox.id,
            (ui.saved().activeActivity as WorkShift).selectedBox.id
        )
        ui.motion()
        ui.readyForTilt()
        compose.onNodeWithText(
            ui.context.getString(PresentationR.string.game_pause)
        ).performScrollTo().performClick()
        ui.await { ui.saved().clock.isPaused }
        val snapshot = ui.saved()
        File(ui.context.filesDir, "tilt-checkpoint.txt").writeText(
            listOf(
                snapshot.id,
                (snapshot.activeActivity as WorkShift).id,
                snapshot.player.moneyMinor,
                ui.wageCount(),
                ui.transactions().size
            ).joinToString("|")
        )
        ui.screenshot("tilt-before-process-restart")
    }

    @Test fun bContinueAfterProcessDeathCompletesTheTiltShiftAndPaysOnce() {
        val ui = HardwareScenarioUi(compose)
        val checkpoint = File(ui.context.filesDir, "tilt-checkpoint.txt").readText().split('|')
        assertEquals(checkpoint[0], ui.saved().id)
        assertEquals(checkpoint[1], (ui.saved().activeActivity as WorkShift).id)
        ui.motion()
        ui.openExistingOrFirstGame()
        ui.click("tab.${PresentationR.string.game_work_section}")
        ui.waitFor("work.box")
        if (ui.exists("pause.resume")) ui.click("pause.resume")
        ui.readyForTilt()
        val workModel = model()
        val beforeLock = ui.saved().activeActivity as WorkShift
        ui.automation.executeShellCommand("input keyevent 223").close()
        ui.await { workModel.state.value.motion == MotionStatus.STOPPED }
        assertEquals(
            beforeLock.selectedBox.id,
            (ui.saved().activeActivity as WorkShift).selectedBox.id
        )
        ui.automation.executeShellCommand("input keyevent 224").close()
        ui.automation.executeShellCommand("wm dismiss-keyguard").close()
        ui.context.startActivity(
            Intent(ui.context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        ui.waitFor("work.box")
        ui.motion()
        ui.readyForTilt()
        var gestures = 0
        while (ui.saved().activeActivity is WorkShift && gestures < 24) {
            ui.motion()
            ui.readyForTilt(allowFinished = true)
            val shift = ui.saved().activeActivity as? WorkShift ?: break
            when (shift.selectedBox.category) {
                BoxCategory.FOOD -> ui.motion(4.0)
                BoxCategory.GENERAL -> ui.motion(-4.0)
                BoxCategory.FRAGILE -> ui.click("work.tilt.center")
            }
            ui.await {
                val current = ui.saved().activeActivity as? WorkShift
                current == null || current.processedBoxIds.size > shift.processedBoxIds.size
            }
            gestures++
        }
        val final = ui.saved()
        assertEquals(checkpoint[0], final.id)
        assertTrue(final.activeActivity == null)
        val result = checkNotNull(final.lastShiftResult)
        assertTrue(result.correct > 0)
        assertTrue(gestures > 5)
        val changes = ui.transactions().drop(checkpoint[4].toInt())
        assertEquals(
            checkpoint[2].toLong() + changes.sumOf { it.amountMinor },
            final.player.moneyMinor
        )
        assertEquals(
            result.wageMinor,
            changes.single { it.category == TransactionCategory.WAGE }.amountMinor
        )
        assertEquals(checkpoint[3].toInt() + 1, ui.wageCount())
        ui.waitFor("screen.work")
        compose.onNodeWithText(ui.context.getString(PresentationR.string.game_shift_complete))
            .performScrollTo()
        ui.screenshot("tilt-shift-result")
        compose.activityRule.scenario.recreate()
        ui.waitFor("screen.work")
        assertEquals(final.player.moneyMinor, ui.saved().player.moneyMinor)
        assertFalse(model().state.value.motion == MotionStatus.ACTIVE)
        assertNotEquals(0L, result.wageMinor)
    }
}
