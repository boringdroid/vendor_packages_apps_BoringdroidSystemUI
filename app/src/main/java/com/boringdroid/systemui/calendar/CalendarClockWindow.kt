// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.calendar

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.boringdroid.systemui.R

/**
 * Owns the boringdroid-managed calendar / clock overlay window, opened by tapping the taskbar
 * clock.
 *
 * Mirrors `com.boringdroid.systemui.actioncenter.ActionCenterWindow`'s mixed-context pattern: the
 * plugin context carries resources/classloader/theme, but Compose's `WindowRecomposer` and
 * `AndroidCompositionLocals` invoke Application-level APIs that the plugin ContextWrapper chain
 * does not satisfy. A delegating [ContextWrapper] routes just those surfaces to the host SystemUI
 * application.
 *
 * Mutual exclusion with the action center is enforced by the caller (`SystemUIOverlay`) — the
 * window itself does not know about its sibling.
 */
class CalendarClockWindow(private val pluginContext: Context, private val hostContext: Context) {
    private val windowManager: WindowManager =
        pluginContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var windowContentView: View? = null
    private var shown = false
    private var pluginLifecycle: PluginLifecycleOwner? = null
    private var loader: CalendarEventLoader? = null

    @SuppressLint("ClickableViewAccessibility")
    fun toggle() {
        if (shown) {
            dismiss()
            return
        }
        val ctx: Context = buildComposeContext()
        val root = FrameLayout(ctx)
        root.id = R.id.calendar_clock_root
        val colorAttr = TypedValue()
        root.setBackgroundColor(
            if (ctx.theme.resolveAttribute(android.R.attr.colorPrimaryDark, colorAttr, true)) {
                colorAttr.data
            } else {
                Color.BLACK
            }
        )
        val layout = CalendarClockLayout(ctx)
        root.addView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val owner = PluginLifecycleOwner().also { it.moveToResumed() }
        pluginLifecycle = owner
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        val cornerRadius =
            pluginContext.resources.getDimension(R.dimen.calendar_clock_corner_radius)
        root.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        root.clipToOutline = true
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) dismiss()
            false
        }
        windowContentView = root
        windowManager.addView(root, generateLayoutParams())
        shown = true

        // Fire an initial query for the current month grid window. The panel will refine the
        // range when the user scrolls months forward/backward.
        val loader = CalendarEventLoader(hostContext).also { this.loader = it }
        val gridStart = CalendarGridHelpers.monthGridStart(System.currentTimeMillis())
        val gridEnd = CalendarGridHelpers.monthGridEnd(gridStart)
        loader.loadRange(gridStart, gridEnd)
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "opened — querying calendar range [$gridStart, $gridEnd]")
        }
    }

    fun dismiss() {
        if (!shown) return
        try {
            windowManager.removeViewImmediate(windowContentView)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Catch exception when removing calendar clock", e)
        }
        pluginLifecycle?.moveToDestroyed()
        pluginLifecycle = null
        windowContentView = null
        loader?.destroy()
        loader = null
        shown = false
    }

    private fun buildComposeContext(): Context {
        val hostApp = hostContext.applicationContext ?: hostContext
        return object : ContextWrapper(pluginContext) {
            override fun getApplicationContext(): Context = hostApp

            override fun registerComponentCallbacks(cb: ComponentCallbacks) {
                hostApp.registerComponentCallbacks(cb)
            }

            override fun unregisterComponentCallbacks(cb: ComponentCallbacks) {
                hostApp.unregisterComponentCallbacks(cb)
            }
        }
    }

    private fun generateLayoutParams(): WindowManager.LayoutParams {
        val resources = pluginContext.resources
        val windowWidth = resources.getDimension(R.dimen.calendar_clock_window_width).toInt()
        val windowHeight = resources.getDimension(R.dimen.calendar_clock_window_height).toInt()
        val marginEnd = resources.getDimension(R.dimen.calendar_clock_window_margin_end).toInt()
        val marginBottom =
            resources.getDimension(R.dimen.calendar_clock_window_margin_bottom).toInt()
        val params =
            WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = marginEnd
        params.y = marginBottom
        return params
    }

    private class PluginLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = registry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedState.savedStateRegistry

        fun moveToResumed() {
            if (registry.currentState == Lifecycle.State.INITIALIZED) {
                savedState.performRestore(null)
            }
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun moveToDestroyed() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        private const val TAG = "CalendarClockWindow"
    }
}
