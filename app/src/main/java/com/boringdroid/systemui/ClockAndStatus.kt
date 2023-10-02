package com.boringdroid.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockAndStatus(
    private val textView: TextView,
    private val progressBarBattery: ProgressBar,
    private val textViewBatteryPercent: TextView,
    private val WifiStatus: ImageView,
    private val context: Context,
) {
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable =
        object : Runnable {
            override fun run() {
                updateTimeAndBattery()
                updateWifiStrength()
                handler.postDelayed(this, 1000) // Update every 1 second
            }
        }

    private val batteryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    updateBatteryLevel(intent)
                }
            }
        }

    private val wifiReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == WifiManager.RSSI_CHANGED_ACTION) {
                    updateWifiStrength()
                }
            }
        }

    fun startUpdatingTimeAndStatus() {
        handler.post(updateRunnable)
        registerBatteryReceiver()
        registerWifiReceiver()
    }

    fun stopUpdatingTimeAndStatus() {
        handler.removeCallbacks(updateRunnable)
        unregisterBatteryReceiver()
        unregisterWifiReceiver()
    }

    private fun updateTimeAndBattery() {
        updateTime()
        updateBatteryLevel(getBatteryIntent())
    }

    private fun updateTime() {
        val currentTime = System.currentTimeMillis()
        val formattedTime = dateFormat.format(Date(currentTime))
        textView.text = formattedTime
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        context.unregisterReceiver(batteryReceiver)
    }

    private fun getBatteryIntent(): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return context.registerReceiver(null, filter)
    }

    private fun updateBatteryLevel(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryLevel = (level?.toFloat() ?: 0f) / (scale?.toFloat() ?: 1f) * 100
        progressBarBattery.progress = batteryLevel.toInt()
        textViewBatteryPercent.text = "${batteryLevel.toInt()}%"
    }

    private fun registerWifiReceiver() {
        val filter = IntentFilter(WifiManager.RSSI_CHANGED_ACTION)
        context.registerReceiver(wifiReceiver, filter)
    }

    private fun unregisterWifiReceiver() {
        context.unregisterReceiver(wifiReceiver)
    }

    private fun updateWifiStrength() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val rssi = wifiInfo?.rssi ?: 0

        // Convert RSSI to percentage
        val maxRssi = -40
        val minRssi = -100
        val wifiLevel = 100 * (rssi - minRssi) / (maxRssi - minRssi)

        val wifiDrawable = getWifiStrengthDrawable(wifiLevel)

        if (wifiManager?.isWifiEnabled == true && wifiManager.connectionInfo.supplicantState == SupplicantState.COMPLETED) {
            WifiStatus.setImageDrawable(wifiDrawable)
            WifiStatus.visibility = View.VISIBLE
        } else {
            WifiStatus.visibility = View.INVISIBLE
        }
    }

    private fun getWifiStrengthDrawable(wifiLevel: Int): Drawable? {
        val wifiLevelResourceId =
            when {
                wifiLevel >= 75 -> R.drawable.ic_wifi_signal_4
                wifiLevel >= 50 -> R.drawable.ic_wifi_signal_3
                wifiLevel >= 25 -> R.drawable.ic_wifi_signal_2
                else -> R.drawable.ic_wifi_signal_1
            }
        val drawable = ContextCompat.getDrawable(context, wifiLevelResourceId)
        drawable?.setColorFilter(ContextCompat.getColor(context, R.color.wifi_strength_color), PorterDuff.Mode.SRC_IN)
        return drawable
    }
}
