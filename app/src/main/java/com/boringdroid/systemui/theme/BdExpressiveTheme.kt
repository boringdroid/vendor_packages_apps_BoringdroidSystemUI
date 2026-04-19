package com.boringdroid.systemui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive design system for BoringdroidSystemUI.
 *
 * Token values are exact copies of the design bundle's `tokens.css`
 * (seed #6750A4, purple). Keep this file the single source of truth
 * across the five SystemUI surfaces (taskbar, start menu, action
 * center, calendar panel, overview) so a token tweak in one place
 * lands everywhere.
 */
object BdExpressiveTheme {

    val LightColors: ColorScheme =
        lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            inversePrimary = Color(0xFFD0BCFF),
            secondary = Color(0xFF625B71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF7D5260),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD8E4),
            onTertiaryContainer = Color(0xFF31111D),
            background = Color(0xFFFEF7FF),
            onBackground = Color(0xFF1D1B20),
            surface = Color(0xFFFEF7FF),
            onSurface = Color(0xFF1D1B20),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceTint = Color(0xFF6750A4),
            inverseSurface = Color(0xFF322F35),
            inverseOnSurface = Color(0xFFF5EFF7),
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            scrim = Color(0xFF000000),
            surfaceBright = Color(0xFFFEF7FF),
            surfaceDim = Color(0xFFDED8E1),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F2FA),
            surfaceContainer = Color(0xFFF3EDF7),
            surfaceContainerHigh = Color(0xFFECE6F0),
            surfaceContainerHighest = Color(0xFFE6E0E9),
        )

    val DarkColors: ColorScheme =
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            inversePrimary = Color(0xFF6750A4),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFEFB8C8),
            onTertiary = Color(0xFF492532),
            tertiaryContainer = Color(0xFF633B48),
            onTertiaryContainer = Color(0xFFFFD8E4),
            background = Color(0xFF141218),
            onBackground = Color(0xFFE6E0E9),
            surface = Color(0xFF141218),
            onSurface = Color(0xFFE6E0E9),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceTint = Color(0xFFD0BCFF),
            inverseSurface = Color(0xFFE6E0E9),
            inverseOnSurface = Color(0xFF322F35),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            scrim = Color(0xFF000000),
            surfaceBright = Color(0xFF3B383E),
            surfaceDim = Color(0xFF141218),
            surfaceContainerLowest = Color(0xFF0F0D13),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFF211F26),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B),
        )

    // Compose's FontFamilyResolver runs inside the SystemUI host process using the
    // plugin's Resources; bundling a variable TTF (R.font.google_sans_flex) in the
    // plugin APK crashes SystemUI with `IllegalStateException: Could not load font`
    // on the first Text measure. Fall back to the platform default family until the
    // plugin-context font pipeline is properly wired up — the M3 color/shape/motion
    // tokens still land, only the typeface differs from the design bundle.
    val GoogleSansFlex: FontFamily = FontFamily.Default

    val Typography: Typography =
        Typography(
            displayLarge =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W400,
                    fontSize = 57.sp,
                    lineHeight = 64.sp,
                    letterSpacing = (-0.25).sp,
                ),
            displayMedium =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W400,
                    fontSize = 45.sp,
                    lineHeight = 52.sp,
                ),
            displaySmall =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W400,
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                ),
            headlineLarge =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W500,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                ),
            headlineMedium =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W500,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                ),
            headlineSmall =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                ),
            titleLarge =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W500,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                ),
            titleMedium =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W600,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.15.sp,
                ),
            titleSmall =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W600,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.1.sp,
                ),
            bodyLarge =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W400,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp,
                ),
            bodyMedium =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W400,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.25.sp,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W400,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.4.sp,
                ),
            labelLarge =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W600,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.1.sp,
                ),
            labelMedium =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W600,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.5.sp,
                ),
            labelSmall =
                TextStyle(
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.W600,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.5.sp,
                ),
        )

    val Shapes: Shapes =
        Shapes(
            extraSmall = RoundedCornerShape(BdShape.xs),
            small = RoundedCornerShape(BdShape.sm),
            medium = RoundedCornerShape(BdShape.md),
            large = RoundedCornerShape(BdShape.lg),
            extraLarge = RoundedCornerShape(BdShape.xl),
        )
}

/**
 * Shape tokens beyond what Material3 `Shapes` exposes directly —
 * 2xl (36dp) and `pill` (fully rounded) are used by Action Center
 * cards, the Calendar panel, and the taskbar's Search pill.
 */
object BdShape {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 28.dp
    val xxl = 36.dp

    val extra2Large = RoundedCornerShape(xxl)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Motion tokens mirroring `--md-sys-motion-*` in tokens.css. Compose
 * has no first-class spring-path primitive matching the CSS `linear(...)`
 * keyframes exactly; `easingSpringFast` / `easingSpringSlow` use cubic
 * approximations of the dominant phase. Call sites that need the full
 * overshoot use Compose `spring()` with matching stiffness.
 */
object BdMotion {
    val easingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easingEmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val easingEmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val easingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val easingStandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val easingStandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val easingSpringFast: Easing = CubicBezierEasing(0.2f, 1.3f, 0.2f, 1f)
    val easingSpringSlow: Easing = CubicBezierEasing(0.2f, 1.18f, 0.2f, 1f)

    const val durationShort1 = 50
    const val durationShort2 = 100
    const val durationShort3 = 150
    const val durationShort4 = 200
    const val durationMedium1 = 250
    const val durationMedium2 = 300
    const val durationMedium3 = 350
    const val durationMedium4 = 400
    const val durationLong1 = 450
    const val durationLong2 = 500
    const val durationLong3 = 550
    const val durationLong4 = 600
    const val durationExtraLong1 = 700
    const val durationExtraLong2 = 800
    const val durationExtraLong3 = 900
    const val durationExtraLong4 = 1000
}

/**
 * Apply the Expressive Material3 theme to a subtree. `darkTheme = null`
 * resolves from the system setting; call sites that need to pin a
 * theme explicitly (e.g. the Calendar panel under a dark wallpaper)
 * can pass `darkTheme = true` / `false`.
 */
@Composable
fun BdExpressiveMaterialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) BdExpressiveTheme.DarkColors else BdExpressiveTheme.LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = BdExpressiveTheme.Typography,
        shapes = BdExpressiveTheme.Shapes,
        content = content,
    )
}
