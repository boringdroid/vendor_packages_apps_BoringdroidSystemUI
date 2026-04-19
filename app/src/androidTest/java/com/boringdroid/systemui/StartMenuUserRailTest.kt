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
 * The Start menu's user rail is the bottom strip showing the avatar/name plus three lock / sign-out
 * / power buttons (per the M3 Expressive design). Each button must carry a stable resource id so
 * future cycles can wire real actions onto them via UiAutomator. The buttons currently dismiss the
 * panel as their action; verifying dismissal proves the click was routed.
 */
@RunWith(AndroidJUnit4::class)
class StartMenuUserRailTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        if (device.hasObject(By.res(PluginBaselineTest.PLUGIN_PKG, "all_apps_layout"))) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    @Test
    fun startMenu_showsUserRailWithThreeButtons() {
        openDrawer()

        val rail =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "start_menu_user_rail")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(rail).isNotNull()

        for (id in BUTTON_IDS) {
            val button =
                device.wait(
                    Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, id)),
                    PluginBaselineTest.FIND_TIMEOUT_MS,
                )
            assertThat(button).isNotNull()
        }
    }

    @Test
    fun lockButton_dismissesStartMenu() {
        assertButtonDismissesPanel("start_menu_lock")
    }

    @Test
    fun signoutButton_dismissesStartMenu() {
        assertButtonDismissesPanel("start_menu_signout")
    }

    @Test
    fun powerButton_dismissesStartMenu() {
        assertButtonDismissesPanel("start_menu_power")
    }

    private fun assertButtonDismissesPanel(resId: String) {
        openDrawer()
        val button =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, resId)),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(button).isNotNull()
        button.click()
        val gone =
            device.wait(
                Until.gone(By.res(PluginBaselineTest.PLUGIN_PKG, "all_apps_layout")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(gone).isTrue()
    }

    private fun openDrawer() {
        val button =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "bt_all_apps")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(button).isNotNull()
        button.click()
        device.wait(
            Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "all_apps_layout")),
            PluginBaselineTest.FIND_TIMEOUT_MS,
        )
    }

    companion object {
        private val BUTTON_IDS =
            listOf("start_menu_lock", "start_menu_signout", "start_menu_power")
    }
}
