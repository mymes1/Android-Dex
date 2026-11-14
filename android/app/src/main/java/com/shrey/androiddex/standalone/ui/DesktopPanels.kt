package com.shrey.androiddex.standalone.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.androiddex.standalone.data.WindowKind
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import com.shrey.androiddex.standalone.system.ShizukuManager
import com.shrey.androiddex.standalone.system.SystemActions
import java.text.DateFormat
import java.util.Date

@Composable
fun StartMenu(vm: DesktopViewModel, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accessibility by DesktopAccessibilityService.state.collectAsStateWithLifecycle()
    var power by remember { mutableStateOf(false) }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = DexSurface, tonalElevation = 5.dp, shadowElevation = 14.dp) {
        Column {
            Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, null, tint = DexMint)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text("Your applications", fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text("Installed on this tablet", color = DexMuted, fontSize = 10.sp) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close Start menu", Modifier.size(19.dp)) }
            }
            AppLibrary(vm, Modifier.weight(1f), compact = true, onOpened = onDismiss)
            HorizontalDivider(color = DexBorder)
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.openTool(WindowKind.SETTINGS); onDismiss() }) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Settings", fontSize = 12.sp) }
                TextButton(onClick = { vm.openTool(WindowKind.TASKS); onDismiss() }) { Icon(Icons.Default.SwitchApps, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tasks", fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { power = true }) { Icon(Icons.Default.PowerSettingsNew, "Power and session options") }
                    DropdownMenu(power, { power = false }) {
                        DropdownMenuItem(text = { Text("Power / restart dialog") }, enabled = accessibility.bound,
                            leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) },
                            onClick = { power = false; if (!DesktopAccessibilityService.navigate(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) vm.notify("Android's power dialog is unavailable."); onDismiss() })
                        DropdownMenuItem(text = { Text("Lock screen") }, enabled = accessibility.bound,
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = { power = false; DesktopAccessibilityService.navigate(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); onDismiss() })
                        DropdownMenuItem(text = { Text("Android home") }, leadingIcon = { Icon(Icons.Default.Home, null) },
                            onClick = { power = false; SystemActions.home(context); onDismiss() })
                        DropdownMenuItem(text = { Text("Shizuku recovery guide") }, leadingIcon = { Icon(Icons.Default.HelpOutline, null) },
                            onClick = { power = false; vm.openTool(WindowKind.SETTINGS); onDismiss() })
                    }
                }
            }
            if (!accessibility.bound) Text("Power and lock controls need optional Accessibility permission. Restart is available only if Android's own power dialog offers it.", color = DexMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 9.dp))
        }
    }
}

@Composable
fun QuickTray(vm: DesktopViewModel, status: SystemStatus, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maximum = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = DexSurface, shadowElevation = 14.dp) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.time)), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(status.time)), color = DexMuted, fontSize = 11.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close quick settings", Modifier.size(18.dp)) }
            }
            StatusLine(if (status.battery >= 0) "Battery ${status.battery}%${if (status.charging) " · charging" else ""}" else "Battery status unavailable", status.battery > 20 || status.charging)
            StatusLine(status.network, status.online)
            StatusLine("Shizuku · ${shizuku.label}", shizuku.authorized)
            HorizontalDivider(color = DexBorder)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AssistChip(onClick = { SystemActions.wifi(context) }, label = { Text("Wi-Fi", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Wifi, null, Modifier.size(16.dp)) })
                AssistChip(onClick = { SystemActions.bluetooth(context) }, label = { Text("Bluetooth", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Bluetooth, null, Modifier.size(16.dp)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, null, Modifier.size(20.dp))
                Slider(volume.toFloat(), { value -> volume = value.toInt(); audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) },
                    valueRange = 0f..maximum.toFloat(), steps = (maximum - 1).coerceAtLeast(0), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("$volume", fontSize = 11.sp, color = DexMuted)
            }
            Button(onClick = { vm.openTool(WindowKind.SETTINGS); onDismiss() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Desktop settings") }
            Text("Wi-Fi and Bluetooth open Android controls; Android 15 does not allow this app to silently toggle them.", color = DexMuted, fontSize = 10.sp)
        }
    }
}
