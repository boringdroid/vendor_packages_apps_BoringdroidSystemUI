package com.boringdroid.systemui.peek

import android.content.Context
import android.graphics.PixelFormat
import android.os.Binder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Thin invisible overlay anchored to the top edge of the display that fires [onTrigger] when a
 * mouse pointer hovers into y=0. The window is 1 px tall and full width so it sits above the
 * focused app without claiming visible chrome; we listen for hover events only, which the
 * framework dispatches separately from touch and only generates from a mouse / trackpad / stylus.
 *
 * Two instances of this would fight over input — [show]/[hide] are idempotent so the caller can
 * call show() repeatedly when the peek-target flow re-emits the same non-null value.
 */
class HoverEdgeWindow(
    private val hostContext: Context,
    private val onTrigger: () -> Unit,
) {
    private val windowManager =
        hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show() {
        if (view != null) return
        val v = View(hostContext)
        v.setOnHoverListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_MOVE -> {
                    onTrigger()
                    true
                }
                else -> false
            }
        }
        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                EDGE_HEIGHT_PX,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.token = Binder()
        lp.title = "BoringdroidPeekHoverEdge"
        windowManager.addView(v, lp)
        view = v
    }

    fun hide() {
        view?.let { v ->
            try {
                windowManager.removeViewImmediate(v)
            } catch (_: IllegalArgumentException) {
            }
        }
        view = null
    }

    companion object {
        private const val EDGE_HEIGHT_PX = 1
    }
}
