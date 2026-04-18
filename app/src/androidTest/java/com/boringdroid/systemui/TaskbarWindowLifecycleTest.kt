package com.boringdroid.systemui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * If SystemUI is restarted, the BoringdroidTaskbar window must come back on its own.
 */
@RunWith(AndroidJUnit4::class)
class TaskbarWindowLifecycleTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun taskbar_survivesSystemUIRestart() {
        assertThat(
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "taskbar_root")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        ).isNotNull()
        device.executeShellCommand("killall com.android.systemui")
        device.wait(Until.gone(By.res(PluginBaselineTest.PLUGIN_PKG, "taskbar_root")), 2_000L)
        val bar = device.wait(
            Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "taskbar_root")),
            15_000L,
        )
        assertThat(bar).isNotNull()
    }
}
