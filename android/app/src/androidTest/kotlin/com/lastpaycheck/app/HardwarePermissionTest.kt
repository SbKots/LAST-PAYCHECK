package com.lastpaycheck.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpaycheck.presentation.R as PresentationR
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HardwarePermissionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemDenialRationaleGrantAndRealCameraFrame() {
        compose.waitUntil(20_000) { exists("start.continue") || exists("start.new") }
        if (exists("start.continue")) {
            compose.onNodeWithTag("start.continue").performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("start.new").performScrollTo().performClick()
            compose.waitUntil(20_000) { exists("newgame.start") }
            compose.onNodeWithTag("newgame.start").performScrollTo().performClick()
        }
        compose.waitUntil(20_000) { exists("screen.home") }
        compose.onNodeWithTag("tab.${PresentationR.string.game_work_section}").performClick()
        compose.waitUntil(20_000) { exists("screen.work") }
        compose.onNodeWithText(compose.activity.getString(PresentationR.string.hardware_title))
            .performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("screen.hardware") }
        compose.onNodeWithTag("hardware.motion.toggle").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists("hardware.axes") }
        saveScreen("sensor")
        compose.onNodeWithTag("hardware.motion.toggle").performScrollTo().performClick()
        compose.onNodeWithTag("hardware.camera.permission").performScrollTo().performClick()
        clickSystemPermission("permission_deny_button")
        compose.onNodeWithText(
            compose.activity.getString(PresentationR.string.hardware_permission_denied)
        ).performScrollTo().assertIsDisplayed()
        saveScreen("rationale")
        compose.onNodeWithTag("hardware.camera.permission").performScrollTo().performClick()
        clickSystemPermission("permission_allow_foreground_only_button")
        compose.waitUntil(10_000) { exists("hardware.camera.capture") }
        compose.onNodeWithTag("hardware.camera.capture").performScrollTo().performClick()
        compose.waitUntil(15_000) { exists("hardware.camera.frame") }
        compose.onNodeWithTag("hardware.camera.frame").performScrollTo().assertIsDisplayed()
        saveScreen("camera-frame")
        assertTrue(
            compose.activity.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(
        tag
    ).fetchSemanticsNodes().isNotEmpty()

    private fun clickSystemPermission(suffix: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            val button = root?.findBySuffix(suffix)
            if (button != null) {
                saveScreen(suffix)
                if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
            SystemClock.sleep(100)
        }
        error("System permission control absent: $suffix")
    }

    private fun AccessibilityNodeInfo.findBySuffix(suffix: String): AccessibilityNodeInfo? {
        if (viewIdResourceName?.endsWith(":id/$suffix") == true) return this
        repeat(childCount) { index -> getChild(index)?.findBySuffix(suffix)?.let { return it } }
        return null
    }

    private fun saveScreen(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        FileOutputStream(File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png"))
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
