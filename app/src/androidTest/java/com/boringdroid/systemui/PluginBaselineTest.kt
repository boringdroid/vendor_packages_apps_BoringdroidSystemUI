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
 * Baseline sanity check: the plugin is loaded into SystemUI and at least its all-apps button has
 * been injected into the NavigationBarView. If this fails, every other test will fail — it runs
 * first and makes the root cause obvious.
 */
@RunWith(AndroidJUnit4::class)
class PluginBaselineTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun allAppsButton_isVisibleInNavBar() {
        val button =
            device.wait(Until.findObject(By.res(PLUGIN_PKG, "bt_all_apps")), FIND_TIMEOUT_MS)
        assertThat(button).isNotNull()
    }

    companion object {
        const val PLUGIN_PKG = "com.boringdroid.systemui"
        const val FIND_TIMEOUT_MS = 5_000L
    }
}
