// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manifest-declared broadcast entry-point for the taskbar recents button.
 *
 * Reaching the [OverviewWindow] from the taskbar requires crossing two process boundaries:
 *  - The taskbar Compose hierarchy runs in the host SystemUI process (uid 1000), where we cannot
 *    safely instantiate [OverviewWindow] directly — the plugin classloader diverges from the
 *    service's and [android.view.LayoutInflater] produces an `OverviewLayout` that fails the
 *    `as OverviewLayout` cast.
 *  - [BoringdroidOverviewService] lives in the plugin process but is normally bound lazily by
 *    SystemUI's `OverviewProxyService`; nothing keeps it alive for a taskbar tap.
 *
 * This receiver sits in the manifest so PackageManager always delivers to it. It simply starts
 * the service (which registers its own in-process receiver on onCreate) and re-broadcasts the
 * same action inside the plugin process once the service is up.
 */
class OverviewToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive: $action (pid=${android.os.Process.myPid()})")
        val serviceIntent = Intent(context, BoringdroidOverviewService::class.java)
        // startService keeps the process alive long enough for the service's own receiver to pick
        // up the same broadcast. We stuff the action onto the service intent as an extra so the
        // service can fall back to driving the window directly if the in-process receiver hasn't
        // registered yet (first-launch race).
        serviceIntent.putExtra(EXTRA_PENDING_ACTION, action)
        context.startService(serviceIntent)
    }

    companion object {
        private const val TAG = "BoringdroidOverview"
        const val EXTRA_PENDING_ACTION = "pending_action"
    }
}
