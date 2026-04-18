package com.boringdroid.systemui

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

/**
 * After a rotation-induced configuration change, SystemUI recreates NavigationBarView; the plugin
 * must re-attach its views. Regression guard for "taskbar disappears after rotate".
 */
@RunWith(AndroidJUnit4::class)
class RotationTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        device.setOrientationNatural()
        device.waitForIdle()
        device.unfreezeRotation()
    }

    @Test
    fun taskbar_survivesRotation() {
        // Baseline visible
        var button =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "bt_all_apps")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(button).isNotNull()

        device.setOrientationLeft()
        device.waitForIdle()

        button =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "bt_all_apps")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(button).isNotNull()

        device.setOrientationNatural()
        device.waitForIdle()

        button =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "bt_all_apps")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(button).isNotNull()
    }
}
