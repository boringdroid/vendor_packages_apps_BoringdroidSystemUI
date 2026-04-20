// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

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
 * Owns the boringdroid-managed action-center overlay window.
 *
 * Mirrors [com.boringdroid.systemui.AllAppsWindow]'s mixed-context pattern: the plugin context
 * carries resources/classloader/theme, but Compose's
 * [androidx.compose.ui.platform.WindowRecomposer] and
 * [androidx.compose.ui.platform.AndroidCompositionLocals] invoke Application-level APIs
 * (`getContentResolver`, `registerComponentCallbacks`) that the plugin ContextWrapper chain does
 * not satisfy. A delegating [ContextWrapper] routes just those surfaces to the host SystemUI
 * application.
 */
class ActionCenterWindow(private val pluginContext: Context, private val hostContext: Context) {
    private val windowManager: WindowManager =
        pluginContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var windowContentView: View? = null
    private var shown = false
    // Recreated on each open: LifecycleRegistry rejects DESTROYED→RESUMED, so
    // a single long-lived owner would refuse to re-resume after the first
    // dismiss().
    private var pluginLifecycle: PluginLifecycleOwner? = null

    @SuppressLint("ClickableViewAccessibility")
    fun toggle() {
        if (shown) {
            dismiss()
            return
        }
        val ctx: Context = buildComposeContext()
        val root = FrameLayout(ctx)
        root.id = R.id.action_center_root
        val colorAttr = TypedValue()
        root.setBackgroundColor(
            if (ctx.theme.resolveAttribute(android.R.attr.colorPrimaryDark, colorAttr, true)) {
                colorAttr.data
            } else {
                Color.BLACK
            }
        )
        val layout = ActionCenterLayout(ctx)
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
        val cornerRadius = pluginContext.resources.getDimension(R.dimen.action_center_corner_radius)
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
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "opened — feed size=${NotificationFeed.flow.value.size}")
        }
    }

    fun dismiss() {
        if (!shown) return
        try {
            windowManager.removeViewImmediate(windowContentView)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Catch exception when removing action center", e)
        }
        pluginLifecycle?.moveToDestroyed()
        pluginLifecycle = null
        windowContentView = null
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
        val windowWidth = resources.getDimension(R.dimen.action_center_window_width).toInt()
        val windowHeight = resources.getDimension(R.dimen.action_center_window_height).toInt()
        val marginEnd = resources.getDimension(R.dimen.action_center_window_margin_end).toInt()
        // Just the small breathing gap: Gravity.BOTTOM is computed relative to the inset-
        // adjusted bottom (above the TYPE_NAVIGATION_BAR taskbar), so WMS already leaves room
        // for the 64dp taskbar. Adding taskbar_window_height here would double-count.
        val marginBottom = resources.getDimensionPixelSize(R.dimen.panel_taskbar_gap)
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

    /**
     * Minimal [LifecycleOwner] + [SavedStateRegistryOwner] for the plugin's WindowManager-attached
     * overlay. Compose requires both on the view tree; without an Activity to provide them we
     * hand-drive RESUMED on show and DESTROYED on dismiss.
     */
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
        private const val TAG = "ActionCenterWindow"
    }
}
