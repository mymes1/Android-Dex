package com.shrey.androiddex.standalone.ui

import android.accessibilityservice.AccessibilityService
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.androiddex.standalone.data.NativeTask
import com.shrey.androiddex.standalone.data.PixelBounds
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import com.shrey.androiddex.standalone.system.DesktopMediaService
import com.shrey.androiddex.standalone.system.ShizukuManager
import com.shrey.androiddex.standalone.system.SystemActions
import java.text.DateFormat
import java.util.Date

enum class DockPanel { APPS, TASKS, TRAY }

private val DockGlass = Color(0xE6415A62)
private val DockGlassDark = Color(0xF0263B42)
private val DockLine = Color(0x33FFFFFF)

@Composable
private fun DockIsland(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier.fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = DockGlass,
        border = androidx.compose.foundation.BorderStroke(1.dp, DockLine), shadowElevation = 6.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
fun DockNavigationBar(onStart: () -> Unit, onHome: () -> Unit, onBack: () -> Unit, onSearch: () -> Unit, startActive: Boolean = false, startDescription: String = "Dock start") {
    DockIsland {
        DockIconButton(Icons.Default.Menu, startDescription, onStart, tint = if (startActive) DexMint else Color.White)
        DockIconButton(Icons.Default.Circle, "Dock home", onHome)
        DockIconButton(Icons.Default.ArrowBackIosNew, "Dock back", onBack)
        DockIconButton(Icons.Default.Search, "Dock search", onSearch)
    }
}

@Composable
fun DockAppsButton(active: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = if (active) Color(0xFF3B82F6) else DockGlass,
        border = androidx.compose.foundation.BorderStroke(1.dp, DockLine), shadowElevation = 6.dp) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize().semantics { contentDescription = "Dock apps" }) {
            Icon(Icons.Default.Apps, "Dock apps", Modifier.size(26.dp), tint = Color.White)
        }
    }
}

@Composable
fun DockMediaIsland(onNeedsAccess: () -> Unit) {
    val media by DesktopMediaService.state.collectAsStateWithLifecycle()
    DockIsland {
        if (media.artwork != null) Image(media.artwork!!, null, Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)))
        else Box(Modifier.size(38.dp).background(DockGlassDark, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(20.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.Center) {
            Text(if (media.hasSession) media.title else if (media.accessGranted) "No media session" else "Media access off",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (media.hasSession) media.artist.ifBlank { media.packageName } else "Optional notification access",
                fontSize = 9.sp, color = Color(0xFFD4E2E5), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (media.hasSession) {
            DockIconButton(Icons.Default.SkipPrevious, "Previous track", DesktopMediaService::previous, size = 32.dp, iconSize = 17.dp)
            DockIconButton(if (media.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (media.playing) "Pause" else "Play", DesktopMediaService::playPause, size = 34.dp, iconSize = 20.dp)
            DockIconButton(Icons.Default.SkipNext, "Next track", DesktopMediaService::next, size = 32.dp, iconSize = 17.dp)
        } else {
            DockIconButton(if (media.accessGranted) Icons.Default.MusicOff else Icons.Default.Lock, "Media access", onNeedsAccess, size = 34.dp, iconSize = 18.dp)
        }
    }
}

@Composable
fun DockTrayIsland(status: SystemStatus, onNotifications: () -> Unit, onSettings: () -> Unit, onTray: () -> Unit, compact: Boolean = false) {
    val media by DesktopMediaService.state.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    DockIsland {
        Box {
            DockIconButton(Icons.Default.ChatBubbleOutline, "Notifications", onNotifications, size = 38.dp, iconSize = 20.dp)
            if (media.notificationCount > 0) Box(Modifier.align(Alignment.TopEnd).defaultMinSize(15.dp, 15.dp).background(Color(0xFFE53935), CircleShape).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                Text(if (media.notificationCount > 99) "99+" else media.notificationCount.toString(), color = Color.White, fontSize = 8.sp, lineHeight = 9.sp)
            }
        }
        Row(Modifier.clickableSafe(onTray).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (status.charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd, "Battery", Modifier.size(17.dp), tint = Color.White)
            if (!compact && status.battery >= 0) Text("${status.battery}%", fontSize = 10.sp)
        }
        DockIconButton(Icons.Default.Settings, "Dock settings", onSettings, size = 36.dp, iconSize = 19.dp)
        Column(Modifier.clickableSafe(onTray).padding(horizontal = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.time)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (!compact) Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(status.time)), fontSize = 8.sp, color = Color(0xFFD4E2E5), maxLines = 1)
        }
        if (!compact) Icon(if (shizuku.authorized) Icons.Default.Link else Icons.Default.LinkOff, "Shizuku ${shizuku.label}", Modifier.size(15.dp), tint = if (shizuku.authorized) DexMint else Color(0xFFD4E2E5))
    }
}

private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

@Composable
private fun DockIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit,
                           size: androidx.compose.ui.unit.Dp = 42.dp, iconSize: androidx.compose.ui.unit.Dp = 21.dp, tint: Color = Color.White) {
    IconButton(onClick = onClick, modifier = Modifier.size(size).semantics { contentDescription = description }) {
        Icon(icon, description, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun DockPanelContent(vm: DesktopViewModel, panel: DockPanel, status: SystemStatus, onDismiss: () -> Unit,
                     onOpenSettings: () -> Unit, onOpenTasks: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(22.dp), color = DockGlassDark.copy(alpha = .96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DockLine), shadowElevation = 16.dp) {
        when (panel) {
            DockPanel.APPS -> Column(Modifier.fillMaxSize().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 4.dp, bottom = 4.dp)) {
                    Text("Applications", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close applications", Modifier.size(19.dp)) }
                }
                AppLibrary(vm, Modifier.weight(1f), compact = true, onOpened = onDismiss)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Settings", fontSize = 12.sp) }
                    TextButton(onClick = onOpenTasks) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Tasks", fontSize = 12.sp) }
                }
            }
            DockPanel.TASKS -> NativeTasksPanel(vm, onDismiss)
            DockPanel.TRAY -> QuickTray(vm, status, onDismiss, Modifier.fillMaxSize(), onSettings = onOpenSettings)
        }
    }
}

@Composable
private fun NativeTasksPanel(vm: DesktopViewModel, onDismiss: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val minimized by vm.minimizedNativeTasks.collectAsStateWithLifecycle()
    val hidden by vm.hiddenNativeTasks.collectAsStateWithLifecycle()
    val maximized by vm.maximizedNativeTasks.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val allTasks = remember(tasks, minimized) { tasks + minimized.values.filterNot { m -> tasks.any { it.id == m.id } } }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Android windows", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            IconButton(onClick = vm::refreshTasks) { Icon(Icons.Default.Refresh, "Refresh tasks", Modifier.size(18.dp)) }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close tasks", Modifier.size(18.dp)) }
        }
        if (!shizuku.bridgeReady) Text("Authorize Shizuku to list and control real Android tasks.", color = DexMuted, fontSize = 12.sp)
        else if (allTasks.isEmpty()) Text("No running Android tasks were reported.", color = DexMuted, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allTasks, key = { it.id }) { task ->
                Surface(color = Color(0x3320343B), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(vm.appsRepository, task.packageName, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(vm.appLabel(task.packageName), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(when { task.id in hidden -> "Minimized"; task.windowingMode == 1 -> "Fullscreen"; task.freeform -> "Freeform window"; else -> "Android task" }, fontSize = 9.sp, color = DexMuted)
                        }
                        if (task.id in hidden) TextButton(onClick = { vm.restoreNativeTask(task.id) }) { Text("Restore", fontSize = 11.sp) }
                        else TextButton(onClick = { vm.switchTask(task) }) { Text("Focus", fontSize = 11.sp) }
                        if ((task.freeform || task.id in maximized) && task.id !in hidden) IconButton(onClick = { vm.toggleNativeMaximize(task.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(if (task.id in maximized) Icons.Default.FilterNone else Icons.Default.CropSquare, if (task.id in maximized) "Restore native window" else "Maximize native window", Modifier.size(16.dp))
                        }
                        IconButton(onClick = { vm.closeTask(task) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Close native window", Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}

/** Dark title bar drawn over a real Android freeform task; dragging calls the shell resize API. */
@Composable
fun NativeCaptionContent(vm: DesktopViewModel, taskId: Int, screenWidth: Int, screenHeight: Int, onGestureActive: (Boolean) -> Unit = {}) {
    // Overlay windows are owned by a Service lifecycle; collect directly so caption controls keep
    // updating while the underlying Android task changes mode/focus.
    val tasks by vm.tasks.collectAsState()
    val boundsFlow by vm.nativeBounds.collectAsState()
    val hidden by vm.hiddenNativeTasks.collectAsState()
    val maximized by vm.maximizedNativeTasks.collectAsState()
    val task = tasks.firstOrNull { it.id == taskId }
    val bounds = boundsFlow[taskId]
    val density = LocalDensity.current.density
    val latestBounds by rememberUpdatedState(bounds)
    if (task == null || bounds == null || taskId in hidden) return
    Surface(Modifier.fillMaxSize(), color = if (task.focused) Color(0xFF0B0C0E) else Color(0xFF171A1D),
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34373F))) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().pointerInput(taskId, screenWidth, screenHeight) {
                detectDragGestures(
                    onDragStart = { onGestureActive(true) },
                    onDragEnd = { latestBounds?.let { vm.commitNativeBounds(taskId, it) }; onGestureActive(false) },
                    onDragCancel = { latestBounds?.let { vm.commitNativeBounds(taskId, it) }; onGestureActive(false) },
                ) { change, amount ->
                    val current = latestBounds ?: return@detectDragGestures
                    vm.dragNativeTask(taskId, current.moved(amount.x.toInt(), amount.y.toInt(), screenWidth, screenHeight))
                    change.consume()
                }
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(vm.appsRepository, task.packageName, 18.dp)
                Text(vm.appLabel(task.packageName), fontSize = 11.sp, fontWeight = if (task.focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.minimizeNativeTask(taskId) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Minimize, "Minimize native window", Modifier.size(16.dp)) }
            IconButton(onClick = {
                Log.e("DesktopDockService", "Caption maximize/restore clicked task=$taskId maximized=${taskId in maximized}")
                vm.toggleNativeMaximize(taskId)
            }, modifier = Modifier.size(32.dp)) { Icon(if (taskId in maximized) Icons.Default.FilterNone else Icons.Default.CropSquare, if (taskId in maximized) "Restore native window" else "Maximize native window", Modifier.size(15.dp)) }
            IconButton(onClick = { vm.closeNativeTask(taskId) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Close native window", Modifier.size(16.dp)) }
            Box(Modifier.size(22.dp, 30.dp).semantics { contentDescription = "Resize native window" }.pointerInput(taskId, screenWidth, screenHeight) {
                detectDragGestures(onDragEnd = { latestBounds?.let { vm.commitNativeBounds(taskId, it) } }) { change, amount ->
                    val current = latestBounds ?: return@detectDragGestures
                    vm.dragNativeTask(taskId, current.resized(amount.x.toInt(), amount.y.toInt(), screenWidth, screenHeight, (280 * density).toInt(), (210 * density).toInt()))
                    change.consume()
                }
            }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragHandle, null, Modifier.size(15.dp), tint = DexMuted) }
        }
    }
}
