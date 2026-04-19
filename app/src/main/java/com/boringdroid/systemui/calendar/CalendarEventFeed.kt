// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.calendar

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.CalendarContract
import android.util.Log
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable snapshot of a single calendar instance for binding into the Compose-driven calendar
 * panel. One [CalendarEvent] corresponds to one occurrence (including a specific occurrence of a
 * recurring event) via `CalendarContract.Instances`.
 */
data class CalendarEvent(
    val instanceId: Long,
    val eventId: Long,
    val title: String?,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val colorArgb: Int?,
)

/**
 * Process-wide store of the calendar events that back the Calendar & Clock panel. Observed as a
 * [StateFlow]; loaded by [CalendarEventLoader] on a background thread and swapped in atomically.
 *
 * Kept parallel to `NotificationFeed` / `QsTileStore` so the panel can bind without a binder hop.
 */
object CalendarEventFeed {
    private val _flow = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val flow: StateFlow<List<CalendarEvent>> = _flow.asStateFlow()

    fun replaceAll(events: List<CalendarEvent>) {
        _flow.value = events.sortedBy { it.beginMillis }
    }

    fun clear() {
        _flow.value = emptyList()
    }
}

/**
 * Queries `CalendarContract.Instances` on a worker thread and pushes results into
 * [CalendarEventFeed]. The plugin runs in the SystemUI process (`uid = 1000`), which holds
 * `READ_CALENDAR` by signature on userdebug, so no runtime grant is required.
 *
 * The loader holds its own single-thread [HandlerThread] rather than using AsyncTask or Dispatchers
 * so the query path stays independent of the Compose main-thread dispatcher — the panel can dismiss
 * while a query is in flight without cancellation subtleties.
 */
class CalendarEventLoader(private val context: Context) {
    private val thread = HandlerThread("bd-calendar-loader").also { it.start() }
    private val handler = Handler(thread.looper)

    /**
     * Load every instance whose begin-time falls in [[windowStartMillis], [windowEndMillis]]. The
     * result is pushed to [CalendarEventFeed.replaceAll] on the loader thread; callers observe via
     * [CalendarEventFeed.flow].
     */
    fun loadRange(windowStartMillis: Long, windowEndMillis: Long) {
        handler.post {
            val loaded =
                try {
                    queryInstances(windowStartMillis, windowEndMillis)
                } catch (t: Throwable) {
                    Log.e(TAG, "calendar query failed", t)
                    emptyList()
                }
            CalendarEventFeed.replaceAll(loaded)
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    private fun queryInstances(startMs: Long, endMs: Long): List<CalendarEvent> {
        if (
            context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CALENDAR not held; skipping query")
            return emptyList()
        }
        val uriBuilder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startMs)
        ContentUris.appendId(uriBuilder, endMs)
        val projection =
            arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
            )
        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uriBuilder.build(),
            projection,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        ).use { cursor ->
            if (cursor == null) return@use
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
            val eventCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
            while (cursor.moveToNext()) {
                val color = if (cursor.isNull(colorCol)) null else cursor.getInt(colorCol)
                events +=
                    CalendarEvent(
                        instanceId = cursor.getLong(idCol),
                        eventId = cursor.getLong(eventCol),
                        title = cursor.getString(titleCol),
                        beginMillis = cursor.getLong(beginCol),
                        endMillis = cursor.getLong(endCol),
                        allDay = cursor.getInt(allDayCol) != 0,
                        colorArgb = color,
                    )
            }
        }
        return events
    }

    companion object {
        private const val TAG = "CalendarEventLoader"
    }
}

/** Helpers for the month-grid view that don't belong on any single class. */
object CalendarGridHelpers {
    /** Begin-of-day timestamp (00:00:00.000) for [millis] in the device's default time zone. */
    fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Begin-of-day for the day after [millis]. */
    fun endOfDay(millis: Long): Long = startOfDay(millis) + DAY_MILLIS

    /** First day rendered in a month grid: the Sunday on or before the first of the month. */
    fun monthGridStart(yearMonthMillis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = yearMonthMillis
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val daysBack = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysBack)
        return cal.timeInMillis
    }

    /** Last millisecond rendered in a 6-row month grid starting at [gridStartMillis]. */
    fun monthGridEnd(gridStartMillis: Long): Long = gridStartMillis + GRID_DAYS * DAY_MILLIS - 1L

    /** Move the selected-month cursor by [monthsDelta] calendar months. */
    fun shiftMonth(yearMonthMillis: Long, monthsDelta: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = yearMonthMillis
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, monthsDelta)
        return cal.timeInMillis
    }

    const val GRID_DAYS = 42
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
