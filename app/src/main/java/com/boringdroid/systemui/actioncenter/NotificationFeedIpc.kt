// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.actioncenter

/**
 * Broadcast contract that carries [SbnSummary] updates from [BoringdroidNotificationMirror] (runs
 * in `com.boringdroid.systemui`, uid 10094) to [com.boringdroid.systemui.SystemUIOverlay] (runs in
 * the host SystemUI process, uid 1000). Both processes host their own copy of [NotificationFeed]
 * because Kotlin `object` state is per-process; the overlay's feed is kept in sync via these
 * broadcasts rather than by any shared-memory bridge.
 *
 * The actions are namespaced under the plugin's package so they are not meaningful to any other
 * app. RECEIVER_EXPORTED is required on the receive side (sender and receiver run in different
 * UIDs). Adding a signature-level custom permission to gate the receiver is a future hardening
 * step.
 */
object NotificationFeedIpc {
    const val ACTION_FEED_RESET = "com.boringdroid.systemui.action.NOTIFICATION_FEED_RESET"
    const val ACTION_NOTIFICATION_POSTED = "com.boringdroid.systemui.action.NOTIFICATION_POSTED"
    const val ACTION_NOTIFICATION_REMOVED = "com.boringdroid.systemui.action.NOTIFICATION_REMOVED"
    const val ACTION_FEED_CLEAR = "com.boringdroid.systemui.action.NOTIFICATION_FEED_CLEAR"

    const val EXTRA_KEY = "com.boringdroid.systemui.extra.KEY"
    const val EXTRA_PACKAGE_NAME = "com.boringdroid.systemui.extra.PACKAGE_NAME"
    const val EXTRA_TITLE = "com.boringdroid.systemui.extra.TITLE"
    const val EXTRA_BODY = "com.boringdroid.systemui.extra.BODY"
    const val EXTRA_POST_TIME = "com.boringdroid.systemui.extra.POST_TIME"
    const val EXTRA_IS_ONGOING = "com.boringdroid.systemui.extra.IS_ONGOING"
}
