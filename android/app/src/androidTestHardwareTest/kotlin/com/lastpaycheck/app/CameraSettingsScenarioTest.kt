package com.lastpaycheck.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.lastpaycheck.presentation.R as PresentationR
import com.lastpaycheck.presentation.hardware.CameraPermission
import com.lastpaycheck.presentation.hardware.HardwareViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CameraSettingsScenarioTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun repeatedDenialSettingsReturnRotationAndRealCameraDoNotLoseTheSession() {
        val ui = HardwareScenarioUi(compose)
        ui.openExistingOrFirstGame()
        ui.click("tab.${PresentationR.string.game_work_section}")
        ui.waitFor("screen.work")
        compose.onNodeWithText(ui.context.getString(PresentationR.string.hardware_title))
            .performScrollTo().performClick()
        ui.waitFor("screen.hardware")
        lateinit var model: HardwareViewModel
        compose.activityRule.scenario.onActivity {
            model = ViewModelProvider(it)[HardwareViewModel::class.java]
        }
        if (model.state.value.permission != CameraPermission.GRANTED) {
            repeat(2) {
                if (ui.exists("hardware.camera.permission")) {
                    ui.click("hardware.camera.permission")
                    ui.systemClick("permission_deny_button", "Don’t allow", "Don't allow")
                    ui.await {
                        !model.state.value.requestPending && model.state.value.permission in
                            listOf(CameraPermission.RATIONALE, CameraPermission.DENIED)
                    }
                }
            }
            ui.waitFor("hardware.camera.settings")
            assertEquals(
                PackageManager.PERMISSION_DENIED,
                ui.context.checkSelfPermission(Manifest.permission.CAMERA)
            )
            ui.screenshot("camera-permanent-denial")
            compose.activityRule.scenario.recreate()
            ui.waitFor("hardware.camera.settings")
            ui.click("hardware.camera.settings")
            Thread.sleep(500)
            ui.screenshot("camera-app-settings")
            ui.systemBack()
            ui.waitFor("hardware.camera.settings")
            assertFalse(ui.exists("hardware.camera.capture"))
            ui.click("hardware.camera.settings")
            ui.systemClick("Permissions")
            ui.systemClick("Camera")
            ui.systemClick("Allow only while using the app", "Allow only while using this app")
            ui.context.startActivity(
                Intent(ui.context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
        ui.waitFor("hardware.camera.capture")
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ui.context.checkSelfPermission(Manifest.permission.CAMERA)
        )
        assertFalse(ui.exists("hardware.camera.frame"))
        ui.click("hardware.camera.capture")
        ui.waitFor("hardware.camera.frame")
        ui.screenshot("camera-granted-frame")
        val snapshot = ui.saved()
        ui.automation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
        )
        ui.await { !model.state.value.cameraBusy && model.state.value.cameraResult == null }
        ui.context.startActivity(
            Intent(ui.context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        ui.waitFor("hardware.camera.capture")
        assertFalse(ui.exists("hardware.camera.frame"))
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        ui.waitFor("hardware.camera.capture")
        assertFalse(ui.exists("hardware.camera.frame"))
        ui.click("hardware.camera.capture")
        ui.waitFor("hardware.camera.frame")
        ui.screenshot("camera-landscape-frame")
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.activityRule.scenario.recreate()
        ui.waitFor("hardware.camera.capture")
        assertEquals(snapshot.id, ui.saved().id)
        assertFalse(ui.exists("hardware.camera.frame"))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.waitFor("screen.work")
    }
}
