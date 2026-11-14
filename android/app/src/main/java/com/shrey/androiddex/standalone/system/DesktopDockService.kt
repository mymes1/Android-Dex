package com.shrey.androiddex.standalone.system

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shrey.androiddex.standalone.R
import com.shrey.androiddex.standalone.data.PreferencesRepository
import com.shrey.androiddex.standalone.ui.DesktopViewModel
import com.shrey.androiddex.standalone.ui.DexTheme
import com.shrey.androiddex.standalone.ui.DockAppsButton
import com.shrey.androiddex.standalone.ui.DockMediaIsland
import com.shrey.androiddex.standalone.ui.DockNavigationBar
import com.shrey.androiddex.standalone.ui.DockPanel
import com.shrey.androiddex.standalone.ui.DockPanelContent
import com.shrey.androiddex.standalone.ui.DockTrayIsland
import com.shrey.androiddex.standalone.ui.NativeCaptionContent
import com.shrey.androiddex.standalone.ui.rememberSystemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * User-enabled SYSTEM_ALERT_WINDOW dock. Separate small windows leave the gaps between islands
 * pass-through to the real Android apps underneath. Dark caption windows sit over native freeform
 * tasks; they do not capture or re-render app video.
 */
class DesktopDockService : Service(), androidx.lifecycle.LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        const val ACTION_SHOW = "com.shrey.androiddex.standalone.SHOW_DOCK"
        const val ACTION_STOP = "com.shrey.androiddex.standalone.STOP_DOCK"
        const val EXTRA_DISPLAY = "display_id"
        private const val NOTIFICATION_ID = 2201
        private const val CHANNEL = "dex_persistent_dock"
    }

    override val lifecycle = LifecycleRegistry(this)
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var jobs: List<Job> = emptyList()
    private lateinit var vm: DesktopViewModel
    private var currentDisplayId = -1
    private var shizukuAcquired = false
    private var screenOn = true
    private var displayContext: Context? = null
    private var windows: WindowManager? = null
    private val dockViews = mutableListOf<View>()
    private val captions = mutableMapOf<Int, CaptionWindow>()
    private var panelView: View? = null
    private var activePanel: DockPanel? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var density = 1f
    private var overlayError: String? = null

    private data class CaptionWindow(val view: ComposeView, val params: WindowManager.LayoutParams)

    private val appOpsWatcher = AppOpsManager.OnOpChangedListener { op, packageName ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && packageName == this.packageName) {
            main.post { if (!Settings.canDrawOverlays(this)) DesktopDockController.disable(this) }
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
            if (!screenOn) dismissPanel()
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::vm.isInitialized) { vm.appsRepository.invalidateIcons(); vm.refreshApps() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Desktop dock", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Ongoing notice while the optional desktop dock is displayed over other apps."
            setShowBadge(false)
        })
        runCatching { getSystemService(AppOpsManager::class.java).startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, appOpsWatcher) }
        val screenFilter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        val packageFilter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, screenFilter)
            registerReceiver(packageReceiver, packageFilter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { DesktopDockController.disable(this); stopSelf(); return START_NOT_STICKY }
        val requestedDisplay = intent?.getIntExtra(EXTRA_DISPLAY, 0) ?: 0
        if (!PreferencesRepository(this, requestedDisplay).overlayDock || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        val stopIntent = PendingIntent.getService(this, 1001, Intent(this, DesktopDockService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_desktop).setContentTitle("Android DeX dock")
            .setContentText("Desktop controls are visible over your apps.")
            .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide dock", stopIntent).build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }.onFailure { stopSelf(); return START_NOT_STICKY }
        lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        if (currentDisplayId != requestedDisplay || !::vm.isInitialized) setupDisplay(requestedDisplay)
        if (windows == null) {
            overlayError = "Display WindowManager unavailable"
            Log.e("DesktopDockService", overlayError!!)
        }
        showDockWindows()
        DesktopDockController.markRunning(true, currentDisplayId, overlayError ?: "")
        return START_STICKY
    }

    private fun setupDisplay(displayId: Int) {
        removeAllWindows()
        jobs.forEach { it.cancel() }; jobs = emptyList()
        if (::vm.isInitialized) viewModelStore.clear()
        currentDisplayId = displayId
        val display = getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId)
        displayContext = display?.let { createDisplayContext(it) } ?: this
        windows = displayContext!!.getSystemService(WindowManager::class.java)
        val metrics = displayContext!!.resources.displayMetrics
        screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels; density = metrics.density
        vm = ViewModelProvider(this, DesktopViewModel.Factory(application, displayId))[DesktopViewModel::class.java]
        if (!shizukuAcquired) { ShizukuManager.acquire(); shizukuAcquired = true }
        ShizukuManager.refresh()
        jobs = listOf(
            scope.launch { ShizukuManager.state.collect { if (it.bridgeReady) { vm.probeCapabilities(); vm.refreshTasks() } } },
            scope.launch { while (isActive) { if (screenOn && ShizukuManager.state.value.bridgeReady) vm.refreshTasks(); delay(1500) } },
            scope.launch { vm.tasks.collect { syncCaptions() } },
            scope.launch { vm.nativeBounds.collect { syncCaptions() } }
        )
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun baseParams(width: Int, height: Int, gravity: Int, focusable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            title = "DeX desktop dock"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

    private fun compose(content: @Composable () -> Unit): ComposeView {
        val context = displayContext ?: this
        return ComposeView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setViewTreeLifecycleOwner(this@DesktopDockService)
            setViewTreeViewModelStoreOwner(this@DesktopDockService)
            setViewTreeSavedStateRegistryOwner(this@DesktopDockService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { DexTheme { content() } }
        }
    }

    private fun showDockWindows() {
        if (dockViews.isNotEmpty() || windows == null) return
        overlayError = null
        val widthDp = screenWidth / density
        val compact = widthDp < 640
        val trayWidth = if (compact) 218 else 300
        fun add(widthDp: Int, gravity: Int, xDp: Int, content: @Composable () -> Unit) {
            val view = compose(content)
            val params = baseParams(dp(widthDp), dp(56), gravity, false).apply { x = dp(xDp); y = dp(10) }
            runCatching { windows!!.addView(view, params) }
                .onSuccess { dockViews += view }
                .onFailure { overlayError = "Dock overlay refused: ${it.message?.take(180)}"; Log.e("DesktopDockService", "Dock addView failed", it) }
        }
        add(184, Gravity.BOTTOM or Gravity.START, 12) {
            DockNavigationBar(onStart = { togglePanel(DockPanel.APPS) }, onHome = { SystemActions.desktop(this, currentDisplayId); dismissPanel() },
                onBack = { vm.dockBack() }, onSearch = { togglePanel(DockPanel.APPS) })
        }
        if (widthDp >= 900) add(260, Gravity.BOTTOM or Gravity.START, 204) {
            DockMediaIsland { SystemActions.notificationAccess(this); dismissPanel() }
        }
        add(56, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0) {
            DockAppsButton(activePanel == DockPanel.APPS) { togglePanel(DockPanel.APPS) }
        }
        add(trayWidth, Gravity.BOTTOM or Gravity.END, 12) {
            val status = rememberSystemStatus()
            DockTrayIsland(status, compact = compact,
                onNotifications = { if (!DesktopAccessibilityService.navigate(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) SystemActions.notificationAccess(this) },
                onSettings = { openDesktopSettings() }, onTray = { togglePanel(DockPanel.TRAY) })
        }
        if (dockViews.isEmpty() && overlayError == null) {
            overlayError = "No dock overlay windows were added"
            Log.e("DesktopDockService", overlayError!!)
        }
    }

    private fun togglePanel(panel: DockPanel) { if (activePanel == panel) dismissPanel() else showPanel(panel) }

    private fun showPanel(panel: DockPanel) {
        dismissPanel()
        if (windows == null) return
        activePanel = panel
        val width = minOf(dp(560), screenWidth - dp(24))
        val height = minOf(dp(540), screenHeight - dp(150))
        val gravity = if (panel == DockPanel.TRAY) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val params = baseParams(width, height, gravity, panel == DockPanel.APPS).apply {
            y = dp(76); if (panel == DockPanel.TRAY) x = dp(12)
            title = "DeX ${panel.name.lowercase()} panel"
        }
        val view = compose {
            val status = rememberSystemStatus()
            DockPanelContent(vm, panel, status, onDismiss = { dismissPanel() }, onOpenSettings = { openDesktopSettings() }, onOpenTasks = { showPanel(DockPanel.TASKS) })
        }
        view.setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { dismissPanel(); true } else false }
        runCatching { windows!!.addView(view, params) }.onSuccess { panelView = view }.onFailure { activePanel = null }
    }

    private fun dismissPanel() {
        panelView?.let { view -> runCatching { windows?.removeViewImmediate(view) } }
        panelView = null; activePanel = null
    }

    private fun openDesktopSettings() {
        dismissPanel()
        SystemActions.desktopSettings(this, currentDisplayId)
    }

    private fun syncCaptions() {
        if (windows == null || !::vm.isInitialized) return
        val hidden = vm.hiddenNativeTasks.value
        val boundsMap = vm.nativeBounds.value
        val maximized = vm.maximizedNativeTasks.value
        val wanted = vm.tasks.value.filter { (it.freeform || it.id in maximized) && it.visible && it.id !in hidden && boundsMap[it.id]?.isEmpty == false }.associateBy { it.id }
        val remove = captions.keys - wanted.keys
        remove.forEach { id -> captions.remove(id)?.let { runCatching { windows!!.removeViewImmediate(it.view) } } }
        wanted.forEach { (id, task) ->
            val bounds = boundsMap[id] ?: return@forEach
            val existing = captions[id]
            if (existing == null) {
                val view = compose { NativeCaptionContent(vm, id, screenWidth, screenHeight) }
                val params = WindowManager.LayoutParams(bounds.width, dp(38), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT).apply {
                    gravity = Gravity.TOP or Gravity.START; x = bounds.left; y = bounds.top
                    title = "DeX window bar ${task.packageName}"
                    setFitInsetsTypes(0)
                }
                runCatching { windows!!.addView(view, params) }
                    .onSuccess { captions[id] = CaptionWindow(view, params) }
                    .onFailure { overlayError = "Window bar overlay refused: ${it.message?.take(180)}"; Log.e("DesktopDockService", "Caption addView failed", it) }
            } else {
                val p = existing.params
                if (p.width != bounds.width || p.x != bounds.left || p.y != bounds.top) {
                    p.width = bounds.width; p.x = bounds.left; p.y = bounds.top
                    runCatching { windows!!.updateViewLayout(existing.view, p) }
                }
            }
        }
    }

    private fun removeAllWindows() {
        dismissPanel()
        captions.values.forEach { runCatching { windows?.removeViewImmediate(it.view) } }; captions.clear()
        dockViews.forEach { runCatching { windows?.removeViewImmediate(it) } }; dockViews.clear()
    }

    override fun onDestroy() {
        scope.cancel(); jobs = emptyList()
        removeAllWindows()
        runCatching { getSystemService(AppOpsManager::class.java).stopWatchingMode(appOpsWatcher) }
        runCatching { unregisterReceiver(screenReceiver) }; runCatching { unregisterReceiver(packageReceiver) }
        if (shizukuAcquired) { ShizukuManager.release(); shizukuAcquired = false }
        if (::vm.isInitialized) viewModelStore.clear()
        lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        DesktopDockController.markRunning(false, currentDisplayId.coerceAtLeast(0))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
