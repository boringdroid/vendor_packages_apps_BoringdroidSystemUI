// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Compose-based Action Center panel per the M3 Expressive spec (M5.4).
 *
 * Composition:
 * - A large clock/date header.
 * - A 3x3 grid of named quick-settings tiles backed by [QsTileStore].
 * - An optional now-playing media card gated on [QsController.mediaSession].
 * - A [LazyColumn] of [NotificationFeed] rows with a bottom "Clear all" button that fires
 *   [NotificationFeedIpc.ACTION_CLEAR_ALL].
 *
 * The existing test surface (`qs_wifi` / `qs_bluetooth` / `qs_dnd` / `notification_title` /
 * `notification_body`) is preserved so the pre-existing suite continues to match the same
 * `By.res(PLUGIN_PKG, …)` selectors. The outer `R.id.action_center_root` wrapper lives in
 * [ActionCenterWindow] and is untouched.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ActionCenterLayout(context: Context) : FrameLayout(context) {
    private val composeView: ComposeView = ComposeView(context)

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.setContent { BdExpressiveMaterialTheme { ActionCenterPanel() } }
        addView(composeView)
    }
}

/**
 * Compose writes the raw testTag into `AccessibilityNodeInfo.setViewIdResourceName` when
 * `testTagsAsResourceId = true`. Prefix every testTag with `pkg:id/` so UiAutomator's
 * `By.res(pkg, id)` matches — same convention as [com.boringdroid.systemui.taskbar.Taskbar].
 */
private const val ID = "com.boringdroid.systemui:id/"

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ActionCenterPanel() {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier =
            Modifier.fillMaxSize().semantics {
                testTagsAsResourceId = true
                testTag = ID + "action_center_panel"
            },
        color = colors.surface,
        // `extraLarge` defaults to 28dp, which reads as a bubbly, oversized corner on the
        // compact 320×680 panel. `large` (16dp) matches the design's `.bd-notif-panel`
        // rounding and keeps the outer edge consistent with the inner QS tiles.
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimeHeader()
            QsGrid()
            MediaCard()
            // weight(1f) anchors the notification list to the remaining panel space so the
            // "Clear all" button at its bottom edge is always on-screen — UiAutomator finds
            // nodes by a11y tree but filters on visibleToUser, so an off-screen button is
            // effectively unreachable.
            NotificationList(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TimeHeader() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // Tick every 30s so HH:mm stays current without draining idle frames. The panel is transient,
    // so the worst-case skew is half a minute and only while the panel is open.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val dateFormat = remember(context) { DateFormat.getLongDateFormat(context) }
    val date = remember(now) { Date(now) }
    Column(
        modifier =
            Modifier.fillMaxWidth().semantics {
                testTagsAsResourceId = true
                testTag = ID + "bd_time_header"
            }
    ) {
        Text(
            text = timeFormat.format(date),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = dateFormat.format(date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The eight [QsTile]s that form the Expressive grid. Only Wi-Fi, Bluetooth, and Do Not Disturb are
 * wired to real radios via [QsController]; the rest are visual placeholders whose toggles log and
 * no-op until the follow-up milestone lands their system bindings.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun QsGrid() {
    val wifi by QsTileStore.wifi.collectAsState()
    val bt by QsTileStore.bluetooth.collectAsState()
    val dnd by QsTileStore.dnd.collectAsState()
    val autoRotate by QsTileStore.autoRotate.collectAsState()
    val airplane by QsTileStore.airplane.collectAsState()
    val batterySaver by QsTileStore.batterySaver.collectAsState()
    val nightLight by QsTileStore.nightLight.collectAsState()
    val hotspot by QsTileStore.hotspot.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.fillMaxWidth().height(300.dp).semantics {
                testTagsAsResourceId = true
                testTag = ID + "qs_grid"
            },
        userScrollEnabled = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        item {
            QsTile("qs_wifi", Icons.Filled.Wifi, "Wi-Fi", wifi) {
                QsController.instance?.toggleWifi()
            }
        }
        item {
            QsTile("qs_bluetooth", Icons.Filled.Bluetooth, "Bluetooth", bt) {
                QsController.instance?.toggleBluetooth()
            }
        }
        item {
            QsTile("qs_dnd", Icons.Filled.DoNotDisturb, "DND", dnd) {
                QsController.instance?.toggleDnd()
            }
        }
        item {
            QsTile("qs_auto_rotate", Icons.Filled.ScreenRotation, "Rotate", autoRotate) {
                QsController.instance?.toggleAutoRotate()
            }
        }
        item {
            QsTile("qs_airplane", Icons.Filled.AirplanemodeActive, "Airplane", airplane) {
                QsController.instance?.toggleAirplane()
            }
        }
        item {
            QsTile(
                "qs_battery_saver",
                Icons.Filled.BatteryChargingFull,
                "Battery saver",
                batterySaver,
            ) {
                QsController.instance?.toggleBatterySaver()
            }
        }
        item {
            QsTile("qs_night_light", Icons.Filled.NightsStay, "Night light", nightLight) {
                QsController.instance?.toggleNightLight()
            }
        }
        item {
            QsTile("qs_hotspot", Icons.Filled.SignalWifi4Bar, "Hotspot", hotspot) {
                QsController.instance?.toggleHotspot()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun QsTile(
    tag: String,
    icon: ImageVector,
    label: String,
    state: QsState,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val surface = if (state.isOn) colors.primaryContainer else colors.surfaceVariant
    val content = if (state.isOn) colors.onPrimaryContainer else colors.onSurfaceVariant
    // `clearAndSetSemantics` is the only reliable way to produce a single a11y node for this
    // tile: both `Modifier.clickable` and a plain `Modifier.semantics { ... }` expose their
    // properties as separate `AccessibilityNodeInfo`s (even when the semantics are merged for
    // screen-reader purposes, the on-screen a11y tree still has two nodes). UiAutomator
    // selectors like `By.res(PLUGIN_PKG, "qs_wifi").descContains("on")` match only when both
    // properties sit on the same node, so we clear descendant semantics and publish exactly
    // the testTag + contentDescription we need. The click is still wired through
    // `Modifier.clickable` above — the touch dispatcher is independent of the a11y tree, and
    // UiAutomator's `.click()` uses a raw tap at the node's bounds, not an a11y action.
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(surface)
                .clickable { onToggle() }
                .clearAndSetSemantics {
                    testTagsAsResourceId = true
                    testTag = ID + tag
                    contentDescription = state.contentDescription
                }
                .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content)
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Now-playing card. Visible only while [QsController.mediaSession] emits non-null. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MediaCard() {
    val media by QsController.mediaSession.collectAsState()
    AnimatedVisibility(visible = media != null) {
        val info = media ?: return@AnimatedVisibility
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "media_card"
                },
            color = colors.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!info.artist.isNullOrEmpty()) {
                        Text(
                            text = info.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector =
                            if (info.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription =
                            if (info.isPlaying) "Pause playback" else "Start playback",
                        tint = colors.onSecondaryContainer,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skip to next",
                        tint = colors.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun NotificationList(modifier: Modifier = Modifier) {
    val items by NotificationFeed.flow.collectAsState()
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier =
                Modifier.fillMaxWidth().weight(1f).semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "notif_list"
                },
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.key }) { item -> NotificationRow(item) }
        }
        if (items.isNotEmpty()) {
            TextButton(
                onClick = {
                    context.sendBroadcast(Intent(NotificationFeedIpc.ACTION_CLEAR_ALL))
                    NotificationFeed.clear()
                },
                modifier =
                    Modifier.align(Alignment.End).semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "clear_all_button"
                    },
            ) {
                Text(text = "Clear all")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun NotificationRow(item: SbnSummary) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = item.title ?: item.packageName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "notification_title"
                    },
            )
            Text(
                text = item.body ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "notification_body"
                    },
            )
        }
    }
}
