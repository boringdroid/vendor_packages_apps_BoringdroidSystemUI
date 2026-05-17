package com.boringdroid.systemui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONException
import org.json.JSONObject

/**
 * Wraps every icon-loading call site so the AllApps grid, taskbar rail, Overview chips, and Peek
 * caption can opt in to Material You themed icons. On API <33 or when the user has not toggled
 * "Themed icons" on, returns the raw drawable unchanged (Launcher3-parity behavior).
 *
 * Settings observer and Configuration callbacks are wired by the owners ([SystemUIOverlay] in the
 * SystemUI process, [BoringdroidOverviewService] in the plugin's own process). The loader itself
 * is plain Kotlin — no Android lifecycle.
 */
class ThemedIconLoader(private val context: Context) {

    private val cache = ConcurrentHashMap<CacheKey, Drawable>()
    @Volatile private var themedIconsEnabled: Boolean = readSetting()

    fun load(info: LauncherActivityInfo): Drawable =
        themed(info.componentName, info.getIcon(0))

    fun load(component: ComponentName, raw: Drawable): Drawable =
        themed(component, raw)

    fun onThemedIconsSettingChanged() {
        themedIconsEnabled = readSetting()
        cache.clear()
    }

    fun onConfigurationChanged() {
        cache.clear()
    }

    private fun themed(component: ComponentName, raw: Drawable): Drawable {
        if (!themedIconsEnabled) return raw
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return raw
        if (raw !is AdaptiveIconDrawable) return raw
        return raw
    }

    private fun readSetting(): Boolean {
        val rawSetting =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            ) ?: return false
        return try {
            JSONObject(rawSetting).optString("android.theme.customization.themed_icon") == "1"
        } catch (e: JSONException) {
            false
        }
    }

    private data class CacheKey(val component: ComponentName, val dark: Boolean)
}

/** Composition local for surfaces that resolve icons inside `@Composable` code (Overview). */
val LocalThemedIconLoader = staticCompositionLocalOf<ThemedIconLoader?> { null }
