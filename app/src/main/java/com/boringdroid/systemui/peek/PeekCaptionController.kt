package com.boringdroid.systemui.peek

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.boringdroid.systemui.wm.TaskActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PeekCaptionController(
    private val pluginContext: Context,
    private val hostContext: Context,
) : PeekPanelWindow.Callbacks {

    private val monitor = TaskFullscreenMonitor(pluginContext)
    private val actions = TaskActions(pluginContext, hostContext)
    private val edge = HoverEdgeWindow(hostContext) { onEdgeHover() }
    private val panel = PeekPanelWindow(pluginContext, hostContext, this)
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
        actions.toggleMaximize(target.token, target.currentMode, target.currentBounds)
        panel.hide()
    }

    override fun onMinimize(taskId: Int) {
        val target = currentTarget ?: return
        if (target.taskId != taskId) return
        actions.minimize(target.token)
        panel.hide()
    }

    override fun onClose(taskId: Int) {
        val target = currentTarget ?: return
        if (target.taskId != taskId) return
        actions.close(target.token)
        panel.hide()
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
