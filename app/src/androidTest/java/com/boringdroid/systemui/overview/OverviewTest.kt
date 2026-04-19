package com.boringdroid.systemui.overview

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.boringdroid.systemui.PluginBaselineTest.Companion.FIND_TIMEOUT_MS
import com.boringdroid.systemui.PluginBaselineTest.Companion.PLUGIN_PKG
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverviewTest {
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
        // Gate on the plugin's nav-bar injection landing, then warm up the
        // IOverviewProxy binding by cycling the overview open and closed.
        // After a fresh install or force-stop of BoringdroidSystemUI, the
        // very first KEYCODE_APP_SWITCH can be dropped because the proxy
        // hasn't finished wiring onOverviewToggle yet; subsequent keycodes
        // land on a hot binding. Retry a few times so a cold start doesn't
        // cause the first real test to fail spuriously.
        device.wait(Until.hasObject(By.res(PLUGIN_PKG, "bt_all_apps")), FIND_TIMEOUT_MS)
        for (i in 0 until WARMUP_RETRY_COUNT) {
            if (i > 0) {
                // Give the proxy binding time to settle between failed attempts
                // so we don't just repeatedly race the same handshake.
                Thread.sleep(SNAPSHOT_SETTLE_MS)
            }
            device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
            val opened =
                device.wait(Until.hasObject(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
            if (opened) {
                device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
                device.wait(Until.gone(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
                // One final settle so the post-warmup test body lands on a
                // quiescent input/overlay state, not the tail of this cycle.
                device.waitForIdle()
                break
            }
        }
    }

    @After
    fun tearDown() {
        // Dismiss the overview if still showing so it doesn't leak into later tests.
        if (device.findObject(By.res(PLUGIN_PKG, "overview_root")) != null) {
            device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
            device.wait(Until.gone(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
        }
        device.pressHome()
    }

    @Test
    fun appSwitch_opensBoringdroidOverview() {
        device.executeShellCommand("am start -a android.intent.action.MAIN com.android.settings")
        device.wait(Until.hasObject(By.pkg("com.android.settings")), 6_000L)
        device.pressHome()
        device.waitForIdle()
        // KEYCODE_APP_SWITCH (187) is the recents key; SystemUI routes it to
        // IOverviewProxy.onOverviewToggle, which BoringdroidSystemUI wires to
        // OverviewWindow.toggle(). Alt+Tab goes through PhoneWindowManager
        // instead and never reaches the proxy on AOSP 14, so we trigger the
        // app-switch path directly.
        //
        // Even after the @Before warmup, cumulative emulator load across
        // back-to-back suite runs can occasionally drop an APP_SWITCH that
        // arrives while the proxy binding is still settling after Home. Retry
        // up to OPEN_RETRY_COUNT times so a single dropped keycode doesn't
        // fail the test.
        var overview: UiObject2? = null
        for (attempt in 0 until OPEN_RETRY_COUNT) {
            if (attempt > 0) {
                Thread.sleep(SNAPSHOT_SETTLE_MS)
            }
            device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
            overview =
                device.wait(Until.findObject(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
            if (overview != null) break
        }
        assertThat(overview).isNotNull()
    }

    @Test
    fun appSwitch_overviewShowsRecentTaskCard() {
        // Launch Settings so at least one non-excluded task exists in recents.
        device.executeShellCommand("am start -a android.intent.action.MAIN com.android.settings")
        device.wait(Until.hasObject(By.pkg("com.android.settings")), 6_000L)
        device.pressHome()
        device.waitForIdle()
        val card = openOverviewAndFindCardChild("overview_card_label")
        assertThat(card).isNotNull()
    }

    @Test
    fun appSwitch_overviewCardHasThumbnailSlot() {
        // The thumbnail ImageView is inflated per card. It should be findable
        // via the accessibility tree whether or not the ActivityManager has
        // produced a real snapshot yet.
        device.executeShellCommand("am start -a android.intent.action.MAIN com.android.settings")
        device.wait(Until.hasObject(By.pkg("com.android.settings")), 6_000L)
        device.pressHome()
        device.waitForIdle()
        val thumbnail = openOverviewAndFindCardChild("overview_card_thumbnail")
        assertThat(thumbnail).isNotNull()
    }

    @Test
    fun cardClick_launchesTaskAndDismissesOverview() {
        // End-to-end coverage for the card click handler:
        //   OverviewCardAdapter.itemView.onClick
        //     -> ActivityManagerWrapper.startActivityFromRecents(taskId)
        //     -> onTaskLaunched() -> OverviewWindow.hide()
        // We launch Settings so a known-resolvable task sits in recents, then
        // drop to home so Settings is a background task (not the foreground
        // filter target of RecentTasksProvider), open the overview, click the
        // Settings card, and assert (a) Settings is foregrounded and (b) the
        // overview window is torn down.
        device.executeShellCommand("am start -a android.intent.action.MAIN com.android.settings")
        // Use LAUNCH_TIMEOUT_MS (10 s) for the initial Settings cold-launch —
        // under rapid back-to-back test runs the emulator is under GC/IO
        // pressure and 6 s is too tight.
        val settingsUp = device.wait(Until.hasObject(By.pkg(TARGET_PKG)), LAUNCH_TIMEOUT_MS)
        assertThat(settingsUp).isTrue()
        device.pressHome()
        device.waitForIdle()
        device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
        device.wait(Until.findObject(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
        // Let the a11y tree settle after the overview window raises —
        // RecyclerView layout + onBindViewHolder run on the main thread
        // asynchronously, and the card label search can race those events
        // on an emulator under GC/IO pressure. Matches the gate used by
        // openOverviewAndFindCardChild for the two sibling tests.
        Thread.sleep(CARD_BIND_SETTLE_MS)
        // Card layouts are LinearLayouts with no id, but the label TextView
        // carries the app's launcher label. Click the matching label; the
        // touch propagates up to the clickable card root since the label
        // itself is not clickable.
        val settingsLabel =
            device.wait(
                Until.findObject(
                    By.res(PLUGIN_PKG, "overview_card_label").textContains("Settings")
                ),
                LAUNCH_TIMEOUT_MS,
            )
        assertThat(settingsLabel).isNotNull()
        settingsLabel.click()
        // (a) Settings back in the foreground within a generous timeout —
        // startActivityFromRecents performs a full task restart in the
        // emulator and can take >2s under load.
        val launched = device.wait(Until.hasObject(By.pkg(TARGET_PKG)), LAUNCH_TIMEOUT_MS)
        assertThat(launched).isTrue()
        // (b) Overview window gone. UiAutomator's accessibility tree reflects
        // TYPE_APPLICATION_OVERLAY removal, so Until.gone is the right probe.
        val gone = device.wait(Until.gone(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
        assertThat(gone).isTrue()
    }

    /**
     * Opens the overview via APP_SWITCH and returns the first descendant whose resource id matches
     * [childResId], dismiss-and-reopening up to [SNAPSHOT_RETRY_COUNT] times if the first snapshot
     * yields an empty card list.
     *
     * `RecentTasksProvider.getRecentTasks()` is invoked synchronously from `OverviewWindow.show()`
     * and `TaskStackChangeListener` only refreshes thumbnails, not the list itself. If
     * `ActivityTaskManager.getRecentTasks()` races a post-Home stack update and returns empty, that
     * empty state persists until the next show(). A dismiss-reopen cycle with a short settle pause
     * gives the stack time to publish the just-backgrounded task before the next snapshot.
     */
    private fun openOverviewAndFindCardChild(childResId: String): UiObject2? {
        for (attempt in 0 until SNAPSHOT_RETRY_COUNT) {
            if (attempt > 0) {
                Thread.sleep(SNAPSHOT_SETTLE_MS)
            }
            device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
            val root =
                device.wait(Until.findObject(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
            if (root != null) {
                // After the overview window raises, RecyclerView layout and
                // onBindViewHolder happen asynchronously on the main thread.
                // Wait for the a11y tree to quiesce before searching for card
                // children; otherwise the search can race the bind pass and
                // miss freshly inflated TextView/ImageView nodes.
                Thread.sleep(CARD_BIND_SETTLE_MS)
                val child =
                    device.wait(Until.findObject(By.res(PLUGIN_PKG, childResId)), LAUNCH_TIMEOUT_MS)
                if (child != null) return child
            }
            device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
            device.wait(Until.gone(By.res(PLUGIN_PKG, "overview_root")), FIND_TIMEOUT_MS)
            device.waitForIdle()
        }
        return null
    }

    companion object {
        private const val TARGET_PKG = "com.android.settings"
        private const val LAUNCH_TIMEOUT_MS = 10_000L
        private const val SNAPSHOT_RETRY_COUNT = 3
        private const val SNAPSHOT_SETTLE_MS = 750L
        private const val WARMUP_RETRY_COUNT = 8
        private const val OPEN_RETRY_COUNT = 3
        private const val CARD_BIND_SETTLE_MS = 500L
    }
}
