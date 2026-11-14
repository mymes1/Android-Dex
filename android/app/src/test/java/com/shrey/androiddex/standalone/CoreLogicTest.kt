package com.shrey.androiddex.standalone

import com.shrey.androiddex.standalone.data.*
import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {
    @Test fun geometryClampsToWorkspace() {
        val bounds = WindowGeometry.clamp(-50f, 900f, 600f, 400f, 800f, 500f)
        assertEquals(0f, bounds.x)
        assertEquals(100f, bounds.y)
        assertEquals(600f, bounds.width)
        assertEquals(400f, bounds.height)
    }
    @Test fun geometryHandlesTinyWorkspace() {
        val bounds = WindowGeometry.clamp(50f, 50f, 500f, 400f, 120f, 80f)
        assertEquals(Bounds(0f, 0f, 120f, 80f), bounds)
    }
    @Test fun resizeCannotEscapeOrCollapse() {
        val window = DesktopWindow(kind = WindowKind.NOTES, x = 400f, y = 200f, width = 300f, height = 250f)
        val bounds = WindowGeometry.resize(window, -1000f, -1000f, 800f, 500f)
        assertEquals(WindowGeometry.MIN_WIDTH, bounds.width)
        assertEquals(WindowGeometry.MIN_HEIGHT, bounds.height)
        assertEquals(400f, bounds.x)
        assertEquals(200f, bounds.y)
    }
    @Test fun calculatorPrecedenceAndParentheses() {
        assertEquals("14", Calculator.evaluate("2 + 3 * 4"))
        assertEquals("20", Calculator.evaluate("(2 + 3) * 4"))
        assertEquals("-2", Calculator.evaluate("-(3 + 1) / 2"))
        assertEquals("0.3", Calculator.evaluate("0.1 + 0.2"))
        assertEquals("1", Calculator.evaluate("10 % 3"))
    }
    @Test(expected = IllegalArgumentException::class) fun calculatorRejectsZeroDivisor() { Calculator.evaluate("4 / (3 - 3)") }
    @Test(expected = IllegalArgumentException::class) fun calculatorRejectsCode() { Calculator.evaluate("Runtime.exec('id')") }
    @Test(expected = IllegalArgumentException::class) fun calculatorRejectsOverflow() { Calculator.evaluate("1e309") }
    @Test(expected = IllegalArgumentException::class) fun calculatorRejectsIncompleteInput() { Calculator.evaluate("2 +") }
    @Test fun shizukuNeverFakesAuthorization() {
        assertEquals(ShizukuPhase.NOT_INSTALLED, evaluateShizukuPhase(false, true, true, 13))
        assertEquals(ShizukuPhase.STOPPED, evaluateShizukuPhase(true, false, true, 13))
        assertEquals(ShizukuPhase.UPDATE_REQUIRED, evaluateShizukuPhase(true, true, true, 0))
        assertEquals(ShizukuPhase.UNAUTHORIZED, evaluateShizukuPhase(true, true, false, 13))
        assertEquals(ShizukuPhase.CONNECTED, evaluateShizukuPhase(true, true, true, 13))
        assertFalse(ShizukuState(installed = true, binderAlive = false, permissionGranted = true).authorized)
        assertFalse(ShizukuState(installed = true, binderAlive = true, permissionGranted = false).authorized)
    }
    @Test fun freeformRequiresLiveShellAndActiveSupport() {
        assertFalse(Capabilities(freeformFeature = true, taskApi = true, shellUid = -1).canOfferFreeform)
        assertFalse(Capabilities(freeformSetting = "1", taskApi = false, shellUid = 2000).canOfferFreeform)
        assertTrue(Capabilities(freeformSetting = "1", taskApi = true, shellUid = 2000).canOfferFreeform)
    }
    @Test fun nativeBoundsParsingRejectsEmptyOrShortArrays() {
        assertNull(PixelBounds.fromArray(null))
        assertNull(PixelBounds.fromArray(intArrayOf(1, 2, 3)))
        assertNull(PixelBounds.fromArray(intArrayOf(10, 10, 10, 20)))
        assertEquals(PixelBounds(10, 20, 310, 260), PixelBounds.fromArray(intArrayOf(10, 20, 310, 260)))
    }
    @Test fun nativeWindowMoveCannotLeaveDisplay() {
        val bounds = PixelBounds(100, 100, 500, 400)
        assertEquals(PixelBounds(0, 0, 400, 300), bounds.moved(-1000, -1000, 1280, 800))
        assertEquals(PixelBounds(880, 500, 1280, 800), bounds.moved(5000, 5000, 1280, 800))
    }
    @Test fun nativeWindowResizeHonorsMinimumAndDisplayEdge() {
        val bounds = PixelBounds(100, 100, 500, 400)
        val shrunk = bounds.resized(-1000, -1000, 1280, 800)
        assertEquals(320, shrunk.width)
        assertEquals(240, shrunk.height)
        val grown = bounds.resized(5000, 5000, 1280, 800)
        assertEquals(1280, grown.right)
        assertEquals(800, grown.bottom)
    }
    @Test fun nativeCascadeLeavesRoomForDock() {
        val bounds = PixelBounds.cascade(1920, 1200, 2f, 4)
        assertTrue(bounds.left >= 48)
        assertTrue(bounds.top >= 48)
        assertTrue(bounds.bottom <= 1200 - 168)
        assertTrue(bounds.width >= 480)
        assertTrue(bounds.height >= 360)
    }
}
