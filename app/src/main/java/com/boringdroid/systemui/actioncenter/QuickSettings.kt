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
 * Immutable snapshot of a quick-setting tile. Rendered by [ActionCenterLayout] and observed via
 * [QsTileStore]'s [StateFlow]s.
 *
 * The [contentDescription] convention — "<label> on" / "<label> off" — is the surface UiAutomator
 * addresses: `By.res(..., "qs_wifi").descContains("off")` is how external state changes are
 * validated without leaning on drawable identity.
 */
data class QsState(val label: String, val isOn: Boolean) {
    val contentDescription: String
        get() = if (isOn) "$label on" else "$label off"
}

/**
 * Process-wide store of quick-settings state. Updated by [QsController] in response to system
 * broadcasts; observed by the action center UI.
 *
 * Kept parallel to [NotificationFeed] so the overlay can bind both without a binder hop. Receivers
 * live in the plugin process (uid 1000) alongside the UI, so no cross-process bridge is required.
 */
object QsTileStore {
    const val LABEL_WIFI = "wifi"
    const val LABEL_BLUETOOTH = "bluetooth"
    const val LABEL_DND = "dnd"
    const val LABEL_FLASHLIGHT = "flashlight"
    const val LABEL_AUTO_ROTATE = "auto-rotate"
    const val LABEL_AIRPLANE = "airplane mode"
    const val LABEL_BATTERY_SAVER = "battery saver"
    const val LABEL_NIGHT_LIGHT = "night light"
    const val LABEL_HOTSPOT = "hotspot"

    private val _wifi = MutableStateFlow(QsState(LABEL_WIFI, false))
    val wifi: StateFlow<QsState> = _wifi.asStateFlow()

    private val _bluetooth = MutableStateFlow(QsState(LABEL_BLUETOOTH, false))
    val bluetooth: StateFlow<QsState> = _bluetooth.asStateFlow()

    private val _dnd = MutableStateFlow(QsState(LABEL_DND, false))
    val dnd: StateFlow<QsState> = _dnd.asStateFlow()

    // The next six tiles fill out the 3x3 Expressive grid. Their radio-level bindings are
    // staged post-M5.4 (toggles in [QsController] are placeholders that log and no-op).
    // Seeding them as "off" keeps the grid visually consistent until the controller lands.
    private val _flashlight = MutableStateFlow(QsState(LABEL_FLASHLIGHT, false))
    val flashlight: StateFlow<QsState> = _flashlight.asStateFlow()

    private val _autoRotate = MutableStateFlow(QsState(LABEL_AUTO_ROTATE, false))
    val autoRotate: StateFlow<QsState> = _autoRotate.asStateFlow()

    private val _airplane = MutableStateFlow(QsState(LABEL_AIRPLANE, false))
    val airplane: StateFlow<QsState> = _airplane.asStateFlow()

    private val _batterySaver = MutableStateFlow(QsState(LABEL_BATTERY_SAVER, false))
    val batterySaver: StateFlow<QsState> = _batterySaver.asStateFlow()

    private val _nightLight = MutableStateFlow(QsState(LABEL_NIGHT_LIGHT, false))
    val nightLight: StateFlow<QsState> = _nightLight.asStateFlow()

    private val _hotspot = MutableStateFlow(QsState(LABEL_HOTSPOT, false))
    val hotspot: StateFlow<QsState> = _hotspot.asStateFlow()

    fun setWifi(isOn: Boolean) {
        _wifi.value = QsState(LABEL_WIFI, isOn)
    }

    fun setBluetooth(isOn: Boolean) {
        _bluetooth.value = QsState(LABEL_BLUETOOTH, isOn)
    }

    fun setDnd(isOn: Boolean) {
        _dnd.value = QsState(LABEL_DND, isOn)
    }

    fun setFlashlight(isOn: Boolean) {
        _flashlight.value = QsState(LABEL_FLASHLIGHT, isOn)
    }

    fun setAutoRotate(isOn: Boolean) {
        _autoRotate.value = QsState(LABEL_AUTO_ROTATE, isOn)
    }

    fun setAirplane(isOn: Boolean) {
        _airplane.value = QsState(LABEL_AIRPLANE, isOn)
    }

    fun setBatterySaver(isOn: Boolean) {
        _batterySaver.value = QsState(LABEL_BATTERY_SAVER, isOn)
    }

    fun setNightLight(isOn: Boolean) {
        _nightLight.value = QsState(LABEL_NIGHT_LIGHT, isOn)
    }

    fun setHotspot(isOn: Boolean) {
        _hotspot.value = QsState(LABEL_HOTSPOT, isOn)
    }
}

/**
 * Now-playing snapshot surfaced by [QsController.mediaSession] to the action-center media card.
 *
 * Independent from Android's [android.media.session.MediaSession] because the card is
 * presentation-only — the mirror-side wiring that listens to the active `MediaController` and fills
 * this in lands in a follow-up. For M5.4 the flow emits `null` so the card renders nothing.
 */
data class MediaInfo(val title: String, val artist: String?, val isPlaying: Boolean)

/**
 * Binds [QsTileStore] to the relevant system broadcasts and seeds initial state. Instantiated with
 * the host SystemUI [Context] because the plugin context chain does not own the broadcast-receiver
 * registration and its lifetime is tied to the plugin's onCreate / onDestroy.
 *
 * The controller also exposes [toggleWifi] / [toggleBluetooth] / [toggleDnd] write-paths. Views
 * reach it via [QsController.instance] — set in [start], cleared in [stop] — so the composable tile
 * doesn't need to be parameterized on the controller.
 */
class QsController(private val hostContext: Context) {
    private val wifiManager: WifiManager? =
        hostContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (hostContext.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager)
            ?.adapter
    private val notificationManager: NotificationManager? =
        hostContext.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        val state =
                            intent.getIntExtra(
                                WifiManager.EXTRA_WIFI_STATE,
                                WifiManager.WIFI_STATE_UNKNOWN,
                            )
                        QsTileStore.setWifi(state == WifiManager.WIFI_STATE_ENABLED)
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state =
                            intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.STATE_OFF,
                            )
                        QsTileStore.setBluetooth(state == BluetoothAdapter.STATE_ON)
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        val filter = notificationManager?.currentInterruptionFilter
                        QsTileStore.setDnd(
                            filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL
                        )
                    }
                }
            }
        }

    fun start() {
        val filter =
            IntentFilter().apply {
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
     * Flip the Wi-Fi radio. SystemUI (uid 1000) holds `android.permission.NETWORK_SETTINGS`, so the
     * deprecated [WifiManager.setWifiEnabled] is still callable here — the API-29+ restriction
     * targets third-party callers. Broadcast receiver will propagate the new state back into
     * [QsTileStore].
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
     * Flip flashlight, auto-rotate, airplane mode, battery saver, night light, and hotspot.
     *
     * These six toggles exist to complete the 3x3 Expressive grid. The underlying system writes
     * (`CameraManager.setTorchMode`, `Settings.System.ACCELEROMETER_ROTATION`,
     * `ConnectivityManager.setAirplaneMode`, `PowerManager.setPowerSaveModeEnabled`, the color-mode
     * night-light binder API, and `WifiManager.startTethering`) each require separate permissions
     * and signature access paths. They land in a follow-up; for now each stub logs and no-ops so the
     * UI still renders active/inactive tiles without a crash if a user taps one.
     */
    fun toggleFlashlight() {
        Log.i(TAG, "toggleFlashlight: not yet implemented")
    }

    fun toggleAutoRotate() {
        Log.i(TAG, "toggleAutoRotate: not yet implemented")
    }

    fun toggleAirplane() {
        Log.i(TAG, "toggleAirplane: not yet implemented")
    }

    fun toggleBatterySaver() {
        Log.i(TAG, "toggleBatterySaver: not yet implemented")
    }

    fun toggleNightLight() {
        Log.i(TAG, "toggleNightLight: not yet implemented")
    }

    fun toggleHotspot() {
        Log.i(TAG, "toggleHotspot: not yet implemented")
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
                }
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "toggleDnd denied", e)
        }
    }

    private fun seedState() {
        wifiManager?.let { QsTileStore.setWifi(it.wifiState == WifiManager.WIFI_STATE_ENABLED) }
        bluetoothAdapter?.let { QsTileStore.setBluetooth(it.isEnabled) }
        notificationManager?.let {
            QsTileStore.setDnd(
                it.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
    }

    companion object {
        private const val TAG = "QsController"

        @Volatile
        var instance: QsController? = null
            private set

        private val _mediaSession = MutableStateFlow<MediaInfo?>(null)

        /**
         * Current now-playing snapshot, or `null` when nothing is playing. Observed by the
         * action-center media card; when `null` the card is hidden.
         */
        val mediaSession: StateFlow<MediaInfo?> = _mediaSession.asStateFlow()

        fun setMediaSession(info: MediaInfo?) {
            _mediaSession.value = info
        }
    }
}
