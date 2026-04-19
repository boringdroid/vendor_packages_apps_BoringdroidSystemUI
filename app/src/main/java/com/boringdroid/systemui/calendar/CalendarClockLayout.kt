// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.calendar

import android.content.Context
import android.text.format.DateFormat
import android.widget.FrameLayout
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

/**
 * Compose-based Calendar & Clock panel (M5.5) per the M3 Expressive design. Mirrors
 * `.bd-calendar` in `project/BoringdroidSystemUI.html`:
 *  - Big time + long-date header (`.cal-clock`).
 *  - Month header with prev/next chevrons (`.cal-head`).
 *  - 7-column day grid with dow labels, today highlight, event dot (`.cal-grid`).
 *  - "Today's agenda" list (`.cal-agenda`) backed by `CalendarEventFeed`.
 *
 * Event data flows from [CalendarEventLoader] → [CalendarEventFeed.flow]; the loader is owned
 * by [CalendarClockWindow] so the panel itself stays hermetic to recomposition.
 */
@OptIn(ExperimentalComposeUiApi::class)
class CalendarClockLayout(context: Context) : FrameLayout(context) {
    private val composeView: ComposeView = ComposeView(context)

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.setContent { BdExpressiveMaterialTheme { CalendarClockPanel() } }
        addView(composeView)
    }
}

/**
 * Compose writes the raw testTag into `AccessibilityNodeInfo.setViewIdResourceName` when
 * `testTagsAsResourceId = true`. Prefix every testTag with `pkg:id/` so UiAutomator's
 * `By.res(pkg, id)` matches — same convention as the taskbar / action center.
 */
private const val ID = "com.boringdroid.systemui:id/"

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CalendarClockPanel() {
    val colors = MaterialTheme.colorScheme
    // The selected month is stored as the first-of-month timestamp; month navigation mutates it.
    var monthAnchor by remember {
        mutableStateOf(startOfMonth(System.currentTimeMillis()))
    }
    Surface(
        modifier =
            Modifier.fillMaxSize().semantics {
                testTagsAsResourceId = true
                testTag = ID + "calendar_clock_panel"
            },
        color = colors.surface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CalendarClockHeader()
            MonthHeader(
                monthAnchor = monthAnchor,
                onPrev = { monthAnchor = CalendarGridHelpers.shiftMonth(monthAnchor, -1) },
                onNext = { monthAnchor = CalendarGridHelpers.shiftMonth(monthAnchor, +1) },
            )
            MonthGrid(monthAnchor = monthAnchor)
            AgendaList(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CalendarClockHeader() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // Tick every 30 s — same cadence as the action center clock; the panel is transient and the
    // worst-case HH:mm skew is half a minute.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val dateFormat = remember(context) { DateFormat.getLongDateFormat(context) }
    val date = remember(now) { Date(now) }
    Column(
        modifier =
            Modifier.fillMaxWidth().semantics {
                testTagsAsResourceId = true
                testTag = ID + "calendar_clock_header"
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

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MonthHeader(monthAnchor: Long, onPrev: () -> Unit, onNext: () -> Unit) {
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics {
                testTagsAsResourceId = true
                testTag = ID + "calendar_month_header"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrev,
            modifier =
                Modifier.semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "calendar_prev_month"
                },
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = monthFormat.format(Date(monthAnchor)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.weight(1f).semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "calendar_month_label"
                },
        )
        IconButton(
            onClick = onNext,
            modifier =
                Modifier.semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "calendar_next_month"
                },
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MonthGrid(monthAnchor: Long) {
    val events by CalendarEventFeed.flow.collectAsState()
    val gridStart = remember(monthAnchor) { CalendarGridHelpers.monthGridStart(monthAnchor) }
    val eventDaySet =
        remember(events) {
            events.asSequence().map { CalendarGridHelpers.startOfDay(it.beginMillis) }.toHashSet()
        }
    val today = remember(monthAnchor) { CalendarGridHelpers.startOfDay(System.currentTimeMillis()) }
    val selectedMonth = remember(monthAnchor) { monthOf(monthAnchor) }
    val dowLabels = remember { buildDowLabels() }
    Column(
        modifier =
            Modifier.fillMaxWidth().semantics {
                testTagsAsResourceId = true
                testTag = ID + "calendar_grid"
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dowLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }
        // 6 weeks × 7 days. Hard-coding the 42-cell grid avoids nested LazyGrid inside the
        // non-scrolling panel Column (which would need explicit height constraints).
        for (week in 0 until 6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (dow in 0 until 7) {
                    val dayMillis = gridStart + (week * 7 + dow) * CalendarGridHelpers.DAY_MILLIS
                    DayCell(
                        dayMillis = dayMillis,
                        inSelectedMonth = monthOf(dayMillis) == selectedMonth,
                        isToday = dayMillis == today,
                        hasEvent = eventDaySet.contains(dayMillis),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun DayCell(
    dayMillis: Long,
    inSelectedMonth: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val day = remember(dayMillis) { dayOfMonth(dayMillis) }
    val textColor =
        when {
            isToday -> colors.onPrimary
            !inSelectedMonth -> colors.onSurface.copy(alpha = 0.35f)
            else -> colors.onSurface
        }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .then(if (isToday) Modifier.background(colors.primary) else Modifier)
                .clickable(enabled = false) {}
                .clearAndSetSemantics {
                    testTagsAsResourceId = true
                    testTag = ID + "cal_day_$day"
                    contentDescription =
                        buildString {
                            append("Day ")
                            append(day)
                            if (isToday) append(" today")
                            if (hasEvent) append(" has event")
                        }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
        )
        if (hasEvent && !isToday) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AgendaList(modifier: Modifier = Modifier) {
    val events by CalendarEventFeed.flow.collectAsState()
    val todayStart = remember { CalendarGridHelpers.startOfDay(System.currentTimeMillis()) }
    val todayEnd = remember { todayStart + CalendarGridHelpers.DAY_MILLIS }
    val todaysEvents =
        remember(events) {
            events.filter {
                // An instance belongs to "today" if it overlaps [todayStart, todayEnd).
                it.beginMillis < todayEnd && it.endMillis > todayStart
            }
        }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Today's agenda",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        LazyColumn(
            modifier =
                Modifier.fillMaxWidth().weight(1f).semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "calendar_agenda"
                },
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (todaysEvents.isEmpty()) {
                item { AgendaEmpty() }
            } else {
                items(todaysEvents, key = { it.instanceId }) { event -> AgendaRow(event) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AgendaEmpty() {
    Surface(
        modifier =
            Modifier.fillMaxWidth().semantics {
                testTagsAsResourceId = true
                testTag = ID + "calendar_agenda_empty"
            },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "No events today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AgendaRow(event: CalendarEvent) {
    val colors = MaterialTheme.colorScheme
    val dot =
        remember(event.colorArgb) {
            event.colorArgb?.let { Color(it or 0xFF000000.toInt()) } ?: Color.Unspecified
        }
    val timeLabel = remember(event.beginMillis, event.endMillis, event.allDay) { timeLabel(event) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier.padding(top = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (dot == Color.Unspecified) colors.primary else dot)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title ?: "(No title)",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.semantics {
                            testTagsAsResourceId = true
                            testTag = ID + "agenda_item_title"
                        },
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier =
                        Modifier.semantics {
                            testTagsAsResourceId = true
                            testTag = ID + "agenda_item_time"
                        },
                )
            }
        }
    }
}

private fun timeLabel(event: CalendarEvent): String {
    if (event.allDay) return "All day"
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${fmt.format(Date(event.beginMillis))} – ${fmt.format(Date(event.endMillis))}"
}

private fun startOfMonth(millis: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = millis
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun monthOf(millis: Long): Int {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = millis
    return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
}

private fun dayOfMonth(millis: Long): Int {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = millis
    return cal.get(Calendar.DAY_OF_MONTH)
}

private fun buildDowLabels(): List<String> {
    // Sunday-first ordering matches CalendarGridHelpers.monthGridStart.
    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.set(Calendar.YEAR, 2024)
    cal.set(Calendar.MONTH, Calendar.JANUARY)
    cal.set(Calendar.DAY_OF_MONTH, 7) // a Sunday
    val labels = mutableListOf<String>()
    for (i in 0 until 7) {
        labels += fmt.format(cal.time).take(2)
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return labels
}
