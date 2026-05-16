package com.boringdroid.systemui.taskbar

import android.app.WindowConfiguration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberPlainTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme

/**
 * Compose's `testTagsAsResourceId=true` writes the raw testTag string into
 * `AccessibilityNodeInfo.setViewIdResourceName`. UiAutomator's `By.res(pkg, id)` matches the exact
 * string `pkg:id/id`, so prefixing every testTag with this constant is what keeps the existing
 * `By.res(PLUGIN_PKG, "…")` test surface working against the Compose taskbar.
 */
private const val ID = "com.boringdroid.systemui:id/"

/**
 * Callbacks the [Taskbar] composable needs from the hosting plugin. Hoisted out of the composable
 * so it doesn't directly touch Android service APIs — keeps recomposition hermetic and makes the
 * preview/testing path cheap.
 */
data class TaskbarCallbacks(
    val onStartClick: () -> Unit,
    val onSearchClick: () -> Unit,
    val onBellClick: () -> Unit,
    val onClockClick: () -> Unit,
    val onOverviewClick: () -> Unit,
    val onTaskClick: (BdTaskInfo) -> Unit,
    val onTaskClose: (BdTaskInfo) -> Unit,
    val onTaskMinimize: (BdTaskInfo) -> Unit,
    val onTaskMaximize: (BdTaskInfo) -> Unit,
)

/**
 * Root taskbar composable. Renders the three-column StartCluster | AppRail | Tray onto a
 * surface-container pill pinned to the bottom of the display by
 * [com.boringdroid.systemui.TaskbarWindow].
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun Taskbar(state: TaskbarState, callbacks: TaskbarCallbacks) {
    BdExpressiveMaterialTheme {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier =
                Modifier.fillMaxSize().semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "taskbar_root"
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainer.copy(alpha = 0.72f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StartCluster(
                    onStartClick = callbacks.onStartClick,
                    onSearchClick = callbacks.onSearchClick,
                )
                AppRail(
                    state = state,
                    onTaskClick = callbacks.onTaskClick,
                    onTaskClose = callbacks.onTaskClose,
                    onTaskMinimize = callbacks.onTaskMinimize,
                    onTaskMaximize = callbacks.onTaskMaximize,
                    modifier = Modifier.weight(1f),
                )
                Tray(
                    state = state,
                    onBellClick = callbacks.onBellClick,
                    onClockClick = callbacks.onClockClick,
                    onOverviewClick = callbacks.onOverviewClick,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun StartCluster(onStartClick: () -> Unit, onSearchClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
                    .clickable(onClick = onStartClick)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "bt_all_apps"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "Start",
                tint = colors.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Row(
            modifier =
                Modifier.height(48.dp)
                    .widthIn(min = 220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.onSurface.copy(alpha = 0.06f))
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 16.dp)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "tb_search"
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Search apps, files, settings",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRail(
    state: TaskbarState,
    onTaskClick: (BdTaskInfo) -> Unit,
    onTaskClose: (BdTaskInfo) -> Unit,
    onTaskMinimize: (BdTaskInfo) -> Unit,
    onTaskMaximize: (BdTaskInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks by state.tasks.collectAsState()
    val activeTaskId by state.activeTaskId.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        // Center running-app icons within the rail's expanded weight(1f) slot so the
        // taskbar reads as [Start ... Apps ... Tray] with the icons anchored at the middle
        // rather than hugging the left edge.
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(items = tasks, key = { it.id }) { task ->
            val taskIndex = tasks.indexOfFirst { it.id == task.id }
            AppRailItem(
                task = task,
                isActive = task.id == activeTaskId,
                onClick = {
                    onTaskClick(task)
                    if (taskIndex >= 0) {
                        scope.launch { ensureItemFullyVisible(listState, taskIndex) }
                    }
                },
                onClose = { onTaskClose(task) },
                onMinimize = { onTaskMinimize(task) },
                onMaximize = { onTaskMaximize(task) },
            )
        }
    }
}

/**
 * Scroll the LazyRow only when the target item is not already entirely within the viewport. A
 * fully-visible item is left in place — we don't want clicking a centred icon to shove it to
 * the leftmost slot. A clipped or off-screen item is brought just inside the visible edge so
 * the user can see the action they just triggered.
 */
private suspend fun ensureItemFullyVisible(state: LazyListState, index: Int) {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        state.animateScrollToItem(index)
        return
    }
    val viewportStart = info.viewportStartOffset
    val viewportEnd = info.viewportEndOffset
    val itemStart = item.offset
    val itemEnd = item.offset + item.size
    when {
        itemStart < viewportStart -> state.animateScrollBy((itemStart - viewportStart).toFloat())
        itemEnd > viewportEnd -> state.animateScrollBy((itemEnd - viewportEnd).toFloat())
    }
}

@Composable
@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
private fun AppRailItem(
    task: BdTaskInfo,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pillWidth by
        animateDpAsState(
            targetValue = if (isActive) 22.dp else 14.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "app_rail_pill",
        )
    val label = task.label?.toString()?.takeIf { it.isNotBlank() } ?: task.packageName
    val tooltipState = rememberPlainTooltipState()
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    // Material3 PlainTooltipBox drives both mouse-hover and long-press. Boringdroid is
    // desktop-first so the hover path is the primary one: pointing the mouse at a running-app
    // icon surfaces the app name in a plain tooltip above the taskbar. AOSP ships material3
    // 1.2.0-alpha04 which exposes PlainTooltipBox rather than the newer TooltipBox +
    // PlainTooltip split.
    PlainTooltipBox(
        tooltip = { Text(text = label) },
        tooltipState = tooltipState,
    ) {
        Box(
            modifier =
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isActive) {
                            Modifier.background(colors.primary.copy(alpha = 0.18f))
                        } else Modifier
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuExpanded = true },
                    )
                    .pointerInput(task.id) {
                        // Right-click handler. Covers two dispatch paths:
                        //   (1) Synthetic ACTION_DOWN with buttonState=BUTTON_SECONDARY from
                        //       UiAutomation.injectInputEvent — handled by awaitFirstDown.
                        //   (2) Real mouse right-click while the cursor is already hovering —
                        //       InputDispatcher fires ACTION_BUTTON_PRESS without a fresh
                        //       ACTION_DOWN, so awaitFirstDown alone misses it. We loop on
                        //       awaitPointerEvent and inspect every Press regardless of
                        //       whether it's a fresh down or just a button-state change.
                        // Default Main pass, so combinedClickable's gesture detector sees
                        // events first and processes left-click without interference; we
                        // only consume changes when isSecondaryPressed is actually set.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press &&
                                    event.buttons.isSecondaryPressed
                                ) {
                                    menuExpanded = true
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    .semantics {
                        testTagsAsResourceId = true
                        // Per-package testTag so instrumentation tests can target a specific
                        // running-app task by package even when the rail contains orphan
                        // tasks from earlier cases. Compose's `testTagsAsResourceId=true`
                        // writes this through to AccessibilityNodeInfo.viewIdResourceName,
                        // so the resource-id is literally "com.boringdroid.systemui:id/
                        // iv_task_info_icon__<pkg>" — addressable as
                        // `By.res(PLUGIN_PKG, "iv_task_info_icon__<pkg>")` from UiAutomator.
                        // The double underscore is a delimiter that can't appear inside a
                        // legal package name. Tests that don't care about which icon should
                        // use `By.res(Pattern.compile("iv_task_info_icon__.*"))`.
                        testTag = ID + "iv_task_info_icon__" + task.packageName
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                TaskIcon(task.icon, contentDescription = label)
            }
            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .width(pillWidth)
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(colors.primary)
            )
            TaskbarContextMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                isFullscreen = task.mode == WindowConfiguration.WINDOWING_MODE_FULLSCREEN,
                hasToken = task.token != null,
                onClose = {
                    menuExpanded = false
                    onClose()
                },
                onMinimize = {
                    menuExpanded = false
                    onMinimize()
                },
                onMaximize = {
                    menuExpanded = false
                    onMaximize()
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TaskbarContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isFullscreen: Boolean,
    hasToken: Boolean,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
) {
    // The taskbar lives at the bottom of the display, so DropdownMenu's auto-flip places the
    // menu above the icon (no room below). The default horizontal anchor is the icon's
    // start-edge; that pushes a ~180dp menu off the right side of the screen on a 48dp icon.
    // Half-icon (24dp) minus half the typical menu width (~90dp) shifts the menu's centre
    // over the icon's centre. Vertical -8dp adds a 8dp gap above the icon.
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = (-66).dp, y = (-8).dp),
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        DropdownMenuItem(
            text = { Text(if (isFullscreen) "Restore" else "Maximize") },
            leadingIcon = {
                Icon(
                    imageVector =
                        if (isFullscreen) Icons.Filled.CloseFullscreen
                        else Icons.Filled.OpenInFull,
                    contentDescription = null,
                )
            },
            enabled = hasToken,
            onClick = onMaximize,
            modifier = Modifier.semantics { testTag = ID + "taskbar_menu_maximize" },
        )
        DropdownMenuItem(
            text = { Text("Minimize") },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
            },
            enabled = hasToken,
            onClick = onMinimize,
            modifier = Modifier.semantics { testTag = ID + "taskbar_menu_minimize" },
        )
        DropdownMenuItem(
            text = { Text("Close") },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
            },
            enabled = hasToken,
            onClick = onClose,
            modifier = Modifier.semantics { testTag = ID + "taskbar_menu_close" },
        )
    }
}

@Composable
private fun TaskIcon(drawable: Drawable?, contentDescription: String) {
    val density = LocalDensity.current
    if (drawable == null) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        return
    }
    val px = with(density) { 28.dp.toPx().toInt().coerceAtLeast(1) }
    val bitmap =
        try {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            bmp
        } catch (t: Throwable) {
            null
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun Tray(
    state: TaskbarState,
    onBellClick: () -> Unit,
    onClockClick: () -> Unit,
    onOverviewClick: () -> Unit,
) {
    val time by state.time.collectAsState()
    val date by state.date.collectAsState()
    val battery by state.batteryPercent.collectAsState()
    val wifi by state.wifiLevel.collectAsState()
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier.size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    // Clicking the tray Wi-Fi indicator opens the Action Center, whose QS grid
                    // hosts the actual Wi-Fi toggle tile. Routes through onBellClick so the
                    // mutual-exclusion with Calendar/Overview stays centralised there.
                    .clickable(onClick = onBellClick)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "taskbar_wifi"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (wifi != null) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = if (wifi != null) "wifi on" else "wifi off",
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Row(
            modifier =
                Modifier.height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp)
                    .semantics(mergeDescendants = true) {
                        testTagsAsResourceId = true
                        testTag = ID + "textViewBatteryPercent"
                        text = AnnotatedString("$battery%")
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.BatteryStd,
                contentDescription = "battery",
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "$battery%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface,
            )
        }
        Box(
            modifier =
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOverviewClick)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "taskbar_recents"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.GridView,
                contentDescription = "recents",
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier =
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBellClick)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "action_center_bell"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "notifications",
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier =
                Modifier.heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClockClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "clock"
                        text = AnnotatedString(time)
                    },
            contentAlignment = Alignment.Center,
        ) {
            ClockStack(time = time, date = date)
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ClockStack(time: String, date: String) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.End,
        )
        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
