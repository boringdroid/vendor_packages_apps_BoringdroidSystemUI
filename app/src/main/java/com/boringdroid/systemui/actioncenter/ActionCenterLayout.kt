// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.boringdroid.systemui.R

/**
 * FrameLayout wrapper around a ComposeView that renders the action center's
 * two stacked sections: a [QsTileStore]-driven grid of quick-settings tiles
 * and a [NotificationFeed.flow]-driven list of notification rows.
 *
 * Each tile inflates [R.layout.layout_qs_tile] and is assigned a stable
 * Android resource id (`qs_wifi`, `qs_bluetooth`, `qs_dnd`) so UiAutomator
 * `By.res` can address it. `contentDescription` reflects on/off state.
 * Notification rows keep their existing [R.id.notification_title] /
 * [R.id.notification_body] ids.
 */
class ActionCenterLayout(context: Context) : FrameLayout(context) {
    private val composeView: ComposeView = ComposeView(context)

    init {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.setContent { ActionCenterContent() }
        addView(composeView)
    }

    @Composable
    private fun ActionCenterContent() {
        Column(modifier = Modifier.fillMaxSize()) {
            QsRow()
            NotificationList()
        }
    }

    @Composable
    private fun QsRow() {
        val wifi by QsTileStore.wifi.collectAsState()
        val bt by QsTileStore.bluetooth.collectAsState()
        val dnd by QsTileStore.dnd.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QsTile(id = R.id.qs_wifi, state = wifi) { QsController.instance?.toggleWifi() }
            QsTile(id = R.id.qs_bluetooth, state = bt) { QsController.instance?.toggleBluetooth() }
            QsTile(id = R.id.qs_dnd, state = dnd) { QsController.instance?.toggleDnd() }
        }
    }

    @Composable
    private fun QsTile(id: Int, state: QsState, onToggle: () -> Unit) {
        AndroidView(
            factory = { ctx ->
                val view =
                    LayoutInflater.from(ctx).inflate(R.layout.layout_qs_tile, null) as TextView
                view.id = id
                view.setOnClickListener { onToggle() }
                view
            },
            update = { view ->
                view.text = state.label
                view.contentDescription = state.contentDescription
                view.alpha = if (state.isOn) 1.0f else 0.5f
            },
        )
    }

    @Composable
    private fun NotificationList() {
        val items by NotificationFeed.flow.collectAsState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
        ) {
            items(items, key = { it.key }) { item ->
                AndroidView(
                    factory = { ctx ->
                        LayoutInflater.from(ctx)
                            .inflate(R.layout.layout_notification_row, null) as ViewGroup
                    },
                    update = { row ->
                        row.findViewById<TextView>(R.id.notification_title).text =
                            item.title ?: item.packageName
                        row.findViewById<TextView>(R.id.notification_body).text =
                            item.body ?: ""
                    },
                )
            }
        }
    }
}
