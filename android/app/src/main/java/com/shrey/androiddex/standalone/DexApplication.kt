package com.shrey.androiddex.standalone

import android.app.Application
import com.shrey.androiddex.standalone.system.ShizukuManager

class DexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.initialize(this)
    }
}
