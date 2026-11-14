package com.shrey.androiddex.standalone

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shrey.androiddex.standalone.data.PreferencesRepository
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DesktopFlowTest {
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

    private fun enterDesktop() {
        compose.onNodeWithText("Your tablet. Your desktop.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Enter desktop").performClick()
        compose.onNodeWithContentDescription("Start menu").assertIsDisplayed()
    }

    @Test fun firstRunCompletesAndDoesNotRepeat() {
        enterDesktop()
        assertTrue(PreferencesRepository(context, 0).onboarded)
        scenario.recreate()
        compose.onNodeWithContentDescription("Start menu").assertIsDisplayed()
        compose.onNodeWithText("Your tablet. Your desktop.").assertDoesNotExist()
    }

    @Test fun notesAreRealPersistentWindows() {
        enterDesktop()
        compose.onNodeWithText("Notes", substring = false).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Hello from the tablet")
        compose.waitUntil(6000) { PreferencesRepository(context, 0).notes().any { it.text == "Hello from the tablet" } }
        compose.onNodeWithContentDescription("Close Notes", substring = true).performClick()
        compose.onNodeWithText("Notes", substring = false).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Hello from the tablet")
    }

    @Test fun windowControlsAndTaskbarRestoreWork() {
        enterDesktop()
        compose.onNodeWithText("Calculator", substring = false).performClick()
        compose.onNodeWithContentDescription("Maximize Calculator").performClick()
        compose.onNodeWithContentDescription("Restore Calculator").assertIsDisplayed()
        compose.onNodeWithContentDescription("Minimize Calculator").performClick()
        compose.onNodeWithContentDescription("Restore Calculator").assertDoesNotExist()
        compose.onNodeWithContentDescription("Taskbar Calculator").performClick()
        compose.onNodeWithContentDescription("Restore Calculator").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Calculator").performClick()
    }

    @Test fun startSearchAndKeyboardShortcutsWork() {
        enterDesktop()
        compose.onNodeWithContentDescription("Start menu").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("settings")
        compose.onNode(hasSetTextAction()).assertTextContains("settings")
        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Your applications").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.MetaLeft) }
        compose.onNodeWithText("Your applications").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Calculator", substring = false).performClick()
        compose.onRoot().performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.W); keyUp(Key.CtrlLeft) }
        compose.onNodeWithContentDescription("Close Calculator").assertDoesNotExist()
    }

    @Test fun absentShizukuIsNotReportedConnected() {
        val installed = runCatching { context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0) }.isSuccess
        Assume.assumeFalse("This test checks the real absent-service path", installed)
        enterDesktop()
        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Not installed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Request permission").assertIsNotEnabled()
    }

    @Test fun accessibilityActivationIsObservedWhenTestHarnessEnablesIt() {
        Assume.assumeTrue("CI enables the real service using test-only ADB before this test", DesktopAccessibilityService.isEnabled(context))
        // The service is bound by Android in the app process; Compose idling does not advance that binder callback.
        val deadline = System.currentTimeMillis() + 15000
        while (!DesktopAccessibilityService.connected() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Android should bind the enabled AccessibilityService", DesktopAccessibilityService.connected())
        assertTrue(DesktopAccessibilityService.state.value.bound)
        enterDesktop()
        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Service enabled and bound").performScrollTo().assertIsDisplayed()
    }
}
