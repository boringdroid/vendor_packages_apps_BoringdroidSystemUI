// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log

/**
 * Service bound by SystemUI's OverviewProxyService once the RRO points
 * `config_recentsComponentName` at com.boringdroid.systemui.
 *
 * onBind() returns an IOverviewProxy.Stub implementation that drives an [OverviewWindow] in
 * response to overview callbacks.
 */
class BoringdroidOverviewService : Service() {

    private lateinit var window: OverviewWindow
    private lateinit var proxy: OverviewProxyImpl

    override fun onCreate() {
        super.onCreate()
        window = OverviewWindow(this)
        proxy = OverviewProxyImpl(window, Handler(mainLooper))
        Log.i(TAG, "BoringdroidOverviewService onCreate")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "BoringdroidOverviewService onBind $intent")
        return proxy
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
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BoringdroidOverview"
    }
}
