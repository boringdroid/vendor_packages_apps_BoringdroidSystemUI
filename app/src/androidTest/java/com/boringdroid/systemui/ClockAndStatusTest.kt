package com.boringdroid.systemui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The plugin's clock and battery-percentage chips sit in the nav bar's end-button group. They
 * should render on boot with a non-blank clock time.
 */
@RunWith(AndroidJUnit4::class)
class ClockAndStatusTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun clock_isVisibleWithNonEmptyTime() {
        val clock =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "clock")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(clock).isNotNull()
        assertThat(clock.text).isNotEmpty()
    }

    @Test
    fun batteryPercent_isVisibleWithNonEmptyText() {
        val battery =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "textViewBatteryPercent")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(battery).isNotNull()
        assertThat(battery.text).isNotEmpty()
    }
}
