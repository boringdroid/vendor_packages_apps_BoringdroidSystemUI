// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable snapshot of a [StatusBarNotification], suitable for binding into the
 * Compose-driven action center UI. Held by [NotificationFeed].
 *
 * `key` matches [StatusBarNotification.getKey] and is used as the row identity:
 * a re-post with the same key replaces the existing entry.
 */
data class SbnSummary(
    val key: String,
    val packageName: String,
    val title: String?,
    val body: String?,
    val postTime: Long,
    val smallIcon: Icon?,
    val contentIntent: PendingIntent?,
    val isOngoing: Boolean,
)

/**
 * Process-wide store of every [SbnSummary] currently posted to the system,
 * mirrored from [BoringdroidNotificationMirror]. The action center UI observes
 * [flow] and re-renders on change.
 *
 * Keeping this as an `object` (rather than a bound-service interface) lets the
 * UI subscribe without a binder hop — the listener service and the action
 * center window both live in the BoringdroidSystemUI plugin process.
 */
object NotificationFeed {
    private val _flow = MutableStateFlow<List<SbnSummary>>(emptyList())

    /** Most-recent-first list of active notifications. */
    val flow: StateFlow<List<SbnSummary>> = _flow.asStateFlow()

    /** Replace the entire feed (used by the listener's initial sync). */
    fun replaceAll(items: List<SbnSummary>) {
        _flow.value = items.sortedByDescending { it.postTime }
    }

    /** Insert or replace a single entry by [SbnSummary.key]. */
    fun upsert(item: SbnSummary) {
        val current = _flow.value
        val without = current.filterNot { it.key == item.key }
        _flow.value = (without + item).sortedByDescending { it.postTime }
    }

    /** Remove an entry by key. No-op if not present. */
    fun remove(key: String) {
        val current = _flow.value
        if (current.none { it.key == key }) return
        _flow.value = current.filterNot { it.key == key }
    }

    /** Drop every entry (listener disconnect / test reset). */
    fun clear() {
        _flow.value = emptyList()
    }
}
