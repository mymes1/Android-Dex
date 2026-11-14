package com.shrey.androiddex.standalone.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shrey.androiddex.standalone.data.*
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import com.shrey.androiddex.standalone.system.ShizukuManager
import com.shrey.androiddex.standalone.system.SystemActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DesktopPreferences(
    val wallpaper: Int = 0, val wallpaperRevision: Int = 0, val floatingNavigation: Boolean = false,
    val immersive: Boolean = true, val keepScreenOn: Boolean = false, val landscape: Boolean = true,
    val overlayDock: Boolean = false
)

class DesktopViewModel(private val application: Application, val displayId: Int) : ViewModel() {
    val preferences = PreferencesRepository(application, displayId)
    val appsRepository = AppRepository(application)
    private val _windows = MutableStateFlow(preferences.loadWindows())
    val windows = _windows.asStateFlow()
    private val _focused = MutableStateFlow(_windows.value.lastOrNull { !it.minimized }?.id)
    val focused = _focused.asStateFlow()
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _appsLoading = MutableStateFlow(true)
    val appsLoading = _appsLoading.asStateFlow()
    private val _pins = MutableStateFlow(preferences.pinnedPackages())
    val pins = _pins.asStateFlow()
    private val _recents = MutableStateFlow(preferences.recentApps())
    val recents = _recents.asStateFlow()
    private val _settings = MutableStateFlow(readSettings())
    val settings = _settings.asStateFlow()
    private val _iconRevision = MutableStateFlow(0)
    val iconRevision = _iconRevision.asStateFlow()
    private val _tasks = MutableStateFlow<List<NativeTask>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _tasksKnown = MutableStateFlow(false)
    val tasksKnown = _tasksKnown.asStateFlow()
    private val _nativeBounds = MutableStateFlow<Map<Int, PixelBounds>>(emptyMap())
    val nativeBounds = _nativeBounds.asStateFlow()
    private val _hiddenNativeTasks = MutableStateFlow<Set<Int>>(emptySet())
    val hiddenNativeTasks = _hiddenNativeTasks.asStateFlow()
    private val _minimizedNativeTasks = MutableStateFlow<Map<Int, NativeTask>>(emptyMap())
    val minimizedNativeTasks = _minimizedNativeTasks.asStateFlow()
    private val _maximizedNativeTasks = MutableStateFlow<Set<Int>>(emptySet())
    val maximizedNativeTasks = _maximizedNativeTasks.asStateFlow()
    private val restoreBounds = mutableMapOf<Int, PixelBounds>()
    private data class NativeResizeRequest(val taskId: Int, val bounds: PixelBounds, val commit: Boolean = false)
    private val nativeResizeRequests = Channel<NativeResizeRequest>(Channel.CONFLATED)
    private val _taskError = MutableStateFlow("Connect and authorize Shizuku to list real Android tasks.")
    val taskError = _taskError.asStateFlow()
    private val _capabilities = MutableStateFlow(Capabilities(
        freeformFeature = application.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
        secondaryDisplaysFeature = application.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)))
    val capabilities = _capabilities.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private var appJob: Job? = null
    private var taskJob: Job? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = readSettings() }

    init {
        application.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceListener)
        viewModelScope.launch {
            for (request in nativeResizeRequests) {
                runCatching { ShizukuManager.resizeTask(request.taskId, request.bounds) }
                    .onFailure { if (request.commit) notify(it.message ?: "Android refused the window resize") }
                if (request.commit) refreshTasks()
                delay(30)
            }
        }
        refreshApps()
    }

    fun notify(message: String) { _messages.trySend(message) }
    private fun readSettings() = DesktopPreferences(preferences.wallpaper, preferences.wallpaperRevision, preferences.floatingNavigation,
        preferences.immersive, preferences.keepScreenOn, preferences.landscape, preferences.overlayDock)
    private fun updateSettings(block: () -> Unit) { block(); preferencesChanged(); _settings.value = readSettings() }
    private fun preferencesChanged() {
        // Preference changes also notify the Accessibility Service; increment is not used as a permission signal.
        application.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).edit().putLong("settings_revision", System.currentTimeMillis()).apply()
    }

    private fun change(persist: Boolean = true, block: (List<DesktopWindow>) -> List<DesktopWindow>) {
        val updated = block(_windows.value)
        if (updated == _windows.value) return
        _windows.value = updated
        if (_focused.value !in _windows.value.map { it.id }) _focused.value = _windows.value.lastOrNull { !it.minimized }?.id
        if (persist) persist()
    }
    fun persist() { preferences.saveWindows(_windows.value) }
    fun fitWorkspace(width: Float, height: Float) {
        change { list -> list.map { w ->
            val b = WindowGeometry.clamp(w.x, w.y, w.width, w.height, width, height)
            w.copy(x = b.x, y = b.y, width = b.width, height = b.height)
        } }
    }
    fun focus(id: String) {
        if (_windows.value.none { it.id == id }) return
        _focused.value = id
        change { list -> list.filterNot { it.id == id } + list.first { it.id == id }.copy(minimized = false) }
    }
    fun openTool(kind: WindowKind) {
        if (kind == WindowKind.NOTES) { openNote(preferences.notes().firstOrNull()?.id); return }
        val existing = _windows.value.firstOrNull { it.kind == kind }
        if (existing != null) { focus(existing.id); return }
        val count = _windows.value.size
        if (count >= 12) { notify("Close a window first; this desktop keeps at most 12 windows to protect tablet memory."); return }
        val width = when (kind) { WindowKind.CALCULATOR -> 340f; WindowKind.SETTINGS, WindowKind.APPS, WindowKind.FILES -> 650f; else -> 560f }
        val height = if (kind == WindowKind.CALCULATOR) 420f else 450f
        val window = DesktopWindow(kind = kind, x = 150f + (count % 5) * 28, y = 24f + (count % 5) * 24, width = width, height = height)
        change { it + window }
        _focused.value = window.id
        if (kind == WindowKind.TASKS) refreshTasks()
    }
    fun openNote(noteId: String?) {
        val existing = noteId?.let { id -> _windows.value.firstOrNull { it.noteId == id } }
        if (existing != null) { focus(existing.id); return }
        if (_windows.value.size >= 12) { notify("Close a window before opening another note."); return }
        val id = noteId ?: preferences.createNote()
        val note = Note(id, preferences.note(id), System.currentTimeMillis())
        val count = _windows.value.size
        val window = DesktopWindow(kind = WindowKind.NOTES, title = "Notes · ${note.title}", noteId = id,
            x = 170f + (count % 5) * 24, y = 32f + (count % 5) * 20, width = 520f, height = 360f)
        change { it + window }
        _focused.value = window.id
    }
    fun closeWindow(id: String) { change { it.filterNot { w -> w.id == id } } }
    fun minimize(id: String) { change { it.map { w -> if (w.id == id) w.copy(minimized = true) else w } } }
    fun maximize(id: String) { focus(id); change { it.map { w -> if (w.id == id) w.copy(maximized = !w.maximized) else w } } }
    fun moveWindow(id: String, dx: Float, dy: Float, width: Float, height: Float) {
        change(false) { it.map { w -> if (w.id == id && !w.maximized) {
            val b = WindowGeometry.clamp(w.x + dx, w.y + dy, w.width, w.height, width, height); w.copy(x = b.x, y = b.y)
        } else w } }
    }
    fun resizeWindow(id: String, dx: Float, dy: Float, width: Float, height: Float) {
        change(false) { it.map { w -> if (w.id == id && !w.maximized) {
            val b = WindowGeometry.resize(w, dx, dy, width, height); w.copy(width = b.width, height = b.height)
        } else w } }
    }
    fun cycleWindow(): Boolean {
        val visible = _windows.value.filter { !it.minimized }
        if (visible.isEmpty()) return false
        val index = visible.indexOfFirst { it.id == _focused.value }
        focus(visible[(index + 1) % visible.size].id)
        return true
    }
    fun closeActive(): Boolean {
        val id = _focused.value ?: return false
        if (_windows.value.none { it.id == id && !it.minimized }) return false
        closeWindow(id); return true
    }
    fun saveNote(id: String, text: String) {
        preferences.saveNote(id, text)
        val title = Note(id, text, 0).title
        change { it.map { w -> if (w.noteId == id) w.copy(title = "Notes · $title") else w } }
    }
    fun deleteNote(id: String) { preferences.deleteNote(id); closeWindowsForNote(id) }
    private fun closeWindowsForNote(id: String) { change { it.filterNot { w -> w.noteId == id } } }
    fun moveIcon(key: String, x: Float, y: Float) { preferences.setIconPosition(key, x, y); preferencesChanged() }
    fun resetIcons() { preferences.resetIcons(); _iconRevision.value++; preferencesChanged() }
    fun setWallpaper(index: Int) { updateSettings { preferences.wallpaper = index } }
    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            runCatching { WallpaperStore.import(application, uri) }
                .onSuccess { updateSettings { preferences.wallpaper = -1; preferences.wallpaperRevision++ }; notify("Wallpaper saved locally. Source metadata was removed.") }
                .onFailure { notify(it.message ?: "Could not import this image") }
        }
    }
    fun setFloatingNavigation(value: Boolean) { updateSettings { preferences.floatingNavigation = value } }
    fun setImmersive(value: Boolean) { updateSettings { preferences.immersive = value } }
    fun setKeepScreenOn(value: Boolean) { updateSettings { preferences.keepScreenOn = value } }
    fun setLandscape(value: Boolean) { updateSettings { preferences.landscape = value } }
    fun setOverlayDock(value: Boolean) { updateSettings { preferences.overlayDock = value } }
    fun togglePin(packageName: String) { _pins.value = preferences.togglePinned(packageName) }
    fun clearRecents() { preferences.clearRecents(); _recents.value = emptyList() }
    fun reviewSetup() { preferences.onboarded = false }

    fun refreshApps() {
        appJob?.cancel()
        appJob = viewModelScope.launch {
            _appsLoading.value = true
            runCatching { appsRepository.loadApps() }
                .onSuccess { _apps.value = it; _recents.value = preferences.recentApps().filter { r -> it.any { a -> a.packageName == r.packageName } } }
                .onFailure { notify("Could not read the launchable app list: ${it.message?.take(140)}") }
            _appsLoading.value = false
        }
    }
    /** null = prefer a real native window when the live capability probe allows it; false = fullscreen. */
    fun launchApp(entry: AppEntry, freeform: Boolean? = null) {
        val useFreeform = freeform ?: (_capabilities.value.canOfferFreeform && ShizukuManager.state.value.bridgeReady)
        if (useFreeform) {
            if (!_capabilities.value.canOfferFreeform || !ShizukuManager.state.value.bridgeReady) { notify("Native freeform is not available. Open the app normally instead."); return }
            val metrics = nativeDisplayMetrics()
            val bounds = PixelBounds.cascade(metrics.widthPixels, metrics.heightPixels, metrics.density, _tasks.value.size)
            viewModelScope.launch {
                runCatching { ShizukuManager.launchFreeform(entry, displayId, bounds) }
                    .onSuccess {
                        preferences.recordLaunch(entry.packageName); _recents.value = preferences.recentApps()
                        notify("${entry.label} opened in an Android window. Firmware may still override the mode.")
                        delay(600); refreshTasks()
                    }
                    .onFailure { notify(it.message ?: "Android refused the freeform launch") }
            }
            return
        }
        val intent = appsRepository.launchIntent(entry)
        if (intent == null) { notify("This shortcut is no longer available."); refreshApps(); return }
        val options = if (displayId != 0) android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle() else null
        runCatching { application.startActivity(intent, options) }
            .onSuccess {
                preferences.recordLaunch(entry.packageName); _recents.value = preferences.recentApps()
                notify("${entry.label} opened in Android. Use Recents or the DeX dock to return.")
            }
            .onFailure { notify("Android could not open ${entry.label}. The shortcut may have changed.") }
    }

    private fun nativeDisplayMetrics(): android.util.DisplayMetrics {
        val display = application.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId)
        return (display?.let { application.createDisplayContext(it) } ?: application).resources.displayMetrics
    }
    fun refreshTasks() {
        // Polling must not cancel an in-flight shell query every 1.5s; that can starve updates on
        // slower firmware and race a just-applied maximize/restore back to stale task state.
        if (taskJob?.isActive == true) return
        taskJob = viewModelScope.launch {
            if (!ShizukuManager.state.value.bridgeReady) {
                _tasks.value = emptyList(); _tasksKnown.value = false
                _taskError.value = "Shizuku's task bridge is not connected. Recent launches below are history, not a running-process list."
                return@launch
            }
            runCatching { ShizukuManager.tasks(displayId) }
                .onSuccess { list ->
                    val running = list.filter { task -> task.running }
                    _tasks.value = running; _tasksKnown.value = true; _taskError.value = ""
                    val ids = running.map { it.id }.toSet()
                    _minimizedNativeTasks.value = _minimizedNativeTasks.value.filterKeys { it in ids }
                    _hiddenNativeTasks.value = _hiddenNativeTasks.value.intersect(ids)
                    _maximizedNativeTasks.value = _maximizedNativeTasks.value.intersect(ids)
                    val bounds = _nativeBounds.value.filterKeys { it in ids }.toMutableMap()
                    running.filter { (it.freeform || it.id in _maximizedNativeTasks.value) && it.visible && it.id !in _hiddenNativeTasks.value }.take(12).forEach { task ->
                        ShizukuManager.taskBounds(task.id)?.let { bounds[task.id] = it }
                    }
                    _nativeBounds.value = bounds
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _tasks.value = emptyList(); _tasksKnown.value = false; _nativeBounds.value = emptyMap(); _taskError.value = it.message ?: "Task listing is unsupported on this firmware."
                }
        }
    }
    fun probeCapabilities() {
        val pm = application.packageManager
        val freeform = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val secondary = pm.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
        viewModelScope.launch {
            if (!ShizukuManager.state.value.bridgeReady) {
                _capabilities.value = Capabilities(freeform, secondary)
                return@launch
            }
            runCatching { ShizukuManager.probe(freeform, secondary, displayId) }
                .onSuccess { _capabilities.value = it }
                .onFailure { _capabilities.value = Capabilities(freeform, secondary, probeMessage = it.message ?: "Capability probe failed") }
        }
    }
    fun setFreeform(enabled: Boolean, forceResizable: Boolean) {
        viewModelScope.launch {
            runCatching { ShizukuManager.setFreeform(enabled, forceResizable) }
                .onSuccess { probeCapabilities(); refreshTasks(); notify("Android development settings updated. Restart affected apps; some firmware requires a reboot.") }
                .onFailure { notify(it.message ?: "Android refused the setting change") }
        }
    }
    fun switchTask(task: NativeTask) {
        viewModelScope.launch { runCatching { ShizukuManager.bringToFront(task) }
            .onFailure { notify(it.message ?: "Could not switch task") } }
    }
    fun closeTask(task: NativeTask) {
        viewModelScope.launch { runCatching { ShizukuManager.removeTask(task) }
            .onSuccess { refreshTasks(); notify("Android task closed.") }
            .onFailure { notify(it.message ?: "Could not close task") } }
    }

    fun minimizeNativeTask(taskId: Int) {
        viewModelScope.launch {
            runCatching { ShizukuManager.setTaskHidden(taskId, true) }
                .onSuccess {
                    _tasks.value.firstOrNull { it.id == taskId }?.let { _minimizedNativeTasks.value = _minimizedNativeTasks.value + (taskId to it) }
                    _hiddenNativeTasks.value = _hiddenNativeTasks.value + taskId
                    refreshTasks()
                }
                .onFailure { notify(it.message ?: "Android refused to minimize this window") }
        }
    }
    fun restoreNativeTask(taskId: Int) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: _minimizedNativeTasks.value[taskId] ?: return
        viewModelScope.launch {
            runCatching {
                ShizukuManager.setTaskHidden(taskId, false)
                if (taskId in _maximizedNativeTasks.value) {
                    ShizukuManager.setTaskWindowingMode(taskId, 5)
                    restoreBounds[taskId]?.let { ShizukuManager.resizeTask(taskId, it) }
                }
                ShizukuManager.bringToFront(task)
            }.onSuccess {
                _hiddenNativeTasks.value = _hiddenNativeTasks.value - taskId
                _minimizedNativeTasks.value = _minimizedNativeTasks.value - taskId
                _maximizedNativeTasks.value = _maximizedNativeTasks.value - taskId
                refreshTasks()
            }.onFailure { notify(it.message ?: "Android refused to restore this window") }
        }
    }
    fun toggleNativeMaximize(taskId: Int) {
        viewModelScope.launch {
            val maximize = taskId !in _maximizedNativeTasks.value
            runCatching {
                if (!maximize) {
                    ShizukuManager.setTaskWindowingMode(taskId, 5)
                    val bounds = restoreBounds[taskId] ?: _nativeBounds.value[taskId]
                    if (bounds != null) {
                        ShizukuManager.resizeTask(taskId, bounds)
                        previewNativeBounds(taskId, bounds)
                    }
                } else {
                    val current = ShizukuManager.taskBounds(taskId) ?: _nativeBounds.value[taskId]
                    if (current != null && !current.isEmpty) restoreBounds[taskId] = current
                    ShizukuManager.setTaskWindowingMode(taskId, 1)
                }
            }.onSuccess {
                Log.e("DesktopDockService", "Native mode request success task=$taskId maximize=$maximize")
                _maximizedNativeTasks.value = if (maximize) _maximizedNativeTasks.value + taskId else _maximizedNativeTasks.value - taskId
                // Update the caption immediately; the next shell poll confirms the real mode/bounds.
                _tasks.value = _tasks.value.map { if (it.id == taskId) it.copy(windowingMode = if (maximize) 1 else 5) else it }
                refreshTasks()
            }.onFailure {
                Log.e("DesktopDockService", "Native maximize/restore request failed for task $taskId", it)
                notify(it.message ?: "Android refused the window mode change")
            }
        }
    }
    fun closeNativeTask(taskId: Int) {
        viewModelScope.launch {
            runCatching { ShizukuManager.removeTask(NativeTask(taskId, displayId, "", "", true, false, false, false, 5)) }
                .onSuccess {
                    Log.e("DesktopDockService", "Native close request success task=$taskId")
                    _hiddenNativeTasks.value = _hiddenNativeTasks.value - taskId; _minimizedNativeTasks.value = _minimizedNativeTasks.value - taskId; _maximizedNativeTasks.value = _maximizedNativeTasks.value - taskId; restoreBounds.remove(taskId); refreshTasks(); notify("Android window closed.")
                }
                .onFailure { Log.e("DesktopDockService", "Native close request failed task=$taskId", it); notify(it.message ?: "Could not close this Android window") }
        }
    }
    fun previewNativeBounds(taskId: Int, bounds: PixelBounds) {
        _nativeBounds.value = _nativeBounds.value + (taskId to bounds)
    }
    fun dragNativeTask(taskId: Int, bounds: PixelBounds) {
        previewNativeBounds(taskId, bounds)
        nativeResizeRequests.trySend(NativeResizeRequest(taskId, bounds))
    }
    fun commitNativeBounds(taskId: Int, bounds: PixelBounds) {
        Log.e("DesktopDockService", "Native bounds commit task=$taskId bounds=$bounds")
        previewNativeBounds(taskId, bounds)
        nativeResizeRequests.trySend(NativeResizeRequest(taskId, bounds, commit = true))
    }
    fun dockBack() {
        if (DesktopAccessibilityService.navigate(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) return
        viewModelScope.launch { runCatching { ShizukuManager.back() }.onFailure { notify("Enable Accessibility navigation or authorize Shizuku for Back.") } }
    }
    fun switchNextNativeTask(): Boolean {
        if (!_tasksKnown.value) return false
        val candidates = _tasks.value.filter { it.running && it.packageName != application.packageName }
        if (candidates.isEmpty()) return false
        val current = candidates.indexOfFirst { it.focused }
        switchTask(candidates[(current + 1) % candidates.size])
        return true
    }
    fun showRecents() {
        if (com.shrey.androiddex.standalone.system.DesktopAccessibilityService.navigate(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)) return
        viewModelScope.launch { runCatching { ShizukuManager.showRecents() }.onFailure { notify("Enable Accessibility navigation or authorize Shizuku to open Android Recents.") } }
    }
    fun appLabel(packageName: String): String = _apps.value.firstOrNull { it.packageName == packageName }?.label ?: packageName
    override fun onCleared() {
        application.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        nativeResizeRequests.close()
        persist()
        super.onCleared()
    }

    class Factory(private val application: Application, private val displayId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DesktopViewModel(application, displayId) as T
    }
}
