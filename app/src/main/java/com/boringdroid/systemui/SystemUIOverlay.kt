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
import android.view.View
import com.android.systemui.plugins.OverlayPlugin
import com.android.systemui.plugins.annotations.Requires
import com.boringdroid.systemui.actioncenter.ActionCenterWindow
import com.boringdroid.systemui.actioncenter.NotificationFeed
import com.boringdroid.systemui.actioncenter.NotificationFeedIpc
import com.boringdroid.systemui.actioncenter.QsController
import com.boringdroid.systemui.actioncenter.SbnSummary
import com.boringdroid.systemui.calendar.CalendarClockWindow
import com.boringdroid.systemui.overview.BoringdroidOverviewService
import com.boringdroid.systemui.taskbar.BdTaskInfo
import com.boringdroid.systemui.taskbar.TaskbarCallbacks
import com.boringdroid.systemui.taskbar.TaskbarState
import java.lang.reflect.InvocationTargetException
import java.util.Arrays
import java.util.stream.Collectors
import kotlin.collections.ArrayList

@Requires(target = OverlayPlugin::class, version = OverlayPlugin.VERSION)
class SystemUIOverlay : OverlayPlugin {
    private var pluginContext: Context? = null
    private var systemUIContext: Context? = null
    private var allAppsWindow: AllAppsWindow? = null
    private var taskbarWindow: TaskbarWindow? = null
    private var taskbarState: TaskbarState? = null
    private var actionCenterWindow: ActionCenterWindow? = null
    private var calendarClockWindow: CalendarClockWindow? = null
    private var qsController: QsController? = null
    private var resolver: ContentResolver? = null
    private val tunerKeys: MutableList<String> = ArrayList()
    private val tunerKeyObserver: ContentObserver = TunerKeyObserver()
    private val closeSystemDialogsReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "receive intent $intent")
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS != intent.action) return
                allAppsWindow?.dismiss()
                actionCenterWindow?.dismiss()
                calendarClockWindow?.dismiss()
                context.sendBroadcast(
                    Intent(BoringdroidOverviewService.ACTION_HIDE_OVERVIEW)
                        .setPackage(context.packageName)
                )
            }
        }

    // The mirror service lives in com.boringdroid.systemui's own process (uid 10094)
    // while this plugin runs in SystemUI (uid 1000). NotificationFeed is a per-process
    // singleton; bridge writes from the mirror into this process's copy via broadcasts.
    private val notificationFeedReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Log.isLoggable(BRIDGE_TAG, Log.VERBOSE)) {
                    Log.v(
                        BRIDGE_TAG,
                        "onReceive action=${intent.action} " +
                            "key=${intent.getStringExtra(NotificationFeedIpc.EXTRA_KEY)} " +
                            "title=${intent.getStringExtra(NotificationFeedIpc.EXTRA_TITLE)}",
                    )
                }
                when (intent.action) {
                    NotificationFeedIpc.ACTION_FEED_RESET,
                    NotificationFeedIpc.ACTION_FEED_CLEAR,
                    NotificationFeedIpc.ACTION_CLEAR_ALL -> NotificationFeed.clear()
                    NotificationFeedIpc.ACTION_NOTIFICATION_POSTED -> {
                        val key = intent.getStringExtra(NotificationFeedIpc.EXTRA_KEY) ?: return
                        val pkg =
                            intent.getStringExtra(NotificationFeedIpc.EXTRA_PACKAGE_NAME) ?: return
                        NotificationFeed.upsert(
                            SbnSummary(
                                key = key,
                                packageName = pkg,
                                title = intent.getStringExtra(NotificationFeedIpc.EXTRA_TITLE),
                                body = intent.getStringExtra(NotificationFeedIpc.EXTRA_BODY),
                                postTime =
                                    intent.getLongExtra(NotificationFeedIpc.EXTRA_POST_TIME, 0L),
                                smallIcon = null,
                                contentIntent = null,
                                isOngoing =
                                    intent.getBooleanExtra(
                                        NotificationFeedIpc.EXTRA_IS_ONGOING,
                                        false,
                                    ),
                            )
                        )
                        if (Log.isLoggable(BRIDGE_TAG, Log.VERBOSE)) {
                            val feed = NotificationFeed.flow.value
                            Log.v(
                                BRIDGE_TAG,
                                "upsert key=$key postTime=${
                                    intent.getLongExtra(NotificationFeedIpc.EXTRA_POST_TIME, 0L)
                                } feedSize=${feed.size} top3=${
                                    feed.take(3).joinToString { "[${it.title}@${it.postTime}]" }
                                }",
                            )
                        }
                    }
                    NotificationFeedIpc.ACTION_NOTIFICATION_REMOVED -> {
                        val key = intent.getStringExtra(NotificationFeedIpc.EXTRA_KEY) ?: return
                        NotificationFeed.remove(key)
                    }
                }
            }
        }

    override fun setup(statusBar: View, navBar: View?) {
        // navBar is unused — the plugin renders into its own TaskbarWindow, not the
        // stock NavigationBarView. See TaskbarWindow.kt.
        Log.d(TAG, "setup status bar $statusBar, nav bar $navBar")
        // The taskbar state + window were created and shown in onCreate. Nothing
        // extra to assemble here.
    }

    override fun holdStatusBarOpen(): Boolean = false

    override fun setCollapseDesired(collapseDesired: Boolean) {
        // Do nothing
    }

    override fun onCreate(sysUIContext: Context, pluginContext: Context) {
        systemUIContext = sysUIContext
        this.pluginContext = pluginContext
        allAppsWindow = AllAppsWindow(pluginContext, sysUIContext)
        actionCenterWindow = ActionCenterWindow(pluginContext, sysUIContext)
        calendarClockWindow = CalendarClockWindow(pluginContext, sysUIContext)
        // The Overview window lives in BoringdroidOverviewService (plugin process); we drive it
        // via broadcasts rather than owning a second instance here. A second OverviewWindow in
        // the SystemUI process would share the class name but not the classloader, triggering a
        // ClassCastException when LayoutInflater returns the plugin-loaded OverviewLayout.
        val state = TaskbarState(pluginContext, sysUIContext).also { it.start() }
        taskbarState = state
        val window = TaskbarWindow(pluginContext, sysUIContext)
        window.callbacks =
            TaskbarCallbacks(
                onStartClick = {
                    // AllAppsWindow was designed as a click toggle against the original
                    // View-based "bt_all_apps" button; toggling dismissal works the same
                    // here — we just fire an onClick against a dummy View to reuse it.
                    allAppsWindow?.onClick(View(pluginContext))
                },
                onSearchClick = { allAppsWindow?.onClick(View(pluginContext)) },
                onBellClick = {
                    // Calendar, Overview, and Action Center are mutually exclusive surfaces.
                    calendarClockWindow?.dismiss()
                    sysUIContext.sendBroadcast(
                        Intent(BoringdroidOverviewService.ACTION_HIDE_OVERVIEW)
                            .setPackage(pluginContext.packageName)
                    )
                    actionCenterWindow?.toggle()
                },
                onClockClick = {
                    actionCenterWindow?.dismiss()
                    sysUIContext.sendBroadcast(
                        Intent(BoringdroidOverviewService.ACTION_HIDE_OVERVIEW)
                            .setPackage(pluginContext.packageName)
                    )
                    calendarClockWindow?.toggle()
                },
                onOverviewClick = {
                    actionCenterWindow?.dismiss()
                    calendarClockWindow?.dismiss()
                    // Overview lives in the plugin process; talk to it via broadcast.
                    sysUIContext.sendBroadcast(
                        Intent(BoringdroidOverviewService.ACTION_TOGGLE_OVERVIEW)
                            .setPackage(pluginContext.packageName)
                    )
                },
                onTaskClick = { task: BdTaskInfo -> state.bringTaskToFront(task.id) },
            )
        window.show(state)
        taskbarWindow = window
        resolver = sysUIContext.contentResolver
        initializeTuningServiceSettingKeys(resolver, tunerKeyObserver)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS) }
        // Android 14 (API 34) requires an explicit export flag for receivers registered
        // for non-protected broadcasts. ACTION_CLOSE_SYSTEM_DIALOGS is only delivered
        // to this plugin from the host SystemUI process, so NOT_EXPORTED is correct.
        systemUIContext!!.registerReceiver(
            closeSystemDialogsReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
        val feedFilter =
            IntentFilter().apply {
                addAction(NotificationFeedIpc.ACTION_FEED_RESET)
                addAction(NotificationFeedIpc.ACTION_FEED_CLEAR)
                addAction(NotificationFeedIpc.ACTION_CLEAR_ALL)
                addAction(NotificationFeedIpc.ACTION_NOTIFICATION_POSTED)
                addAction(NotificationFeedIpc.ACTION_NOTIFICATION_REMOVED)
            }
        // Sender (mirror) runs in a different UID, so the receiver must be exported.
        // Adding a signature-level custom permission to gate it is future hardening.
        systemUIContext!!.registerReceiver(
            notificationFeedReceiver,
            feedFilter,
            Context.RECEIVER_EXPORTED,
        )
        qsController = QsController(systemUIContext!!).also { it.start() }
    }

    override fun onDestroy() {
        if (systemUIContext != null) {
            try {
                systemUIContext!!.unregisterReceiver(closeSystemDialogsReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Try to unregister close system dialogs receiver without registering")
            }
            try {
                systemUIContext!!.unregisterReceiver(notificationFeedReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Try to unregister notification feed receiver without registering")
            }
        }
        resolver?.unregisterContentObserver(tunerKeyObserver)
        qsController?.stop()
        qsController = null
        taskbarWindow?.hide()
        taskbarWindow = null
        taskbarState?.stop()
        taskbarState = null
        actionCenterWindow = null
        calendarClockWindow?.dismiss()
        calendarClockWindow = null
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
        private const val BRIDGE_TAG = "BdNotifBridge"

        // Copied from systemui source code, please keep it update to source code.
        private const val ACTION_PLUGIN_CHANGED = "com.android.systemui.action.PLUGIN_CHANGED"
    }
}
