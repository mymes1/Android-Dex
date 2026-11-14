package com.shrey.androiddex.standalone.system

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.os.RemoteException
import android.system.Os
import androidx.annotation.Keep
import com.shrey.androiddex.standalone.BuildConfig
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Constructed by the official Shizuku UserService runtime, never an exported Android Service. */
class PrivilegedTaskService : IDesktopTaskBridge.Stub {
    @Keep constructor()
    @Keep constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dex-shell-command").apply { isDaemon = true }
    }

    @Volatile private var currentProcess: java.lang.Process? = null
    // Tokens never cross into the app process; the shell service keeps them for validated task IDs.
    private val taskTokens = ConcurrentHashMap<Int, Any>()

    override fun destroy() {
        currentProcess?.destroyForcibly()
        executor.shutdownNow()
        System.exit(0)
    }

    override fun identity(): Int = Os.getuid()

    override fun tasks(maximum: Int, displayId: Int): List<DesktopTask> {
        requireShell()
        val limit = maximum.coerceIn(1, 50)
        if (displayId < 0) throw RemoteException("Invalid display")
        return try {
            rawTasks(limit, displayId).mapNotNull { value ->
                val task = value ?: return@mapNotNull null
                val top = read(task, "topActivity") as? ComponentName
                val base = read(task, "baseActivity") as? ComponentName
                val component = top ?: base ?: return@mapNotNull null
                val taskDisplay = integer(task, "displayId", 0)
                if (taskDisplay != displayId) return@mapNotNull null
                if (component.packageName == "com.android.systemui") return@mapNotNull null
                val configuration = read(task, "configuration")
                val windowConfiguration = configuration?.let { read(it, "windowConfiguration") }
                val mode = windowConfiguration?.let { read(it, "windowingMode") } as? Int ?: -1
                val id = integer(task, "taskId", -1)
                read(task, "token")?.let { token -> if (id >= 0) taskTokens[id] = token }
                DesktopTask(id, taskDisplay, component.packageName, component.flattenToShortString(),
                    boolean(task, "isRunning"), boolean(task, "isVisible"), boolean(task, "isFocused"),
                    boolean(task, "isResizeable"), mode)
            }.filter { it.taskId >= 0 }
        } catch (e: Exception) {
            throw RemoteException("Task API unavailable on this firmware: ${rootMessage(e)}")
        }
    }

    override fun bringTaskToFront(taskId: Int, displayId: Int): Boolean {
        requireShell()
        if (taskId < 0 || displayId < 0) throw RemoteException("Invalid task or display")
        return try {
            val manager = activityTaskManager()
            val method = manager.javaClass.methods.first { it.name == "moveTaskToFront" && it.parameterCount == 5 }
            // Shell's IActivityTaskManager signature is stable across API 30–35; no app-process hidden API calls.
            method.invoke(manager, null, "com.android.shell", taskId, 0, null as Bundle?)
            true
        } catch (e: Exception) {
            throw RemoteException("Android refused to switch tasks: ${rootMessage(e)}")
        }
    }

    override fun removeTask(taskId: Int): Boolean {
        requireShell()
        if (taskId < 0) throw RemoteException("Invalid task")
        return try {
            val manager = activityTaskManager()
            val method = manager.javaClass.methods.first { it.name == "removeTask" && it.parameterCount == 1 }
            method.invoke(manager, taskId) as? Boolean ?: false
        } catch (e: Exception) {
            throw RemoteException("Android refused to close this task: ${rootMessage(e)}")
        }
    }

    override fun readSetting(key: String): String {
        requireShell()
        validateSetting(key)
        return command(listOf("settings", "get", "global", key)).ifBlank { "null" }
    }

    override fun writeSetting(key: String, value: String): String {
        requireShell()
        validateSetting(key)
        if (value != "0" && value != "1") throw RemoteException("Only 0 and 1 are allowed")
        return command(listOf("settings", "put", "global", key, value)).ifBlank { "Setting updated" }
    }

    override fun launchFreeform(component: String, displayId: Int): String {
        requireShell()
        validateComponent(component)
        if (displayId < 0) throw RemoteException("Invalid display")
        // AOSP ActivityManagerShellCommand spells this option --display (not --displayId).
        val output = command(listOf("am", "start", "-W", "--windowingMode", "5", "--display", displayId.toString(), "-n", component))
        if (output.contains("Error:", true) || output.contains("Exception", true) || output.contains("does not exist", true))
            throw RemoteException(output.take(400))
        return "Android accepted the launch. The firmware may still choose fullscreen."
    }

    override fun launchFreeformBounds(component: String, displayId: Int, left: Int, top: Int, right: Int, bottom: Int): String {
        requireShell()
        validateBounds(left, top, right, bottom)
        val launch = launchFreeform(component, displayId)
        val target = ComponentName.unflattenFromString(component)
        val task = runCatching { tasks(30, displayId) }.getOrNull()?.firstOrNull { it.component == component || it.packageName == target?.packageName }
            ?: return "$launch Bounds were not applied because the new task was not visible yet."
        return runCatching { resizeTask(task.taskId, left, top, right, bottom) }
            .getOrElse { "$launch Bounds were refused: ${rootMessage(Exception(it))}" }
    }

    override fun taskBounds(taskId: Int): IntArray {
        requireShell()
        if (taskId < 0) throw RemoteException("Invalid task")
        return try {
            val method = activityTaskManager().javaClass.methods.first { it.name == "getTaskBounds" && it.parameterCount == 1 }
            val rect = method.invoke(activityTaskManager(), taskId) as? Rect ?: throw NoSuchMethodException("No task bounds")
            intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
        } catch (e: Exception) {
            throw RemoteException("Task bounds are unavailable on this firmware: ${rootMessage(e)}")
        }
    }

    override fun setTaskWindowingMode(taskId: Int, windowingMode: Int): String {
        requireShell()
        if (windowingMode != 1 && windowingMode != 5) throw RemoteException("Only fullscreen and freeform modes are allowed")
        applyTaskChange(taskId) { transaction, token, tokenClass ->
            transaction.javaClass.getMethod("setWindowingMode", tokenClass, Int::class.javaPrimitiveType).invoke(transaction, token, windowingMode)
        }
        return if (windowingMode == 5) "Android accepted the freeform restore." else "Android accepted the fullscreen request."
    }

    override fun setTaskHidden(taskId: Int, hidden: Boolean): Boolean {
        requireShell()
        applyTaskChange(taskId) { transaction, token, tokenClass ->
            transaction.javaClass.getMethod("setHidden", tokenClass, Boolean::class.javaPrimitiveType).invoke(transaction, token, hidden)
        }
        return true
    }

    override fun resizeTask(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): String {
        requireShell()
        validateBounds(left, top, right, bottom)
        val rect = Rect(left, top, right, bottom)
        applyTaskChange(taskId) { transaction, token, tokenClass ->
            transaction.javaClass.getMethod("setBounds", tokenClass, Rect::class.java).invoke(transaction, token, rect)
        }
        return "Android accepted the window bounds."
    }

    override fun back() {
        requireShell()
        command(listOf("input", "keyevent", "KEYCODE_BACK"))
    }

    override fun launchDesktop(component: String, displayId: Int): String {
        requireShell()
        validateComponent(component)
        if (ComponentName.unflattenFromString(component)?.packageName != BuildConfig.APPLICATION_ID)
            throw RemoteException("This fallback can only open DeX itself")
        if (displayId < 0) throw RemoteException("Invalid display")
        return command(listOf("am", "start", "--display", displayId.toString(), "-n", component))
    }

    override fun showRecents() {
        requireShell()
        command(listOf("input", "keyevent", "KEYCODE_APP_SWITCH"))
    }

    private fun rawTasks(limit: Int, displayId: Int): List<*> {
        val manager = activityTaskManager()
        val method = manager.javaClass.methods.firstOrNull { it.name == "getTasks" && it.parameterCount == 4 }
            ?: manager.javaClass.methods.firstOrNull { it.name == "getTasks" && it.parameterCount == 3 }
            ?: throw NoSuchMethodException("No compatible getTasks method")
        val arguments: Array<Any> = if (method.parameterCount == 4) arrayOf(limit, false, false, displayId) else arrayOf(limit, false, false)
        return method.invoke(manager, *arguments) as? List<*> ?: emptyList<Any>()
    }

    /** WindowContainerTransaction is a framework @TestApi; the shell UID holds MANAGE_ACTIVITY_TASKS. */
    private fun applyTaskChange(taskId: Int, change: (Any, Any, Class<*>) -> Unit) {
        if (taskId < 0) throw RemoteException("Invalid task")
        try {
            val token = taskTokens[taskId] ?: run {
                val task = rawTasks(50, 0).firstOrNull { integer(it ?: return@firstOrNull false, "taskId", -1) == taskId }
                    ?: throw RemoteException("Task no longer exists on the default display")
                read(task, "token") ?: throw NoSuchFieldException("Task token is unavailable")
            }
            val tokenClass = Class.forName("android.window.WindowContainerToken")
            val transactionClass = Class.forName("android.window.WindowContainerTransaction")
            val transaction = transactionClass.getConstructor().newInstance()
            change(transaction, token, tokenClass)
            val organizerClass = Class.forName("android.window.WindowOrganizer")
            val organizer = organizerClass.getConstructor().newInstance()
            organizerClass.getMethod("applyTransaction", transactionClass).invoke(organizer, transaction)
        } catch (e: Exception) {
            throw RemoteException("Android refused the window change: ${rootMessage(e)}")
        }
    }

    private fun validateBounds(left: Int, top: Int, right: Int, bottom: Int) {
        if (left < 0 || top < 0 || right <= left || bottom <= top || right - left < 200 || bottom - top < 160 ||
            right > 16384 || bottom > 16384) throw RemoteException("Invalid window bounds")
    }

    private fun requireShell() {
        if (Os.getuid() != 2000) throw RemoteException("This feature requires Shizuku started with Wireless ADB (shell UID 2000)")
    }
    private fun validateSetting(key: String) {
        if (key !in setOf("enable_freeform_support", "force_resizable_activities")) throw RemoteException("Setting is not allowed")
    }
    private fun validateComponent(component: String) {
        if (!component.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))) throw RemoteException("Invalid component")
    }
    private fun activityTaskManager(): Any = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        ?: throw RemoteException("Activity task service is unavailable")
    private fun read(obj: Any, name: String): Any? = runCatching { obj.javaClass.getField(name).get(obj) }.getOrNull()
    private fun integer(obj: Any, name: String, default: Int): Int = read(obj, name) as? Int ?: default
    private fun boolean(obj: Any, name: String): Boolean = read(obj, name) as? Boolean ?: false
    private fun rootMessage(error: Exception): String = (error.cause?.message ?: error.message ?: error.javaClass.simpleName).take(220)

    private fun command(arguments: List<String>): String {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        currentProcess = process
        val future = executor.submit(Callable {
            val output = process.inputStream.bufferedReader().use { it.readText() }.take(8192)
            val exit = process.waitFor()
            if (exit != 0) throw RemoteException(output.ifBlank { "Command exited with $exit" }.take(400))
            output.trim()
        })
        return try {
            future.get(6, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            process.destroyForcibly()
            future.cancel(true)
            throw RemoteException("Android command timed out")
        } catch (e: Exception) {
            process.destroyForcibly()
            throw RemoteException(rootMessage(e))
        } finally {
            if (currentProcess === process) currentProcess = null
        }
    }
}
