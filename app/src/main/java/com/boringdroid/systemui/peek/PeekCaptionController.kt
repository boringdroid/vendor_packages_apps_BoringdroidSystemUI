package com.boringdroid.systemui.peek

import android.annotation.SuppressLint
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.util.Log
import android.window.WindowContainerTransaction
import android.window.WindowOrganizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the peek-caption stack: the [TaskFullscreenMonitor] that decides when the foreground task
 * needs a peek caption, the [HoverEdgeWindow] that arms the trigger, and the [PeekPanelWindow]
 * that actually shows when the user hovers the top edge.
 *
 * Gated twice:
 *   1. `persist.boringdroid.peek_caption` (default true) — kill switch for the feature.
 *   2. `persist.wm.debug.desktop_mode` / `persist.wm.debug.desktop_mode_2` — if either is true,
 *      WMShell uses `DesktopModeWindowDecoration` whose maximize stays in freeform and keeps the
 *      in-window caption visible. Peek would duplicate that caption, so we skip arming.
 */
class PeekCaptionController(
    private val pluginContext: Context,
    private val hostContext: Context,
) : PeekPanelWindow.Callbacks {

    private val monitor = TaskFullscreenMonitor(pluginContext)
    private val edge = HoverEdgeWindow(hostContext) { onEdgeHover() }
    private val panel = PeekPanelWindow(pluginContext, hostContext, this)
    private val windowOrganizer = WindowOrganizer()
    private val scope: CoroutineScope = MainScope()
    private var collectJob: Job? = null
    private var currentTarget: PeekTarget? = null
    private var started = false

    fun start() {
        if (started) return
        if (!isFeatureEnabled()) {
            Log.i(TAG, "persist.boringdroid.peek_caption=false; not arming peek caption")
            return
        }
        if (isDesktopModeEnabled()) {
            Log.i(TAG, "DesktopMode active; not arming peek caption (in-window caption stays visible)")
            return
        }
        started = true
        monitor.start()
        collectJob =
            scope.launch {
                monitor.peekTarget.collectLatest { target ->
                    currentTarget = target
                    if (target == null) {
                        panel.hide()
                        edge.hide()
                    } else {
                        edge.show()
                    }
                }
            }
    }

    fun stop() {
        if (!started) return
        started = false
        collectJob?.cancel()
        collectJob = null
        scope.cancel()
        panel.hide()
        edge.hide()
        monitor.stop()
        currentTarget = null
    }

    private fun onEdgeHover() {
        val target = currentTarget ?: return
        panel.show(target)
    }

    override fun onRestore(taskId: Int) {
        val target = currentTarget ?: return
        if (target.taskId != taskId) return
        val wct = WindowContainerTransaction()
        wct.setWindowingMode(target.token, WindowConfiguration.WINDOWING_MODE_FREEFORM)
        applyTransaction(wct, "restore")
        panel.hide()
    }

    override fun onMinimize(taskId: Int) {
        // Reordering the task to the back of its parent in a WCT is the same path WMShell's
        // TaskOperations.minimizeTask uses. Without a shell-transition player attached here
        // the framework still applies the reorder, just without an animated slide.
        val target = currentTarget ?: return
        if (target.taskId != taskId) return
        val wct = WindowContainerTransaction()
        wct.reorder(target.token, /* onTop= */ false)
        applyTransaction(wct, "minimize")
        // Fall back to launching home so the user gets back to a usable surface even if the
        // reorder gets coalesced out by the transition layer.
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            hostContext.startActivity(home)
        } catch (e: SecurityException) {
            Log.w(TAG, "could not launch home as minimise fallback", e)
        }
        panel.hide()
    }

    override fun onClose(taskId: Int) {
        val target = currentTarget ?: return
        if (target.taskId != taskId) return
        val wct = WindowContainerTransaction()
        wct.removeTask(target.token)
        applyTransaction(wct, "close")
        panel.hide()
    }

    private fun applyTransaction(wct: WindowContainerTransaction, label: String) {
        try {
            windowOrganizer.applyTransaction(wct)
        } catch (e: RuntimeException) {
            Log.w(TAG, "peek caption $label transaction failed", e)
        }
    }

    @SuppressLint("PrivateApi")
    private fun isFeatureEnabled(): Boolean = readBoolProp(SYSPROP, defaultValue = true)

    @SuppressLint("PrivateApi")
    private fun isDesktopModeEnabled(): Boolean =
        readBoolProp(DESKTOP_MODE_PROTO1_PROP, defaultValue = false) ||
            readBoolProp(DESKTOP_MODE_PROTO2_PROP, defaultValue = false)

    @SuppressLint("PrivateApi")
    private fun readBoolProp(key: String, defaultValue: Boolean): Boolean {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val get =
                cls.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            get.invoke(null, key, defaultValue) as Boolean
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "could not read $key; defaulting to $defaultValue", e)
            defaultValue
        }
    }

    companion object {
        private const val TAG = "PeekCaptionController"
        private const val SYSPROP = "persist.boringdroid.peek_caption"
        private const val DESKTOP_MODE_PROTO1_PROP = "persist.wm.debug.desktop_mode"
        private const val DESKTOP_MODE_PROTO2_PROP = "persist.wm.debug.desktop_mode_2"
    }
}
