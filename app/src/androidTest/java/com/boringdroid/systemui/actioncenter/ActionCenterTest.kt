package com.boringdroid.systemui.actioncenter

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.boringdroid.systemui.PluginBaselineTest.Companion.FIND_TIMEOUT_MS
import com.boringdroid.systemui.PluginBaselineTest.Companion.PLUGIN_PKG
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionCenterTest {
    private lateinit var device: UiDevice

    companion object {
        private const val MIRROR_COMPONENT =
            "com.boringdroid.systemui/.actioncenter.BoringdroidNotificationMirror"
    }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // The instrumentation runner force-stops the target package (com.boringdroid.systemui)
        // at the start of each run, which kills BoringdroidNotificationMirror's process. The
        // notification-listener framework does NOT auto-rebind after force-stop, so toggle the
        // grant to force a fresh bind; without this the mirror-side NotificationFeed stays
        // empty and cross-process broadcasts never fire.
        device.executeShellCommand("cmd notification disallow_listener $MIRROR_COMPONENT")
        device.executeShellCommand("cmd notification allow_listener $MIRROR_COMPONENT")
        // Poll dumpsys for the service to appear bound before proceeding.
        val deadline = System.currentTimeMillis() + FIND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val out = device.executeShellCommand("dumpsys activity service $MIRROR_COMPONENT")
            if (out.contains("BoringdroidNotificationMirror")) break
            Thread.sleep(200)
        }
        device.pressHome()
        device.waitForIdle()
        // A previous test in the same suite run can leave the action center open; the bell
        // click in the test body would then toggle it closed. Dismiss-if-open so every test
        // starts with a known-closed state. waitForIdle after the dismiss lets the
        // accessibility cache flush the previous overlay window's nodes — without it
        // findObject in the test body can return stale rows from the dismissed window.
        if (device.hasObject(By.res(PLUGIN_PKG, "action_center_root"))) {
            device.findObject(By.res(PLUGIN_PKG, "action_center_bell"))?.click()
            device.wait(Until.gone(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
            device.waitForIdle(1_000L)
        }
    }

    @Test
    fun bell_isVisibleInTaskbar() {
        assertThat(
                device.wait(
                    Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")),
                    FIND_TIMEOUT_MS,
                )
            )
            .isNotNull()
    }

    @Test
    fun tappingBell_opensActionCenter() {
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        assertThat(
                device.wait(
                    Until.findObject(By.res(PLUGIN_PKG, "action_center_root")),
                    FIND_TIMEOUT_MS,
                )
            )
            .isNotNull()
    }

    @Test
    fun actionCenter_showsPostedNotification() {
        // executeShellCommand does not run through `sh -c`, so quotes are preserved
        // verbatim in argv. Use unquoted unique tokens; a unique title sidesteps the
        // top-9-viewport drift that bites quoted shared-tag posts.
        device.executeShellCommand(
            "cmd notification post -S bigtext -t bdSinglePostTitle bdSinglePostTag body"
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        device.wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
        val matches = pollForMatchingTitles("bdSinglePostTitle", expectedSize = 1)
        assertThat(matches).hasSize(1)
    }

    @Test
    fun wifiToggle_reflectsExternalState() {
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        val wifi = device.wait(Until.findObject(By.res(PLUGIN_PKG, "qs_wifi")), FIND_TIMEOUT_MS)
        assertThat(wifi).isNotNull()
        device.executeShellCommand("svc wifi disable")
        device.wait(Until.hasObject(By.res(PLUGIN_PKG, "qs_wifi").descContains("off")), 3_000L)
        device.executeShellCommand("svc wifi enable")
    }

    @Test
    fun wifiTile_toggleFlipsState() {
        device.executeShellCommand("svc wifi enable")
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        val wifi =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "qs_wifi").descContains("on")),
                FIND_TIMEOUT_MS,
            )
        assertThat(wifi).isNotNull()
        wifi.click()
        assertThat(
                device.wait(
                    Until.hasObject(By.res(PLUGIN_PKG, "qs_wifi").descContains("off")),
                    5_000L,
                )
            )
            .isTrue()
        device.executeShellCommand("svc wifi enable")
    }

    /**
     * Re-posting a notification with the same (package, id, tag) triple collapses to one row. The
     * mirror calls [NotificationFeed.upsert] per post; the plugin-side broadcast receiver calls
     * `upsert` on each `ACTION_NOTIFICATION_POSTED`; the LazyColumn de-duplicates by `key = {
     * it.key }`. This test exercises all three legs of the coherence bridge converging on a single
     * row.
     */
    @Test
    fun diagnostic_dumpVisibleNotificationTitles() {
        device.executeShellCommand(
            "cmd notification post -S bigtext -t bdDiagTitleQQ bdDiagTag body"
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        device.wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
        Thread.sleep(1000L)
        val titles = device.findObjects(By.res(PLUGIN_PKG, "notification_title"))
        Log.v(
            "BdDiag",
            "notification_title count=${titles.size}; " +
                "texts=${titles.joinToString(separator = "|") { "[" + (it.text ?: "null") + "]" }}",
        )
        val bodies = device.findObjects(By.res(PLUGIN_PKG, "notification_body"))
        Log.v(
            "BdDiag",
            "notification_body count=${bodies.size}; " +
                "texts=${bodies.joinToString(separator = "|") { "[" + (it.text ?: "null") + "]" }}",
        )
    }

    @Test
    fun noDuplicateRendering_samePackageAndTag() {
        // UiDevice.executeShellCommand does not run through `sh`, so single quotes are
        // preserved verbatim in argv (see `cmd notification list`). Pass the title/tag/body
        // unquoted so the argv tokens are clean.
        device.executeShellCommand(
            "cmd notification post -S bigtext -t bdDupTitleXyz bdDupTag firstBody"
        )
        device.executeShellCommand(
            "cmd notification post -S bigtext -t bdDupTitleXyz bdDupTag secondBody"
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        // Gate on the overlay root before querying rows: UiAutomator's accessibility cache
        // settles a frame after the window is added, and without this gate the first
        // findObject() on notification_title races the initial Compose lay-out.
        device.wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
        // Poll for the expected row (up to 5s) instead of fixed 1s sleep — under suite-run
        // the bridge upsert can land >1s after the open as the listener finishes rebinding.
        val matches = pollForMatchingTitles("bdDupTitleXyz", expectedSize = 1)
        Log.v(
            "BdDiag",
            "noDuplicate matches=${matches.size} " +
                "allTitles=${device.findObjects(By.res(PLUGIN_PKG, "notification_title"))
                    .joinToString("|") { "[" + (it.text ?: "null") + "]" }}",
        )
        assertThat(matches).hasSize(1)
    }

    /**
     * Polls [findObjects] for `notification_title` rows whose text equals [text] until
     * `expectedSize` matches are found or the timeout expires. Closing and re-opening the action
     * center between polls both forces a fresh Compose composition and flushes UiAutomator's
     * accessibility cache — the LazyColumn recomposes to the correct row count on every
     * `NotificationFeed.flow` emission, but the a11y snapshot that `findObjects` reads from can
     * retain stale rows for several seconds after rows are detached. Close-reopen resolves both the
     * recomposition and the cache lag, so the same helper works for presence (`expectedSize > 0`)
     * and absence (`expectedSize = 0`) assertions.
     */
    private fun pollForMatchingTitles(
        text: String,
        expectedSize: Int,
        timeoutMs: Long = 5_000L,
    ): List<androidx.test.uiautomator.UiObject2> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var matches = emptyList<androidx.test.uiautomator.UiObject2>()
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250L)
            matches = device.findObjects(By.res(PLUGIN_PKG, "notification_title").text(text))
            if (matches.size == expectedSize) return matches
            // Force a fresh composition: close + reopen the overlay.
            device.findObject(By.res(PLUGIN_PKG, "action_center_bell"))?.click()
            device.wait(Until.gone(By.res(PLUGIN_PKG, "action_center_root")), 1_000L)
            device.waitForIdle(500L)
            device.findObject(By.res(PLUGIN_PKG, "action_center_bell"))?.click()
            device.wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_root")), 1_000L)
        }
        return matches
    }

    /**
     * Per-key removal path (mirror→plugin half of the dismiss-from-action-center flow).
     *
     * When the notification-listener framework delivers [onNotificationRemoved] for a specific key
     * — e.g. a user-dismiss in the stock shade, a system-side snooze, or an app-initiated cancel —
     * [BoringdroidNotificationMirror] broadcasts [NotificationFeedIpc.ACTION_NOTIFICATION_REMOVED]
     * carrying that key, and the plugin-side receiver drops just that one row from
     * [NotificationFeed], leaving any other rows untouched.
     *
     * This complements [listenerReset_clearsAndRehydratesActionCenter] (which exercises the
     * clear-all path via listener disconnect) and locks down the remove-one-row path. A future
     * UI-initiated dismiss affordance (handoff-34 Priority 3) will ride on the same IPC with an
     * added `ACTION_REQUEST_DISMISS` going plugin→mirror; this test covers the mirror→plugin half
     * so that side can regress independently.
     *
     * `cmd notification snooze --for <ms> <key>` is the only in-tree shell mechanism to trigger a
     * per-key onNotificationRemoved without a custom posting app — the `cmd notification`
     * subcommand table lacks a `cancel <key>` form as of AOSP-14.
     */
    @Test
    fun externallyRemovedNotification_isRemovedFromActionCenter() {
        device.executeShellCommand(
            "cmd notification post -S bigtext -t bdDismissTitleZ bdDismissTag body"
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        device.wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
        val seeded = pollForMatchingTitles("bdDismissTitleZ", expectedSize = 1)
        assertThat(seeded).hasSize(1)

        // Find the notification-key to snooze. `cmd notification list` prints one row
        // per active notification as `user|pkg|id|tag|uid`. The key returned by the
        // listener is the same first-field form (see StatusBarNotification#getKey).
        val listOut = device.executeShellCommand("cmd notification list")
        val key =
            listOut.lineSequence().firstOrNull { it.contains("bdDismissTag") }?.trim()
                ?: error("bdDismissTag not present in cmd notification list output:\n$listOut")
        // UiAutomation.executeShellCommand argv-splits on whitespace without a shell
        // (Runtime.exec path), so '|' inside $key is a harmless literal and single
        // quotes would become part of the token — must NOT be quoted.
        device.executeShellCommand("cmd notification snooze --for 60000 $key")

        // Same close-reopen flush for absence as listenerReset — the row is removed from
        // the feed promptly but the a11y cache keeps it addressable until the overlay is
        // re-composed.
        val afterDismiss =
            pollForMatchingTitles("bdDismissTitleZ", expectedSize = 0, timeoutMs = 10_000L)
        assertThat(afterDismiss).isEmpty()
    }

    /**
     * Cross-surface coherence: when the notification shade loses its view on a notification (the
     * listener service disconnects), the action center must follow. Re-binding the listener
     * re-seeds active notifications and the action center repopulates.
     *
     * Disallowing the listener triggers [BoringdroidNotificationMirror.onListenerDisconnected],
     * which broadcasts [NotificationFeedIpc.ACTION_FEED_CLEAR]; the plugin-side receiver clears its
     * feed and the LazyColumn empties. Re-allowing triggers
     * [BoringdroidNotificationMirror.onListenerConnected], which broadcasts
     * [NotificationFeedIpc.ACTION_FEED_RESET] + per-item `ACTION_NOTIFICATION_POSTED`, so the row
     * returns.
     */
    @Test
    fun listenerReset_clearsAndRehydratesActionCenter() {
        device.executeShellCommand(
            "cmd notification post -S bigtext -t bdReseedTitle bdReseedTag body"
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        device.wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
        // Same close-reopen polling pattern as noDuplicateRendering: under suite-run the
        // bridge upsert can land >1s after the first open.
        val seededMatches = pollForMatchingTitles("bdReseedTitle", expectedSize = 1)
        Log.v(
            "BdDiag",
            "listenerReset seed: matches=${seededMatches.size} " +
                "allTitles=${device.findObjects(By.res(PLUGIN_PKG, "notification_title"))
                    .joinToString("|") { "[" + (it.text ?: "null") + "]" }}",
        )
        assertThat(seededMatches).hasSize(1)

        device.executeShellCommand("cmd notification disallow_listener $MIRROR_COMPONENT")
        // Close-reopen polling for absence: LazyColumn recomposes on the empty flow
        // immediately, but UiAutomator's a11y cache keeps the detached rows addressable
        // for several seconds.
        val afterDisallow =
            pollForMatchingTitles("bdReseedTitle", expectedSize = 0, timeoutMs = 5_000L)
        assertThat(afterDisallow).isEmpty()

        device.executeShellCommand("cmd notification allow_listener $MIRROR_COMPONENT")
        // Rehydration is async: framework rebinds the listener → onListenerConnected
        // queries activeNotifications → mirror broadcasts ACTION_FEED_RESET then per-item
        // ACTION_NOTIFICATION_POSTED. Under suite-run the rebind can take several seconds.
        // Reuse pollForMatchingTitles so we get the same close-reopen recomposition kick
        // the seed step uses — a raw Until.hasObject can't force Compose to snapshot a
        // post-open StateFlow update.
        val rehydrated =
            pollForMatchingTitles("bdReseedTitle", expectedSize = 1, timeoutMs = 15_000L)
        Log.v(
            "BdDiag",
            "listenerReset rehydrate: matches=${rehydrated.size} " +
                "allTitles=${device.findObjects(By.res(PLUGIN_PKG, "notification_title"))
                    .joinToString("|") { "[" + (it.text ?: "null") + "]" }}",
        )
        assertThat(rehydrated).hasSize(1)
    }
}
