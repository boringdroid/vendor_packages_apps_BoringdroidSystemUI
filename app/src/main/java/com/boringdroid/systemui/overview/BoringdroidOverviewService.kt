// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.boringdroid.systemui.theme.ThemedIconLoader

/**
 * Service bound by SystemUI's OverviewProxyService once the RRO points
 * `config_recentsComponentName` at com.boringdroid.systemui.
 *
 * onBind() returns an IOverviewProxy.Stub implementation that drives an [OverviewWindow] in
 * response to overview callbacks.
 *
 * Also wakeable via [OverviewToggleReceiver]: when the taskbar's recents button (running in the
 * SystemUI host process) fires [ACTION_TOGGLE_OVERVIEW], the manifest receiver starts this
 * service with the action as an extra, and [onStartCommand] drives the window. Going through
 * the service avoids instantiating [OverviewWindow] in a foreign classloader — doing so directly
 * from the plugin-in-SystemUI process produced a ClassCastException on LayoutInflater's cast.
 */
class BoringdroidOverviewService : Service() {

    private lateinit var window: OverviewWindow
    private lateinit var proxy: OverviewProxyImpl
    private var themedIconLoader: ThemedIconLoader? = null
    private val themedIconsObserver: ContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                themedIconLoader?.onThemedIconsSettingChanged()
            }
        }
    private val themedIconsConfigCallbacks: ComponentCallbacks2 =
        object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                themedIconLoader?.onConfigurationChanged()
            }

            override fun onLowMemory() {}

            override fun onTrimMemory(level: Int) {}
        }

    override fun onCreate() {
        super.onCreate()
        val loader = ThemedIconLoader(this)
        themedIconLoader = loader
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
            /* notifyForDescendants = */ false,
            themedIconsObserver,
        )
        registerComponentCallbacks(themedIconsConfigCallbacks)
        window = OverviewWindow(this, loader)
        proxy = OverviewProxyImpl(window, Handler(mainLooper))
        Log.i(TAG, "BoringdroidOverviewService onCreate")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "BoringdroidOverviewService onBind $intent")
        return proxy
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pending = intent?.getStringExtra(OverviewToggleReceiver.EXTRA_PENDING_ACTION)
        when (pending) {
            ACTION_TOGGLE_OVERVIEW -> {
                Log.i(TAG, "onStartCommand: TOGGLE")
                window.toggle()
            }
            ACTION_HIDE_OVERVIEW -> {
                if (window.isShowing()) window.hide()
            }
        }
        // Not sticky — the manifest receiver re-starts us on every subsequent toggle broadcast.
        return START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "BoringdroidOverviewService onUnbind $intent")
        if (::window.isInitialized && window.isShowing()) {
            window.hide()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "BoringdroidOverviewService onDestroy")
        if (::window.isInitialized && window.isShowing()) {
            window.hide()
        }
        contentResolver.unregisterContentObserver(themedIconsObserver)
        unregisterComponentCallbacks(themedIconsConfigCallbacks)
        themedIconLoader = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BoringdroidOverview"
        const val ACTION_TOGGLE_OVERVIEW = "com.boringdroid.systemui.action.TOGGLE_OVERVIEW"
        const val ACTION_HIDE_OVERVIEW = "com.boringdroid.systemui.action.HIDE_OVERVIEW"
    }
}
