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
import org.robolectric.annotation.Config

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

    @Test
    @Config(sdk = [33])
    fun load_settingOn_adaptiveIconWithMonochrome_returnsThemedAdaptive() {
        enableThemedIconsSetting()
        val loader = ThemedIconLoader(context)
        val mono = ColorDrawable(0xFF000000.toInt())
        val raw = AdaptiveIconDrawable(
            ColorDrawable(0xFFAAAAAA.toInt()),
            ColorDrawable(0xFFCCCCCC.toInt()),
            mono,
        )
        val themed = loader.load(component, raw)
        assertThat(themed).isInstanceOf(AdaptiveIconDrawable::class.java)
        assertThat(themed).isNotSameInstanceAs(raw)
    }

    @Test
    @Config(sdk = [33])
    fun load_settingOn_adaptiveIconWithMonochrome_isCachedByComponent() {
        enableThemedIconsSetting()
        val loader = ThemedIconLoader(context)
        val mono = ColorDrawable(0xFF000000.toInt())
        val raw = AdaptiveIconDrawable(
            ColorDrawable(0xFFAAAAAA.toInt()),
            ColorDrawable(0xFFCCCCCC.toInt()),
            mono,
        )
        val first = loader.load(component, raw)
        val second = loader.load(component, raw)
        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    @Config(sdk = [33])
    fun onThemedIconsSettingChanged_clearsCache() {
        enableThemedIconsSetting()
        val loader = ThemedIconLoader(context)
        val raw = AdaptiveIconDrawable(
            ColorDrawable(0xFFAAAAAA.toInt()),
            ColorDrawable(0xFFCCCCCC.toInt()),
            ColorDrawable(0xFF000000.toInt()),
        )
        val first = loader.load(component, raw)
        // Toggle off then on; observer fires onThemedIconsSettingChanged().
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            """{"android.theme.customization.themed_icon":"0"}""",
        )
        loader.onThemedIconsSettingChanged()
        val afterOff = loader.load(component, raw)
        assertThat(afterOff).isSameInstanceAs(raw) // setting off → raw

        enableThemedIconsSetting()
        loader.onThemedIconsSettingChanged()
        val afterOn = loader.load(component, raw)
        assertThat(afterOn).isInstanceOf(AdaptiveIconDrawable::class.java)
        assertThat(afterOn).isNotSameInstanceAs(first) // rebuilt, not the old cached one
    }

    @Test
    @Config(sdk = [33])
    fun onConfigurationChanged_clearsCache() {
        enableThemedIconsSetting()
        val loader = ThemedIconLoader(context)
        val raw = AdaptiveIconDrawable(
            ColorDrawable(0xFFAAAAAA.toInt()),
            ColorDrawable(0xFFCCCCCC.toInt()),
            ColorDrawable(0xFF000000.toInt()),
        )
        val first = loader.load(component, raw)
        loader.onConfigurationChanged()
        val second = loader.load(component, raw)
        assertThat(second).isNotSameInstanceAs(first)
    }

    @Test
    fun load_settingMalformedJson_treatedAsOff() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            "not-json-at-all",
        )
        val loader = ThemedIconLoader(context)
        val raw = ColorDrawable(0xFF112233.toInt())
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
