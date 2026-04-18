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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.android.systemui.plugins.OverlayPlugin
import com.android.systemui.plugins.annotations.Requires
import java.lang.reflect.InvocationTargetException
import java.util.Arrays
import java.util.stream.Collectors
import kotlin.collections.ArrayList

@Requires(target = OverlayPlugin::class, version = OverlayPlugin.VERSION)
class SystemUIOverlay : OverlayPlugin {
    private var pluginContext: Context? = null
    private var systemUIContext: Context? = null
    private var btAllAppsGroup: ViewGroup? = null
    private var clockAndStatus: ViewGroup? = null
    private var appStateLayout: AppStateLayout? = null
    private var btAllApps: View? = null
    private var allAppsWindow: AllAppsWindow? = null
    private var taskbarWindow: TaskbarWindow? = null
    private var resolver: ContentResolver? = null
    private val tunerKeys: MutableList<String> = ArrayList()
    private val tunerKeyObserver: ContentObserver = TunerKeyObserver()
    private val closeSystemDialogsReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "receive intent $intent")
                if (allAppsWindow == null) {
                    return
                }
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS != intent.action) {
                    return
                }
                allAppsWindow!!.dismiss()
            }
        }

    override fun setup(statusBar: View, navBar: View?) {
        // navBar is unused — the plugin renders into its own TaskbarWindow,
        // not the stock NavigationBarView. See TaskbarWindow.kt.
        Log.d(TAG, "setup status bar $statusBar, nav bar $navBar")
        val root =
            taskbarWindow?.getRoot() ?: run {
                Log.w(TAG, "setup called before taskbar window was shown; skipping")
                return
            }
        // Detach any existing children so setup() is idempotent across SystemUI restarts.
        (btAllAppsGroup?.parent as? ViewGroup)?.removeView(btAllAppsGroup)
        (appStateLayout?.parent as? ViewGroup)?.removeView(appStateLayout)
        (clockAndStatus?.parent as? ViewGroup)?.removeView(clockAndStatus)
        root.removeAllViews()

        btAllAppsGroup!!.tag = TAG_ALL_APPS_GROUP
        root.addView(
            btAllAppsGroup,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        appStateLayout!!.tag = TAG_APP_STATE_LAYOUT
        // App-state takes the middle with weight=1 so the clock is pushed to the end,
        // mirroring the original FrameLayout END-gravity placement.
        root.addView(
            appStateLayout,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        appStateLayout!!.initTasks()

        clockAndStatus!!.tag = TAG_CLOCK_AND_STATUS_GROUP
        root.addView(
            clockAndStatus,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val clockTextView = clockAndStatus!!.findViewById<TextView>(R.id.clock)
        val batteryBar = clockAndStatus!!.findViewById<ProgressBar>(R.id.progressBar)
        val wifiBar = clockAndStatus!!.findViewById<ImageView>(R.id.progressBarWifi)
        val batteryText = clockAndStatus!!.findViewById<TextView>(R.id.textViewBatteryPercent)
        this.pluginContext
            ?.let { ClockAndStatus(clockTextView, batteryBar, batteryText, wifiBar, it) }
            ?.startUpdatingTimeAndStatus()
    }

    override fun holdStatusBarOpen(): Boolean {
        return false
    }

    override fun setCollapseDesired(collapseDesired: Boolean) {
        // Do nothing
    }

    override fun onCreate(sysUIContext: Context, pluginContext: Context) {
        systemUIContext = sysUIContext
        this.pluginContext = pluginContext
        btAllAppsGroup = initializeAllAppsButton(this.pluginContext, btAllAppsGroup)
        clockAndStatus = initializeClockAndStatus(this.pluginContext, clockAndStatus)
        appStateLayout = initializeAppStateLayout(this.pluginContext, appStateLayout)
        appStateLayout!!.reloadActivityManager(systemUIContext)
        btAllApps = btAllAppsGroup!!.findViewById(R.id.bt_all_apps)
        allAppsWindow = AllAppsWindow(this.pluginContext, sysUIContext)
        btAllApps!!.setOnClickListener(allAppsWindow)
        taskbarWindow = TaskbarWindow(pluginContext, sysUIContext).also { it.show() }
        resolver = sysUIContext.contentResolver
        initializeTuningServiceSettingKeys(resolver, tunerKeyObserver)
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        // Android 14 (API 34) requires an explicit export flag for receivers registered
        // for non-protected broadcasts. ACTION_CLOSE_SYSTEM_DIALOGS is only delivered to
        // this plugin from the host SystemUI process, so NOT_EXPORTED is correct.
        systemUIContext!!.registerReceiver(
            closeSystemDialogsReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        if (systemUIContext != null) {
            try {
                systemUIContext!!.unregisterReceiver(closeSystemDialogsReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Try to unregister close system dialogs receiver without registering")
            }
        }
        if (resolver != null) {
            resolver!!.unregisterContentObserver(tunerKeyObserver)
        }
        btAllAppsGroup!!.post {
            btAllAppsGroup!!.setOnClickListener(null)
            btAllApps!!.setOnClickListener(null)
        }
        taskbarWindow?.hide()
        taskbarWindow = null
        pluginContext = null
    }

    @SuppressLint("PrivateApi")
    private fun initializeTuningServiceSettingKeys(
        resolver: ContentResolver?,
        observer: ContentObserver,
    ) {
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod =
                systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            val tunerKeys = getMethod.invoke(null, "persist.sys.bd.tunerkeys", "") as String
            Log.d(TAG, "Got tuner keys $tunerKeys")
            val tunerKeyList =
                Arrays.stream(tunerKeys.split("--").toTypedArray())
                    .map { obj: String -> obj.trim { it <= ' ' } }
                    .filter { key: String -> !key.isEmpty() }
                    .collect(Collectors.toList())
            this.tunerKeys.clear()
            this.tunerKeys.addAll(tunerKeyList)
            for (key in this.tunerKeys) {
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
            ?: LayoutInflater.from(context).inflate(R.layout.layout_bt_all_apps, null) as ViewGroup
    }

    @SuppressLint("InflateParams")
    private fun initializeClockAndStatus(context: Context?, clockAndStatus: ViewGroup?): ViewGroup {
        return clockAndStatus
            ?: LayoutInflater.from(context).inflate(R.layout.layout_clock_and_status, null)
                as ViewGroup
    }

    private fun initializeAppStateLayout(
        context: Context?,
        appStateLayout: AppStateLayout?,
    ): AppStateLayout {
        // Inflating layout_app_state.xml via the host (SystemUI) LayoutInflater causes
        // a ClassCastException: the XML names <com.boringdroid.systemui.AppStateLayout>,
        // which the inflater resolves through SystemUI's classloader — yielding a different
        // Class object than the one the plugin's classloader holds for the same FQCN.
        // Instantiate directly through the plugin classloader so both references agree.
        return appStateLayout ?: AppStateLayout(context!!)
    }

    private fun onTunerChange(uri: Uri) {
        val keyName = uri.lastPathSegment
        val value = Settings.Secure.getString(resolver, keyName)
        Log.d(TAG, "onTunerChange $uri, value $value")
        val packageUri = Uri.fromParts("package", pluginContext!!.packageName, null)
        Log.d(TAG, "onTunerChange packageUri $packageUri")
        val pluginChangedIntent = Intent(ACTION_PLUGIN_CHANGED, packageUri)
        pluginContext!!.sendBroadcast(pluginChangedIntent)
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
        private const val TAG_CLOCK_AND_STATUS_GROUP = "tag-clock-and-status-group"
        private const val TAG_APP_STATE_LAYOUT = "tag-app-state-layout"
    }
}
