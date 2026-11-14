package com.shrey.androiddex.standalone.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.shrey.androiddex.standalone.BuildConfig
import com.shrey.androiddex.standalone.data.AppEntry
import com.shrey.androiddex.standalone.data.Capabilities
import com.shrey.androiddex.standalone.data.NativeTask
import com.shrey.androiddex.standalone.data.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Permission is always queried from the live official API, never restored from preferences. */
object ShizukuManager {
    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(ShizukuState())
    val state = _state.asStateFlow()
    private var bridge: IDesktopTaskBridge? = null
    private var activeClients = 0
    private var binding = false
    private var generation = 0
    private var timeout: Runnable? = null
    private const val PERMISSION_REQUEST = 4101
    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, PrivilegedTaskService::class.java))
            .daemon(false).debuggable(BuildConfig.DEBUG).processNameSuffix("desktop_shell")
            .tag("dex-task-bridge-v1").version(BuildConfig.VERSION_CODE)
    }

    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener {
        main.post {
            generation++
            cancelTimeout()
            bridge = null
            binding = false
            refresh()
        }
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == PERMISSION_REQUEST) {
            refresh()
            if (result != PackageManager.PERMISSION_GRANTED) {
                _state.value = _state.value.copy(denied = true,
                    message = "Authorization was denied. Desktop tools still work. You can allow this app in Shizuku → Authorized applications.")
            }
        }
    }

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(received, main)
        Shizuku.addBinderDeadListener(dead, main)
        Shizuku.addRequestPermissionResultListener(permissionResult, main)
        refresh()
    }

    fun acquire() { activeClients++; refresh() }
    fun release() {
        activeClients = (activeClients - 1).coerceAtLeast(0)
        if (activeClients == 0) unbind()
    }

    fun reconnectBridge() {
        unbind()
        refresh()
    }

    fun refresh() {
        if (!::appContext.isInitialized) return
        val installed = runCatching { appContext.packageManager.getApplicationInfo(SystemActions.SHIZUKU_PACKAGE, 0); true }.getOrDefault(false)
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val version = if (alive) runCatching { Shizuku.getVersion() }.getOrDefault(-1) else -1
        val preV11 = alive && runCatching { Shizuku.isPreV11() }.getOrDefault(false)
        val granted = alive && !preV11 && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        val uid = if (alive && granted) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1
        val previous = _state.value
        val next = ShizukuState(installed, alive, granted, if (preV11) 0 else version, uid,
            denied = previous.denied && !granted, bridgeReady = previous.bridgeReady && granted && alive,
            binding = previous.binding && granted && alive)
        _state.value = next.copy(message = when {
            !installed -> "Install the official Shizuku app to enable advanced Android features."
            !alive -> "Shizuku is not running. Start it with Wireless debugging on this tablet; a reboot stops the service."
            preV11 || version in 0..10 -> "This app requires Shizuku API v11 or newer. Update Shizuku."
            !granted && previous.denied -> "Authorization denied or revoked. Allow Shrey's Android DeX in Shizuku's authorized-app settings."
            !granted -> "Shizuku is running. Request authorization to enable task switching and capability probes."
            next.bridgeReady -> "Shizuku is authorized and the task bridge is connected."
            next.binding -> "Shizuku authorized. Connecting the on-demand task bridge…"
            else -> "Shizuku is authorized. Advanced tools connect while the desktop is open."
        })
        if (!granted || !alive) unbind()
        else if (activeClients > 0 && !binding && bridge == null) bind()
    }

    fun requestPermission() {
        refresh()
        val current = _state.value
        when {
            !current.installed -> _state.value = current.copy(message = "Install Shizuku first. The desktop remains available without it.")
            !current.binderAlive -> _state.value = current.copy(message = "Start Shizuku from its Wireless debugging screen, then return here.")
            current.authorized -> return
            runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false) ->
                _state.value = current.copy(denied = true, message = "Shizuku remembers a denial. Open Shizuku → Authorized applications and allow this app, then refresh.")
            else -> runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
                .onFailure { _state.value = _state.value.copy(message = "Could not open Shizuku authorization: ${it.message?.take(180)}") }
        }
    }

    private fun bind() {
        if (binding || bridge != null || !_state.value.authorized) return
        binding = true
        val ticket = ++generation
        _state.value = _state.value.copy(binding = true, message = "Shizuku authorized. Connecting the on-demand task bridge…")
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                main.post {
                    if (ticket != generation) return@post
                    cancelTimeout()
                    binding = false
                    if (!_state.value.authorized || binder == null) { refresh(); return@post }
                    val remote = IDesktopTaskBridge.Stub.asInterface(binder)
                    val uid = runCatching { remote.identity() }.getOrDefault(-1)
                    if (uid != 2000) {
                        _state.value = _state.value.copy(bridgeReady = false, message = "Advanced task features need Shizuku started by Wireless ADB (shell), not another backend.")
                        unbind()
                        return@post
                    }
                    bridge = remote
                    _state.value = _state.value.copy(bridgeReady = true, binding = false, message = "Shizuku is authorized and the task bridge is connected.")
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                main.post { if (ticket == generation) { bridge = null; binding = false; refresh() } }
            }
            override fun onNullBinding(name: ComponentName?) {
                main.post { if (ticket == generation) failBinding("Shizuku could not create the task bridge. Reconnect Shizuku and retry.") }
            }
            override fun onBindingDied(name: ComponentName?) {
                main.post { if (ticket == generation) { bridge = null; failBinding("Task bridge stopped. Refresh to reconnect."); refresh() } }
            }
        }
        timeout = Runnable { if (ticket == generation) { failBinding("Task bridge timed out. Refresh to retry."); unbind(); refresh() } }.also { main.postDelayed(it, 12000) }
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }.onFailure {
            failBinding("Could not bind Shizuku UserService: ${it.message?.take(180)}")
            unbind()
        }
    }

    private fun failBinding(message: String) {
        cancelTimeout(); binding = false; bridge = null
        _state.value = _state.value.copy(binding = false, bridgeReady = false, message = message)
    }
    private fun cancelTimeout() { timeout?.let(main::removeCallbacks); timeout = null }
    private fun unbind() {
        generation++
        cancelTimeout()
        val wasBound = binding || bridge != null
        binding = false
        bridge = null
        if (wasBound && runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, null, true) }
        }
        _state.value = _state.value.copy(bridgeReady = false, binding = false)
    }

    private fun requireBridge(): IDesktopTaskBridge {
        // Detect revoked permission even if no Activity resume or callback has occurred yet.
        val granted = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!granted) { main.post { refresh() }; error("Shizuku authorization is no longer available") }
        return bridge ?: error("Task bridge is not connected. Refresh Shizuku in Settings.")
    }

    suspend fun tasks(displayId: Int): List<NativeTask> = withContext(Dispatchers.IO) {
        requireBridge().tasks(40, displayId).map {
            NativeTask(it.taskId, it.displayId, it.packageName, it.component, it.running, it.visible, it.focused, it.resizable, it.windowingMode)
        }
    }
    suspend fun bringToFront(task: NativeTask): Unit = withContext(Dispatchers.IO) {
        check(requireBridge().bringTaskToFront(task.id, task.displayId)) { "Android did not accept the task switch" }
    }
    suspend fun removeTask(task: NativeTask): Unit = withContext(Dispatchers.IO) {
        check(requireBridge().removeTask(task.id)) { "Android did not close this task" }
    }
    suspend fun launchFreeform(entry: AppEntry, displayId: Int): String = withContext(Dispatchers.IO) {
        requireBridge().launchFreeform(entry.component, displayId)
    }
    suspend fun launchDesktop(displayId: Int): String = withContext(Dispatchers.IO) {
        requireBridge().launchDesktop(ComponentName(appContext, com.shrey.androiddex.standalone.MainActivity::class.java).flattenToShortString(), displayId)
    }
    suspend fun showRecents(): Unit = withContext(Dispatchers.IO) { requireBridge().showRecents() }
    suspend fun setFreeform(enabled: Boolean, forceResizable: Boolean): Unit = withContext(Dispatchers.IO) {
        val remote = requireBridge()
        remote.writeSetting("enable_freeform_support", if (enabled) "1" else "0")
        remote.writeSetting("force_resizable_activities", if (enabled && forceResizable) "1" else "0")
    }
    suspend fun probe(freeformFeature: Boolean, secondaryFeature: Boolean, displayId: Int): Capabilities = withContext(Dispatchers.IO) {
        val remote = requireBridge()
        val freeform = runCatching { remote.readSetting("enable_freeform_support") }.getOrDefault("unknown")
        val force = runCatching { remote.readSetting("force_resizable_activities") }.getOrDefault("unknown")
        val taskApi = runCatching { remote.tasks(1, displayId); true }.getOrDefault(false)
        Capabilities(freeformFeature, secondaryFeature, freeform, force, taskApi, remote.identity(),
            if (taskApi) "Live shell probe complete. Firmware can still override individual launches."
            else "Shell is connected, but this firmware does not expose a compatible task-list API. Use Android Recents.")
    }
}
