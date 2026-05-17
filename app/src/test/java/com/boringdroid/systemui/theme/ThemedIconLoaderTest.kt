package com.boringdroid.systemui.theme

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemedIconLoaderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val component = ComponentName("com.example.app", "com.example.app.MainActivity")

    @Before
    fun resetSetting() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            null,
        )
    }

    @Test
    fun load_settingOff_returnsRawDrawable() {
        val loader = ThemedIconLoader(context)
        val raw = ColorDrawable(0xFF112233.toInt())
        assertThat(loader.load(component, raw)).isSameInstanceAs(raw)
    }

    @Test
    fun load_settingOn_nonAdaptiveIcon_returnsRaw() {
        enableThemedIconsSetting()
        val loader = ThemedIconLoader(context)
        val raw = ColorDrawable(0xFF112233.toInt())
        assertThat(loader.load(component, raw)).isSameInstanceAs(raw)
    }

    @Test
    fun load_settingOn_adaptiveIconNoMonochrome_returnsRaw() {
        enableThemedIconsSetting()
        val loader = ThemedIconLoader(context)
        val raw = AdaptiveIconDrawable(
            ColorDrawable(0xFFAAAAAA.toInt()),
            ColorDrawable(0xFFCCCCCC.toInt()),
        )
        assertThat(loader.load(component, raw)).isSameInstanceAs(raw)
    }

    private fun enableThemedIconsSetting() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            """{"android.theme.customization.themed_icon":"1"}""",
        )
    }
}
