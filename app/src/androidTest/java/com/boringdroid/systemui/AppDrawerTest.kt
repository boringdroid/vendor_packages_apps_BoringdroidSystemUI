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
    fun searchField_isAutoFocusedOnPanelOpen() {
        openDrawer()
        val searchField =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_input")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(searchField).isNotNull()
        // Field should take focus as soon as the panel composes so the user can type without a tap.
        // Poll briefly — the LaunchedEffect that calls requestFocus() runs on the first frame, but
        // accessibility focus state propagation is async on a cold plugin window.
        val deadline = System.currentTimeMillis() + PluginBaselineTest.FIND_TIMEOUT_MS
        var focused = searchField.isFocused
        while (!focused && System.currentTimeMillis() < deadline) {
            device.waitForIdle()
            focused =
                device
                    .findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_input"))
                    ?.isFocused == true
        }
        assertThat(focused).isTrue()
    }

    @Test
    fun searchField_filtersAppGrid() {
        openDrawer()

        val initialNames =
            device.wait(
                Until.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(initialNames).isNotNull()
        assertThat(initialNames).isNotEmpty()
        val initialCount = initialNames.size

        val searchField =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_input")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(searchField).isNotNull()
        searchField.click()
        // A string no app label is going to contain — verifies the grid empties.
        searchField.text = "zzqxnomatchxx"
        // SetText routes via SemanticsAction.SetText then triggers Compose recomposition; on a
        // cold plugin window this can lag past `waitForIdle()`. Poll the grid size up to
        // FIND_TIMEOUT_MS instead of asserting on the first read after waitForIdle.
        val filterDeadline = System.currentTimeMillis() + PluginBaselineTest.FIND_TIMEOUT_MS
        var filteredCount =
            device.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")).size
        while (filteredCount >= initialCount && System.currentTimeMillis() < filterDeadline) {
            device.waitForIdle()
            filteredCount =
                device.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")).size
        }
        assertThat(filteredCount).isLessThan(initialCount)

        searchField.clear()
        val restoreDeadline = System.currentTimeMillis() + PluginBaselineTest.FIND_TIMEOUT_MS
        var restoredCount =
            device.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")).size
        while (restoredCount < initialCount && System.currentTimeMillis() < restoreDeadline) {
            device.waitForIdle()
            restoredCount =
                device.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")).size
        }
        assertThat(restoredCount).isEqualTo(initialCount)
    }

    @Test
    fun searchField_clearIconRestoresGrid() {
        openDrawer()

        val initialNames =
            device.wait(
                Until.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(initialNames).isNotNull()
        val initialCount = initialNames.size

        val searchField =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_input")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(searchField).isNotNull()

        // Clear icon should not be present while the field is empty.
        assertThat(device.hasObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_clear")))
            .isFalse()

        searchField.click()
        searchField.text = "zzqxnomatchxx"
        device.waitForIdle()

        // Clear icon appears once there's text.
        val clearIcon =
            device.wait(
                Until.findObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_clear")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(clearIcon).isNotNull()

        clearIcon.click()

        // Grid should be back to its full population. Poll: SetText recompositions can lag
        // beyond a single waitForIdle on the cold plugin window.
        val restoreDeadline = System.currentTimeMillis() + PluginBaselineTest.FIND_TIMEOUT_MS
        var restoredCount =
            device.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")).size
        while (restoredCount < initialCount && System.currentTimeMillis() < restoreDeadline) {
            device.waitForIdle()
            restoredCount =
                device.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "app_info_name")).size
        }
        assertThat(restoredCount).isEqualTo(initialCount)
        assertThat(device.hasObject(By.res(PluginBaselineTest.PLUGIN_PKG, "search_field_clear")))
            .isFalse()
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
        assertThat(device.currentPackageName).isNotEqualTo("com.android.systemui")
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
