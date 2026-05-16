package com.boringdroid.systemui

import android.app.ActivityOptions
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $SETTINGS_PKG")
        device.pressHome()
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        device.executeShellCommand("am force-stop $SETTINGS_PKG")
        device.pressBack()
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
        assertThat(close).isNotNull()
    }

    @Test
    fun rightClick_opensMenu() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        rightClick(icon.visibleBounds.centerX().toFloat(), icon.visibleBounds.centerY().toFloat())

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
    fun menuClose_removesTask() {
        launchSettingsFreeform()
        val icon = waitForTaskbarIcon()
        rightClick(icon.visibleBounds.centerX().toFloat(), icon.visibleBounds.centerY().toFloat())

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
        rightClick(icon.visibleBounds.centerX().toFloat(), icon.visibleBounds.centerY().toFloat())

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
        rightClick(icon.visibleBounds.centerX().toFloat(), icon.visibleBounds.centerY().toFloat())

        val maximizeItem =
            device.wait(
                Until.findObject(By.res(PLUGIN_PKG, "taskbar_menu_maximize")),
                FIND_TIMEOUT_MS,
            ) ?: throw AssertionError("maximize menu item never appeared")
        maximizeItem.click()

        waitForWindowingMode("fullscreen")

        // Re-open the menu; now the middle row reads "Restore".
        val iconAgain = waitForTaskbarIcon()
        rightClick(
            iconAgain.visibleBounds.centerX().toFloat(),
            iconAgain.visibleBounds.centerY().toFloat(),
        )
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

    protected fun waitForTaskbarIcon() =
        device.wait(
            Until.findObject(By.res(PLUGIN_PKG, "iv_task_info_icon")),
            FIND_TIMEOUT_MS,
        ) ?: throw AssertionError("taskbar running-app icon never appeared")

    protected fun rightClick(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        val down =
            MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                /* pointerCount= */ 1,
                arrayOf(props), arrayOf(coords),
                /* metaState= */ 0, MotionEvent.BUTTON_SECONDARY,
                /* xPrecision= */ 1f, /* yPrecision= */ 1f,
                /* deviceId= */ 0, /* edgeFlags= */ 0,
                InputDevice.SOURCE_MOUSE, /* flags= */ 0,
            )
        automation.injectInputEvent(down, /* sync= */ true)
        val up =
            MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                /* pointerCount= */ 1,
                arrayOf(props), arrayOf(coords),
                /* metaState= */ 0, /* buttonState= */ 0,
                /* xPrecision= */ 1f, /* yPrecision= */ 1f,
                /* deviceId= */ 0, /* edgeFlags= */ 0,
                InputDevice.SOURCE_MOUSE, /* flags= */ 0,
            )
        automation.injectInputEvent(up, /* sync= */ true)
        down.recycle()
        up.recycle()
    }

    companion object {
        const val PLUGIN_PKG = "com.boringdroid.systemui"
        const val SETTINGS_PKG = "com.boringdroid.settings"
        const val FIND_TIMEOUT_MS = 5_000L
        const val LAUNCH_SETTLE_MS = 1_500L
        const val POLL_INTERVAL_MS = 200L
    }
}
