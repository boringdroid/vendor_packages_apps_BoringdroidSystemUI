package com.boringdroid.systemui.taskbar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Task-filtering utility for the taskbar's running-app rail.
 *
 * Extracted from the historical AppStateLayout helper so the Compose-based
 * taskbar and its unit tests can share the predicate. Same behavior as the
 * View-based implementation — just relocated to a file whose lifetime is
 * not tied to a RecyclerView.
 */
object TaskFilter {

    private const val TAG = "TaskFilter"

    fun shouldIgnoreTopTask(context: Context?, componentName: ComponentName?): Boolean {
        if (componentName == null) {
            return true
        }
        val packageName = componentName.packageName
        if ("android" == packageName) return true
        if (isSpecialLauncherPackage(packageName)) return true
        if (context != null && packageName.startsWith(context.packageName)) return true
        if (context != null && isLauncherComponent(context, componentName)) return true
        if (packageName.startsWith("com.android.systemui")) return true
        return false
    }

    private fun isSpecialLauncherPackage(packageName: String?): Boolean {
        if ("com.farmerbb.taskbar" == packageName) return true
        if ("com.teslacoilsw.launcher" == packageName) return true
        return "ch.deletescape.lawnchair.plah" == packageName
    }

    @VisibleForTesting
    fun isLauncherComponent(context: Context, componentName: ComponentName?): Boolean {
        if (componentName == null) return false
        val packageName = componentName.packageName
        val className = componentName.className
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo?.activityInfo ?: continue
            if (packageName == activityInfo.packageName && className == activityInfo.name) {
                Log.d(TAG, "Component $componentName matches home launcher")
                return true
            }
        }
        return false
    }
}
