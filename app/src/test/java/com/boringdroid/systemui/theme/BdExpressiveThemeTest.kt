package com.boringdroid.systemui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
