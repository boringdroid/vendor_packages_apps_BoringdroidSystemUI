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
import android.util.Log

/**
 * Surfaces the list of recent tasks to the overview UI.
 *
 * BoringdroidSystemUI is the declared recents component
 * (`config_recentsComponentName` overlay in device/generic/boringdroid_x86_64),
 * so `ActivityTaskManagerService.isCallerRecents(callingUid)` returns true
 * and `getRecentTasks` does not enforce REAL_GET_TASKS against this package.
 */
data class RecentAppTask(
    val taskId: Int,
    val packageName: String,
    val component: ComponentName?,
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
            RecentAppTask(info.taskId, pkg, component)
        }
    }
}
