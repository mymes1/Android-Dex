package com.shrey.androiddex.standalone.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.shrey.androiddex.standalone.data.DockState
import com.shrey.androiddex.standalone.data.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The dock is opt-in. SYSTEM_ALERT_WINDOW is checked live; no app-op or grant is written by the app. */
object DesktopDockController {
    private val _state = MutableStateFlow(DockState())
    val state = _state.asStateFlow()

    fun hasPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun sync(context: Context, displayId: Int = 0) {
        val app = context.applicationContext
        val desired = PreferencesRepository(app, displayId).overlayDock
        val permission = hasPermission(app)
        _state.value = _state.value.copy(desired = desired, permission = permission, displayId = displayId,
            message = when {
                desired && !permission -> "Allow Display over other apps, then return to DeX."
                desired && permission -> "Starting the persistent desktop dock…"
                else -> "The persistent dock is off."
            })
        if (desired && permission) {
            runCatching {
                app.startForegroundService(Intent(app, DesktopDockService::class.java)
                    .setAction(DesktopDockService.ACTION_SHOW).putExtra(DesktopDockService.EXTRA_DISPLAY, displayId))
            }.onFailure { _state.value = _state.value.copy(running = false, message = it.message ?: "Android refused to start the dock") }
        } else {
            runCatching { app.stopService(Intent(app, DesktopDockService::class.java)) }
            _state.value = _state.value.copy(running = false)
        }
    }

    fun disable(context: Context) {
        val app = context.applicationContext
        PreferencesRepository(app, 0).overlayDock = false
        runCatching { app.stopService(Intent(app, DesktopDockService::class.java)) }
        _state.value = DockState(desired = false, permission = hasPermission(app), message = "The persistent dock is off.")
    }

    internal fun markRunning(running: Boolean, displayId: Int, message: String = "") {
        _state.value = _state.value.copy(running = running, displayId = displayId, permission = true,
            message = if (message.isNotBlank()) message else if (running) "Dock and native window bars are visible." else "The dock stopped.")
    }
}
