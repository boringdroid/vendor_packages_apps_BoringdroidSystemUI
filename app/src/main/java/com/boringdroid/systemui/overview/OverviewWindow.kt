// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.content.Context
import android.graphics.PixelFormat
import android.os.Binder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.boringdroid.systemui.R

/**
 * Owns the boringdroid Overview window.
 *
 * Binds a single fullscreen system window whose root is the Compose-hosting
 * [OverviewLayout] — the `overview_root` anchor the `OverviewTest` UiAutomator selector looks for.
 *
 * Runs in the BoringdroidSystemUI process (bound via [BoringdroidOverviewService]), so it uses its
 * own package context for WindowManager — unlike `TaskbarWindow`, which piggybacks on the host
 * SystemUI context.
 */
class OverviewWindow(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: OverviewLayout? = null
    // Compose's AbstractComposeView#onAttachedToWindow requires a LifecycleOwner and
    // SavedStateRegistryOwner on the view tree. The window is added via WindowManager and has no
    // Activity to provide them, so we ship our own and hand-drive the lifecycle.
    private val pluginLifecycle = PluginLifecycleOwner()

    // Keep the overview's thumbnails fresh while it's visible.
    // onTaskSnapshotChanged fires on the main thread (dispatched via TaskStackChangeListeners'
    // Handler), so bumping the Compose state directly is safe. We do NOT consume the snapshot
    // bitmap here (return false) — the Compose card re-calls ActivityManagerWrapper.getTaskThumbnail
    // when the snapshotVersion key invalidates.
    private val taskStackListener =
        object : TaskStackChangeListener {
            override fun onTaskSnapshotChanged(taskId: Int, snapshot: ThumbnailData?): Boolean {
                val layout = root ?: return false
                if (layout.positionOfTaskId(taskId) >= 0) {
                    layout.bumpSnapshotVersion()
                    if (DEBUG) Log.d(TAG, "onTaskSnapshotChanged: repaint taskId=$taskId")
                } else if (DEBUG) {
                    Log.d(TAG, "onTaskSnapshotChanged: taskId=$taskId not in overview")
                }
                return false
            }
        }

    fun show() {
        if (root != null) {
            if (DEBUG) Log.d(TAG, "show: already visible")
            return
        }
        val view =
            LayoutInflater.from(context).inflate(R.layout.layout_overview, null) as OverviewLayout
        // TYPE_APPLICATION_OVERLAY is auto-granted for /system apps; no SYSTEM_ALERT_WINDOW
        // runtime permission dance needed.
        val taskbarHeight =
            context.resources.getDimensionPixelSize(R.dimen.taskbar_window_height)
        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            )
        // Sit above the 64dp taskbar so its buttons (including the recents toggle) stay clickable.
        lp.gravity = Gravity.TOP
        lp.y = 0
        lp.verticalMargin = 0f
        lp.height = context.resources.displayMetrics.heightPixels - taskbarHeight
        lp.token = Binder()
        lp.title = "BoringdroidOverview"
        // Outside touch dismisses.
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hide()
                true
            } else {
                false
            }
        }
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
            ) {
                hide()
                true
            } else {
                false
            }
        }
        // Install lifecycle + saved-state owners so Compose can build a recomposer.
        pluginLifecycle.moveToResumed()
        view.setViewTreeLifecycleOwner(pluginLifecycle)
        view.setViewTreeSavedStateRegistryOwner(pluginLifecycle)
        view.setCallbacks(onCardClick = ::onCardClick, onCardClose = ::onCardClose)
        val tasks = RecentTasksProvider.getRecentTasks(context)
        view.setData(tasks)
        windowManager.addView(view, lp)
        view.requestFocus()
        root = view
        // Kick off the fly-from-real-windows-to-grid entrance animation on the next frame
        // (post to the view handler so the children are measured & laid out first — the
        // flight reads each card's laid-out grid position at t=0).
        view.post { view.beginShow() }
        // Register only while visible — snapshot churn outside the overview is irrelevant to us.
        TaskStackChangeListeners.getInstance().registerTaskStackListener(taskStackListener)
        Log.i(
            TAG,
            "show: overview window attached; recent tasks count=${tasks.size}" +
                " first=${tasks.firstOrNull()?.packageName}",
        )
    }

    fun hide() {
        val v: OverviewLayout = root ?: run {
            if (DEBUG) Log.d(TAG, "hide: not visible")
            return
        }
        // Unregister before tearing down the layout so a late-dispatched snapshot callback can't
        // touch a stale Compose state. Defer the actual WindowManager.removeView until the exit
        // flight animation completes so the user sees cards fly back to their windows before
        // the overlay disappears.
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(taskStackListener)
        v.beginHide {
            val current = root ?: return@beginHide
            try {
                windowManager.removeViewImmediate(current)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "hide: removeViewImmediate threw", e)
            }
            pluginLifecycle.moveToDestroyed()
            root = null
            Log.i(TAG, "hide: overview window removed")
        }
    }

    fun toggle() {
        if (root == null) show() else hide()
    }

    fun isShowing(): Boolean = root != null

    private fun onCardClick(task: RecentAppTask) {
        val ok = ActivityManagerWrapper.getInstance().startActivityFromRecents(task.taskId, null)
        Log.i(TAG, "onCardClick: startActivityFromRecents taskId=${task.taskId} ok=$ok")
        if (ok) {
            hide()
        } else {
            // Leave the overview on screen so other cards stay tappable, and surface the failure so
            // the user isn't left wondering why the tap "did nothing". Expected triggers: the task
            // was killed between RecentTasksProvider.snapshot() and this click, or a future
            // manifest regression that drops START_TASKS_FROM_RECENTS.
            Toast.makeText(context, R.string.overview_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onCardClose(task: RecentAppTask) {
        try {
            ActivityManagerWrapper.getInstance().removeTask(task.taskId)
            Log.i(TAG, "onCardClose: removeTask taskId=${task.taskId}")
        } catch (e: SecurityException) {
            Log.w(TAG, "onCardClose: removeTask denied taskId=${task.taskId}", e)
        }
        val layout = root ?: return
        val remaining = RecentTasksProvider.getRecentTasks(context)
        layout.setData(remaining)
        if (remaining.isEmpty()) hide()
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
        private const val TAG = "BoringdroidOverview"
        private const val DEBUG = true
    }
}
