package com.boringdroid.systemui

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Insets
import android.graphics.PixelFormat
import android.os.Binder
import android.view.Gravity
import android.view.InsetsFrameProvider
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.boringdroid.systemui.taskbar.Taskbar
import com.boringdroid.systemui.taskbar.TaskbarCallbacks
import com.boringdroid.systemui.taskbar.TaskbarState

/**
 * Owns a boringdroid-managed window pinned to the bottom of the display that hosts the Compose
 * [Taskbar]. The window replaces the NavigationBar on boringdroid builds; SystemUI's native
 * NavigationBarView is suppressed via the RRO shipped alongside this plugin.
 *
 * The ComposeView needs a `LifecycleOwner` + `SavedStateRegistryOwner` on the view tree — attached
 * here because the plugin's `WindowManager.addView` target has no hosting Activity. The same
 * pattern is used in [AllAppsWindow] and
 * [com.boringdroid.systemui.actioncenter.ActionCenterWindow].
 *
 * The plugin context carries resources/classloader/theme, but Compose's `WindowRecomposer` and
 * `AndroidCompositionLocals` reach for `applicationContext.getContentResolver` /
 * `registerComponentCallbacks` surfaces the plugin ContextWrapper chain doesn't satisfy — a
 * delegating wrapper routes just those calls to the host SystemUI application.
 */
class TaskbarWindow(private val pluginContext: Context, private val hostContext: Context) {
    private val windowManager =
        hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private val pluginLifecycle = PluginLifecycleOwner()

    var callbacks: TaskbarCallbacks? = null

    fun show(state: TaskbarState) {
        if (root != null) return
        val ctx: Context = buildComposeContext()
        val frame = FrameLayout(ctx)
        val compose = ComposeView(ctx)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        compose.setContent {
            val active = callbacks ?: return@setContent
            Taskbar(state = state, callbacks = active)
        }
        frame.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        pluginLifecycle.moveToResumed()
        frame.setViewTreeLifecycleOwner(pluginLifecycle)
        frame.setViewTreeSavedStateRegistryOwner(pluginLifecycle)

        val heightPx = pluginContext.resources.getDimensionPixelSize(R.dimen.taskbar_window_height)
        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                // TYPE_NAVIGATION_BAR (not _PANEL) is the window type whose providedInsets WMS
                // actually propagates to app windows. NAVIGATION_BAR_PANEL is a supplementary
                // panel layered over the bar and has its inset advertisement ignored, leaving
                // Launcher3 and other apps drawing under the taskbar. The stock SystemUI nav bar
                // is disabled on boringdroid (config_showNavigationBar=false), so there is no
                // conflicting TYPE_NAVIGATION_BAR window.
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.token = Binder()
        lp.title = "BoringdroidTaskbar"
        // Advertise this window as a navigation-bar insets source so Launcher3 (and any other
        // full-height app) leaves room for the taskbar instead of drawing under it. The
        // advertised inset is `taskbar_window_height + panel_taskbar_gap` — the visual taskbar
        // plus the same breathing gap our own panels leave above it. Reporting exactly
        // `taskbar_window_height` made Launcher3's Hotseat icons sit flush against the top
        // edge of the taskbar (`hotseatBarBottomSpacePx = mInsets.bottom + minQsbMargin` in
        // DeviceProfile only adds a minQsbMargin of a couple px), which reads as the taskbar
        // "overlapping" Launcher content. The extra 8dp restores a visible gap.
        val insetBottom = heightPx + pluginContext.resources.getDimensionPixelSize(R.dimen.panel_taskbar_gap)
        lp.providedInsets =
            arrayOf(
                InsetsFrameProvider(lp.token, 0, WindowInsets.Type.navigationBars())
                    .setInsetsSize(Insets.of(0, 0, 0, insetBottom))
            )
        windowManager.addView(frame, lp)
        root = frame
    }

    fun hide() {
        root?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: IllegalArgumentException) {
                // View was never attached or already detached — nothing to do.
            }
        }
        pluginLifecycle.moveToDestroyed()
        root = null
    }

    /**
     * Back-compat: some callers still want to query the root view (e.g. for test-driven attachment
     * assertions). Returns the frame hosting the [ComposeView], or null while the window is hidden.
     */
    fun getRoot(): View? = root

    private fun buildComposeContext(): Context {
        val hostApp = hostContext.applicationContext ?: hostContext
        return object : ContextWrapper(pluginContext) {
            override fun getApplicationContext(): Context = hostApp

            override fun registerComponentCallbacks(cb: ComponentCallbacks) {
                hostApp.registerComponentCallbacks(cb)
            }

            override fun unregisterComponentCallbacks(cb: ComponentCallbacks) {
                hostApp.unregisterComponentCallbacks(cb)
            }
        }
    }

    /**
     * Minimal [LifecycleOwner] + [SavedStateRegistryOwner] for the plugin's WindowManager-attached
     * taskbar. Compose needs both on the view tree; no Activity exists to provide them so we
     * hand-drive the lifecycle to RESUMED on show and DESTROYED on hide.
     */
    private class PluginLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = registry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedState.savedStateRegistry

        fun moveToResumed() {
            if (registry.currentState == Lifecycle.State.INITIALIZED) {
                savedState.performRestore(null)
            }
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun moveToDestroyed() {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
