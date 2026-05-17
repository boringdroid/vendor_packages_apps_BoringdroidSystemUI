package com.boringdroid.systemui.peek

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.widget.ImageView
import com.boringdroid.systemui.R

/**
 * Drop-down caption that slides into view at the top of the display when the user hovers the
 * [HoverEdgeWindow]. Shows the foreground task's icon and label plus restore / minimize / close
 * buttons. Auto-hides after the cursor has been outside the panel for [AUTO_HIDE_MS]; hovering
 * back over the panel cancels the pending hide.
 *
 * The plugin context drives the Compose tree (resources, classloader); the host SystemUI context
 * owns the WindowManager binder and is where the panel is registered. Same split as the other
 * plugin windows.
 */
class PeekPanelWindow(
    private val pluginContext: Context,
    private val hostContext: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onRestore(taskId: Int)
        fun onMinimize(taskId: Int)
        fun onClose(taskId: Int)
    }

    private val windowManager =
        hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val pluginLifecycle = PluginLifecycleOwner()
    private var root: FrameLayout? = null
    private val heightPx =
        pluginContext.resources.getDimensionPixelSize(R.dimen.peek_panel_height)
    private val hideRunnable = Runnable { hide() }

    fun show(target: PeekTarget) {
        if (root != null) return
        val ctx = buildComposeContext()
        val frame = FrameLayout(ctx)
        frame.setOnHoverListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_MOVE -> {
                    handler.removeCallbacks(hideRunnable)
                    true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    handler.removeCallbacks(hideRunnable)
                    handler.postDelayed(hideRunnable, AUTO_HIDE_MS)
                    true
                }
                else -> false
            }
        }
        val compose = ComposeView(ctx)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        compose.setContent {
            PeekCaption(
                target = target,
                onRestore = { callbacks.onRestore(target.taskId) },
                onMinimize = { callbacks.onMinimize(target.taskId) },
                onClose = { callbacks.onClose(target.taskId) },
            )
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

        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.token = Binder()
        lp.title = "BoringdroidPeekPanel"
        frame.translationY = -heightPx.toFloat()
        windowManager.addView(frame, lp)
        root = frame
        frame.animate().translationY(0f).setDuration(ANIM_DURATION_MS).start()
    }

    fun hide() {
        val v = root ?: return
        handler.removeCallbacks(hideRunnable)
        v.animate()
            .translationY(-heightPx.toFloat())
            .setDuration(ANIM_DURATION_MS)
            .withEndAction {
                try {
                    windowManager.removeViewImmediate(v)
                } catch (_: IllegalArgumentException) {
                }
            }
            .start()
        pluginLifecycle.moveToDestroyed()
        root = null
    }

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

    @Composable
    @OptIn(ExperimentalComposeUiApi::class)
    private fun PeekCaption(
        target: PeekTarget,
        onRestore: () -> Unit,
        onMinimize: () -> Unit,
        onClose: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(heightDp()),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            ) {
                Box(
                    modifier =
                        Modifier.size(20.dp).semantics {
                            testTagsAsResourceId = true
                            // Single tag — only one peek panel is ever visible. Mirrors the
                            // Taskbar pattern (Modifier.semantics { testTagsAsResourceId =
                            // true }.testTag(...)) so instrumentation tests can resolve this
                            // icon via `By.res("com.boringdroid.systemui:id/peek_caption_icon")`.
                            testTag = "com.boringdroid.systemui:id/peek_caption_icon"
                        },
                ) {
                    target.icon?.let { drawable ->
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply { setImageDrawable(drawable) }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = target.label?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().padding(end = 0.dp)) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onMinimize) {
                            Icon(Icons.Filled.Remove, contentDescription = "Minimize")
                        }
                        IconButton(onClick = onRestore) {
                            Icon(
                                Icons.Filled.CloseFullscreen,
                                contentDescription = "Restore",
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun heightDp(): androidx.compose.ui.unit.Dp {
        val density = androidx.compose.ui.platform.LocalDensity.current
        return with(density) { heightPx.toDp() }
    }

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

    companion object {
        private const val ANIM_DURATION_MS = 150L
        private const val AUTO_HIDE_MS = 400L
    }
}
