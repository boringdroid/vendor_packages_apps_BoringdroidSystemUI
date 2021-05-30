package com.boringdroid.systemui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.systemui.plugins.OverlayPlugin
import com.android.systemui.plugins.annotations.Requires
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.stream.Collectors

@Requires(target = OverlayPlugin::class, version = OverlayPlugin.VERSION)
class SystemUIOverlay : OverlayPlugin {
    private var mPluginContext: Context? = null
    private var mSystemUIContext: Context? = null
    private var mNavBarButtonGroup: View? = null
    private var mBtAllAppsGroup: ViewGroup? = null
    private var mAppStateLayout: AppStateLayout? = null
    private var mBtAllApps: View? = null
    private var mAllAppsWindow: AllAppsWindow? = null
    private var mNavBarButtonGroupId = -1
    private var mResolver: ContentResolver? = null
    private val mTunerKeys: MutableList<String> = ArrayList()
    private val mTunerKeyObserver: ContentObserver = TunerKeyObserver()
    private val mCloseSystemDialogsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "receive intent $intent")
            if (mAllAppsWindow == null) {
                return
            }
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS != intent.action) {
                return
            }
            mAllAppsWindow!!.dismiss()
        }
    }

    override fun setup(statusBar: View, navBar: View) {
        Log.d(TAG, "setup status bar $statusBar, nav bar $navBar")
        if (mNavBarButtonGroupId > 0) {
            val buttonGroup = navBar.findViewById<View>(mNavBarButtonGroupId)
            if (buttonGroup is ViewGroup) {
                mNavBarButtonGroup = buttonGroup
                // We must set the height to match parent programmatically
                // to let all apps button group be center of navigation
                // bar view.
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                val oldBtAllAppsGroup = buttonGroup.findViewWithTag<View>(TAG_ALL_APPS_GROUP)
                if (oldBtAllAppsGroup != null) {
                    buttonGroup.removeView(oldBtAllAppsGroup)
                }
                mBtAllAppsGroup!!.tag = TAG_ALL_APPS_GROUP
                buttonGroup.addView(mBtAllAppsGroup, 0, layoutParams)
                val oldAppStateLayout = buttonGroup.findViewWithTag<View>(TAG_APP_STATE_LAYOUT)
                if (oldAppStateLayout != null) {
                    buttonGroup.removeView(oldAppStateLayout)
                }
                mAppStateLayout!!.tag = TAG_APP_STATE_LAYOUT
                // The first item is all apps group.
                // The next three item is back button, home button, recents button.
                // So we should add app state layout to the 5th, index 4.
                buttonGroup.addView(mAppStateLayout, 4, layoutParams)
                mAppStateLayout!!.initTasks()
            }
        }
    }

    override fun holdStatusBarOpen(): Boolean {
        return false
    }

    override fun setCollapseDesired(collapseDesired: Boolean) {
        // Do nothing
    }

    override fun onCreate(sysUIContext: Context, pluginContext: Context) {
        mSystemUIContext = sysUIContext
        mPluginContext = pluginContext
        mNavBarButtonGroupId = sysUIContext
            .resources
            .getIdentifier("ends_group", "id", "com.android.systemui")
        mBtAllAppsGroup = initializeAllAppsButton(mPluginContext, mBtAllAppsGroup)
        mAppStateLayout = initializeAppStateLayout(mPluginContext, mAppStateLayout)
        mAppStateLayout!!.reloadActivityManager(mSystemUIContext)
        mBtAllApps = mBtAllAppsGroup!!.findViewById(R.id.bt_all_apps)
        mAllAppsWindow = AllAppsWindow(mPluginContext)
        mBtAllApps!!.setOnClickListener(mAllAppsWindow)
        mResolver = sysUIContext.contentResolver
        initializeTuningServiceSettingKeys(mResolver, mTunerKeyObserver)
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        mSystemUIContext!!.registerReceiver(mCloseSystemDialogsReceiver, filter)
    }

    override fun onDestroy() {
        if (mSystemUIContext != null) {
            try {
                mSystemUIContext!!.unregisterReceiver(mCloseSystemDialogsReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Try to unregister close system dialogs receiver without registering")
            }
        }
        if (mResolver != null) {
            mResolver!!.unregisterContentObserver(mTunerKeyObserver)
        }
        mBtAllAppsGroup!!.post {
            mBtAllAppsGroup!!.setOnClickListener(null)
            mBtAllApps!!.setOnClickListener(null)
            if (mNavBarButtonGroup is ViewGroup) {
                (mNavBarButtonGroup as ViewGroup).removeView(mBtAllAppsGroup)
                (mNavBarButtonGroup as ViewGroup).removeView(mAppStateLayout)
            }
        }
        mPluginContext = null
    }

    @SuppressLint("PrivateApi")
    private fun initializeTuningServiceSettingKeys(
        resolver: ContentResolver?, observer: ContentObserver
    ) {
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod =
                systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            val tunerKeys = getMethod.invoke(null, "persist.sys.bd.tunerkeys", "") as String
            Log.d(TAG, "Got tuner keys $tunerKeys")
            val tunerKeyList = Arrays.stream(tunerKeys.split("--").toTypedArray())
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { key: String -> !key.isEmpty() }
                .collect(Collectors.toList())
            mTunerKeys.clear()
            mTunerKeys.addAll(tunerKeyList)
            for (key in mTunerKeys) {
                Log.d(TAG, "Got key $key")
                val uri = Settings.Secure.getUriFor(key)
                resolver!!.registerContentObserver(uri, false, observer)
            }
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Failed to get tuner keys from properties, so fallback to default")
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "Failed to get tuner keys from properties, so fallback to default")
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "Failed to get tuner keys from properties, so fallback to default")
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "Failed to get tuner keys from properties, so fallback to default")
        }
    }

    @SuppressLint("InflateParams")
    private fun initializeAllAppsButton(context: Context?, btAllAppsGroup: ViewGroup?): ViewGroup {
        return btAllAppsGroup
            ?: LayoutInflater.from(context)
                .inflate(R.layout.layout_bt_all_apps, null) as ViewGroup
    }

    @SuppressLint("InflateParams")
    private fun initializeAppStateLayout(
        context: Context?, appStateLayout: AppStateLayout?
    ): AppStateLayout {
        return appStateLayout
            ?: LayoutInflater.from(context)
                .inflate(R.layout.layout_app_state, null) as AppStateLayout
    }

    private fun onTunerChange(uri: Uri) {
        val keyName = uri.lastPathSegment
        val value = Settings.Secure.getString(mResolver, keyName)
        Log.d(TAG, "onTunerChange $uri, value $value")
        val packageUri = Uri.fromParts("package", mPluginContext!!.packageName, null)
        Log.d(TAG, "onTunerChange packageUri $packageUri")
        val pluginChangedIntent = Intent(ACTION_PLUGIN_CHANGED, packageUri)
        mPluginContext!!.sendBroadcast(pluginChangedIntent)
    }

    private inner class TunerKeyObserver : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "TunerKeyChanged $uri, self changed $selfChange")
            onTunerChange(uri!!)
        }
    }

    companion object {
        private const val TAG = "SystemUIOverlay"

        // Copied from systemui source code, please keep it update to source code.
        private const val ACTION_PLUGIN_CHANGED = "com.android.systemui.action.PLUGIN_CHANGED"
        private const val TAG_ALL_APPS_GROUP = "tag-bt-all-apps-group"
        private const val TAG_APP_STATE_LAYOUT = "tag-app-state-layout"
    }
}