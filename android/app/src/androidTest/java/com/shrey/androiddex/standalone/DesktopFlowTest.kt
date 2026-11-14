package com.shrey.androiddex.standalone

import android.app.UiAutomation
import android.content.ComponentName
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
        compose.waitUntil(5000) { compose.onAllNodesWithText("Your applications").fetchSemanticsNodes().isNotEmpty() }
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
        // Two framework behaviours make a naive grant insufficient under `am instrument`:
        //  1. The instrumented process restart kills the previously bound service binder, which
        //     AccessibilityManagerService records in its "crashed" set and then refuses to rebind
        //     until the grant is toggled off and on (removeDisabledServicesFromTemporaryStates).
        //  2. A default UiAutomation connection suppresses every real AccessibilityService.
        // So connect with FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES and toggle the test-only ADB
        // grant off/on. Application code never writes Accessibility settings; only this harness does.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            ?: throw AssertionError("UiAutomation did not connect; cannot exercise the accessibility grant")
        val service = ComponentName(context, DesktopAccessibilityService::class.java).flattenToString()
        fun shell(command: String) {
            automation.executeShellCommand(command).use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            }
        }
        shell("settings put secure enabled_accessibility_services ''")
        Thread.sleep(750)
        shell("settings put secure enabled_accessibility_services $service")
        shell("settings put secure accessibility_enabled 1")
        Assume.assumeTrue("Test harness enabled the real service", DesktopAccessibilityService.isEnabled(context))
        val deadline = System.currentTimeMillis() + 20000
        while (!(DesktopAccessibilityService.connected() && DesktopAccessibilityService.state.value.bound) &&
            System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Android should bind the enabled AccessibilityService", DesktopAccessibilityService.connected())
        assertTrue(DesktopAccessibilityService.state.value.bound)
        enterDesktop()
        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Service enabled and bound").performScrollTo().assertIsDisplayed()
    }
}
