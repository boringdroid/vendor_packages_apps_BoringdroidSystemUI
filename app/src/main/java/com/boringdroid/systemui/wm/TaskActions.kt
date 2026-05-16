package com.boringdroid.systemui.wm

import android.annotation.SuppressLint
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowOrganizer

/**
 * Stateless task-control surface shared by the peek caption and the taskbar context menu. The
 * three operations mirror the WMShell caption buttons:
 *
 *  - [close]    — analogue of `TaskOperations.closeTask`.
 *  - [minimize] — analogue of `TaskOperations.minimizeTask`, plus a home-launch fallback so a
 *                 user-initiated minimise from outside WMShell never leaves an empty screen
 *                 when the reorder gets coalesced by the transition layer.
 *  - [toggleMaximize] — analogue of `TaskOperations.maximizeTask` (legacy decor) or
 *                       `DesktopTasksController.toggleDesktopTaskSize` (modern desktop-mode
 *                       decor). The variant is chosen by reading
 *                       `persist.wm.debug.desktop_mode[_2]` — same condition
 *                       `DesktopModeStatus.isAnyEnabled` uses to pick the WMShell decor.
 */
class TaskActions(
    private val pluginContext: Context,
    private val hostContext: Context,
    /**
     * Called shortly (~150ms) after [toggleMaximize] applies its WCT, on the main thread. The
     * WCT-only path bypasses WMShell's shell-transition wrapper, so
     * `TaskStackChangeListener.onTaskStackChanged` does NOT fire for in-place windowing-mode
     * flips or bounds changes. Callers that observe task state through that listener need this
     * hook to re-poll once AMS has committed the change. The 150ms delay is grounded in
     * observed WMShell-handler latency between WCT submission and the resulting task-info
     * update being visible via `ActivityManager.getRunningTasks`.
     *
     * [close] and [minimize] don't need this — task removal and home-to-front naturally
     * trigger `onTaskRemoved` / `onTaskMovedToFront` callbacks.
     */
    private val onWctApplied: () -> Unit = {},
) {
    private val windowOrganizer = WindowOrganizer()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun close(token: WindowContainerToken) {
        val wct = WindowContainerTransaction().removeTask(token)
        apply(wct, "close")
    }

    fun minimize(token: WindowContainerToken) {
        val wct = WindowContainerTransaction().reorder(token, /* onTop= */ false)
        apply(wct, "minimize")
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            hostContext.startActivity(home)
        } catch (e: SecurityException) {
            Log.w(TAG, "could not launch home as minimise fallback", e)
        }
    }

    fun toggleMaximize(
        token: WindowContainerToken,
        currentMode: Int,
        currentBounds: Rect,
        displayMode: Int,
    ) {
        val wct = WindowContainerTransaction()
        if (isDesktopModeEnabled()) {
            val stable = stableDisplayBounds()
            if (currentBounds == stable) {
                wct.setBounds(token, defaultDesktopBounds(stable))
            } else {
                wct.setBounds(token, stable)
            }
        } else {
            // Mirror AOSP's `TaskOperations.maximizeTask` (frameworks/base/libs/WindowManager/
            // Shell/.../windowdecor/TaskOperations.java) exactly. When the target mode equals
            // the display's mode (usually FULLSCREEN -> FULLSCREEN), submitting
            // `setWindowingMode(target)` leaves the task with an explicit mode override that
            // duplicates the display state — WMS doesn't reparent the surface to the display
            // root, so the task's freeform surface stays in place and shows a black underlay
            // where the fullscreen bounds extend past the old freeform rect. The fix is
            // `setWindowingMode(UNDEFINED)`, which tells WMS "inherit from parent" and
            // triggers the surface re-parent / bounds-inherit path.
            val target =
                if (currentMode == WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
                    WindowConfiguration.WINDOWING_MODE_FREEFORM
                } else {
                    WindowConfiguration.WINDOWING_MODE_FULLSCREEN
                }
            val effectiveTarget =
                if (target == displayMode) WindowConfiguration.WINDOWING_MODE_UNDEFINED
                else target
            wct.setWindowingMode(token, effectiveTarget)
            if (target == WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
                wct.setBounds(token, null)
            }
        }
        apply(wct, "toggleMaximize")
        mainHandler.postDelayed(onWctApplied, WCT_OBSERVE_DELAY_MS)
    }

    private fun stableDisplayBounds(): Rect {
        val wm = pluginContext.getSystemService(WindowManager::class.java)
        val metrics = wm.maximumWindowMetrics
        val insets =
            metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
        val bounds = Rect(metrics.bounds)
        bounds.left += insets.left
        bounds.top += insets.top
        bounds.right -= insets.right
        bounds.bottom -= insets.bottom
        return bounds
    }

    private fun defaultDesktopBounds(stable: Rect): Rect {
        val density =
            pluginContext.resources.displayMetrics.densityDpi.toFloat() /
                DisplayMetrics.DENSITY_DEFAULT
        val width = (DESKTOP_MODE_DEFAULT_WIDTH_DP * density + 0.5f).toInt()
        val height = (DESKTOP_MODE_DEFAULT_HEIGHT_DP * density + 0.5f).toInt()
        val bounds = Rect(0, 0, width, height)
        bounds.offset(stable.centerX() - bounds.centerX(), stable.centerY() - bounds.centerY())
        return bounds
    }

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

    private fun apply(wct: WindowContainerTransaction, label: String) {
        try {
            windowOrganizer.applyTransaction(wct)
        } catch (e: RuntimeException) {
            Log.w(TAG, "$label transaction failed", e)
        }
    }

    companion object {
        private const val TAG = "TaskActions"
        private const val DESKTOP_MODE_PROTO1_PROP = "persist.wm.debug.desktop_mode"
        private const val DESKTOP_MODE_PROTO2_PROP = "persist.wm.debug.desktop_mode_2"
        // Same constants as wm/shell/desktopmode/DesktopTasksController.kt.
        private const val DESKTOP_MODE_DEFAULT_WIDTH_DP = 840
        private const val DESKTOP_MODE_DEFAULT_HEIGHT_DP = 630
        // Empirically: ~50ms minimum observed between WCT submission and AMS having the new
        // task-info; 150ms is a comfortable cushion that's still imperceptible to the user.
        private const val WCT_OBSERVE_DELAY_MS = 150L
    }
}
