package com.shrey.androiddex.standalone.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.shrey.androiddex.standalone.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccessibilityState(
    val enabled: Boolean = false,
    val bound: Boolean = false,
    val foregroundPackage: String? = null,
    val lastExternalPackage: String? = null
)

/** Event-driven; no key logging, text extraction, screenshots, polling or silent activation. */
class DesktopAccessibilityService : AccessibilityService() {
    companion object {
        private val _state = MutableStateFlow(AccessibilityState())
        val state = _state.asStateFlow()
        @Volatile private var instance: DesktopAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, DesktopAccessibilityService::class.java)
            val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            val listed = manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.any {
                it.id == component.flattenToString() || it.id == component.flattenToShortString() ||
                    it.resolveInfo?.serviceInfo?.let { info -> info.packageName == component.packageName && info.name == component.className } == true
            } == true
            val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            return listed || setting.split(':').any {
                ComponentName.unflattenFromString(it)?.equals(component) == true
            }
        }
        fun refreshEnabled(context: Context) { _state.value = _state.value.copy(enabled = isEnabled(context)) }
        fun navigate(action: Int): Boolean = instance?.performGlobalAction(action) == true
        fun connected(): Boolean = instance != null
    }

    private lateinit var windows: WindowManager
    private lateinit var preferences: PreferencesRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var anchor: View? = null
    private var panel: View? = null
    private var screenOn = true
    private val controls = mutableListOf<ControlTarget>()
    private data class ControlTarget(val node: AccessibilityNodeInfo, val label: String, val click: Boolean, val longClick: Boolean, val scroll: Boolean)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateOverlay() }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
            updateOverlay()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windows = getSystemService(WindowManager::class.java)
        preferences = PreferencesRepository(this, 0)
        getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceListener)
        val screenFilter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, screenFilter)
        _state.value = AccessibilityState(enabled = true, bound = true)
        updateOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        controls.clear()
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val previous = _state.value
        _state.value = previous.copy(enabled = true, bound = true, foregroundPackage = pkg,
            lastExternalPackage = if (pkg != packageName && pkg != "com.android.systemui") pkg else previous.lastExternalPackage)
    }

    override fun onInterrupt() { controls.clear() }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        _state.value = AccessibilityState(enabled = isEnabled(this))
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        _state.value = AccessibilityState()
        super.onDestroy()
    }

    private fun cleanup() {
        if (::preferences.isInitialized) getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        runCatching { unregisterReceiver(screenReceiver) }
        removePanel(); removeAnchor(); controls.clear(); instance = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun background() = GradientDrawable().apply {
        setColor(Color.rgb(22, 23, 27)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(65, 100, 100))
    }
    private fun params(width: Int, height: Int, verticalOffset: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        x = dp(16); y = dp(verticalOffset)
        title = "DeX desktop navigation"
    }

    private fun updateOverlay() {
        if (!::windows.isInitialized) return
        if (!preferences.floatingNavigation || !screenOn) { removePanel(); removeAnchor(); return }
        if (anchor != null) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; background = background(); setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        row.addView(Button(this).apply {
            text = "DeX"; contentDescription = "Return to Shrey's Android DeX"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT); minWidth = 0; minimumWidth = 0
            setOnClickListener { returnToDesktop() }
        })
        row.addView(Button(this).apply {
            text = "Nav"; contentDescription = "Open accessible desktop navigation"; setTextColor(Color.rgb(85, 230, 193))
            setBackgroundColor(Color.TRANSPARENT); minWidth = 0; minimumWidth = 0
            setOnClickListener { showNavigationPanel() }
        })
        runCatching { windows.addView(row, params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 76)) }
            .onSuccess { anchor = row }
            .onFailure { Toast.makeText(this, "Android could not attach the navigation overlay", Toast.LENGTH_SHORT).show() }
    }
    private fun removeAnchor() { anchor?.let { runCatching { windows.removeViewImmediate(it) } }; anchor = null }
    private fun removePanel() { panel?.let { runCatching { windows.removeViewImmediate(it) } }; panel = null }

    private fun returnToDesktop() {
        if (SystemActions.desktop(this, 0)) { removePanel(); return }
        if (ShizukuManager.state.value.authorized && ShizukuManager.state.value.bridgeReady) {
            scope.launch { runCatching { ShizukuManager.launchDesktop(0) }.onFailure { Toast.makeText(this@DesktopAccessibilityService, "Open DeX from the app launcher", Toast.LENGTH_SHORT).show() } }
        } else Toast.makeText(this, "Android blocked the return shortcut. Open DeX from the launcher or connect Shizuku.", Toast.LENGTH_LONG).show()
    }

    private fun showNavigationPanel() {
        removePanel()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = background(); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        fun text(value: String) = TextView(this).apply { text = value; setTextColor(Color.WHITE); setPadding(0, dp(6), 0, dp(6)) }
        fun action(label: String, block: () -> Unit) {
            content.addView(Button(this).apply {
                text = label; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(31, 34, 40))
                setOnClickListener { block() }
                (layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(6)
            })
        }
        content.addView(text("Desktop navigation"))
        action("Return to desktop") { returnToDesktop() }
        action("Back") { if (!performGlobalAction(GLOBAL_ACTION_BACK)) Toast.makeText(this, "Back is unavailable here", Toast.LENGTH_SHORT).show() }
        action("Home") { performGlobalAction(GLOBAL_ACTION_HOME) }
        action("Recents / app switching") { performGlobalAction(GLOBAL_ACTION_RECENTS) }
        action("Lock screen") { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }
        action("Inspect actionable controls") { showControls() }
        action("Close navigation") { removePanel() }
        val scroll = ScrollView(this).apply { addView(content) }
        val height = minOf(dp(380), resources.displayMetrics.heightPixels - dp(160))
        runCatching { windows.addView(scroll, params(minOf(dp(320), resources.displayMetrics.widthPixels - dp(32)), height, 136)) }
            .onSuccess { panel = scroll }
    }

    private fun showControls() {
        controls.clear()
        val root = rootInActiveWindow ?: getWindows().firstOrNull { it.isActive && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }?.root
        if (root == null) { Toast.makeText(this, "No accessible application window is available", Toast.LENGTH_SHORT).show(); return }
        if (root.packageName?.toString() == packageName || root.packageName?.toString() == "com.android.systemui") {
            Toast.makeText(this, "Open another app, then inspect its controls from Nav", Toast.LENGTH_LONG).show(); return
        }
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 12 || controls.size >= 20) return
            // Never read text/descriptions/view IDs. Password and editable nodes are excluded entirely.
            if (node.isVisibleToUser && !node.isPassword && !node.isEditable) {
                val click = node.isClickable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
                val longClick = node.isLongClickable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
                val scroll = node.isScrollable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
                if ((click || longClick || scroll) && node.isEnabled) {
                    val kind = node.className?.toString()?.substringAfterLast('.') ?: "Interface"
                    controls.add(ControlTarget(node, "$kind control ${controls.size + 1}", click, longClick, scroll))
                }
            }
            for (i in 0 until node.childCount) { visit(node.getChild(i), depth + 1); if (controls.size >= 20) break }
        }
        visit(root, 0)
        removePanel()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = background(); setPadding(dp(14), dp(14), dp(14), dp(14)) }
        list.addView(TextView(this).apply { text = "Controls · labels and input text are not read"; setTextColor(Color.WHITE) })
        if (controls.isEmpty()) list.addView(TextView(this).apply { text = "No safe actionable controls were exposed by this app."; setTextColor(Color.LTGRAY) })
        controls.forEachIndexed { index, target ->
            list.addView(TextView(this).apply { text = target.label; setTextColor(Color.LTGRAY); setPadding(0, dp(8), 0, 0) })
            val row = LinearLayout(this)
            fun add(label: String, action: Int) {
                row.addView(Button(this).apply {
                    text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0
                    setOnClickListener {
                        val ok = runCatching { target.node.performAction(action) }.getOrDefault(false)
                        Toast.makeText(this@DesktopAccessibilityService, if (ok) "Action sent" else "Control changed or action refused", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            if (target.click) add("Tap", AccessibilityNodeInfo.ACTION_CLICK)
            if (target.longClick) add("Hold", AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (target.scroll) add("Scroll", AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            list.addView(row)
        }
        list.addView(Button(this).apply { text = "Close inspector"; setOnClickListener { controls.clear(); removePanel() } })
        val scroll = ScrollView(this).apply { addView(list) }
        runCatching { windows.addView(scroll, params(minOf(dp(340), resources.displayMetrics.widthPixels - dp(32)), minOf(dp(400), resources.displayMetrics.heightPixels - dp(160)), 136)) }
            .onSuccess { panel = scroll }
    }
}
