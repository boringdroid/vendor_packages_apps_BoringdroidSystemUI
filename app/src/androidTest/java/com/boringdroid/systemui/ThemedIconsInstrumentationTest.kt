package com.boringdroid.systemui

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Rect
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the ThemedIconLoader wire-up reached every surface — not visual fidelity. For each of
 * (AllApps grid, taskbar rail, Overview chip, Peek caption), we capture a region screenshot with
 * Themed Icons OFF, toggle the system setting ON, wait for the icon to refresh, capture again, and
 * assert the pixel buffers differ.
 *
 * Test target package is com.android.settings — its launcher icon ships a <monochrome> layer on
 * AOSP 13+, so toggling Themed Icons demonstrably changes its rendered bitmap.
 *
 * Run with:
 *   atest BoringdroidSystemUITests:com.boringdroid.systemui.ThemedIconsInstrumentationTest
 */
@RunWith(AndroidJUnit4::class)
class ThemedIconsInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val targetPackage = "com.android.settings"

    @Before
    fun setUp() {
        setThemedIcons(enabled = false)
        Thread.sleep(500) // let the observer + refresh settle
    }

    @After
    fun tearDown() {
        setThemedIcons(enabled = false)
    }

    @Test
    fun allAppsGrid_themedIconRendersDifferentlyWhenEnabled() {
        device.pressHome()
        openAllApps()
        val selector = "com.boringdroid.systemui:id/allapps_icon__$targetPackage"
        val before = captureIconRegion(selector)
            ?: error("could not capture AllApps icon for $targetPackage")
        setThemedIcons(enabled = true)
        Thread.sleep(1500)
        val after = captureIconRegion(selector)
            ?: error("could not capture AllApps icon for $targetPackage")
        assertThat(after).isNotEqualTo(before)
    }

    @Test
    fun taskbarRail_themedIconRendersDifferentlyWhenEnabled() {
        launchAndReveal(targetPackage)
        val selector = "com.boringdroid.systemui:id/iv_task_info_icon__$targetPackage"
        val before = captureIconRegion(selector)
            ?: error("could not capture taskbar icon for $targetPackage")
        setThemedIcons(enabled = true)
        Thread.sleep(1500)
        val after = captureIconRegion(selector)
            ?: error("could not capture taskbar icon for $targetPackage")
        assertThat(after).isNotEqualTo(before)
    }

    @Test
    fun overviewChip_themedIconRendersDifferentlyWhenEnabled() {
        launchAndReveal(targetPackage)
        openOverview()
        val selector = "com.boringdroid.systemui:id/overview_chip_icon__$targetPackage"
        val before = captureIconRegion(selector)
            ?: error("could not capture Overview icon for $targetPackage")
        setThemedIcons(enabled = true)
        Thread.sleep(1500)
        openOverview()
        val after = captureIconRegion(selector)
            ?: error("could not capture Overview icon for $targetPackage")
        assertThat(after).isNotEqualTo(before)
    }

    @Test
    fun peekCaption_themedIconRendersDifferentlyWhenEnabled() {
        launchAndReveal(targetPackage)
        maximizeForegroundWindow()
        triggerPeek()
        val selector = "com.boringdroid.systemui:id/peek_caption_icon"
        val before = captureIconRegion(selector)
            ?: error("could not capture peek caption icon")
        setThemedIcons(enabled = true)
        Thread.sleep(1500)
        triggerPeek()
        val after = captureIconRegion(selector)
            ?: error("could not capture peek caption icon")
        assertThat(after).isNotEqualTo(before)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun setThemedIcons(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        device.executeShellCommand(
            "settings put secure ${Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES} " +
                "'{\"android.theme.customization.themed_icon\":\"$value\"}'",
        )
    }

    private fun openAllApps() {
        device.executeShellCommand("input keyevent KEYCODE_META_LEFT")
        // Wait for any AllApps testTag we can rely on. `search_field_input` is always
        // present at the top of the panel; the per-app icon tag we actually screenshot
        // off of is enumerated later via captureIconRegion.
        device.wait(Until.hasObject(By.res("com.boringdroid.systemui:id/search_field_input")), 3000L)
    }

    private fun openOverview() {
        device.executeShellCommand("input keyevent KEYCODE_APP_SWITCH")
        device.wait(Until.hasObject(By.res("com.boringdroid.systemui:id/overview_root")), 3000L)
    }

    private fun launchAndReveal(pkg: String) {
        device.executeShellCommand("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
        Thread.sleep(1200)
    }

    private fun maximizeForegroundWindow() {
        device.executeShellCommand("input keyevent KEYCODE_META_LEFT KEYCODE_UP")
    }

    private fun triggerPeek() {
        val width = device.displayWidth
        device.executeShellCommand("input motionevent HOVER_MOVE ${width / 2} 1")
        Thread.sleep(800)
    }

    private fun captureIconRegion(viewResId: String): ByteArray? {
        val obj = device.findObject(By.res(viewResId)) ?: return null
        val rect = obj.visibleBounds
        // `UiDevice.takeScreenshot()` only writes to a File; the Bitmap form lives on
        // `UiAutomation`, which is what we use here so we can crop in-memory.
        val full = instrumentation.uiAutomation.takeScreenshot() ?: return null
        return cropToBytes(full, rect)
    }

    private fun cropToBytes(full: Bitmap, rect: Rect): ByteArray {
        val crop = Bitmap.createBitmap(full, rect.left, rect.top, rect.width(), rect.height())
        val stream = ByteArrayOutputStream()
        crop.compress(CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
