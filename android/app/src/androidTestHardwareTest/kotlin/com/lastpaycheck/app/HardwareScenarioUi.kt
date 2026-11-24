package com.lastpaycheck.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpaycheck.data.local.LocalPersistence
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.FinancialTransaction
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSession
import com.lastpaycheck.domain.model.TransactionCategory
import com.lastpaycheck.domain.repository.CheckpointChoice
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking

internal class HardwareScenarioUi(
    val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
) {
    private var motionSequence = System.nanoTime()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val persistence by lazy { LocalPersistence(context) }
    val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    fun exists(tag: String): Boolean = try {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    } catch (error: IllegalStateException) {
        if (error.message?.contains("No compose hierarchies") != true) throw error
        false
    }
    fun waitFor(tag: String) {
        compose.waitUntil(20_000) { exists(tag) }
    }
    fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        try {
            node.performScrollTo()
        } catch (error: AssertionError) {
            if (error.message?.contains("no parent layout with a Scroll") != true) throw error
        }
        node.performClick()
    }

    fun openExistingOrFirstGame() {
        check(
            context.packageName.endsWith(".hardwaretest")
        ) { "Isolated hardware test APK required" }
        val saved = runBlocking { persistence.sessions.load(CheckpointChoice.LATEST) }
        compose.waitUntil(20_000) { exists("start.continue") || exists("start.new") }
        if (saved is DomainResult.Failure) {
            check(saved.error == GameError.NoSession)
            click("start.new")
            waitFor("newgame.start")
            click("newgame.start")
        } else {
            click("start.continue")
        }
        waitFor("screen.home")
    }

    fun screenshot(name: String) {
        val bitmap = checkNotNull(automation.takeScreenshot())
        FileOutputStream(File(context.getExternalFilesDir(null), "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    fun saved(): GameSession = runBlocking {
        (persistence.sessions.load(CheckpointChoice.LATEST) as DomainResult.Success).value
    }

    fun transactions(): List<FinancialTransaction> = runBlocking {
        (persistence.sessions.readTransactions(saved().identity) as DomainResult.Success)
            .value
    }

    fun wageCount(): Int = transactions().count { it.category == TransactionCategory.WAGE }

    fun motion(x: Double = 0.0, y: Double = 0.0) {
        val temporary = File(context.filesDir, "motion-request.tmp")
        temporary.writeText("${++motionSequence}|$x:$y:9.80665")
        check(temporary.renameTo(File(context.filesDir, "motion-request.txt")))
    }

    fun readyForTilt(allowFinished: Boolean = false) {
        compose.waitUntil(10_000) {
            (allowFinished && saved().activeActivity == null) ||
                (
                    exists("work.tilt.center") && SemanticsProperties.Disabled !in
                        compose.onNodeWithTag("work.tilt.center").fetchSemanticsNode().config
                    )
        }
    }

    fun calibrate() {
        compose.waitUntil(10_000) {
            exists("work.tilt.calibrate") && SemanticsProperties.Disabled !in
                compose.onNodeWithTag("work.tilt.calibrate").fetchSemanticsNode().config
        }
        click("work.tilt.calibrate")
        readyForTilt()
    }

    fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Hardware condition timed out" }
            SystemClock.sleep(100)
        }
    }

    fun systemBack() {
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun systemClick(vararg labels: String) {
        val deadline = SystemClock.uptimeMillis() + 12_000
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        while (SystemClock.uptimeMillis() < deadline) {
            val target = automation.rootInActiveWindow?.find(labels.toSet())
            if (target != null) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                var node: AccessibilityNodeInfo? = target
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            }
            SystemClock.sleep(100)
        }
        screenshot("system-control-missing")
        error("System control absent: ${labels.toList()}, root=${automation.rootInActiveWindow}")
    }

    private fun AccessibilityNodeInfo.find(labels: Set<String>): AccessibilityNodeInfo? {
        if (text?.toString() in labels ||
            labels.any { viewIdResourceName?.endsWith(":id/$it") == true }
        ) {
            return this
        }
        repeat(childCount) { getChild(it)?.find(labels)?.let { node -> return node } }
        return null
    }
}
