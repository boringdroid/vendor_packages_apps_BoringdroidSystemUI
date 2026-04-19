package com.boringdroid.systemui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Re-enables the [SystemUIOverlay] plugin component if it has been disabled.
 *
 * The SystemUI host disables a plugin component (`pm disable
 * com.boringdroid.systemui/.SystemUIOverlay`) when it crashes after loading
 * the plugin, on the assumption that the plugin caused the crash. Recovery
 * required a manual `adb shell pm enable ...`, which trapped both fresh
 * boots and ad-hoc `adb install -r BoringdroidSystemUI.apk` iterations.
 *
 * This receiver fires on BOOT_COMPLETED and on MY_PACKAGE_REPLACED. Both
 * events arrive on a different component than [SystemUIOverlay], so they
 * are still delivered when the overlay component is in the disabled state.
 * If the overlay component is currently disabled, flip it back to enabled
 * so the next SystemUI restart will pick up the plugin again.
 */
class PluginEnableReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.packageManager
        val component = ComponentName(context, SystemUIOverlay::class.java)
        val state = try {
            pm.getComponentEnabledSetting(component)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read component enabled state", t)
            return
        }

        if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            return
        }

        try {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            Log.i(TAG, "Re-enabled SystemUIOverlay (trigger=${intent.action})")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to re-enable SystemUIOverlay", t)
        }
    }

    companion object {
        private const val TAG = "BoringdroidPluginEnable"
    }
}
