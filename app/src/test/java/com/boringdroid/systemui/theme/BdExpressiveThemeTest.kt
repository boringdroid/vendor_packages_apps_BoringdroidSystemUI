package com.boringdroid.systemui.theme

import android.content.Context
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the M3 Expressive seed colors against accidental drift.
 *
 * The SystemUI surfaces ship a hand-transcribed copy of `tokens.css` (see the design bundle under
 * `boringdroid/designs/boringdroid-app-design/`). If the design bundle seed color moves, this test
 * fails loud — the token update should land here at the same time as the rest of the theme.
 */
@RunWith(RobolectricTestRunner::class)
class BdExpressiveThemeTest {

    @Test
    fun lightColors_primaryMatchesDesignSeed() {
        assertThat(BdExpressiveTheme.LightColors.primary).isEqualTo(Color(0xFF6750A4))
    }

    @Test
    fun darkColors_primaryMatchesDesignSeed() {
        assertThat(BdExpressiveTheme.DarkColors.primary).isEqualTo(Color(0xFFD0BCFF))
    }

    @Test
    fun lightColors_surfaceContainerIsExpressive() {
        assertThat(BdExpressiveTheme.LightColors.surfaceContainer).isEqualTo(Color(0xFFF3EDF7))
    }

    @Test
    fun darkColors_backgroundIsExpressive() {
        assertThat(BdExpressiveTheme.DarkColors.background).isEqualTo(Color(0xFF141218))
    }

    @Test
    fun resolveColorScheme_dynamicColorFalse_light_returnsFixedSeed() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val scheme = resolveColorScheme(ctx, darkTheme = false, dynamicColor = false)
        assertThat(scheme.primary).isEqualTo(BdExpressiveTheme.LightColors.primary)
    }

    @Test
    fun resolveColorScheme_dynamicColorFalse_dark_returnsFixedSeed() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val scheme = resolveColorScheme(ctx, darkTheme = true, dynamicColor = false)
        assertThat(scheme.primary).isEqualTo(BdExpressiveTheme.DarkColors.primary)
    }

    @Test
    @Config(sdk = [31])
    fun resolveColorScheme_dynamicColorTrue_postSPath_matchesDynamicLightScheme() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val scheme = resolveColorScheme(ctx, darkTheme = false, dynamicColor = true)
        val expected = dynamicLightColorScheme(ctx)
        assertThat(scheme.primary).isEqualTo(expected.primary)
        assertThat(scheme.surface).isEqualTo(expected.surface)
        assertThat(scheme.primary).isNotEqualTo(BdExpressiveTheme.LightColors.primary)
    }
}
