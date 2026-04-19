package com.boringdroid.systemui

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.lang.ref.WeakReference

class AllAppsWindow(private val mContext: Context?, private val hostContext: Context? = null) :
    View.OnClickListener {
    private val windowManager: WindowManager
    private var windowContentView: View? = null
    private var allAppsLayout: AllAppsLayout? = null
    private var shown = false
    private val appLoaderTask: AppLoaderTask
    private val handler = H(this)
    // Compose's AbstractComposeView#onAttachedToWindow requires a LifecycleOwner
    // and SavedStateRegistryOwner attached to the view tree. Plugin-owned windows
    // added via WindowManager.addView have no Activity to provide them, so we
    // ship our own.
    private val pluginLifecycle = PluginLifecycleOwner()

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onClick(v: View) {
        if (shown) {
            dismiss()
            return
        }
        val layoutParams = generateLayoutParams(mContext, windowManager)
        // Build the popup hierarchy programmatically. Inflating layout_all_apps.xml
        // would route <com.boringdroid.systemui.AllAppsLayout> through the host
        // SystemUI classloader, producing a ClassCastException on findViewById
        // when the plugin-classloader's Class for the same FQCN diverges from
        // the host one. See the same mitigation in SystemUIOverlay.kt for
        // AppStateLayout.
        // The plugin context is correct for resources/classloader/theme, but its
        // ContextWrapper chain doesn't support application-level APIs that Compose
        // invokes on getApplicationContext():
        //   * WindowRecomposer calls applicationContext.getContentResolver()
        //   * AndroidCompositionLocals calls applicationContext.registerComponentCallbacks
        // The host SystemUI context IS a real Application, so we delegate just those
        // surfaces to it while keeping everything else (resources, layouts, theme,
        // classloader, package name for UiAutomator id resolution) on the plugin.
        val ctx: Context =
            if (hostContext != null) {
                val hostApp = hostContext.applicationContext ?: hostContext
                object : ContextWrapper(mContext) {
                    override fun getApplicationContext(): Context = hostApp

                    override fun registerComponentCallbacks(cb: ComponentCallbacks) {
                        hostApp.registerComponentCallbacks(cb)
                    }

                    override fun unregisterComponentCallbacks(cb: ComponentCallbacks) {
                        hostApp.unregisterComponentCallbacks(cb)
                    }
                }
            } else {
                mContext!!
            }
        val wrapper = RelativeLayout(ctx)
        wrapper.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        val colorAttr = TypedValue()
        wrapper.setBackgroundColor(
            if (ctx.theme.resolveAttribute(android.R.attr.colorPrimaryDark, colorAttr, true))
                colorAttr.data
            else Color.BLACK
        )
        val inner = AllAppsLayout(ctx)
        inner.id = R.id.all_apps_layout
        // The Compose panel inside [AllAppsLayout] paints its own M3 surface
        // background and internal padding, so make it fill the rounded wrapper
        // exactly — no outer margin strip in the legacy colorPrimaryDark tint.
        val innerLp =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        wrapper.addView(inner, innerLp)
        // Install lifecycle + saved-state owners so Compose can build a recomposer.
        pluginLifecycle.moveToResumed()
        wrapper.setViewTreeLifecycleOwner(pluginLifecycle)
        wrapper.setViewTreeSavedStateRegistryOwner(pluginLifecycle)
        windowContentView = wrapper
        allAppsLayout = inner
        allAppsLayout!!.handler = handler
        val elevation = mContext!!.resources.getInteger(R.integer.all_apps_elevation)
        windowContentView!!.elevation = elevation.toFloat()
        windowContentView!!.setOnTouchListener { _: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismiss()
            }
            false
        }
        val cornerRadius = mContext.resources.getDimension(R.dimen.all_apps_corner_radius)
        windowContentView!!.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        windowContentView!!.clipToOutline = true
        windowManager.addView(windowContentView, layoutParams)
        appLoaderTask.start()
        shown = true
    }

    private fun generateLayoutParams(
        context: Context?,
        windowManager: WindowManager,
    ): WindowManager.LayoutParams {
        val resources = context!!.resources
        val windowWidth = resources.getDimension(R.dimen.all_apps_window_width).toInt()
        val windowHeight = resources.getDimension(R.dimen.all_apps_window_height).toInt()
        val layoutParams =
            WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.RGB_565,
            )
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val size = Point()
        windowManager.defaultDisplay.getRealSize(size)
        val marginStart = resources.getDimension(R.dimen.all_apps_window_margin_horizontal).toInt()
        val marginVertical = resources.getDimension(R.dimen.all_apps_window_margin_vertical).toInt()
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = marginStart
        // TODO: Looks like the heightPixels is incorrect, so we use multi margin to
        //  achieve looks-fine vertical margin of window. Figure out the real reason
        //  of this problem, and fix it.
        layoutParams.y = displayMetrics.heightPixels - windowHeight - marginVertical * 3
        Log.d(TAG, "All apps window location (" + layoutParams.x + ", " + layoutParams.y + ")")
        return layoutParams
    }

    fun dismiss() {
        // CLOSE_SYSTEM_DIALOGS is broadcast for many reasons unrelated to us
        // (home press, power menu, volume dialog). Without this guard every
        // boot would call removeViewImmediate(null) and then try to drive the
        // untouched LifecycleRegistry from INITIALIZED → DESTROYED, which is
        // not a legal transition.
        if (!shown) return
        try {
            windowManager.removeViewImmediate(windowContentView)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Catch exception when remove all apps window", e)
        }
        pluginLifecycle.moveToDestroyed()
        windowContentView = null
        shown = false
    }

    /**
     * Minimal [LifecycleOwner] + [SavedStateRegistryOwner] for the plugin's WindowManager-attached
     * popup. Compose requires both on the view tree; there's no Activity to provide them here, so
     * we hand-drive the lifecycle to RESUMED while the window is shown and to DESTROYED on
     * dismiss.
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
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun notifyLoadSucceed() {
        allAppsLayout!!.setData(appLoaderTask.allApps)
    }

    private class H(allAppsWindow: AllAppsWindow?) : Handler() {
        private val allAppsWindow: WeakReference<AllAppsWindow?>

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HandlerConstant.H_LOAD_SUCCEED ->
                    runMethodSafely(
                        object : RunAllAppsWindowMethod {
                            override fun run(allAppsWindow: AllAppsWindow?) {
                                allAppsWindow!!.notifyLoadSucceed()
                            }
                        }
                    )
                HandlerConstant.H_DISMISS_ALL_APPS_WINDOW ->
                    runMethodSafely(
                        object : RunAllAppsWindowMethod {
                            override fun run(allAppsWindow: AllAppsWindow?) {
                                allAppsWindow!!.dismiss()
                            }
                        }
                    )
                else -> {
                    // Do nothing
                }
            }
        }

        private fun runMethodSafely(method: RunAllAppsWindowMethod) {
            if (allAppsWindow.get() != null) {
                method.run(allAppsWindow.get())
            }
        }

        private interface RunAllAppsWindowMethod {
            fun run(allAppsWindow: AllAppsWindow?)
        }

        init {
            this.allAppsWindow = WeakReference(allAppsWindow)
        }
    }

    companion object {
        private const val TAG = "AllAppsWindow"
    }

    init {
        windowManager = mContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        appLoaderTask = AppLoaderTask(mContext, handler)
    }
}
