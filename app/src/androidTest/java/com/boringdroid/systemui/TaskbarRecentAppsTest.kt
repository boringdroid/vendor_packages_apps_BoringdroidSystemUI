package com.boringdroid.systemui

import android.content.Intent
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
 * After a user has launched apps, the taskbar's recent-apps row (`AppStateLayout`) should render
 * one icon per task. `iv_task_info_icon` is the icon ImageView inflated for each entry.
 */
@RunWith(AndroidJUnit4::class)
class TaskbarRecentAppsTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun launchingApp_addsIconToTaskbar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            context.packageManager.getLaunchIntentForPackage("com.android.settings")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        assertThat(intent).isNotNull()
        context.startActivity(intent)

        // Wait for Settings to actually come up — otherwise the task event hasn't propagated yet.
        val settingsUp =
            device.wait(Until.hasObject(By.pkg("com.android.settings")), 8_000L)
        assertThat(settingsUp).isTrue()

        device.pressHome()
        device.waitForIdle()

        val icons =
            device.wait(
                Until.findObjects(By.res(PluginBaselineTest.PLUGIN_PKG, "iv_task_info_icon")),
                PluginBaselineTest.FIND_TIMEOUT_MS,
            )
        assertThat(icons).isNotNull()
        assertThat(icons).isNotEmpty()
    }
}
