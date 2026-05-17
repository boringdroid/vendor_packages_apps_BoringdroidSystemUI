package com.boringdroid.systemui

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.android.systemui.plugins.OverlayPlugin
import com.android.systemui.plugins.annotations.Requires
import com.boringdroid.systemui.actioncenter.ActionCenterWindow
import com.boringdroid.systemui.actioncenter.NotificationFeed
import com.boringdroid.systemui.actioncenter.NotificationFeedIpc
import com.boringdroid.systemui.actioncenter.QsController
import com.boringdroid.systemui.actioncenter.SbnSummary
import com.boringdroid.systemui.calendar.CalendarClockWindow
import com.boringdroid.systemui.overview.BoringdroidOverviewService
import com.boringdroid.systemui.peek.PeekCaptionController
import com.boringdroid.systemui.taskbar.BdTaskInfo
import com.boringdroid.systemui.taskbar.TaskbarCallbacks
import com.boringdroid.systemui.taskbar.TaskbarState
import com.boringdroid.systemui.theme.ThemedIconLoader
import com.boringdroid.systemui.wm.TaskActions
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
    private var peekCaption: PeekCaptionController? = null
    private var qsController: QsController? = null
    private var resolver: ContentResolver? = null
    private val tunerKeys: MutableList<String> = ArrayList()
    private val tunerKeyObserver: ContentObserver = TunerKeyObserver()
    private var themedIconLoader: ThemedIconLoader? = null

    private val themedIconsObserver: ContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                themedIconLoader?.onThemedIconsSettingChanged()
                allAppsWindow?.refreshApps()
                taskbarState?.refresh()
            }
        }

    private val themedIconsConfigCallbacks: ComponentCallbacks2 =
        object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                themedIconLoader?.onConfigurationChanged()
                allAppsWindow?.refreshApps()
                taskbarState?.refresh()
            }
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        }

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
                        .setPackage("com.boringdroid.systemui")
                )
            }
        }

    // Receiver for PhoneWindowManager's Meta (Windows) key intercept — see the matching
    // region boringdroid patch in PhoneWindowManager.interceptKeyBeforeDispatching. When the
    // user taps Meta on its own, PWM fires ACTION_TOGGLE_ALL_APPS targeted at this package,
    // and we toggle the start menu. Mutually exclusive with the other plugin panels.
    private val toggleAllAppsReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_TOGGLE_ALL_APPS != intent.action) return
                actionCenterWindow?.dismiss()
                calendarClockWindow?.dismiss()
                context.sendBroadcast(
                    Intent(BoringdroidOverviewService.ACTION_HIDE_OVERVIEW)
                        .setPackage("com.boringdroid.systemui")
                )
                allAppsWindow?.onClick(View(pluginContext))
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
        val loader = ThemedIconLoader(sysUIContext)
        themedIconLoader = loader
        sysUIContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
            /* notifyForDescendants = */ false,
            themedIconsObserver,
        )
        sysUIContext.registerComponentCallbacks(themedIconsConfigCallbacks)
        allAppsWindow = AllAppsWindow(pluginContext, sysUIContext)
        actionCenterWindow = ActionCenterWindow(pluginContext, sysUIContext)
        calendarClockWindow = CalendarClockWindow(pluginContext, sysUIContext)
        // The Overview window lives in BoringdroidOverviewService (plugin process); we drive it
        // via broadcasts rather than owning a second instance here. A second OverviewWindow in
        // the SystemUI process would share the class name but not the classloader, triggering a
        // ClassCastException when LayoutInflater returns the plugin-loaded OverviewLayout.
        val state = TaskbarState(pluginContext, sysUIContext).also { it.start() }
        taskbarState = state
        val taskActions =
            TaskActions(pluginContext, sysUIContext, onWctApplied = { state.refresh() })
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
                onTaskClose = { task: BdTaskInfo ->
                    task.token?.let { taskActions.close(it) }
                },
                onTaskMinimize = { task: BdTaskInfo ->
                    task.token?.let {
                        taskActions.minimize(it)
                        // Mark UI state so the running-app context menu hides
                        // Maximize/Minimize/Restore items on the next long-press of this
                        // icon. Cleared automatically when the task is brought back to
                        // front (icon tap or any path through state.bringTaskToFront).
                        state.markMinimized(task.id)
                    }
                },
                onTaskMaximize = { task: BdTaskInfo ->
                    task.token?.let {
                        taskActions.toggleMaximize(
                            token = it,
                            currentMode = task.mode,
                            currentBounds = task.bounds,
                            displayMode = task.displayMode,
                        )
                    }
                },
            )
        window.show(state)
        taskbarWindow = window
        refreshHomeLauncherForNewInsets(sysUIContext)
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
        // PhoneWindowManager lives in system_server (uid=1000) just like SystemUI, but the
        // broadcast crosses package boundaries (system → com.android.systemui), so we mark the
        // receiver EXPORTED. The sender directs the Intent with setPackage + sends as
        // UserHandle.CURRENT; no third-party app can reach this action.
        systemUIContext!!.registerReceiver(
            toggleAllAppsReceiver,
            IntentFilter().apply { addAction(ACTION_TOGGLE_ALL_APPS) },
            Context.RECEIVER_EXPORTED,
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
        peekCaption =
            PeekCaptionController(pluginContext, sysUIContext).also { it.start() }
        registerMetaKeySystemAction()
    }

    /**
     * Claim `GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS` so the Meta (Windows) key — which the
     * framework's [PhoneWindowManager.launchAllAppsViaA11y] routes through this accessibility
     * system action — ends up toggling boringdroid's Start menu instead of falling through to
     * whatever stock launcher's all-apps drawer happens to be installed. Requires the
     * `MANAGE_ACCESSIBILITY` permission, which this plugin carries via its platform signature.
     *
     * Also implicitly supports Alt+Tab — PhoneWindowManager already calls
     * `StatusBarManager.showRecentApps()` for Alt+Tab, which `OverviewProxyService` dispatches
     * to our [BoringdroidOverviewService] binder, so no extra wiring is needed.
     */
    private fun registerMetaKeySystemAction() {
        val ctx = systemUIContext ?: return
        val am =
            ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return
        val intent =
            Intent(ACTION_TOGGLE_ALL_APPS)
                .setPackage("com.android.systemui")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pi =
            PendingIntent.getBroadcast(
                ctx,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val action =
            RemoteAction(
                Icon.createWithResource(pluginContext!!, R.drawable.bt_all_apps),
                "All Apps",
                "Toggle boringdroid start menu",
                pi,
            )
        try {
            am.registerSystemAction(
                action,
                AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS,
            )
            Log.i(TAG, "registered Meta key system action for ALL_APPS")
        } catch (e: SecurityException) {
            Log.w(TAG, "registerSystemAction denied; Meta key will fall back to stock", e)
        }
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
            try {
                systemUIContext!!.unregisterReceiver(toggleAllAppsReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Try to unregister toggle-all-apps receiver without registering")
            }
            val am =
                systemUIContext!!.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? AccessibilityManager
            try {
                am?.unregisterSystemAction(
                    AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "unregisterSystemAction denied", e)
            }
        }
        resolver?.unregisterContentObserver(tunerKeyObserver)
        systemUIContext?.contentResolver?.unregisterContentObserver(themedIconsObserver)
        systemUIContext?.unregisterComponentCallbacks(themedIconsConfigCallbacks)
        themedIconLoader = null
        qsController?.stop()
        qsController = null
        peekCaption?.stop()
        peekCaption = null
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

    /**
     * Force-stop the current home launcher so it respawns with our [TaskbarWindow]'s nav-bar
     * inset already advertised.
     *
     * The launcher app caches its DeviceProfile (specifically the hotseat's bottom space, computed
     * as `mInsets.bottom + minQsbMargin`) from the [android.view.WindowInsets] it sees at first
     * onAttach. At boot — and after a SystemUI restart — the launcher's home activity typically
     * attaches BEFORE this plugin runs, so its cached profile has the nav-bar inset zeroed and
     * the Hotseat icons end up drawn under the taskbar's visual region. The RRO at
     * `vendor/boringdroid/rro/BoringdroidLauncher3Overlay/` neutralises Launcher3QuickStep's
     * tablet-mode inset normalisation, but cannot retroactively fix an already-cached
     * DeviceProfile.
     *
     * Resolves the home package dynamically (via `Intent.CATEGORY_HOME`) so any launcher the user
     * has chosen as default — not just stock Launcher3 — gets the same treatment. Restarting the
     * launcher is cheap: it only re-runs when the user navigates Home, and the cold start is
     * sub-second.
     */
    private fun refreshHomeLauncherForNewInsets(sysUIContext: Context) {
        val homePkg = resolveHomeLauncherPackage(sysUIContext)
        if (homePkg == null) {
            Log.w(TAG, "No home launcher resolved; skipping inset-race workaround")
            return
        }
        val am =
            sysUIContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return
        // Defer the kill: `windowManager.addView` queues the TaskbarWindow's providedInsets
        // change, and WMS may not have propagated the new InsetsState to apps by the time we
        // return from `show()`. If we force-stop immediately, the relaunched launcher reads stale
        // insets and re-caches the wrong DeviceProfile (observed empirically: immediate stop
        // leaves Hotseat at [0,980]; deferred stop fixes it to [0,940]). 750ms is comfortably
        // above the single-digit-ms inset propagation latency we've measured without being long
        // enough for the user to notice the launcher flash.
        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    am.forceStopPackage(homePkg)
                    Log.i(TAG, "Force-stopped home launcher $homePkg to refresh its DeviceProfile")
                } catch (e: SecurityException) {
                    // SystemUI declares FORCE_STOP_PACKAGES via the boringdroid manifest patch;
                    // this only fires on builds missing that patch. Log and continue — the user
                    // will see the overlap until they manually swipe Home.
                    Log.w(TAG, "Could not force-stop $homePkg to refresh its DeviceProfile", e)
                }
            },
            LAUNCHER_REFRESH_DELAY_MS,
        )
    }

    private fun resolveHomeLauncherPackage(context: Context): String? {
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        // Avoid restarting our own host SystemUI process if it somehow registers itself as home
        // (defensive — shouldn't happen in stock boringdroid).
        val pkg = info?.activityInfo?.packageName ?: return null
        if (pkg == "com.android.systemui") return null
        return pkg
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
        private const val LAUNCHER_REFRESH_DELAY_MS = 750L

        // Copied from systemui source code, please keep it update to source code.
        private const val ACTION_PLUGIN_CHANGED = "com.android.systemui.action.PLUGIN_CHANGED"

        /**
         * Broadcast fired when the Meta (Windows) key is tapped — the framework's
         * `PhoneWindowManager.launchAllAppsViaA11y()` dispatches
         * `GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS` to the accessibility system, which we claim
         * via `AccessibilityManager.registerSystemAction()` below so the PendingIntent fires
         * this broadcast.
         */
        const val ACTION_TOGGLE_ALL_APPS = "com.boringdroid.systemui.action.TOGGLE_ALL_APPS"
    }
}
