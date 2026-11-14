package com.shrey.androiddex.standalone.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SystemActions {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_DOWNLOAD = "https://github.com/RikkaApps/Shizuku/releases/latest"

    fun open(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun accessibility(context: Context): Boolean = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun overlayPermission(context: Context): Boolean =
        open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) ||
            open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    fun notificationAccess(context: Context): Boolean = open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun webSearch(context: Context, query: String, perplexity: Boolean = false): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return if (perplexity) url(context, "https://www.perplexity.ai/") else url(context, "https://www.google.com/")
        val encoded = Uri.encode(trimmed)
        val address = if (perplexity) "https://www.perplexity.ai/search?q=$encoded" else "https://www.google.com/search?q=$encoded"
        return open(context, Intent(Intent.ACTION_VIEW, Uri.parse(address))) ||
            open(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, trimmed))
    }
    fun voiceSearch(context: Context): Boolean = open(context, Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH))
    fun developerOptions(context: Context): Boolean = open(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
        open(context, Intent(Settings.ACTION_SETTINGS))
    fun deviceInfo(context: Context): Boolean = open(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) ||
        open(context, Intent(Settings.ACTION_SETTINGS))
    fun display(context: Context): Boolean = open(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun wifi(context: Context): Boolean = open(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) ||
        open(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    fun bluetooth(context: Context): Boolean = open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun appInfo(context: Context, packageName: String): Boolean = open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    fun url(context: Context, url: String): Boolean = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun shizuku(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return if (launch != null) open(context, launch) else url(context, SHIZUKU_DOWNLOAD)
    }
    fun home(context: Context): Boolean = open(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun desktop(context: Context, displayId: Int? = null): Boolean {
        val intent = Intent(context, com.shrey.androiddex.standalone.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (displayId != null && displayId != 0) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = displayId?.let { android.app.ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
        return runCatching { context.startActivity(intent, options); true }.getOrDefault(false)
    }
    fun desktopSettings(context: Context, displayId: Int = 0): Boolean {
        val intent = Intent(context, com.shrey.androiddex.standalone.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(com.shrey.androiddex.standalone.MainActivity.EXTRA_OPEN_SETTINGS, true)
        if (displayId != 0) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = if (displayId != 0) android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle() else null
        return runCatching { context.startActivity(intent, options); true }.getOrDefault(false)
    }
}
