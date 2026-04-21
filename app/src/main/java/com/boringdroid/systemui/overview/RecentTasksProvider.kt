// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.util.Log

/**
 * Surfaces the list of recent tasks to the overview UI.
 *
 * BoringdroidSystemUI is the declared recents component (`config_recentsComponentName` overlay in
 * device/generic/boringdroid_x86_64), so `ActivityTaskManagerService.isCallerRecents(callingUid)`
 * returns true and `getRecentTasks` does not enforce REAL_GET_TASKS against this package.
 */
/**
 * [windowBounds] carries the task's on-screen bounds in pixels — freeform window rect for
 * freeform tasks, full-display bounds for maximised ones. The Overview grid uses it to size each
 * card in proportion to its real window (common scale across the whole row) and as the
 * start/end anchor for the Mission-Control-style fly-to-grid animation. Null when the framework
 * didn't expose a valid Configuration.windowConfiguration, in which case the card falls back to
 * a default 16:10 slot.
 */
data class RecentAppTask(
    val taskId: Int,
    val packageName: String,
    val component: ComponentName?,
    val windowBounds: Rect?,
)

object RecentTasksProvider {
    private const val TAG = "BoringdroidRecentTasks"
    private const val DEFAULT_MAX = 20

    private val EXCLUDED_PACKAGES =
        setOf(
            "com.android.systemui",
            "com.boringdroid.systemui",
            "com.android.launcher3",
            "com.boringdroid.launcher3",
        )

    fun getRecentTasks(context: Context, max: Int = DEFAULT_MAX): List<RecentAppTask> {
        val userId = context.userId
        val raw: List<ActivityManager.RecentTaskInfo> =
            try {
                ActivityTaskManager.getInstance().getRecentTasks(max, 0, userId)
            } catch (e: SecurityException) {
                Log.w(TAG, "getRecentTasks: SecurityException, returning empty list", e)
                return emptyList()
            }
        return raw.mapNotNull { info ->
            val component = info.baseActivity ?: info.topActivity ?: info.realActivity
            val pkg = component?.packageName ?: return@mapNotNull null
            if (pkg in EXCLUDED_PACKAGES) return@mapNotNull null
            // configuration.windowConfiguration.bounds is the task's on-screen rect; it's the
            // freeform window rect on boringdroid's PC mode, the full display for fullscreen
            // tasks. Zero-sized bounds fall through as null so the card falls back to default.
            val rawBounds = info.configuration?.windowConfiguration?.bounds
            val bounds =
                rawBounds?.takeIf { !it.isEmpty }?.let { Rect(it) }
            RecentAppTask(info.taskId, pkg, component, bounds)
        }
    }
}
