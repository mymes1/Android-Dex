package com.shrey.androiddex.standalone

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.shrey.androiddex.standalone.data.AppEntry
import com.shrey.androiddex.standalone.data.PixelBounds
import com.shrey.androiddex.standalone.system.DesktopDockController
import com.shrey.androiddex.standalone.system.ShizukuManager
import kotlinx.coroutines.runBlocking
import org.junit.*;
import org.junit.Assert.*;
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

    @Test fun nativeFreeformWindowAndOverlayDockControlRealTask() {
        Assume.assumeTrue("Official Shizuku must be installed", runCatching { context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0) }.isSuccess)
        Assume.assumeTrue("CI must grant Display over other apps on the disposable emulator", Settings.canDrawOverlays(context))
        enterDesktop()
        await(20000) { ShizukuManager.state.value.authorized && ShizukuManager.state.value.bridgeReady }
        assertTrue("Shizuku task bridge must be connected", ShizukuManager.state.value.bridgeReady)

        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Enable native app windows").performScrollTo().performClick()
        compose.onNodeWithText("Apply settings").performClick()
        await(10000) { runCatching { runBlocking { ShizukuManager.probe(false, false, 0).freeformSetting } }.getOrNull() == "1" }
        assertEquals("1", runBlocking { ShizukuManager.probe(false, false, 0).freeformSetting })

        compose.onNodeWithContentDescription("Persistent desktop dock").performScrollTo().performClick()
        await(10000) { DesktopDockController.state.value.running }
        assertTrue("The opt-in overlay dock should be running", DesktopDockController.state.value.running)
        compose.onNodeWithContentDescription("Close Desktop settings").performClick()

        val launch = context.packageManager.getLaunchIntentForPackage("com.android.settings")?.component
        Assume.assumeTrue("AOSP Settings must be launchable", launch != null)
        val entry = AppEntry("com.android.settings", launch!!.flattenToShortString(), "Settings")
        val metrics = context.resources.displayMetrics
        val requested = PixelBounds.cascade(metrics.widthPixels, metrics.heightPixels, metrics.density, 0)
        runBlocking { ShizukuManager.launchFreeform(entry, 0, requested) }
        await(12000) { runBlocking { ShizukuManager.tasks(0) }.any { it.packageName == "com.android.settings" && it.freeform && it.visible } }
        val task = runBlocking { ShizukuManager.tasks(0) }.firstOrNull { it.packageName == "com.android.settings" && it.freeform }
        assertNotNull("Settings should be a real freeform task, not a fake embedded window", task)
        assertEquals(5, task!!.windowingMode)
        val bounds = runBlocking { ShizukuManager.taskBounds(task.id) }
        assertNotNull("The shell should report real task bounds", bounds)
        assertFalse(bounds!!.isEmpty)

        val minimize = device.findObject(UiSelector().description("Minimize native window"))
        assertTrue("The overlay should draw a dark caption over the native task", minimize.waitForExists(8000))
        // Touch the app content below the caption; the overlay must not steal the whole window.
        device.click(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
        Thread.sleep(400)
        assertTrue(runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.focused })

        device.findObject(UiSelector().description("Maximize native window")).click()
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.windowingMode == 1 } }
        assertEquals(1, runBlocking { ShizukuManager.tasks(0) }.first { it.id == task.id }.windowingMode)
        device.findObject(UiSelector().description("Restore native window")).click()
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.windowingMode == 5 } }

        val before = runBlocking { ShizukuManager.taskBounds(task.id) }!!
        device.swipe(before.left + before.width / 3, before.top + 12, before.left + before.width / 3 + 90, before.top + 70, 24)
        await(6000) { runCatching { runBlocking { ShizukuManager.taskBounds(task.id) } }.getOrNull()?.let { it.left != before.left || it.top != before.top } == true }
        val moved = runBlocking { ShizukuManager.taskBounds(task.id) }
        assertNotNull(moved)
        assertTrue("Dragging the overlay caption should move the real Android task", moved!!.left != before.left || moved.top != before.top)

        device.findObject(UiSelector().description("Minimize native window")).click()
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.firstOrNull { it.id == task.id }?.visible != true }
        device.findObject(UiSelector().description("Dock start")).click()
        val tasksButton = device.findObject(UiSelector().text("Tasks"))
        assertTrue(tasksButton.waitForExists(5000))
        tasksButton.click()
        val restore = device.findObject(UiSelector().text("Restore"))
        assertTrue(restore.waitForExists(5000))
        restore.click()
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.any { it.id == task.id && it.visible } }

        device.findObject(UiSelector().description("Close native window")).click()
        await(8000) { runBlocking { ShizukuManager.tasks(0) }.none { it.id == task.id } }
        assertTrue("Closing the caption should remove the real Android task", runBlocking { ShizukuManager.tasks(0) }.none { it.id == task.id })
    }
}
