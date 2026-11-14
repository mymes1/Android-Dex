package com.shrey.androiddex.standalone.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.androiddex.standalone.R
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import com.shrey.androiddex.standalone.system.ShizukuManager
import com.shrey.androiddex.standalone.system.SystemActions

@Composable
fun OnboardingScreen(vm: DesktopViewModel, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val accessibility by DesktopAccessibilityService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val titles = listOf("Your tablet. Your desktop.", "Connect advanced features", "Navigate beyond the desktop", "Ready for your workspace")
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(wallpaperGradients[0])).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text("SHREY'S ANDROID DEX", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 14.sp)
                    Text("STANDALONE ANDROID EDITION", color = DexMint, fontSize = 10.sp, letterSpacing = 1.4.sp)
                }
                Text("${step + 1} / 4", color = DexMuted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { Box(Modifier.weight(1f).height(3.dp).background(if (it <= step) DexMint else DexBorder, RoundedCornerShape(2.dp))) }
            }
            Text(titles[step], fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (step) {
                    0 -> {
                        DexCard(Modifier.fillMaxWidth()) {
                            SectionTitle("A real app, running on this tablet", "No computer, remote desktop, emulator, root or Samsung DeX support required.")
                            Text("Launch Android apps, arrange local desktop windows, take notes, browse files you select, and use a mouse, trackpad or keyboard. Optional Shizuku and Accessibility integrations add system navigation and task tools.", color = DexMuted, lineHeight = 22.sp)
                            InfoRow("Detected device", "${Build.MANUFACTURER} ${Build.MODEL}")
                            InfoRow("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                            StatusLine("Android 11+ required for on-device wireless setup", Build.VERSION.SDK_INT >= 30)
                            Text("Targeted at Galaxy Tab A9 SM-X110 / Android 15. Features are probed at runtime; Samsung firmware and hardware limits still apply.", color = DexMuted, fontSize = 13.sp)
                        }
                        DexCard(Modifier.fillMaxWidth()) {
                            SectionTitle("What “windows” means on Android")
                            Text("Notes, Files, Calculator, Settings and other DeX tools have real draggable, resizable windows. Other Android apps open in Android's own fullscreen, split-screen or supported freeform mode. Shizuku cannot turn arbitrary apps into embedded views inside this APK.", color = DexMuted, fontSize = 13.sp)
                            Text("There are no fake minimize/maximize controls for third-party apps. When native freeform is unavailable, use the real Android task switcher.", color = DexMuted, fontSize = 13.sp)
                        }
                    }
                    1 -> ShizukuSetupCard()
                    2 -> AccessibilitySetupCard(vm)
                    3 -> {
                        DexCard(Modifier.fillMaxWidth()) {
                            SectionTitle("Your choices are saved", "Setup will not interrupt every launch. Recheck live status any time in Desktop settings.")
                            StatusLine("Shizuku: ${shizuku.label}", shizuku.authorized, if (shizuku.authorized) "Advanced features connect on demand." else "Optional skipped or not yet connected. Normal desktop tools remain available.")
                            StatusLine("Accessibility: ${if (accessibility.bound) "enabled and bound" else "not enabled"}", accessibility.bound, "Optional foreground detection and navigation.")
                            StatusLine("Overlay permission: not required", true, "The optional navigation pill uses the Accessibility Service's own overlay, not SYSTEM_ALERT_WINDOW.")
                            Text("After a reboot, Shizuku must be started again using Wireless debugging. This app will detect that it stopped and never display a false connected state.", color = DexWarning, fontSize = 13.sp)
                        }
                        DexCard(Modifier.fillMaxWidth()) {
                            SectionTitle("Hardware note for SM-X110")
                            Text("The tablet's USB-C port is USB 2.0 and does not provide MHL/DisplayPort video output. Wired HDMI desktop mode is not a software feature this app can add. Bluetooth/USB keyboards and mice use Android's normal input support.", color = DexMuted, fontSize = 13.sp)
                            TextButton(onClick = { SystemActions.display(context) }) { Text("Open Android display settings") }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (step == 1 || step == 2) TextButton(onClick = { step++ }) { Text("Skip for now") }
                Button(onClick = { if (step < 3) step++ else { vm.preferences.onboarded = true; onFinish() } }) {
                    Text(if (step == 3) "Enter desktop" else "Continue")
                    Spacer(Modifier.width(7.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
                }
            }
        }
    }
}
