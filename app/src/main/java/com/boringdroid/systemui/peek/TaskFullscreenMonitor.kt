package com.boringdroid.systemui.peek

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.window.WindowContainerToken
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.boringdroid.systemui.theme.ThemedIconLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-task snapshot exposed to the peek-caption UI. Non-null only when the foreground task is a
 * freeform window that has been maximised to fullscreen, the case the peek caption is built for —
 * apps launched directly into fullscreen are left alone. The [token] lets the controller drive
 * the task back to freeform / minimise / close via [android.window.WindowContainerTransaction].
 */
data class PeekTarget(
    val taskId: Int,
    val token: WindowContainerToken,
    val component: ComponentName?,
    val icon: Drawable?,
    val label: CharSequence?,
    val currentMode: Int,
    val currentBounds: Rect,
    val displayMode: Int,
)

/**
 * Watches the running-task stack and tells the rest of the peek-caption stack when there's a
 * maximise-from-freeform task in front. Uses [TaskStackChangeListeners] (the shared SystemUI
 * binder helper) and remembers each task's previous windowing mode so we can distinguish
 * "freeform → fullscreen" (a maximise the user just performed) from "fullscreen from launch".
 *
 * Only the legacy WMShell caption maximize is interesting — its `TaskOperations.maximizeTask`
 * toggles `WINDOWING_MODE_FREEFORM ↔ WINDOWING_MODE_FULLSCREEN`, leaving no caption on screen.
 * Modern desktop-mode caption stays in freeform and keeps its own in-window caption visible, so
 * peek would just double up. The decor-variant gate lives in [PeekCaptionController].
 */
class TaskFullscreenMonitor(
    private val pluginContext: Context,
    private val themedIconLoader: ThemedIconLoader? = null,
) {
    private val activityManager =
        pluginContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val launcherApps =
        pluginContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = pluginContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val listeners = TaskStackChangeListeners.getInstance()
    private val listener = MonitorListener()

    private val previousMode = HashMap<Int, Int>()
    private val maximizedFromFreeform = HashSet<Int>()

    private val _peekTarget = MutableStateFlow<PeekTarget?>(null)
    val peekTarget: StateFlow<PeekTarget?> = _peekTarget.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        listeners.registerTaskStackListener(listener)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        listeners.unregisterTaskStackListener(listener)
        previousMode.clear()
        maximizedFromFreeform.clear()
        _peekTarget.value = null
    }

    /**
     * Re-poll the running-task stack and recompute [peekTarget]. Exposed so [TaskActions]'
     * post-WCT hook can force a refresh after an in-place windowing-mode flip — the WCT path
     * bypasses WMShell's shell-transition wrapper, so `TaskStackChangeListener.onTaskStackChanged`
     * does NOT fire. Without this call, a peek-driven Restore would leave the hover edge armed
     * against a now-freeform task.
     */
    fun refresh() {
        val running = activityManager.getRunningTasks(MAX_TASKS)
        val seenIds = HashSet<Int>(running.size)
        for (info in running) {
            val id = info.taskId
            seenIds.add(id)
            val prev = previousMode[id]
            val current = info.configuration.windowConfiguration.windowingMode
            if (prev == WindowConfiguration.WINDOWING_MODE_FREEFORM &&
                current == WindowConfiguration.WINDOWING_MODE_FULLSCREEN
            ) {
                maximizedFromFreeform.add(id)
            } else if (current == WindowConfiguration.WINDOWING_MODE_FREEFORM) {
                maximizedFromFreeform.remove(id)
            }
            previousMode[id] = current
        }
        previousMode.keys.retainAll(seenIds)
        maximizedFromFreeform.retainAll(seenIds)
        val top = running.firstOrNull()
        _peekTarget.value =
            if (top != null &&
                top.taskId in maximizedFromFreeform &&
                top.configuration.windowConfiguration.windowingMode ==
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN
            ) {
                buildPeekTarget(top)
            } else {
                null
            }
    }

    private fun buildPeekTarget(info: ActivityManager.RunningTaskInfo): PeekTarget? {
        val token = info.token ?: return null
        val pkg = info.baseActivity?.packageName
        val icon = if (pkg != null) resolveIcon(pkg) else null
        val label = if (pkg != null) resolveLabel(pkg) else null
        return PeekTarget(
            taskId = info.taskId,
            token = token,
            component = info.topActivity,
            icon = icon,
            label = label,
            currentMode = info.configuration.windowConfiguration.windowingMode,
            currentBounds = Rect(info.configuration.windowConfiguration.bounds),
            displayMode = info.configuration.windowConfiguration.displayWindowingMode,
        )
    }

    private fun resolveIcon(pkg: String): Drawable? {
        for (user in userManager.userProfiles) {
            val list = launcherApps.getActivityList(pkg, user)
            if (!list.isNullOrEmpty()) {
                val item = list[0]
                return themedIconLoader?.load(item) ?: item.getIcon(0)
            }
        }
        return null
    }

    private fun resolveLabel(pkg: String): CharSequence? =
        try {
            val pm = pluginContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }

    private inner class MonitorListener : TaskStackChangeListener {
        override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
            refresh()
        }

        override fun onTaskMovedToFront(taskId: Int) {
            refresh()
        }

        override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
            refresh()
        }

        override fun onTaskStackChanged() {
            refresh()
        }

        override fun onTaskRemoved(taskId: Int) {
            refresh()
        }
    }

    companion object {
        private const val MAX_TASKS = 50
    }
}
