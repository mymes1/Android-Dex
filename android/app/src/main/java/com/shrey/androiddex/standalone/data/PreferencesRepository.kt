package com.shrey.androiddex.standalone.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(context: Context, val displayId: Int) {
    private val global = context.applicationContext.getSharedPreferences("dex_preferences", Context.MODE_PRIVATE)
    private val workspace = context.applicationContext.getSharedPreferences("dex_workspace_$displayId", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = global.getBoolean("onboarded", false)
        set(value) { global.edit().putBoolean("onboarded", value).apply() }
    var wallpaper: Int
        get() = global.getInt("wallpaper", 0).coerceIn(-1, 3)
        set(value) { global.edit().putInt("wallpaper", value.coerceIn(-1, 3)).apply() }
    var wallpaperRevision: Int
        get() = global.getInt("wallpaper_revision", 0)
        set(value) { global.edit().putInt("wallpaper_revision", value).apply() }
    var floatingNavigation: Boolean
        get() = global.getBoolean("floating_navigation", false)
        set(value) { global.edit().putBoolean("floating_navigation", value).apply() }
    var immersive: Boolean
        get() = global.getBoolean("immersive", true)
        set(value) { global.edit().putBoolean("immersive", value).apply() }
    var keepScreenOn: Boolean
        get() = global.getBoolean("keep_screen_on", false)
        set(value) { global.edit().putBoolean("keep_screen_on", value).apply() }
    var landscape: Boolean
        get() = global.getBoolean("landscape", true)
        set(value) { global.edit().putBoolean("landscape", value).apply() }
    /** User-opted persistent dock. Never enabled silently; SYSTEM_ALERT_WINDOW is still checked live. */
    var overlayDock: Boolean
        get() = global.getBoolean("overlay_dock", false)
        set(value) { global.edit().putBoolean("overlay_dock", value).apply() }

    fun pinnedPackages(): Set<String> = global.getStringSet("pinned_packages", emptySet())!!.toSet()
    fun setPinned(packages: Set<String>) { global.edit().putStringSet("pinned_packages", packages).apply() }
    fun togglePinned(packageName: String): Set<String> = pinnedPackages().let { old ->
        (if (packageName in old) old - packageName else old + packageName).also { setPinned(it) }
    }
    fun recentApps(): List<RecentApp> = runCatching {
        val array = JSONArray(global.getString("recent_apps", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val pkg = obj.optString("package")
            if (pkg.isBlank()) null else RecentApp(pkg, obj.optLong("time"))
        }.sortedByDescending { it.openedAt }.take(12)
    }.getOrDefault(emptyList())

    fun recordLaunch(packageName: String) {
        val items = (listOf(RecentApp(packageName, System.currentTimeMillis())) + recentApps().filter { it.packageName != packageName }).take(12)
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("package", it.packageName).put("time", it.openedAt)) }
        global.edit().putString("recent_apps", array.toString()).apply()
    }
    fun clearRecents() { global.edit().remove("recent_apps").apply() }

    fun iconPosition(key: String): Pair<Float, Float>? {
        val parts = workspace.getString("icon_$key", null)?.split(",") ?: return null
        val x = parts.getOrNull(0)?.toFloatOrNull() ?: return null
        val y = parts.getOrNull(1)?.toFloatOrNull() ?: return null
        return if (x.isFinite() && y.isFinite()) x to y else null
    }
    fun setIconPosition(key: String, x: Float, y: Float) { workspace.edit().putString("icon_$key", "$x,$y").apply() }
    fun resetIcons() {
        val editor = workspace.edit()
        workspace.all.keys.filter { it.startsWith("icon_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun notes(): List<Note> = global.getStringSet("note_ids", emptySet())!!.mapNotNull { value ->
        val id = value ?: return@mapNotNull null
        if (!global.contains("note_$id")) null else Note(id, global.getString("note_$id", "") ?: "", global.getLong("note_time_$id", 0))
    }.sortedByDescending { it.updatedAt }
    fun note(id: String): String = global.getString("note_$id", "") ?: ""
    fun createNote(): String {
        val id = java.util.UUID.randomUUID().toString()
        saveNote(id, "")
        return id
    }
    fun saveNote(id: String, text: String) {
        global.edit().putStringSet("note_ids", global.getStringSet("note_ids", emptySet())!! + id)
            .putString("note_$id", text).putLong("note_time_$id", System.currentTimeMillis()).apply()
    }
    fun deleteNote(id: String) {
        global.edit().putStringSet("note_ids", global.getStringSet("note_ids", emptySet())!! - id)
            .remove("note_$id").remove("note_time_$id").apply()
    }

    fun saveWindows(windows: List<DesktopWindow>) {
        val array = JSONArray()
        windows.take(12).forEach { w ->
            array.put(JSONObject().put("id", w.id).put("kind", w.kind.name).put("title", w.title)
                .put("x", w.x.toDouble()).put("y", w.y.toDouble()).put("w", w.width.toDouble()).put("h", w.height.toDouble())
                .put("min", w.minimized).put("max", w.maximized).put("note", w.noteId ?: ""))
        }
        workspace.edit().putString("windows", array.toString()).apply()
    }
    fun loadWindows(): List<DesktopWindow> = runCatching {
        val array = JSONArray(workspace.getString("windows", "[]"))
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val kind = WindowKind.valueOf(o.getString("kind"))
                val noteId = o.optString("note").takeIf { it.isNotBlank() }
                if (kind == WindowKind.NOTES && noteId == null) return@runCatching null
                DesktopWindow(o.getString("id"), kind, o.optString("title", kind.defaultTitle()),
                    o.optDouble("x", 180.0).toFloat().takeIf { it.isFinite() } ?: 180f,
                    o.optDouble("y", 36.0).toFloat().takeIf { it.isFinite() } ?: 36f,
                    o.optDouble("w", 540.0).toFloat().takeIf { it.isFinite() && it > 0 } ?: 540f,
                    o.optDouble("h", 370.0).toFloat().takeIf { it.isFinite() && it > 0 } ?: 370f,
                    o.optBoolean("min"), o.optBoolean("max"), noteId)
            }.getOrNull()
        }.distinctBy { it.id }.take(12)
    }.getOrDefault(emptyList())

    fun savedFolder(): String? = workspace.getString("folder_uri", null)
    fun saveFolder(uri: String?) { workspace.edit().putString("folder_uri", uri).apply() }
}
