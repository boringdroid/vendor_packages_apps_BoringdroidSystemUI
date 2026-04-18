package com.boringdroid.systemui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Binder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager

/**
 * Owns a boringdroid-managed window pinned to the bottom of the display. Replaces the previous
 * approach of injecting views into SystemUI's NavigationBarView — the plugin no longer depends
 * on a NavigationBar existing at all.
 */
class TaskbarWindow(
    private val pluginContext: Context,
    private val hostContext: Context,
) {
    private val windowManager =
        hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: ViewGroup? = null

    fun show() {
        if (root != null) return
        val inflater = LayoutInflater.from(pluginContext)
        val view = inflater.inflate(R.layout.layout_taskbar, null) as ViewGroup
        val heightPx =
            pluginContext.resources.getDimensionPixelSize(R.dimen.taskbar_window_height)
        // TYPE_NAVIGATION_BAR_PANEL lets multiple instances coexist; TYPE_NAVIGATION_BAR
        // collides with the stock NavigationBar0 until the RRO suppresses it (Task 4+).
        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.token = Binder()
        lp.title = "BoringdroidTaskbar"
        windowManager.addView(view, lp)
        root = view
    }

    fun hide() {
        root?.let { windowManager.removeViewImmediate(it) }
        root = null
    }

    fun getRoot(): ViewGroup? = root
}
