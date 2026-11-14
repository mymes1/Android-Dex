package com.shrey.androiddex.standalone.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.InputDevice
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

data class SystemStatus(
    val time: Long = System.currentTimeMillis(), val battery: Int = -1, val charging: Boolean = false,
    val network: String = "Checking network", val online: Boolean = false,
    val keyboard: Boolean = false, val mouse: Boolean = false, val trackpad: Boolean = false
)

/** All callbacks and the minute clock are scoped to the visible composition. No background service. */
@Composable
fun rememberSystemStatus(): SystemStatus {
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf(SystemStatus()) }
    DisposableEffect(context) {
        fun battery(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            status = status.copy(battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1, charging = charging)
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { battery(i) } }
        val batteryIntent = if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        battery(batteryIntent)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        var defaultNetwork: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { defaultNetwork = network; status = status.copy(network = "Connected", online = false) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                defaultNetwork = network
                val transport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                    else -> "Network"
                }
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                status = status.copy(network = if (validated) "$transport · online" else "$transport · no internet", online = validated)
            }
            override fun onLost(network: Network) {
                if (defaultNetwork == network) { defaultNetwork = null; status = status.copy(network = "Offline", online = false) }
            }
        }
        val inputs = context.getSystemService(InputManager::class.java)
        fun refreshInputs() {
            val ids: IntArray = inputs?.getInputDeviceIds() ?: IntArray(0)
            val devices = ids.toList().mapNotNull { id -> InputDevice.getDevice(id) }.filter { device -> !device.isVirtual }
            status = status.copy(keyboard = devices.any { it.supportsSource(InputDevice.SOURCE_KEYBOARD) },
                mouse = devices.any { it.supportsSource(InputDevice.SOURCE_MOUSE) },
                trackpad = devices.any { it.supportsSource(InputDevice.SOURCE_TOUCHPAD) })
        }
        val inputListener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { refreshInputs() }
            override fun onInputDeviceRemoved(deviceId: Int) { refreshInputs() }
            override fun onInputDeviceChanged(deviceId: Int) { refreshInputs() }
        }
        refreshInputs()
        inputs?.registerInputDeviceListener(inputListener, null)
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { connectivity?.unregisterNetworkCallback(callback) }
            inputs?.unregisterInputDeviceListener(inputListener)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            status = status.copy(time = now)
            delay(60000L - now % 60000L)
        }
    }
    return status
}
