package com.shrey.androiddex.standalone.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import com.shrey.androiddex.standalone.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.androiddex.standalone.data.*
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import com.shrey.androiddex.standalone.system.ShizukuManager
import com.shrey.androiddex.standalone.system.SystemActions
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

private data class Shortcut(val key: String, val title: String, val kind: WindowKind? = null, val app: AppEntry? = null)
private data class MenuTarget(val x: Float, val y: Float, val shortcut: Shortcut?)

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DesktopScreen(vm: DesktopViewModel, onReviewSetup: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val windows by vm.windows.collectAsStateWithLifecycle()
    val focused by vm.focused.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val iconRevision by vm.iconRevision.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val accessibility by DesktopAccessibilityService.state.collectAsStateWithLifecycle()
    val tasksKnown by vm.tasksKnown.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val status = rememberSystemStatus()
    var start by remember { mutableStateOf(false) }
    var tray by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val rootFocus = remember { FocusRequester() }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(settings.wallpaper, settings.wallpaperRevision) {
        wallpaper = if (settings.wallpaper == -1) WallpaperStore.load(context)?.asImageBitmap() else null
    }
    DisposableEffect(wallpaper) {
        val disposableBitmap = wallpaper?.asAndroidBitmap()
        onDispose { disposableBitmap?.recycle() }
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(shizuku.bridgeReady) { vm.probeCapabilities(); vm.refreshTasks() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(start, tray, menu) {
        if (!start && !tray && menu == null) rootFocus.requestFocus()
    }
    DisposableEffect(context, vm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { vm.appsRepository.invalidateIcons(); vm.refreshApps() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    BackHandler(enabled = start || tray || menu != null) { start = false; tray = false; menu = null }
    val shortcuts = remember(apps, pins) {
        listOf(Shortcut("notes", "Notes", WindowKind.NOTES), Shortcut("files", "Files", WindowKind.FILES),
            Shortcut("calculator", "Calculator", WindowKind.CALCULATOR), Shortcut("apps", "Applications", WindowKind.APPS),
            Shortcut("settings", "Settings", WindowKind.SETTINGS)) + pins.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            .map { Shortcut("app_${it.packageName}", it.label, app = it) }
    }
    Box(Modifier.fillMaxSize().background(DexBackground)) {
        when {
            wallpaper != null -> Image(wallpaper!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            settings.wallpaper == 0 -> Image(painterResource(R.drawable.dex_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(wallpaperGradients[settings.wallpaper.coerceIn(0, 3)])))
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when {
                    event.isAltPressed && event.key == Key.Tab -> {
                        if (!vm.cycleWindow() && !vm.switchNextNativeTask()) vm.showRecents(); true
                    }
                    event.key == Key.MetaLeft || event.key == Key.MetaRight -> { start = !start; tray = false; menu = null; true }
                    event.key == Key.Escape && (start || tray || menu != null) -> { start = false; tray = false; menu = null; true }
                    event.isCtrlPressed && event.key == Key.W -> {
                        if (!vm.closeActive()) vm.notify("Ctrl+W closes a focused DeX tool window. Use Android tasks to close other apps explicitly."); true
                    }
                    else -> false
                }
            }.focusRequester(rootFocus).focusable()) {
            val areaWidth = maxWidth.value
            val areaHeight = (maxHeight - 72.dp).value.coerceAtLeast(1f)
            LaunchedEffect(areaWidth, areaHeight) { vm.fitWorkspace(areaWidth, areaHeight) }
            Box(Modifier.fillMaxSize().padding(bottom = 72.dp).pointerInput(Unit) {
                detectTapGestures(onLongPress = { position -> menu = MenuTarget(position.x / density, position.y / density, null) })
            }.rightClick { position -> menu = MenuTarget(position.x / density, position.y / density, null) }) {
                val columns = maxOf(1, minOf(4, (areaWidth / 112f).toInt()))
                shortcuts.forEachIndexed { index, shortcut ->
                    key(shortcut.key, iconRevision) {
                        val initial = vm.preferences.iconPosition(shortcut.key) ?: ((index % columns) * 112f + 24f to (index / columns) * 112f + 210f)
                        var position by remember(shortcut.key, iconRevision) { mutableStateOf(Offset(initial.first.coerceIn(0f, (areaWidth - 90).coerceAtLeast(0f)), initial.second.coerceIn(0f, (areaHeight - 100).coerceAtLeast(0f)))) }
                        Column(Modifier.offset { androidx.compose.ui.unit.IntOffset((position.x * density).toInt(), (position.y * density).toInt()) }.width(90.dp).height(100.dp)
                            .clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {
                                if (shortcut.kind != null) vm.openTool(shortcut.kind) else shortcut.app?.let(vm::launchApp)
                            }, onLongClick = { menu = MenuTarget(position.x + 45, position.y + 45, shortcut) })
                            .rightClick { p -> menu = MenuTarget(position.x + p.x / density, position.y + p.y / density, shortcut) }
                            .pointerInput(shortcut.key, areaWidth, areaHeight) {
                                detectDragGestures(onDragEnd = vm::persist, onDragCancel = vm::persist) { change, amount ->
                                    position = Offset((position.x + amount.x / density).coerceIn(0f, (areaWidth - 90).coerceAtLeast(0f)),
                                        (position.y + amount.y / density).coerceIn(0f, (areaHeight - 100).coerceAtLeast(0f)))
                                    vm.moveIcon(shortcut.key, position.x, position.y)
                                    change.consume()
                                }
                            }.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (shortcut.app != null) AppIcon(vm.appsRepository, shortcut.app.packageName, 45.dp)
                            else Box(Modifier.size(45.dp).background(DexSurface.copy(alpha = .9f), RoundedCornerShape(13.dp)).border(1.dp, DexBorder, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                Icon(toolIcon(shortcut.kind!!), null, Modifier.size(25.dp), tint = if (shortcut.kind == WindowKind.SETTINGS) DexMuted else DexMint)
                            }
                            Text(shortcut.title, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, minLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp).width(minOf(560.dp, (areaWidth * 0.46f).dp)), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DesktopSearchBar("Search Google", perplexity = false)
                    DesktopSearchBar("Ask Perplexity AI", perplexity = true)
                }
                AnalogClock(status.time, Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 28.dp).size(minOf(190.dp, (areaHeight * 0.32f).dp)))
                Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), shape = RoundedCornerShape(20.dp), color = Color(0xB3233A40),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))) {
                    Row(Modifier.clickable { SystemActions.url(context, "https://github.com/shrey113") }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Made with", fontSize = 11.sp, color = Color.White)
                        Icon(Icons.Default.Favorite, null, Modifier.size(12.dp), tint = Color(0xFFFF5A5F))
                        Text("by", fontSize = 11.sp, color = Color.White)
                        Icon(Icons.Default.Code, null, Modifier.size(14.dp), tint = Color.White)
                        Text("shrey113", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
                if (accessibility.lastExternalPackage != null) {
                    AssistChip(onClick = { vm.openTool(WindowKind.TASKS) }, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        label = { Text("Last outside app: ${vm.appLabel(accessibility.lastExternalPackage!!)}", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Accessibility, null, Modifier.size(14.dp)) })
                }
                windows.forEach { window ->
                    key(window.id) {
                        if (!window.minimized) DesktopWindowView(vm, window, window.id == focused, areaWidth, areaHeight, status, onReviewSetup)
                    }
                }
            }
            if (start || tray) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { start = false; tray = false })
            Taskbar(vm, status, windows, focused, apps, pins, tasksKnown, tasks, shizuku.authorized, maxWidth.value, start,
                onStart = { start = !start; tray = false; menu = null }, onTray = { tray = !tray; start = false; menu = null }, modifier = Modifier.align(Alignment.BottomCenter))
            if (start) StartMenu(vm, { start = false }, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 80.dp)
                .width(minOf(maxWidth - 16.dp, 440.dp)).height(minOf((areaHeight - 12).coerceAtLeast(1f).dp, 520.dp)))
            if (tray) QuickTray(vm, status, { tray = false }, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 80.dp)
                .width(minOf(maxWidth - 16.dp, 340.dp)).heightIn(max = (areaHeight - 12).coerceAtLeast(1f).dp))
            menu?.let { target ->
                Box(Modifier.offset(target.x.dp.coerceIn(0.dp, maxWidth - 2.dp), target.y.dp.coerceIn(0.dp, maxHeight - 2.dp)).size(1.dp)) {
                    DropdownMenu(expanded = true, onDismissRequest = { menu = null }) {
                        val shortcut = target.shortcut
                        if (shortcut != null) {
                            DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) }, onClick = {
                                if (shortcut.kind != null) vm.openTool(shortcut.kind) else shortcut.app?.let(vm::launchApp); menu = null
                            })
                            if (shortcut.app != null) {
                                if (caps.canOfferFreeform && shizuku.bridgeReady) {
                                    DropdownMenuItem(text = { Text("Open in native window") }, onClick = { vm.launchApp(shortcut.app, true); menu = null })
                                    DropdownMenuItem(text = { Text("Open fullscreen") }, onClick = { vm.launchApp(shortcut.app, false); menu = null })
                                }
                                DropdownMenuItem(text = { Text(if (shortcut.app.packageName in pins) "Unpin from desktop" else "Pin to desktop") }, onClick = { vm.togglePin(shortcut.app.packageName); menu = null })
                                DropdownMenuItem(text = { Text(vm.appsRepository.resizeDescription(shortcut.app)) }, enabled = false, onClick = {})
                                DropdownMenuItem(text = { Text("Android app info") }, onClick = { SystemActions.appInfo(context, shortcut.app.packageName); menu = null })
                            }
                        } else {
                            DropdownMenuItem(text = { Text("New note") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) }, onClick = { vm.openNote(null); menu = null })
                            DropdownMenuItem(text = { Text("Wallpaper & desktop settings") }, leadingIcon = { Icon(Icons.Default.Wallpaper, null) }, onClick = { vm.openTool(WindowKind.SETTINGS); menu = null })
                            DropdownMenuItem(text = { Text("Arrange icons") }, leadingIcon = { Icon(Icons.Default.Grid4x4, null) }, onClick = { vm.resetIcons(); menu = null })
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp, start = 16.dp, end = 16.dp))
        }
    }
}

@Composable
private fun DesktopSearchBar(placeholder: String, perplexity: Boolean) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    fun submit() { SystemActions.webSearch(context, query, perplexity); query = "" }
    TextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().height(48.dp),
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = { Icon(if (perplexity) Icons.Default.AutoAwesome else Icons.Default.Search, null, Modifier.size(19.dp), tint = if (perplexity) Color(0xFF203238) else Color(0xFF4285F4)) },
        trailingIcon = { IconButton(onClick = { if (!SystemActions.voiceSearch(context)) submit() }, modifier = Modifier.size(36.dp)) { Icon(if (perplexity) Icons.Default.AutoAwesome else Icons.Default.Mic, "Voice search", Modifier.size(17.dp), tint = if (perplexity) Color(0xFF18A999) else Color(0xFF4285F4)) } },
        singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xB3C3CDD3), unfocusedContainerColor = Color(0xB3C3CDD3),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color(0xFF17242A), unfocusedTextColor = Color(0xFF17242A),
            focusedPlaceholderColor = Color(0xFF4A5E66), unfocusedPlaceholderColor = Color(0xFF4A5E66), cursorColor = Color(0xFF17242A)))
}

@Composable
private fun AnalogClock(time: Long, modifier: Modifier = Modifier) {
    val calendar = remember(time) { java.util.Calendar.getInstance().apply { timeInMillis = time } }
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF101418), radius, center)
        drawCircle(Color(0xFF2A343A), radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        for (i in 0 until 60) {
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val major = i % 5 == 0
            val outer = radius * 0.90f
            val inner = radius * if (major) 0.76f else 0.84f
            drawLine(if (major) Color.White else Color(0xFF8A969C),
                center + androidx.compose.ui.geometry.Offset(cos(angle).toFloat() * inner, sin(angle).toFloat() * inner),
                center + androidx.compose.ui.geometry.Offset(cos(angle).toFloat() * outer, sin(angle).toFloat() * outer),
                strokeWidth = if (major) 3f else 1.2f)
        }
        val second = calendar.get(java.util.Calendar.SECOND)
        val minute = calendar.get(java.util.Calendar.MINUTE) + second / 60f
        val hour = calendar.get(java.util.Calendar.HOUR) % 12 + minute / 60f
        fun hand(degrees: Float, length: Float, color: Color, width: Float) {
            val angle = Math.toRadians(degrees.toDouble() - 90.0)
            drawLine(color, center, center + androidx.compose.ui.geometry.Offset(cos(angle).toFloat() * radius * length, sin(angle).toFloat() * radius * length), strokeWidth = width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        hand(hour * 30f, 0.45f, Color.White, 5f)
        hand(minute * 6f, 0.66f, Color.White, 3.5f)
        hand(second * 6f, 0.74f, Color(0xFF4C8DFF), 1.6f)
        drawCircle(Color(0xFF4C8DFF), radius * 0.035f, center)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.rightClick(onClick: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.pressed && !it.previousPressed }
        if (change != null && change.type == PointerType.Mouse && event.buttons.isSecondaryPressed) {
            onClick(change.position)
            change.consume()
        }
    }
}

@Composable
private fun DesktopWindowView(vm: DesktopViewModel, window: DesktopWindow, focused: Boolean, areaWidth: Float, areaHeight: Float,
                              status: SystemStatus, onReviewSetup: () -> Unit) {
    val density = LocalDensity.current.density
    val bounds = if (window.maximized) Bounds(0f, 0f, areaWidth, areaHeight) else WindowGeometry.clamp(window.x, window.y, window.width, window.height, areaWidth, areaHeight)
    Surface(Modifier.offset(bounds.x.dp, bounds.y.dp).size(bounds.width.dp, bounds.height.dp)
        .pointerInput(window.id) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial); vm.focus(window.id) } },
        shape = RoundedCornerShape(if (window.maximized) 0.dp else 13.dp), color = DexSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (focused) DexMint.copy(alpha = .65f) else DexBorder),
        shadowElevation = if (focused) 10.dp else 4.dp) {
        Column {
            Row(Modifier.fillMaxWidth().height(42.dp).background(if (focused) DexRaised else DexSurface).padding(start = 11.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).fillMaxHeight().pointerInput(window.id, areaWidth, areaHeight) {
                    detectDragGestures(onDragEnd = vm::persist, onDragCancel = vm::persist) { change, amount ->
                        vm.moveWindow(window.id, amount.x / density, amount.y / density, areaWidth, areaHeight); change.consume()
                    }
                }.pointerInput(window.id) { detectTapGestures(onDoubleTap = { vm.maximize(window.id) }) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(toolIcon(window.kind), null, Modifier.size(16.dp), tint = if (focused) DexMint else DexMuted)
                    Spacer(Modifier.width(8.dp))
                    Text(window.title, fontSize = 12.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.minimize(window.id) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Minimize, "Minimize ${window.title}", Modifier.size(18.dp)) }
                IconButton(onClick = { vm.maximize(window.id) }, modifier = Modifier.size(34.dp)) { Icon(if (window.maximized) Icons.Default.FilterNone else Icons.Default.CropSquare, if (window.maximized) "Restore ${window.title}" else "Maximize ${window.title}", Modifier.size(16.dp)) }
                IconButton(onClick = { vm.closeWindow(window.id) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Close ${window.title}", Modifier.size(17.dp)) }
            }
            HorizontalDivider(color = DexBorder)
            Box(Modifier.weight(1f)) { ToolContent(vm, window, status, onReviewSetup) }
            if (!window.maximized) Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(28.dp, 12.dp).semantics { contentDescription = "Resize ${window.title}" }.pointerInput(window.id, areaWidth, areaHeight) {
                    detectDragGestures(onDragEnd = vm::persist, onDragCancel = vm::persist) { change, amount ->
                        vm.resizeWindow(window.id, amount.x / density, amount.y / density, areaWidth, areaHeight); change.consume()
                    }
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragHandle, null, Modifier.size(17.dp), tint = DexMuted) }
            }
        }
    }
}

@Composable
private fun Taskbar(vm: DesktopViewModel, status: SystemStatus, windows: List<DesktopWindow>, focused: String?, apps: List<AppEntry>, pins: Set<String>,
                    tasksKnown: Boolean, tasks: List<NativeTask>, shizuku: Boolean, width: Float, startOpen: Boolean,
                    onStart: () -> Unit, onTray: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val compact = width < 640
    val trayWidth = if (compact) 218 else 300
    val glass = Color(0xE6415A62)
    Box(modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(184.dp).height(56.dp)) {
                    DockNavigationBar(onStart = onStart, onHome = { SystemActions.desktop(context, vm.displayId) }, onBack = vm::dockBack,
                        onSearch = onStart, startActive = startOpen, startDescription = "Start menu")
                }
                if (width >= 900) Box(Modifier.width(260.dp).height(56.dp)) { DockMediaIsland { SystemActions.notificationAccess(context) } }
            }
            Row(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = glass, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)), shadowElevation = 6.dp) {
                    Row(Modifier.height(56.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(44.dp)) { DockAppsButton(startOpen, onStart) }
                        pins.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }.forEach { app ->
                            Box {
                                IconButton(onClick = { vm.launchApp(app) }, modifier = Modifier.size(42.dp)) { AppIcon(vm.appsRepository, app.packageName, 30.dp) }
                                if (tasksKnown && tasks.any { it.packageName == app.packageName && it.running }) Box(Modifier.size(5.dp).align(Alignment.BottomCenter).background(DexMint, CircleShape))
                            }
                        }
                        windows.forEach { window ->
                            val active = window.id == focused && !window.minimized
                            Button(onClick = { if (active) vm.minimize(window.id) else vm.focus(window.id) }, modifier = Modifier.height(44.dp).widthIn(max = if (width > 700) 150.dp else 50.dp).semantics { contentDescription = "Taskbar ${window.title}" },
                                contentPadding = PaddingValues(horizontal = 9.dp), shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0x55304952) else Color.Transparent, contentColor = Color.White)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(toolIcon(window.kind), null, Modifier.size(18.dp))
                                        if (width > 700) Text(window.title.removePrefix("Notes · "), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Box(Modifier.padding(top = 3.dp).width(if (active) 16.dp else 5.dp).height(2.dp).background(if (window.minimized) Color(0xFFB8C7CC) else DexMint, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.width(trayWidth.dp).height(56.dp)) {
                DockTrayIsland(status, compact = compact,
                    onNotifications = { if (!DesktopAccessibilityService.navigate(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) SystemActions.notificationAccess(context) },
                    onSettings = { vm.openTool(WindowKind.SETTINGS) }, onTray = onTray)
            }
        }
    }
}

private fun toolIcon(kind: WindowKind): ImageVector = when (kind) {
    WindowKind.NOTES -> Icons.Default.StickyNote2
    WindowKind.FILES -> Icons.Default.Folder
    WindowKind.CALCULATOR -> Icons.Default.Calculate
    WindowKind.SETTINGS -> Icons.Default.Settings
    WindowKind.APPS -> Icons.Default.Apps
    WindowKind.TASKS -> Icons.Default.SwapHoriz
    WindowKind.DEVICE -> Icons.Default.TabletAndroid
}
