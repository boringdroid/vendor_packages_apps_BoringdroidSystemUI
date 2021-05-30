package com.boringdroid.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import java.lang.ref.WeakReference

class AllAppsWindow(private val mContext: Context?) : View.OnClickListener {
    private val mWindowManager: WindowManager
    private var mWindowContentView: View? = null
    private var mAllAppsLayout: AllAppsLayout? = null
    private var mShown = false
    private val mAppLoaderTask: AppLoaderTask
    private val mHandler = H(this)
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onClick(v: View) {
        if (mShown) {
            dismiss()
            return
        }
        val layoutParams = generateLayoutParams(mContext, mWindowManager)
        mWindowContentView = LayoutInflater.from(mContext).inflate(R.layout.layout_all_apps, null)
        mAllAppsLayout = mWindowContentView!!.findViewById(R.id.all_apps_layout)
        mAllAppsLayout!!.handler = mHandler
        val elevation = mContext!!.resources.getInteger(R.integer.all_apps_elevation)
        mWindowContentView!!.elevation = elevation.toFloat()
        mWindowContentView!!.setOnTouchListener { _: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismiss()
            }
            false
        }
        val cornerRadius = mContext.resources.getDimension(R.dimen.all_apps_corner_radius)
        mWindowContentView!!.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        mWindowContentView!!.clipToOutline = true
        mWindowManager.addView(mWindowContentView, layoutParams)
        mAppLoaderTask.start()
        mShown = true
    }

    private fun generateLayoutParams(
        context: Context?, windowManager: WindowManager
    ): WindowManager.LayoutParams {
        val resources = context!!.resources
        val windowWidth = resources.getDimension(R.dimen.all_apps_window_width).toInt()
        val windowHeight = resources.getDimension(R.dimen.all_apps_window_height).toInt()
        val layoutParams = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.RGB_565
        )
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val size = Point()
        windowManager.defaultDisplay.getRealSize(size)
        val marginStart = resources.getDimension(R.dimen.all_apps_window_margin_horizontal)
            .toInt()
        val marginVertical = resources.getDimension(R.dimen.all_apps_window_margin_vertical)
            .toInt()
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
        try {
            mWindowManager.removeViewImmediate(mWindowContentView)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Catch exception when remove all apps window", e)
        }
        mWindowContentView = null
        mShown = false
    }

    private fun notifyLoadSucceed() {
        mAllAppsLayout!!.setData(mAppLoaderTask.allApps)
    }

    private class H(allAppsWindow: AllAppsWindow?) : Handler() {
        private val mAllAppsWindow: WeakReference<AllAppsWindow?>
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HandlerConstant.H_LOAD_SUCCEED -> runMethodSafely(
                    object : RunAllAppsWindowMethod {
                        override fun run(allAppsWindow: AllAppsWindow?) {
                            allAppsWindow!!.notifyLoadSucceed()
                        }
                    }
                )
                HandlerConstant.H_DISMISS_ALL_APPS_WINDOW -> runMethodSafely(
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
            if (mAllAppsWindow.get() != null) {
                method.run(mAllAppsWindow.get())
            }
        }

        private interface RunAllAppsWindowMethod {
            fun run(allAppsWindow: AllAppsWindow?)
        }

        init {
            mAllAppsWindow = WeakReference(allAppsWindow)
        }
    }

    companion object {
        private const val TAG = "AllAppsWindow"
    }

    init {
        mWindowManager = mContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mAppLoaderTask = AppLoaderTask(mContext, mHandler)
    }
}