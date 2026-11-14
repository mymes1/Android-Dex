package com.shrey.androiddex.standalone.data

import java.util.UUID

enum class WindowKind { NOTES, FILES, CALCULATOR, SETTINGS, APPS, TASKS, DEVICE }

data class DesktopWindow(
    val id: String = UUID.randomUUID().toString(),
    val kind: WindowKind,
    val title: String = kind.defaultTitle(),
    val x: Float = 180f,
    val y: Float = 36f,
    val width: Float = 540f,
    val height: Float = 370f,
    val minimized: Boolean = false,
    val maximized: Boolean = false,
    val noteId: String? = null
)

fun WindowKind.defaultTitle(): String = when (this) {
    WindowKind.NOTES -> "Notes"
    WindowKind.FILES -> "Files"
    WindowKind.CALCULATOR -> "Calculator"
    WindowKind.SETTINGS -> "Desktop settings"
    WindowKind.APPS -> "App library"
    WindowKind.TASKS -> "Android tasks"
    WindowKind.DEVICE -> "Device & displays"
}

data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float)

object WindowGeometry {
    const val MIN_WIDTH = 280f
    const val MIN_HEIGHT = 210f

    fun clamp(x: Float, y: Float, width: Float, height: Float, areaWidth: Float, areaHeight: Float): Bounds {
        val aw = areaWidth.coerceAtLeast(1f)
        val ah = areaHeight.coerceAtLeast(1f)
        val w = width.coerceIn(minOf(MIN_WIDTH, aw), aw)
        val h = height.coerceIn(minOf(MIN_HEIGHT, ah), ah)
        return Bounds(x.coerceIn(0f, (aw - w).coerceAtLeast(0f)), y.coerceIn(0f, (ah - h).coerceAtLeast(0f)), w, h)
    }

    fun resize(window: DesktopWindow, dx: Float, dy: Float, areaWidth: Float, areaHeight: Float): Bounds {
        val availableWidth = areaWidth.coerceAtLeast(1f)
        val availableHeight = (areaHeight - window.y.coerceAtLeast(0f)).coerceAtLeast(1f)
        val width = (window.width + dx).coerceIn(minOf(MIN_WIDTH, availableWidth), availableWidth)
        val height = (window.height + dy).coerceIn(minOf(MIN_HEIGHT, availableHeight), availableHeight)
        return clamp(window.x, window.y, width, height, areaWidth, areaHeight)
    }
}

data class AppEntry(val packageName: String, val component: String, val label: String)

data class Note(val id: String, val text: String, val updatedAt: Long) {
    val title: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(32) ?: "Untitled note"
}

data class RecentApp(val packageName: String, val openedAt: Long)

enum class ShizukuPhase { NOT_INSTALLED, STOPPED, UPDATE_REQUIRED, UNAUTHORIZED, CONNECTED, ERROR }

data class ShizukuState(
    val installed: Boolean = false,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val serverVersion: Int = -1,
    val uid: Int = -1,
    val denied: Boolean = false,
    val bridgeReady: Boolean = false,
    val binding: Boolean = false,
    val message: String = "Checking Shizuku…"
) {
    val phase: ShizukuPhase get() = evaluateShizukuPhase(installed, binderAlive, permissionGranted, serverVersion)
    val authorized: Boolean get() = phase == ShizukuPhase.CONNECTED
    val label: String get() = when (phase) {
        ShizukuPhase.NOT_INSTALLED -> "Not installed"
        ShizukuPhase.STOPPED -> "Service stopped"
        ShizukuPhase.UPDATE_REQUIRED -> "Update required"
        ShizukuPhase.UNAUTHORIZED -> "Running · not authorized"
        ShizukuPhase.CONNECTED -> "Connected · authorized"
        ShizukuPhase.ERROR -> "Connection error"
    }
}

fun evaluateShizukuPhase(installed: Boolean, alive: Boolean, granted: Boolean, version: Int): ShizukuPhase = when {
    !installed -> ShizukuPhase.NOT_INSTALLED
    !alive -> ShizukuPhase.STOPPED
    version in 0..10 -> ShizukuPhase.UPDATE_REQUIRED
    !granted -> ShizukuPhase.UNAUTHORIZED
    else -> ShizukuPhase.CONNECTED
}

data class NativeTask(
    val id: Int,
    val displayId: Int,
    val packageName: String,
    val component: String,
    val running: Boolean,
    val visible: Boolean,
    val focused: Boolean,
    val resizable: Boolean,
    val windowingMode: Int
) {
    val freeform: Boolean get() = windowingMode == 5
}

/** Real Android task bounds in display pixels. Empty until the shell reports them. */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun moved(dx: Int, dy: Int, screenWidth: Int, screenHeight: Int): PixelBounds {
        val maxLeft = (screenWidth - width).coerceAtLeast(0)
        val maxTop = (screenHeight - height).coerceAtLeast(0)
        val x = (left + dx).coerceIn(0, maxLeft)
        val y = (top + dy).coerceIn(0, maxTop)
        return PixelBounds(x, y, x + width, y + height)
    }

    fun resized(dw: Int, dh: Int, screenWidth: Int, screenHeight: Int, minWidth: Int = 320, minHeight: Int = 240): PixelBounds {
        val maxW = (screenWidth - left).coerceAtLeast(minWidth)
        val maxH = (screenHeight - top).coerceAtLeast(minHeight)
        val w = (width + dw).coerceIn(minOf(minWidth, maxW), maxW)
        val h = (height + dh).coerceIn(minOf(minHeight, maxH), maxH)
        return PixelBounds(left, top, left + w, top + h)
    }

    companion object {
        fun fromArray(values: IntArray?): PixelBounds? {
            if (values == null || values.size < 4) return null
            val bounds = PixelBounds(values[0], values[1], values[2], values[3])
            return bounds.takeUnless { it.isEmpty }
        }

        /** Cascade a new native window inside the workspace, leaving room for the persistent dock. */
        fun cascade(screenWidth: Int, screenHeight: Int, density: Float, index: Int, dockDp: Int = 84): PixelBounds {
            val margin = (24 * density).toInt().coerceAtLeast(12)
            val dock = (dockDp * density).toInt()
            val usableW = (screenWidth - margin * 2).coerceAtLeast(320)
            val usableH = (screenHeight - dock - margin * 2).coerceAtLeast(240)
            val w = (usableW * 0.62f).toInt().coerceIn(minOf(480, usableW), usableW)
            val h = (usableH * 0.72f).toInt().coerceIn(minOf(360, usableH), usableH)
            val step = (28 * density).toInt()
            val slackX = (usableW - w).coerceAtLeast(0)
            val slackY = (usableH - h).coerceAtLeast(0)
            val x = margin + ((index % 5) * step).coerceAtMost(slackX)
            val y = margin + ((index % 5) * step).coerceAtMost(slackY)
            return PixelBounds(x, y, x + w, y + h)
        }
    }
}

data class DockState(val desired: Boolean = false, val permission: Boolean = false, val running: Boolean = false, val displayId: Int = 0, val message: String = "")

data class MediaSnapshot(
    val accessGranted: Boolean = false,
    val notificationCount: Int = 0,
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val packageName: String = "",
    val artwork: androidx.compose.ui.graphics.ImageBitmap? = null
)

data class Capabilities(
    val freeformFeature: Boolean = false,
    val secondaryDisplaysFeature: Boolean = false,
    val freeformSetting: String = "unknown",
    val forceResizableSetting: String = "unknown",
    val taskApi: Boolean = false,
    val shellUid: Int = -1,
    val probeMessage: String = "Authorize Shizuku to check shell-level capabilities."
) {
    val freeformActive: Boolean get() = freeformFeature || freeformSetting == "1"
    val canOfferFreeform: Boolean get() = taskApi && freeformActive && shellUid == 2000
}
