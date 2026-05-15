package com.boringdroid.systemui

import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowOrganizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end checks for the peek-caption stack.
 *
 * Each test:
 *  1. Launches BoringdroidSettings's About screen and uses [WindowOrganizer.applyTransaction] to
 *     anchor it as a freeform window with known bounds. The launch-side mode change goes via the
 *     organizer because `am start --windowingMode 5` is flaky on AOSP 14 once any prior task on
 *     the device has been pushed into fullscreen.
 *  2. Taps the WMShell caption maximize button at the bounds we just set. The maximize has to go
 *     through WMShell so its shell-transition wrapper fires `TaskStackChangeListener.onTaskStack-
 *     Changed`, which is what [TaskFullscreenMonitor] is listening for. A direct organizer
 *     `setWindowingMode(FULLSCREEN)` does not fire that callback.
 *  3. Asserts on the WMS window list (`dumpsys window windows`) for the plugin's peek windows.
 *
 * The sysprop=false path is verified manually with logcat (the controller logs
 * `persist.boringdroid.peek_caption=false; not arming peek caption`) — restarting SystemUI from
 * non-root instrumentation is denied on userdebug, so it can't be covered by a self-contained
 * test here.
 */
@RunWith(AndroidJUnit4::class)
class PeekCaptionTest {

    private lateinit var device: UiDevice
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val organizer = WindowOrganizer()

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Make sure no prior settings task is around — `am start` reuses an existing task with
        // its current windowing mode, defeating the launch-time mode pin.
        device.executeShellCommand("am force-stop $SETTINGS_PKG")
        device.pressHome()
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        device.executeShellCommand("am force-stop $SETTINGS_PKG")
    }

    @Test
    fun peekEdge_attachesWhenFreeformTaskIsMaximized() {
        launchSettingsAndDriveToFreeform()
        tapMaximizeButton()

        waitForWindow(HOVER_EDGE_TITLE, present = true)
        assertThat(windowExists(HOVER_EDGE_TITLE)).isTrue()
    }

    @Test
    fun peekEdge_detachesWhenMaximizedTaskCloses() {
        launchSettingsAndDriveToFreeform()
        tapMaximizeButton()
        waitForWindow(HOVER_EDGE_TITLE, present = true)
        assertThat(windowExists(HOVER_EDGE_TITLE)).isTrue()

        device.executeShellCommand("am force-stop $SETTINGS_PKG")

        waitForWindow(HOVER_EDGE_TITLE, present = false)
        assertThat(windowExists(HOVER_EDGE_TITLE)).isFalse()
    }

    @Test
    fun peekPanel_appearsOnHoverAtTopEdge() {
        launchSettingsAndDriveToFreeform()
        tapMaximizeButton()
        waitForWindow(HOVER_EDGE_TITLE, present = true)
        assertThat(windowExists(HOVER_EDGE_TITLE)).isTrue()

        injectMouseHover(100f, 0f)

        waitForWindow(PEEK_PANEL_TITLE, present = true)
        assertThat(windowExists(PEEK_PANEL_TITLE)).isTrue()
    }

    /**
     * Not a real assertion — a helper invoked explicitly with
     * `am instrument -e class PeekCaptionTest -e method holdPeekForScreenshot` to leave the peek
     * panel visible long enough for a manual `adb shell screencap` from a parallel shell. Kept in
     * the test source so it lives next to the same plumbing it depends on; the regular suite
     * skips it because `@Test` methods can still be filtered out by class+method selection.
     */
    @Test
    fun holdPeekForScreenshot() {
        launchSettingsAndDriveToFreeform()
        tapMaximizeButton()
        waitForWindow(HOVER_EDGE_TITLE, present = true)
        injectMouseEvent(MotionEvent.ACTION_HOVER_ENTER, 100f, 0f)
        waitForWindow(PEEK_PANEL_TITLE, present = true)
        // Re-inject HOVER_MOVE only — InputDispatcher's state machine aborts system_server with
        // "Expected ACTION_HOVER_MOVE" if HOVER_ENTER is fired twice in a row.
        val deadline = SystemClock.uptimeMillis() + HOLD_DURATION_MS
        while (SystemClock.uptimeMillis() < deadline) {
            injectMouseEvent(MotionEvent.ACTION_HOVER_MOVE, 400f, 20f)
            SystemClock.sleep(HOLD_REINJECT_MS)
        }
    }

    private fun launchSettingsAndDriveToFreeform() {
        // `am start --windowingMode 5` is the obvious thing to use, but AMS overrides it once
        // any prior task on the device has spent time in fullscreen. Calling
        // ActivityOptions.setLaunchWindowingMode from the test process (platform-signed, so
        // @hide accessors are reachable) pins freeform at launch time, before the activity is
        // ever placed on the display.
        val intent =
            Intent()
                .setComponent(ComponentName(SETTINGS_PKG, "$SETTINGS_PKG.AboutBoringdroidActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options =
            ActivityOptions.makeBasic().apply {
                launchWindowingMode = WindowConfiguration.WINDOWING_MODE_FREEFORM
                launchBounds = FREEFORM_BOUNDS
            }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.startActivity(intent, options.toBundle())
        val token =
            waitForSettingsToken()
                ?: throw AssertionError("settings task token never surfaced")
        // Defensive belt-and-braces: nudge the task to the bounds we want even if AMS used
        // launch-bounds heuristics to pick something else.
        val wct =
            WindowContainerTransaction()
                .setWindowingMode(token, WindowConfiguration.WINDOWING_MODE_FREEFORM)
                .setBounds(token, FREEFORM_BOUNDS)
        organizer.applyTransaction(wct)
        SystemClock.sleep(CAPTION_DRAW_DELAY_MS)
    }

    private fun tapMaximizeButton() {
        // DesktopModeAppControlsWindowDecoration: 40dp maximize button, 8dp marginEnd to the
        // 40dp close button which itself has 8dp marginEnd from the task's right edge. Maximize
        // horizontal center sits 76dp inside the right edge; the 40dp-tall caption puts the
        // vertical center at task_top + 20dp.
        val x = FREEFORM_BOUNDS.right - MAXIMIZE_BUTTON_INSET_DP
        val y = FREEFORM_BOUNDS.top + CAPTION_HALF_DP
        device.executeShellCommand("input tap $x $y")
    }

    private fun waitForSettingsToken(): WindowContainerToken? {
        val deadline = SystemClock.uptimeMillis() + LONG_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val tasks =
                ActivityTaskManager.getInstance()
                    .getTasks(MAX_TASKS, /* filterOnlyVisibleRecents= */ false)
            val task = tasks.firstOrNull { it.baseActivity?.packageName == SETTINGS_PKG }
            val token = task?.token
            if (token != null) return token
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun waitForWindow(title: String, present: Boolean) {
        val deadline = SystemClock.uptimeMillis() + LONG_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (windowExists(title) == present) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun windowExists(title: String): Boolean {
        val out = device.executeShellCommand("dumpsys window windows")
        return out.contains(title)
    }

    private fun injectMouseHover(x: Float, y: Float) {
        injectMouseEvent(MotionEvent.ACTION_HOVER_ENTER, x, y)
    }

    private fun injectMouseEvent(action: Int, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val event =
            MotionEvent.obtain(downTime, downTime, action, x, y, /* metaState= */ 0)
        event.source = InputDevice.SOURCE_MOUSE
        automation.injectInputEvent(event, /* sync= */ true)
        event.recycle()
    }

    companion object {
        private const val SETTINGS_PKG = "com.boringdroid.settings"
        private const val HOVER_EDGE_TITLE = "BoringdroidPeekHoverEdge"
        private const val PEEK_PANEL_TITLE = "BoringdroidPeekPanel"
        private const val LONG_TIMEOUT_MS = 8_000L
        private const val POLL_INTERVAL_MS = 200L
        private const val MAX_TASKS = 50
        private const val MAXIMIZE_BUTTON_INSET_DP = 76
        private const val CAPTION_HALF_DP = 20
        private const val CAPTION_DRAW_DELAY_MS = 1_500L
        private val FREEFORM_BOUNDS = Rect(200, 200, 1200, 800)
        private const val HOLD_DURATION_MS = 25_000L
        private const val HOLD_REINJECT_MS = 200L
    }
}
