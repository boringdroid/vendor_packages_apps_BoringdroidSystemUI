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
import android.view.ViewGroup
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.boringdroid.systemui.R

/**
 * Owns the boringdroid Overview window.
 *
 * Binds a single fullscreen system window with a root whose id is
 * `@+id/overview_root` — the anchor the `OverviewTest` UiAutomator selector
 * looks for. The window hosts a `RecyclerView` of recent-task cards populated
 * by [OverviewCardAdapter] and [RecentTasksProvider].
 *
 * Runs in the BoringdroidSystemUI process (bound via [BoringdroidOverviewService]),
 * so it uses its own package context for WindowManager — unlike [TaskbarWindow],
 * which piggybacks on the host SystemUI context.
 */
class OverviewWindow(private val context: Context) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: ViewGroup? = null
    private var adapter: OverviewCardAdapter? = null

    // Keep the overview's thumbnails fresh while it's visible.
    // onTaskSnapshotChanged fires on the main thread (dispatched via
    // TaskStackChangeListeners' Handler), so calling
    // RecyclerView.Adapter.notifyItemChanged directly is safe. We do NOT
    // consume the snapshot bitmap here (return false) — onBindViewHolder
    // re-calls ActivityManagerWrapper.getTaskThumbnail.
    private val taskStackListener =
        object : TaskStackChangeListener {
            override fun onTaskSnapshotChanged(taskId: Int, snapshot: ThumbnailData?): Boolean {
                val a = adapter ?: return false
                val pos = a.positionOfTaskId(taskId)
                if (pos >= 0) {
                    a.notifyItemChanged(pos)
                    Log.d(TAG, "onTaskSnapshotChanged: repaint taskId=$taskId pos=$pos")
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
            LayoutInflater.from(context).inflate(R.layout.layout_overview, null) as ViewGroup
        // TYPE_APPLICATION_OVERLAY is auto-granted for /system apps; no
        // SYSTEM_ALERT_WINDOW runtime permission dance needed.
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
        lp.gravity = Gravity.CENTER
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
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
            ) {
                hide()
                true
            } else {
                false
            }
        }
        windowManager.addView(view, lp)
        view.requestFocus()
        root = view
        val tasks = RecentTasksProvider.getRecentTasks(context)
        val cards = view.findViewById<RecyclerView>(R.id.overview_cards)
        cards.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val a = OverviewCardAdapter(context, tasks) { hide() }
        cards.adapter = a
        adapter = a
        // Register only while visible — snapshot churn outside the overview
        // is irrelevant to us.
        TaskStackChangeListeners.getInstance().registerTaskStackListener(taskStackListener)
        Log.i(
            TAG,
            "show: overview window attached; recent tasks count=${tasks.size}" +
                " first=${tasks.firstOrNull()?.packageName}",
        )
    }

    fun hide() {
        val v: View? = root
        if (v == null) {
            if (DEBUG) Log.d(TAG, "hide: not visible")
            return
        }
        // Unregister before tearing down the adapter so a late-dispatched
        // snapshot callback can't touch a stale RecyclerView.
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(taskStackListener)
        try {
            windowManager.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "hide: removeViewImmediate threw", e)
        }
        adapter = null
        root = null
        Log.i(TAG, "hide: overview window removed")
    }

    fun toggle() {
        if (root == null) show() else hide()
    }

    fun isShowing(): Boolean = root != null

    companion object {
        private const val TAG = "BoringdroidOverview"
        private const val DEBUG = true
    }
}
