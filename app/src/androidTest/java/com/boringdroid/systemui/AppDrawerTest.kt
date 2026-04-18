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
 * Tapping the plugin's all-apps button should pop up `layout_all_apps` with installed apps, and
 * tapping an app in the drawer should launch it.
 */
@RunWith(AndroidJUnit4::class)
class AppDrawerTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        // Dismiss the drawer if a test left it open — future tests expect a clean home state.
        if (device.hasObject(By.res(PluginBaselineTest.PLUGIN_PKG, "all_apps_layout"))) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    @Test
    fun tapAllAppsButton_opensDrawer() {
        openDrawer()
        val drawer =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "all_apps_layout")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(drawer).isNotNull()
    }

    @Test
    fun drawer_isPopulatedWithApps() {
        openDrawer()
        val names =
            device.wait(
                Until.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(names).isNotNull()
        assertThat(names).isNotEmpty()
    }

    @Test
    fun tapAppInDrawer_launchesIt() {
        openDrawer()
        val rows =
            device.wait(
                Until.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(rows).isNotNull()
        assertThat(rows).isNotEmpty()

        val firstName = rows[0].text
        rows[0].click()
        // A different package should be in the foreground within a few seconds.
        val launched =
            device.wait(
                Until.gone(By.res(PluginBaselineTest.PLUGIN_PKG, "all_apps_layout")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(launched).isTrue()
        device.waitForIdle()
        // Soft check — we don't know the exact launched package, only that "something else" is up.
        assertThat(device.currentPackageName)
            .isNotEqualTo("com.android.systemui")
        // And the app we tapped should have a non-blank label.
        assertThat(firstName).isNotEmpty()
    }

    private fun openDrawer() {
        val button =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "bt_all_apps")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(button).isNotNull()
        button.click()
    }
}
