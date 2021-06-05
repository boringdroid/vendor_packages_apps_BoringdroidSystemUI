package com.boringdroid.systemui

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import java.lang.ref.WeakReference

class AppLoaderTask(context: Context?, target: Handler?) : Runnable {
    companion object {
        private val WORK_THREAD = HandlerThread("app-loader-thread")

        init {
            WORK_THREAD.start()
        }
    }

    private val handler = Handler(WORK_THREAD.looper)
    private val loaderContext: WeakReference<Context?>?
    private val loaderTarget: WeakReference<Handler?>?
    private val loaderAllApps: MutableList<AppData> = ArrayList()
    private var stopped = false
    override fun run() {
        if (stopped) {
            return
        }
        val context = context ?: return
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val userHandles = userManager.userProfiles
        val activityInfoList: MutableList<LauncherActivityInfo> = ArrayList()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        for (userHandle in userHandles) {
            activityInfoList.addAll(launcherApps.getActivityList(null, userHandle))
        }
        loaderAllApps.clear()
        for (info in activityInfoList) {
            val appData = AppData()
            appData.name = info.label as String
            appData.componentName = info.componentName
            appData.packageName = info.applicationInfo.packageName
            appData.icon = info.getIcon(0)
            loaderAllApps.add(appData)
        }
        loaderAllApps.sortWith { appDataOne: AppData, appDataTwo: AppData ->
            appDataOne.name!!.compareTo(
                appDataTwo.name!!
            )
        }
        val target = target
        target?.sendEmptyMessage(HandlerConstant.H_LOAD_SUCCEED)
    }

    @Synchronized
    fun start() {
        stopped = false
        handler.post(this)
    }

    @Synchronized
    fun stop() {
        stopped = true
        // Could we remove notify() from kotlin
        // notify()
    }

    val allApps: List<AppData>
        get() = ArrayList(loaderAllApps)
    private val target: Handler?
        get() = if (loaderTarget?.get() != null) loaderTarget.get() else null
    private val context: Context?
        get() = if (loaderContext?.get() != null) loaderContext.get() else null

    init {
        loaderContext = WeakReference(context)
        loaderTarget = WeakReference(target)
    }
}
