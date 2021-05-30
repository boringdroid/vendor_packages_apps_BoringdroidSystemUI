package com.boringdroid.systemui

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import java.lang.ref.WeakReference
import java.util.*

class AppLoaderTask(context: Context?, target: Handler?) : Runnable {
    companion object {
        private val WORK_THREAD = HandlerThread("app-loader-thread")

        init {
            WORK_THREAD.start()
        }
    }

    private val sHandler = Handler(WORK_THREAD.looper)
    private val mContext: WeakReference<Context?>?
    private val mTarget: WeakReference<Handler?>?
    private val mAllApps: MutableList<AppData> = ArrayList()
    private var mStopped = false
    override fun run() {
        if (mStopped) {
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
        mAllApps.clear()
        for (info in activityInfoList) {
            val appData = AppData()
            appData.name = info.label as String
            appData.componentName = info.componentName
            appData.packageName = info.applicationInfo.packageName
            appData.icon = info.getIcon(0)
            mAllApps.add(appData)
        }
        mAllApps.sortWith { appDataOne: AppData, appDataTwo: AppData ->
            appDataOne.name!!.compareTo(
                appDataTwo.name!!
            )
        }
        val target = target
        target?.sendEmptyMessage(HandlerConstant.H_LOAD_SUCCEED)
    }

    @Synchronized
    fun start() {
        mStopped = false
        sHandler.post(this)
    }

    @Synchronized
    fun stop() {
        mStopped = true
        // Could we remove notify() from kotlin
        // notify()
    }

    val allApps: List<AppData>
        get() = ArrayList(mAllApps)
    private val target: Handler?
        get() = if (mTarget?.get() != null) mTarget.get() else null
    private val context: Context?
        get() = if (mContext?.get() != null) mContext.get() else null

    init {
        mContext = WeakReference(context)
        mTarget = WeakReference(target)
    }
}