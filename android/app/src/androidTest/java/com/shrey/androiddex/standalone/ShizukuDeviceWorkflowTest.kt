package com.shrey.androiddex.standalone

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.shrey.androiddex.standalone.system.ShizukuManager
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Runs only after CI's disposable-emulator harness installs and starts the official Shizuku app. */
@RunWith(AndroidJUnit4::class)
class ShizukuDeviceWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var context: Context
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("dex_workspace_0", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun teardown() { scenario.close() }

    @Test fun officialAuthorizationDialogAndShellTaskBridge() {
        Assume.assumeTrue("Official Shizuku must be installed by the test harness",
            runCatching { context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0) }.isSuccess)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Enter desktop").performClick()
        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.waitUntil(15000) { ShizukuManager.state.value.binderAlive }
        assertTrue("The real Shizuku binder should be detected", ShizukuManager.state.value.binderAlive)
        if (!ShizukuManager.state.value.authorized) {
            compose.onNodeWithText("Request permission").performScrollTo().performClick()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val allow = device.findObject(UiSelector().resourceId("android:id/button1"))
            assertTrue("Official Shizuku Allow button must appear", allow.waitForExists(10000))
            allow.click()
        }
        compose.waitUntil(20000) { ShizukuManager.state.value.authorized && ShizukuManager.state.value.bridgeReady }
        assertTrue("Permission must come from the official dialog", ShizukuManager.state.value.authorized)
        assertTrue("The non-daemon shell UserService must connect", ShizukuManager.state.value.bridgeReady)
        val tasks = runBlocking { ShizukuManager.tasks(0) }
        assertTrue("The authorized shell should expose at least one real Android task", tasks.isNotEmpty())
        compose.onNodeWithText("Probe capabilities").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(ShizukuManager.state.value.authorized)
    }
}
