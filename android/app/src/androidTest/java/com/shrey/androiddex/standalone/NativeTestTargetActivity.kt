package com.shrey.androiddex.standalone

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Minimal real activity launched as a freeform task by the instrumentation harness. */
class NativeTestTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Native freeform target"
            textSize = 24f
            gravity = Gravity.CENTER
        })
    }
}
