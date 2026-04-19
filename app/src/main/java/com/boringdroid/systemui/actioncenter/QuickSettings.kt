// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable snapshot of a quick-setting tile. Rendered by [ActionCenterLayout]
 * and observed via [QsTileStore]'s [StateFlow]s.
 *
 * The [contentDescription] convention — "<label> on" / "<label> off" — is the
 * surface UiAutomator addresses: `By.res(..., "qs_wifi").descContains("off")`
 * is how external state changes are validated without leaning on drawable
 * identity.
 */
data class QsState(
    val label: String,
    val isOn: Boolean,
) {
    val contentDescription: String
        get() = if (isOn) "$label on" else "$label off"
}

/**
 * Process-wide store of quick-settings state. Updated by [QsController] in
 * response to system broadcasts; observed by the action center UI.
 *
 * Kept parallel to [NotificationFeed] so the overlay can bind both without a
 * binder hop. Receivers live in the plugin process (uid 1000) alongside the
 * UI, so no cross-process bridge is required.
 */
object QsTileStore {
    const val LABEL_WIFI = "wifi"
    const val LABEL_BLUETOOTH = "bluetooth"
    const val LABEL_DND = "dnd"

    private val _wifi = MutableStateFlow(QsState(LABEL_WIFI, false))
    val wifi: StateFlow<QsState> = _wifi.asStateFlow()

    private val _bluetooth = MutableStateFlow(QsState(LABEL_BLUETOOTH, false))
    val bluetooth: StateFlow<QsState> = _bluetooth.asStateFlow()

    private val _dnd = MutableStateFlow(QsState(LABEL_DND, false))
    val dnd: StateFlow<QsState> = _dnd.asStateFlow()

    fun setWifi(isOn: Boolean) {
        _wifi.value = QsState(LABEL_WIFI, isOn)
    }

    fun setBluetooth(isOn: Boolean) {
        _bluetooth.value = QsState(LABEL_BLUETOOTH, isOn)
    }

    fun setDnd(isOn: Boolean) {
        _dnd.value = QsState(LABEL_DND, isOn)
    }
}

/**
 * Binds [QsTileStore] to the relevant system broadcasts and seeds initial
 * state. Instantiated with the host SystemUI [Context] because the plugin
 * context chain does not own the broadcast-receiver registration and its
 * lifetime is tied to the plugin's onCreate / onDestroy.
 *
 * The controller also exposes [toggleWifi] / [toggleBluetooth] / [toggleDnd]
 * write-paths. Views reach it via [QsController.instance] — set in [start],
 * cleared in [stop] — so the composable tile doesn't need to be parameterized
 * on the controller.
 */
class QsController(private val hostContext: Context) {
    private val wifiManager: WifiManager? =
        hostContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (hostContext.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager)?.adapter
    private val notificationManager: NotificationManager? =
        hostContext.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN,
                    )
                    QsTileStore.setWifi(state == WifiManager.WIFI_STATE_ENABLED)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF,
                    )
                    QsTileStore.setBluetooth(state == BluetoothAdapter.STATE_ON)
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val filter = notificationManager?.currentInterruptionFilter
                    QsTileStore.setDnd(
                        filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL,
                    )
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        hostContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        seedState()
        instance = this
    }

    fun stop() {
        if (instance === this) instance = null
        try {
            hostContext.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "unregister without prior register", e)
        }
    }

    /**
     * Flip the Wi-Fi radio. SystemUI (uid 1000) holds
     * `android.permission.NETWORK_SETTINGS`, so the deprecated
     * [WifiManager.setWifiEnabled] is still callable here — the API-29+
     * restriction targets third-party callers. Broadcast receiver will
     * propagate the new state back into [QsTileStore].
     */
    fun toggleWifi() {
        val wm = wifiManager ?: return
        try {
            wm.setWifiEnabled(!wm.isWifiEnabled)
        } catch (e: SecurityException) {
            Log.e(TAG, "toggleWifi denied", e)
        }
    }

    /** Flip the Bluetooth radio via the privileged adapter API. */
    fun toggleBluetooth() {
        val ba = bluetoothAdapter ?: return
        try {
            if (ba.isEnabled) ba.disable() else ba.enable()
        } catch (e: SecurityException) {
            Log.e(TAG, "toggleBluetooth denied", e)
        }
    }

    /**
     * Flip DND between "allow all" and "priority only". SystemUI holds
     * `ACCESS_NOTIFICATION_POLICY`, so the setter is callable directly.
     */
    fun toggleDnd() {
        val nm = notificationManager ?: return
        try {
            val now = nm.currentInterruptionFilter
            nm.setInterruptionFilter(
                if (now == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                },
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "toggleDnd denied", e)
        }
    }

    private fun seedState() {
        wifiManager?.let {
            QsTileStore.setWifi(it.wifiState == WifiManager.WIFI_STATE_ENABLED)
        }
        bluetoothAdapter?.let { QsTileStore.setBluetooth(it.isEnabled) }
        notificationManager?.let {
            QsTileStore.setDnd(
                it.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            )
        }
    }

    companion object {
        private const val TAG = "QsController"

        @Volatile
        var instance: QsController? = null
            private set
    }
}
