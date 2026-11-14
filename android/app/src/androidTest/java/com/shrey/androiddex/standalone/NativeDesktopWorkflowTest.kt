package com.shrey.androiddex.standalone

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.shrey.androiddex.standalone.data.AppEntry
import com.shrey.androiddex.standalone.data.PixelBounds
import com.shrey.androiddex.standalone.system.DesktopDockController
import com.shrey.androiddex.standalone.system.ShizukuManager
import kotlinx.coroutines.runBlocking
import org.junit.*;
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Runs after official Shizuku authorization. CI grants SYSTEM_ALERT_WINDOW only on the disposable emulator. */
@RunWith(AndroidJUnit4::class)
class NativeDesktopWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var context: Context
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var device: UiDevice

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("dex_workspace_0", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun teardown() {
        DesktopDockController.disable(context)
        runCatching { runBlocking { ShizukuManager.setFreeform(false, false) } }
        scenario.close()
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }
    private fun enterDesktop() {
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Enter desktop").performClick()
        compose.onNodeWithContentDescription("Start menu").assertIsDisplayed()
    }

    // TYPE_APPLICATION_OVERLAY windows are not always part of UiAutomator's active-window dump.
    // Search every accessibility window so the test clicks the real dock/caption controls.
    private fun windowTitles(): String = runCatching {
        InstrumentationRegistry.getInstrumentation().uiAutomation.windows.joinToString { it.title?.toString() ?: "(untitled)" }
    }.getOrDefault("(windows unavailable)")

    private fun findOverlayNode(description: String? = null, text: String? = null): AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (window in automation.windows) {
            val root = window.root ?: continue
            search(root, description, text)?.let { return it }
        }
        return null
    }
    private fun search(node: AccessibilityNodeInfo, description: String?, text: String?): AccessibilityNodeInfo? {
        if (description != null && node.contentDescription?.toString() == description) return node
        if (text != null && node.text?.toString() == text) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            search(child, description, text)?.let { return it }
        }
        return null
    }
    private fun overlaySnapshot(): String = runCatching {
        val labels = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo) {
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { labels += "desc=$it" }
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { labels += "text=$it" }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.windows.forEach { it.root?.let(::visit) }
        labels.take(80).joinToString(" | ")
    }.getOrDefault("(snapshot unavailable)")

    private fun clickOverlay(description: String? = null, text: String? = null, timeoutMillis: Long = 8000) {
        await(timeoutMillis) { findOverlayNode(description, text) != null }
        val node = findOverlayNode(description, text)
        assertNotNull("Overlay control not found: ${description ?: text}; windows=${windowTitles()}; nodes=${overlaySnapshot()}", node)
        val bounds = Rect()
        node!!.getBoundsInScreen(bounds)
        assertFalse("Overlay control has empty bounds: ${description ?: text}", bounds.isEmpty)
        assertTrue("Clicking overlay control ${description ?: text}", device.click(bounds.centerX(), bounds.centerY()))
    }

    @Test fun nativeFreeformWindowAndOverlayDockControlRealTask() {
        Assume.assumeTrue("Official Shizuku must be installed", runCatching { context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0) }.isSuccess)
        Assume.assumeTrue("CI must grant Display over other apps on the disposable emulator", Settings.canDrawOverlays(context))
        enterDesktop()
        await(20000) { ShizukuManager.state.value.authorized && ShizukuManager.state.value.bridgeReady }
        assertTrue("Shizuku task bridge must be connected", ShizukuManager.state.value.bridgeReady)
        await(10000) { runCatching { runBlocking { ShizukuManager.probe(false, false, 0).taskApi } }.getOrDefault(false) }

        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Enable native app windows").performScrollTo().performClick()
        compose.onNodeWithText("Apply settings").performClick()
        await(10000) { runCatching { runBlocking { ShizukuManager.probe(false, false, 0).freeformSetting } }.getOrNull() == "1" }
        assertEquals("1", runBlocking { ShizukuManager.probe(false, false, 0).freeformSetting })

        compose.onNodeWithContentDescription("Persistent desktop dock").performScrollTo().performClick()
        await(10000) { DesktopDockController.state.value.running }
        assertTrue("The opt-in overlay dock should be running", DesktopDockController.state.value.running)
        await(8000) { findOverlayNode(description = "Dock start") != null }
        assertNotNull("The dock start island should be visible; windows=${windowTitles()}; dock=${DesktopDockController.state.value}", findOverlayNode(description = "Dock start"))
        compose.onNodeWithContentDescription("Close Desktop settings").performClick()

        // Minimal ATD images do not guarantee a launchable AOSP Settings app. The androidTest APK
        // declares this tiny real Activity so the test always exercises an actual Android task.
        val target = ComponentName(InstrumentationRegistry.getInstrumentation().context, NativeTestTargetActivity::class.java)
        val entry = AppEntry(target.packageName, target.flattenToShortString(), "Native test target")
        val metrics = context.resources.displayMetrics
        val requested = PixelBounds.cascade(metrics.widthPixels, metrics.heightPixels, metrics.density, 0)
        runBlocking { ShizukuManager.launchFreeform(entry, 0, requested) }
        await(12000) { runBlocking { ShizukuManager.tasks(0) }.any { it.packageName == target.packageName && it.freeform && it.visible } }
        val task = runBlocking { ShizukuManager.tasks(0) }.firstOrNull { it.packageName == target.packageName && it.freeform }
        assertNotNull("The test target should be a real freeform task, not a fake embedded window", task)
        assertEquals(5, task!!.windowingMode)
        val bounds = runBlocking { ShizukuManager.taskBounds(task.id) }
        assertNotNull("The shell should report real task bounds", bounds)
        assertFalse(bounds!!.isEmpty)

        await(8000) { findOverlayNode(description = "Minimize native window") != null }
        assertNotNull("The overlay should draw a dark caption over the native task; windows=${windowTitles()}", findOverlayNode(description = "Minimize native window"))
        // Touch the app content below the caption; the overlay must not steal the whole window.
        device.click(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
        Thread.sleep(400)
        assertTrue(runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.focused })

        clickOverlay(description = "Maximize native window")
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.windowingMode == 1 } }
        assertEquals(1, runBlocking { ShizukuManager.tasks(0) }.first { it.id == task.id }.windowingMode)
        clickOverlay(description = "Restore native window")
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.windowingMode == 5 } }

        val before = runBlocking { ShizukuManager.taskBounds(task.id) }!!
        device.swipe(before.left + before.width / 3, before.top + 12, before.left + before.width / 3 + 90, before.top + 70, 24)
        await(6000) { runCatching { runBlocking { ShizukuManager.taskBounds(task.id) } }.getOrNull()?.let { it.left != before.left || it.top != before.top } == true }
        val moved = runBlocking { ShizukuManager.taskBounds(task.id) }
        assertNotNull(moved)
        assertTrue("Dragging the overlay caption should move the real Android task", moved!!.left != before.left || moved.top != before.top)

        clickOverlay(description = "Minimize native window")
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.firstOrNull { it.id == task.id }?.visible != true }
        clickOverlay(description = "Dock start")
        clickOverlay(text = "Tasks")
        clickOverlay(text = "Restore")
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.visible } }

        clickOverlay(description = "Close native window")
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.none { it.id == task.id && it.running } }
        assertTrue("Closing the caption should remove the real Android task", runBlocking { ShizukuManager.tasks(0) }.none { it.id == task.id && it.running })
    }
}
