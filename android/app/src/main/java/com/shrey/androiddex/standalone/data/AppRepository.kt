package com.shrey.androiddex.standalone.data

import android.content.Context
import android.content.Intent

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val icons = LruCache<String, ImageBitmap>(48)
    private val iconLock = Mutex()

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName || !activity.exported) return@mapNotNull null
            AppEntry(activity.packageName, activity.name, info.loadLabel(packageManager).toString())
        }.distinctBy { it.component }.sortedBy { it.label.lowercase() }
    }

    suspend fun icon(packageName: String): ImageBitmap? = iconLock.withLock {
        icons.get(packageName)?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            runCatching {
                val drawable = packageManager.getApplicationIcon(packageName)
                val width = drawable.intrinsicWidth.coerceIn(1, 144)
                val height = drawable.intrinsicHeight.coerceIn(1, 144)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap.asImageBitmap().also { icons.put(packageName, it) }
            }.getOrNull()
        }
    }

    fun invalidateIcons() { icons.evictAll() }

    fun launchIntent(entry: AppEntry): Intent? = runCatching {
        val component = android.content.ComponentName.unflattenFromString(entry.component) ?: return null
        if (component.packageName != entry.packageName) return null
        val info = packageManager.getActivityInfo(component, 0)
        if (!info.exported) return null
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    // ActivityInfo.resizeMode is not a public SDK field. Actual task resizability is
    // reported by the authorized shell task bridge; never guess it in the app process.
    fun resizeDescription(entry: AppEntry): String = "Android decides resize behavior at launch"
}
