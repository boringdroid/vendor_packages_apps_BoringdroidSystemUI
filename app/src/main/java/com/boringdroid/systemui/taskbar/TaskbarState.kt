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
)

/**
 * Single source of truth for taskbar-rendered state. Collects task-stack, clock, battery and wifi
 * updates into [StateFlow]s so the Compose taskbar can observe them without threading or receiver
 * plumbing of its own.
 *
 * Lifecycle: call [start] after construction (the plugin calls this from SystemUIOverlay.onCreate);
 * call [stop] on plugin tear-down. Idempotent.
 */
class TaskbarState(private val pluginContext: Context, private val hostContext: Context) {
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
        hostActivityManager.moveTaskToFront(taskId, 0)
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
                )
            if (filtered.none { it.id == snapshot.id }) {
                filtered.add(snapshot)
            }
            if (topId == -1) topId = info.id
        }
        _tasks.value = filtered
        _activeTaskId.value = topId
    }

    private fun resolveIcon(packageName: String): Drawable? {
        for (userHandle in userManager.userProfiles) {
            val list = launcherApps.getActivityList(packageName, userHandle)
            if (list != null && list.isNotEmpty() && list[0] != null) {
                return list[0].getIcon(0)
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
