package com.shrey.androiddex.standalone

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.shrey.androiddex.standalone.data.PreferencesRepository
import com.shrey.androiddex.standalone.system.DesktopAccessibilityService
import com.shrey.androiddex.standalone.system.ShizukuManager
import com.shrey.androiddex.standalone.ui.DesktopScreen
import com.shrey.androiddex.standalone.ui.DesktopViewModel
import com.shrey.androiddex.standalone.ui.DexTheme
import com.shrey.androiddex.standalone.ui.OnboardingScreen

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_SETTINGS = "open_desktop_settings" }
    private lateinit var viewModel: DesktopViewModel
    private var displayId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        displayId = display?.displayId ?: 0
        viewModel = ViewModelProvider(this, DesktopViewModel.Factory(application, displayId))[DesktopViewModel::class.java]
        setContent {
            val preferences by viewModel.settings.collectAsStateWithLifecycle()
            var desktop by remember { mutableStateOf(PreferencesRepository(this, displayId).onboarded) }

            LaunchedEffect(desktop) {
                if (desktop && intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
                    intent.removeExtra(EXTRA_OPEN_SETTINGS)
                    viewModel.openTool(com.shrey.androiddex.standalone.data.WindowKind.SETTINGS)
                }
            }
            LaunchedEffect(desktop, preferences.immersive, preferences.landscape, preferences.keepScreenOn) {
                requestedOrientation = if (desktop && preferences.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (desktop && preferences.immersive) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
                if (desktop && preferences.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            DexTheme {
                if (desktop) DesktopScreen(viewModel, onReviewSetup = { desktop = false })
                else OnboardingScreen(viewModel, onFinish = { desktop = true })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ShizukuManager.acquire()
    }
    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
        DesktopAccessibilityService.refreshEnabled(this)
        com.shrey.androiddex.standalone.system.DesktopDockController.sync(this, displayId)
        com.shrey.androiddex.standalone.system.DesktopMediaService.refreshAccess(this)
    }
    override fun onPause() {
        viewModel.persist()
        super.onPause()
    }
    override fun onStop() {
        ShizukuManager.release()
        super.onStop()
    }
}
