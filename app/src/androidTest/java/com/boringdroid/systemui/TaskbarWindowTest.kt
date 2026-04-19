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
 * The plugin creates its own WindowManager window instead of adopting SystemUI's NavigationBar.
 * These tests lock that in.
 */
@RunWith(AndroidJUnit4::class)
class TaskbarWindowTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun boringdroidTaskbar_isPresent_atBottomOfScreen() {
        val bar =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "taskbar_root")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(bar).isNotNull()
        val bounds = bar.visibleBounds
        assertThat(bounds.bottom).isEqualTo(device.displayHeight)
    }

    @Test
    fun boringdroidTaskbar_doesNotOverlapNavigationBar() {
        // SystemUI's NavigationBar must be gone (config_showNavigationBar=false overlay).
        val navBar =
            device.wait(
                Until.findObject(
                    By.pkg("com.android.systemui")
                        .clazz("android.widget.FrameLayout")
                        .descContains("Navigation")
                ),
                1_000L,
            )
        assertThat(navBar).isNull()
    }
}
