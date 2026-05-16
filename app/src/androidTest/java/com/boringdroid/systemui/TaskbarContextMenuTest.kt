package com.boringdroid.systemui

import android.app.ActivityOptions
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskbarContextMenuTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $SETTINGS_PKG")
        device.executeShellCommand("am force-stop $CLOCK_PKG")
        device.pressHome()
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        device.executeShellCommand("am force-stop $SETTINGS_PKG")
        device.executeShellCommand("am force-stop $CLOCK_PKG")
        device.pressBack()
    }

    /** Holds the menu open ~15s for a manual screencap. Not part of the regular suite. */
    @Test
    fun holdMenuForScreenshot() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        icon.longClick()
        device.wait(
            Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_close")),
            FIND_TIMEOUT_MS,
        ) ?: throw AssertionError("menu never opened")
        SystemClock.sleep(15_000L)
    }

    @Test
    fun longPress_opensMenu() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        icon.longClick()

        val close =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_close")),
                FIND_TIMEOUT_MS,
            )
        val minimize =
            device.findObject(By.res(PLUGIN_PKG, "taskbar_menu_minimize"))
        val maximize =
            device.findObject(By.res(PLUGIN_PKG, "taskbar_menu_maximize"))

        assertThat(close).isNotNull()
        assertThat(minimize).isNotNull()
        assertThat(maximize).isNotNull()
    }

    @Test
    fun leftClick_doesNotReorderRail() {
        // Stable-order regression guard: clicking a taskbar icon brings the task to front
        // but does NOT shove the icon to the leftmost position. The rail used to follow
        // ActivityManager's MRU order; that was visually disruptive and broke positional
        // muscle memory. TaskbarState now keeps the rail order across refreshes.
        launchSettingsFreeform()
        waitForTaskbarIcon(SETTINGS_PKG)
        device.executeShellCommand("am start -n com.android.deskclock/.DeskClock").trim()
        SystemClock.sleep(LAUNCH_SETTLE_MS)
        waitForTaskbarIcon(CLOCK_PKG)

        val before = railIconLeftXs()
        // Click the icon that's currently NOT top so the click is observable as a foreground
        // change. Either Settings or DeskClock — whichever is not top right now.
        val targetPkg =
            if (currentTopResumedActivity().contains(SETTINGS_PKG)) CLOCK_PKG else SETTINGS_PKG
        waitForTaskbarIcon(targetPkg).click()
        waitForTopResumedActivity(targetPkg)

        val after = railIconLeftXs()
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun leftClick_bringsTaskToFront() {
        // Regression guard: the right-click pointerInput plus the long-press handler must not
        // swallow a plain left tap. Pre-fix, detectTapGestures(onLongPress = ...) consumed the
        // up event for short taps, blocking the .clickable's onClick — so clicking a taskbar
        // icon did nothing. We now use combinedClickable(onClick, onLongClick), which routes
        // both gestures correctly. This test guarantees the click path remains live.
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        // Push Settings off-top so a successful click is observable as "Settings becomes top".
        device.pressHome()
        device.waitForIdle()
        waitForTopResumedActivityNot(SETTINGS_PKG)

        val iconAgain = waitForTaskbarIcon()
        iconAgain.click()

        waitForTopResumedActivity(SETTINGS_PKG)
        assertThat(currentTopResumedActivity()).contains(SETTINGS_PKG)
    }

    @Test
    fun menuClose_removesTask() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        icon.longClick()

        val closeItem =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_close")),
                FIND_TIMEOUT_MS,
            ) ?: throw AssertionError("close menu item never appeared")
        closeItem.click()

        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val out = device.executeShellCommand("dumpsys activity activities")
            if (!out.contains("com.boringdroid.settings/.AboutBoringdroidActivity")) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("settings task did not close after menu Close")
    }

    @Test
    fun menuMinimize_sendsTaskToBack() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        icon.longClick()

        val minimizeItem =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_minimize")),
                FIND_TIMEOUT_MS,
            ) ?: throw AssertionError("minimize menu item never appeared")
        minimizeItem.click()

        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val out = device.executeShellCommand("dumpsys activity activities")
            // Top resumed activity should be a launcher (home), not the settings activity.
            val topLine =
                out.lineSequence()
                    .firstOrNull { it.trim().startsWith("topResumedActivity=") }
                    ?: ""
            if (!topLine.contains("com.boringdroid.settings")) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("settings remained the top resumed activity after Minimize")
    }

    @Test
    fun menuMaximize_togglesWindowingMode() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        icon.longClick()

        val maximizeItem =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_maximize")),
                FIND_TIMEOUT_MS,
            ) ?: throw AssertionError("maximize menu item never appeared")
        maximizeItem.click()

        waitForWindowingMode("fullscreen")

        // Re-open the menu; now the middle row reads "Restore".
        val iconAgain = waitForTaskbarIcon()
        iconAgain.longClick()
        val restoreItem =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_maximize")),
                FIND_TIMEOUT_MS,
            ) ?: throw AssertionError("restore menu item never appeared")
        // Material3 DropdownMenuItem wraps `text = { Text("Restore") }` in a descendant Text
        // node — UiObject2.text on the menu item root returns null. Search the subtree for
        // the label so the state-aware flip is verifiable through UiAutomator.
        val restoreLabel =
            restoreItem.findObject(By.text("Restore"))
                ?: throw AssertionError(
                    "Maximize/Restore label did not flip to 'Restore' after fullscreen toggle"
                )
        assertThat(restoreLabel.text).isEqualTo("Restore")
        restoreItem.click()

        waitForWindowingMode("freeform")
    }

    private fun railIconLeftXs(): List<Int> =
        device.findObjects(
                By.res(java.util.regex.Pattern.compile(".*iv_task_info_icon__.*"))
            )
            .map { it.visibleBounds.left }
            .sorted()

    private fun currentTopResumedActivity(): String {
        val out = device.executeShellCommand("dumpsys activity activities")
        return out.lineSequence()
            .firstOrNull { it.trim().startsWith("topResumedActivity=") }
            .orEmpty()
    }

    private fun waitForTopResumedActivity(pkg: String) {
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (currentTopResumedActivity().contains(pkg)) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("top resumed activity never became $pkg")
    }

    private fun waitForTopResumedActivityNot(pkg: String) {
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (!currentTopResumedActivity().contains(pkg)) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("top resumed activity is still $pkg")
    }

    private fun waitForWindowingMode(expected: String) {
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        // The brief Task-header line for the settings task encodes the windowing mode as
        // `mode=fullscreen` / `mode=freeform` (it does not carry `mWindowingMode=` here —
        // that token only appears later inside mGlobalConfig/mOverrideConfig blocks, well
        // after the first "Hist" marker). Match on the Task header so this stays unambiguous
        // even when other settings tasks share the dump.
        val pattern = Regex("Task\\{[^}]*AboutBoringdroidActivity[^}]*\\bmode=$expected\\b")
        while (SystemClock.uptimeMillis() < deadline) {
            val out = device.executeShellCommand("dumpsys activity activities")
            if (pattern.containsMatchIn(out)) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("settings windowingMode did not become $expected")
    }

    protected fun launchSettingsFreeform() {
        val intent =
            Intent()
                .setComponent(ComponentName(SETTINGS_PKG, "$SETTINGS_PKG.AboutBoringdroidActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options =
            ActivityOptions.makeBasic().apply {
                launchWindowingMode = WindowConfiguration.WINDOWING_MODE_FREEFORM
                launchBounds = Rect(200, 200, 1200, 800)
            }
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(
            intent,
            options.toBundle(),
        )
        SystemClock.sleep(LAUNCH_SETTLE_MS)
    }

    protected fun waitForTaskbarIcon(pkg: String = SETTINGS_PKG) =
        device.wait(
            Until.findObject(By.res(PLUGIN_PKG, "iv_task_info_icon__$pkg")),
            FIND_TIMEOUT_MS,
        ) ?: throw AssertionError("taskbar running-app icon for $pkg never appeared")

    companion object {
        const val PLUGIN_PKG = "com.boringdroid.systemui"
        const val SETTINGS_PKG = "com.boringdroid.settings"
        const val CLOCK_PKG = "com.android.deskclock"
        const val FIND_TIMEOUT_MS = 5_000L
        const val LAUNCH_SETTLE_MS = 1_500L
        const val POLL_INTERVAL_MS = 200L
    }
}
