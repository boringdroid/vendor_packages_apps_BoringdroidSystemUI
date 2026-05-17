package com.boringdroid.systemui.taskbar

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.UserManager
import android.util.Log
import android.window.WindowContainerToken
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.boringdroid.systemui.theme.ThemedIconLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Snapshot of a running task surfaced by the taskbar app rail.
 *
 * `id` is the `ActivityManager.RunningTaskInfo#id` (stable across the task's lifetime); `icon` is
 * resolved from LauncherApps at observation time so the composable doesn't block on PackageManager
 * while rendering.
 */
data class BdTaskInfo(
    val id: Int,
    val packageName: String,
    val component: ComponentName?,
    val icon: Drawable?,
    val label: CharSequence?,
    val token: WindowContainerToken?,
    val mode: Int,
    val bounds: Rect,
    /**
     * The display's windowing mode the task lives on (typically [WindowConfiguration.WINDOWING_MODE_FULLSCREEN]).
     * [com.boringdroid.systemui.wm.TaskActions.toggleMaximize] needs it to decide whether to
     * set the task's mode to [WindowConfiguration.WINDOWING_MODE_UNDEFINED] (inherit from
     * display) versus an explicit mode override — the former is what AOSP's
     * `TaskOperations.maximizeTask` does and is required for the fullscreen surface
     * re-parent to settle cleanly.
     */
    val displayMode: Int,
    /**
     * Whether the user explicitly minimized this task via the running-app context menu.
     * Cleared when the task is brought back to front (icon click) or when a fresh task with
     * the same id appears. We track this ourselves because `ActivityManager.RunningTaskInfo.
     * isVisible` is unreliable on AOSP 14 — it can return `false` even for a clearly visible
     * freeform window — so it can't be used to differentiate "user minimized" from "running
     * but happens to be behind another window".
     *
     * Used to gate the context-menu items: a minimized task only offers Close because
     * Maximize/Minimize/Restore have no observable effect on a non-foreground task.
     */
    val isMinimized: Boolean,
)

/**
 * Single source of truth for taskbar-rendered state. Collects task-stack, clock, battery and wifi
 * updates into [StateFlow]s so the Compose taskbar can observe them without threading or receiver
 * plumbing of its own.
 *
 * Lifecycle: call [start] after construction (the plugin calls this from SystemUIOverlay.onCreate);
 * call [stop] on plugin tear-down. Idempotent.
 */
class TaskbarState(
    private val pluginContext: Context,
    private val hostContext: Context,
    private val themedIconLoader: ThemedIconLoader? = null,
) {
    private val activityManager =
        pluginContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val launcherApps =
        pluginContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = pluginContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val hostActivityManager =
        hostContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _tasks = MutableStateFlow<List<BdTaskInfo>>(emptyList())
    val tasks: StateFlow<List<BdTaskInfo>> = _tasks.asStateFlow()

    private val _activeTaskId = MutableStateFlow(-1)
    val activeTaskId: StateFlow<Int> = _activeTaskId.asStateFlow()

    private val _time = MutableStateFlow(formatTime())
    val time: StateFlow<String> = _time.asStateFlow()

    private val _date = MutableStateFlow(formatDate())
    val date: StateFlow<String> = _date.asStateFlow()

    private val _batteryPercent = MutableStateFlow(0)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private val _wifiLevel = MutableStateFlow<Int?>(null)
    val wifiLevel: StateFlow<Int?> = _wifiLevel.asStateFlow()

    private val taskStackListener = TaskbarTaskStackListener()
    private val taskStackListeners = TaskStackChangeListeners.getInstance()
    private val activityManagerWrapper = ActivityManagerWrapper.getInstance()

    private val scope: CoroutineScope = MainScope()
    private var clockJob: Job? = null
    private var started = false

    private val batteryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                _batteryPercent.value = (level.toFloat() / scale.toFloat() * 100f).toInt()
            }
        }

    private val wifiReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != WifiManager.RSSI_CHANGED_ACTION) return
                updateWifiLevel()
            }
        }

    fun start() {
        if (started) return
        started = true
        taskStackListeners.registerTaskStackListener(taskStackListener)
        refreshRunningTasks(initial = true)
        hostContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        hostContext.registerReceiver(wifiReceiver, IntentFilter(WifiManager.RSSI_CHANGED_ACTION))
        // Seed battery from the sticky broadcast so the taskbar has a value
        // before the first ACTION_BATTERY_CHANGED fires.
        hostContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            batteryReceiver.onReceive(hostContext, it)
        }
        updateWifiLevel()
        clockJob =
            scope.launch(Dispatchers.Main) {
                while (true) {
                    _time.value = formatTime()
                    _date.value = formatDate()
                    delay(CLOCK_TICK_MS)
                }
            }
    }

    fun stop() {
        if (!started) return
        started = false
        taskStackListeners.unregisterTaskStackListener(taskStackListener)
        try {
            hostContext.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "battery receiver not registered: ${e.message}")
        }
        try {
            hostContext.unregisterReceiver(wifiReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "wifi receiver not registered: ${e.message}")
        }
        clockJob?.cancel()
        clockJob = null
        scope.cancel()
    }

    /**
     * Request the host ActivityManager to bring the task with the given id to the front. Runs on
     * the SystemUI process so the foreground-promotion permission check passes.
     */
    fun bringTaskToFront(taskId: Int) {
        minimizedTaskIds.remove(taskId)
        refreshRunningTasks()
        hostActivityManager.moveTaskToFront(taskId, 0)
    }

    /**
     * Record that the user explicitly minimized [taskId] via the context menu. The menu's
     * Maximize/Minimize/Restore items hide for this task until the user brings it back to
     * front (which calls [bringTaskToFront] and clears the flag).
     */
    fun markMinimized(taskId: Int) {
        // Do NOT call refreshRunningTasks() here. That would re-poll AM and run the
        // "if topId in minimizedTaskIds, clear" branch — which is racy against this call.
        // `TaskActions.minimize(token)` returns immediately while the WCT/reorder is still
        // in flight, so AM may still report the just-minimized task as the top resumed task
        // at the moment we re-poll. The clear branch would then wipe the flag we just set.
        // Instead, re-emit the current task list with isMinimized flipped for this id only —
        // the menu reads from `tasks` (via collectAsState), so a fresh emission is enough to
        // trigger recomposition with the new gate value. The natural TaskStackChangeListener
        // refreshes that follow the AM commit are still responsible for clearing the flag
        // when the task is genuinely brought back to front by an external path.
        if (minimizedTaskIds.add(taskId)) {
            _tasks.value =
                _tasks.value.map { if (it.id == taskId) it.copy(isMinimized = true) else it }
        }
    }

    private val minimizedTaskIds = mutableSetOf<Int>()

    /**
     * Re-poll [ActivityManager.getRunningTasks] and re-emit [tasks]. Needed after a
     * [com.boringdroid.systemui.wm.TaskActions.toggleMaximize] call because the AOSP
     * `TaskStackChangeListener` is not notified for in-place windowing-mode flips, so the
     * cached [BdTaskInfo.mode] would otherwise lag behind the system until the next foreground
     * task change.
     */
    fun refresh() {
        refreshRunningTasks()
    }

    private fun refreshRunningTasks(initial: Boolean = false) {
        val running = activityManager.getRunningTasks(MAX_RUNNING_TASKS)
        val ordered = if (initial) running.reversed() else running
        val filtered = mutableListOf<BdTaskInfo>()
        var topId = -1
        for (info in ordered) {
            val topActivity = info.topActivity
            if (TaskFilter.shouldIgnoreTopTask(pluginContext, topActivity)) continue
            val pkg = info.baseActivity?.packageName ?: continue
            val icon = resolveIcon(pkg)
            val label = resolveLabel(pkg)
            val snapshot =
                BdTaskInfo(
                    id = info.id,
                    packageName = pkg,
                    component = info.topActivity,
                    icon = icon,
                    label = label,
                    token = info.token,
                    mode = info.configuration.windowConfiguration.windowingMode,
                    bounds = Rect(info.configuration.windowConfiguration.bounds),
                    displayMode =
                        info.configuration.windowConfiguration.displayWindowingMode,
                    isMinimized = info.id in minimizedTaskIds,
                )
            if (filtered.none { it.id == snapshot.id }) {
                filtered.add(snapshot)
            }
            if (topId == -1) topId = info.id
        }
        // Preserve the rail order across refreshes — tasks the user has seen stay where they
        // are even when `getRunningTasks` reshuffles its MRU order (e.g. after the user taps
        // an icon to bring a task to front). Existing tasks keep their slot with the freshly
        // observed payload; new tasks land at the end in launch order.
        _tasks.value = mergeRailOrder(filtered)
        _activeTaskId.value = topId
        // Drop minimized-tracking ids for tasks no longer running (e.g. force-stopped or
        // removed via the Close menu). Without this, a new task that happens to reuse an
        // old id would inherit a stale "minimized" gate.
        val aliveIds = filtered.mapTo(HashSet()) { it.id }
        minimizedTaskIds.retainAll(aliveIds)
        // If the top resumed task is in our minimized set, it was brought back to front
        // (via direct task switch or another app's intent) — clear the flag.
        if (topId != -1 && topId in minimizedTaskIds) {
            minimizedTaskIds.remove(topId)
        }
    }

    private fun mergeRailOrder(fresh: List<BdTaskInfo>): List<BdTaskInfo> {
        val freshById = fresh.associateBy { it.id }
        val previous = _tasks.value
        val result = ArrayList<BdTaskInfo>(fresh.size)
        for (existing in previous) {
            freshById[existing.id]?.let { result.add(it) }
        }
        val keptIds = result.mapTo(HashSet()) { it.id }
        for (snapshot in fresh) {
            if (snapshot.id !in keptIds) result.add(snapshot)
        }
        return result
    }

    private fun resolveIcon(packageName: String): Drawable? {
        for (userHandle in userManager.userProfiles) {
            val list = launcherApps.getActivityList(packageName, userHandle)
            if (list != null && list.isNotEmpty() && list[0] != null) {
                return themedIconLoader?.load(list[0]) ?: list[0].getIcon(0)
            }
        }
        return null
    }

    private fun resolveLabel(packageName: String): CharSequence? {
        return try {
            val pm = pluginContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun updateWifiLevel() {
        val wm = hostContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val info = wm.connectionInfo
        if (!wm.isWifiEnabled || info?.supplicantState != SupplicantState.COMPLETED) {
            _wifiLevel.value = null
            return
        }
        val rssi = info.rssi
        val maxRssi = -40
        val minRssi = -100
        val level = 100 * (rssi - minRssi) / (maxRssi - minRssi)
        _wifiLevel.value = level.coerceIn(0, 100)
    }

    private fun formatTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun formatDate(): String =
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())

    private inner class TaskbarTaskStackListener : TaskStackChangeListener {
        override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
            refreshRunningTasks()
        }

        override fun onTaskMovedToFront(taskId: Int) {
            refreshRunningTasks()
        }

        override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
            refreshRunningTasks()
        }

        override fun onTaskStackChanged() {
            refreshRunningTasks()
        }

        override fun onTaskRemoved(taskId: Int) {
            refreshRunningTasks()
        }
    }

    companion object {
        private const val TAG = "TaskbarState"
        private const val MAX_RUNNING_TASKS = 50
        private const val CLOCK_TICK_MS = 10_000L
    }
}
