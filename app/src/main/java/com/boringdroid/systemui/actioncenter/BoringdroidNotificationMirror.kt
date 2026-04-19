// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Mirrors every active [StatusBarNotification] into [NotificationFeed].
 *
 * Bound by `system_server` once the user grants notification-listener access (auto-granted on
 * userdebug via `cmd notification allow_listener
 * com.boringdroid.systemui/.actioncenter.BoringdroidNotificationMirror`).
 *
 * The service runs in `com.boringdroid.systemui`'s own process while the action-center UI runs
 * inside the host SystemUI process (the plugin is loaded by SystemUI's classloader).
 * [NotificationFeed] is a per-process `object`, so writes here only update THIS process's copy. To
 * keep the UI-side copy in sync we broadcast each change per [NotificationFeedIpc]; the overlay
 * registers a matching receiver in [com.boringdroid.systemui.SystemUIOverlay].
 */
class BoringdroidNotificationMirror : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        val seed = activeNotifications?.map { it.toSummary() }.orEmpty()
        NotificationFeed.replaceAll(seed)
        sendBroadcast(Intent(NotificationFeedIpc.ACTION_FEED_RESET))
        seed.forEach { broadcastPosted(it) }
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onListenerConnected — seeded ${seed.size} notifications")
        }
    }

    override fun onListenerDisconnected() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onListenerDisconnected — clearing feed")
        }
        NotificationFeed.clear()
        sendBroadcast(Intent(NotificationFeedIpc.ACTION_FEED_CLEAR))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val summary = sbn.toSummary()
        NotificationFeed.upsert(summary)
        broadcastPosted(summary)
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                TAG,
                "onNotificationPosted key=${summary.key} title=${summary.title} " +
                    "feedSize=${NotificationFeed.flow.value.size}",
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NotificationFeed.remove(sbn.key)
        sendBroadcast(
            Intent(NotificationFeedIpc.ACTION_NOTIFICATION_REMOVED).apply {
                putExtra(NotificationFeedIpc.EXTRA_KEY, sbn.key)
            }
        )
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                TAG,
                "onNotificationRemoved key=${sbn.key} " +
                    "feedSize=${NotificationFeed.flow.value.size}",
            )
        }
    }

    private fun broadcastPosted(summary: SbnSummary) {
        sendBroadcast(
            Intent(NotificationFeedIpc.ACTION_NOTIFICATION_POSTED).apply {
                putExtra(NotificationFeedIpc.EXTRA_KEY, summary.key)
                putExtra(NotificationFeedIpc.EXTRA_PACKAGE_NAME, summary.packageName)
                putExtra(NotificationFeedIpc.EXTRA_TITLE, summary.title)
                putExtra(NotificationFeedIpc.EXTRA_BODY, summary.body)
                putExtra(NotificationFeedIpc.EXTRA_POST_TIME, summary.postTime)
                putExtra(NotificationFeedIpc.EXTRA_IS_ONGOING, summary.isOngoing)
            }
        )
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        val items = NotificationFeed.flow.value
        writer.println("BoringdroidNotificationMirror — feed size=${items.size}")
        items.forEach { writer.println("  ${it.key} pkg=${it.packageName} title=${it.title}") }
    }

    private fun StatusBarNotification.toSummary(): SbnSummary {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return SbnSummary(
            key = key,
            packageName = packageName,
            title = title,
            body = body,
            postTime = postTime,
            smallIcon = notification.smallIcon,
            contentIntent = notification.contentIntent,
            isOngoing = isOngoing,
        )
    }

    companion object {
        private const val TAG = "BdNotifMirror"
    }
}
