package com.boringdroid.systemui

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.util.AttributeSet
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.TaskStackChangeListener
import java.util.function.Consumer
import kotlin.math.abs

class AppStateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private val mActivityManager: ActivityManager
    private val mListener = AppStateListener()
    private val mLaunchApps: LauncherApps
    private val mUserManager: UserManager
    private val mTasks: MutableList<TaskInfo> = ArrayList()
    private val mAdapter: TaskAdapter?
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AM_WRAPPER.registerTaskStackListener(mListener)
    }

    override fun onDetachedFromWindow() {
        AM_WRAPPER.unregisterTaskStackListener(mListener)
        super.onDetachedFromWindow()
    }

    fun initTasks() {
        val runningTaskInfos = mActivityManager.getRunningTasks(MAX_RUNNING_TASKS)
        for (i in runningTaskInfos.indices.reversed()) {
            val runningTaskInfo = runningTaskInfos[i]
            if (shouldIgnoreTopTask(runningTaskInfo.topActivity)) {
                continue
            }
            topTask(runningTaskInfo, true)
        }
    }

    private fun removeTask(taskId: Int) {
        mTasks.removeIf { taskInfo: TaskInfo -> taskInfo.id == taskId }
        mAdapter!!.setData(mTasks)
        mAdapter.notifyDataSetChanged()
    }

    private fun getRunningTaskInfoPackageName(runningTaskInfo: RunningTaskInfo): String? {
        return if (runningTaskInfo.baseActivity == null) null else
            runningTaskInfo.baseActivity!!.packageName
    }

    fun shouldIgnoreTopTask(componentName: ComponentName?): Boolean {
        if (componentName == null) {
            Log.d(TAG, "Ignore invalid component name")
            return true
        }
        val packageName = componentName.packageName
        if ("android" == packageName) {
            Log.d(TAG, "Ignore android")
            return true
        }
        if (isSpecialLauncher(packageName)) {
            Log.d(TAG, "Ignore launcher $packageName")
            return true
        }
        if (context != null && packageName.startsWith(context.packageName)) {
            Log.d(TAG, "Ignore self $packageName")
            return true
        }
        if (isLauncher(context, componentName)) {
            Log.d(TAG, "Ignore launcher $componentName")
            return true
        }
        if (packageName.startsWith("com.android.systemui")) {
            Log.d(TAG, "Ignore systemui $packageName")
            return true
        }
        Log.d(TAG, "Don't ignore top task $packageName")
        return false
    }

    private fun topTask(runningTaskInfo: RunningTaskInfo, skipIgnoreCheck: Boolean = false) {
        val packageName = getRunningTaskInfoPackageName(runningTaskInfo)
        if (!skipIgnoreCheck && shouldIgnoreTopTask(runningTaskInfo.topActivity)) {
            mAdapter!!.setTopTaskId(-1)
            mAdapter.notifyDataSetChanged()
            return
        }
        val taskInfo = TaskInfo()
        taskInfo.id = runningTaskInfo.id
        taskInfo.setBaseActivityComponentName(runningTaskInfo.baseActivity)
        taskInfo.setRealActivityComponentName(runningTaskInfo.topActivity)
        taskInfo.packageName = packageName
        val userHandles = mUserManager.userProfiles
        for (userHandle in userHandles) {
            val infoList = mLaunchApps.getActivityList(packageName, userHandle)
            if (infoList.size > 0 && infoList[0] != null) {
                taskInfo.icon = infoList[0]!!.getIcon(0)
                break
            }
        }
        var icon = taskInfo.icon
        icon =
            if (icon == null && context != null)
                context.getDrawable(R.mipmap.default_icon_round) else icon
        if (icon == null) {
            Log.e(TAG, "$packageName's icon is null, context $context")
        }
        taskInfo.icon = icon
        val index = mTasks.indexOf(taskInfo)
        mTasks.remove(taskInfo)
        mTasks.add(if (index >= 0) index else mTasks.size, taskInfo)
        mAdapter!!.setData(mTasks)
        mAdapter.setTopTaskId(taskInfo.id)
        Log.d(TAG, "Top task $taskInfo")
        mAdapter.notifyDataSetChanged()
    }

    private fun isSpecialLauncher(packageName: String?): Boolean {
        if ("com.farmerbb.taskbar" == packageName) {
            return true
        }
        if ("com.teslacoilsw.launcher" == packageName) {
            return true
        }
        return "ch.deletescape.lawnchair.plah" == packageName
    }

    @VisibleForTesting
    fun isLauncher(context: Context, componentName: ComponentName?): Boolean {
        if (componentName == null) {
            return false
        }
        val packageName = componentName.packageName
        val className = componentName.className
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfos) {
            Log.d(TAG, "Found launcher $resolveInfo")
            if (resolveInfo?.activityInfo == null) {
                continue
            }
            val activityInfo = resolveInfo.activityInfo
            if (packageName == activityInfo.packageName && className == activityInfo.name) {
                return true
            }
        }
        return false
    }

    fun reloadActivityManager(context: Context?) {
        mAdapter?.reloadActivityManager(context)
    }

    private inner class AppStateListener : TaskStackChangeListener() {
        override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
            super.onTaskCreated(taskId, componentName)
            Log.d(TAG, "onTaskCreated $taskId, cm $componentName")
            onTaskStackChanged()
        }

        override fun onTaskMovedToFront(taskId: Int) {
            super.onTaskMovedToFront(taskId)
            Log.d(TAG, "onTaskMoveToFront taskId $taskId")
            onTaskStackChanged()
        }

        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo) {
            super.onTaskMovedToFront(taskInfo)
            Log.d(TAG, "onTaskMovedToFront $taskInfo")
            onTaskStackChanged()
        }

        override fun onTaskStackChanged() {
            super.onTaskStackChanged()
            val info = AM_WRAPPER.getRunningTask(false)
            Log.d(TAG, "onTaskStackChanged $info")
            info?.let { topTask(it) }
        }

        override fun onTaskRemoved(taskId: Int) {
            super.onTaskRemoved(taskId)
            Log.d(TAG, "onTaskRemoved $taskId")
            removeTask(taskId)
        }
    }

    private class TaskAdapter(private val mContext: Context, dragCloseThreshold: Int) :
        Adapter<TaskAdapter.ViewHolder>() {
        private val mTasks: MutableList<TaskInfo> = ArrayList()
        private var mSystemUIActivityManager: ActivityManager
        private val mPackageManager: PackageManager
        private var mTopTaskId = -1
        private val mDragCloseThreshold: Int
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val taskInfoLayout = LayoutInflater.from(mContext)
                .inflate(R.layout.layout_task_info, parent, false) as ViewGroup
            return ViewHolder(taskInfoLayout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val taskInfo = mTasks[position]
            val packageName = taskInfo.packageName
            holder.iconIV.setImageDrawable(taskInfo.icon)
            if (taskInfo.id == mTopTaskId) {
                holder.highLightLineTV.setImageResource(R.drawable.line_long)
            } else {
                holder.highLightLineTV.setImageResource(R.drawable.line_short)
            }
            var label: CharSequence? = packageName
            try {
                label = mPackageManager.getApplicationLabel(
                    mPackageManager.getApplicationInfo(packageName!!, PackageManager.GET_META_DATA)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Failed to get label for $packageName")
            }
            holder.iconIV.tag = taskInfo.id
            holder.iconIV.tooltipText = label
            holder.iconIV.setOnClickListener {
                mSystemUIActivityManager.moveTaskToFront(taskInfo.id, 0)
                mContext.sendBroadcast(
                    Intent("com.boringdroid.systemui.CLOSE_RECENTS")
                )
            }
            holder.iconIV.setOnLongClickListener { v: View ->
                val item = ClipData.Item(TAG_TASK_ICON)
                val dragData = ClipData(TAG_TASK_ICON, arrayOf("unknown"), item)
                val shadow: DragShadowBuilder = DragDropShadowBuilder(v)
                holder.iconIV.setOnDragListener(
                    DragDropCloseListener(
                        mDragCloseThreshold,
                        mDragCloseThreshold
                    ) { taskId: Int? ->
                        AM_WRAPPER.removeTask(
                            taskId!!
                        )
                    })
                v.startDragAndDrop(dragData, shadow, null, DRAG_FLAG_GLOBAL)
                true
            }
        }

        override fun getItemCount(): Int {
            return mTasks.size
        }

        fun setData(tasks: List<TaskInfo>?) {
            mTasks.clear()
            mTasks.addAll(tasks!!)
        }

        fun setTopTaskId(id: Int) {
            mTopTaskId = id
        }

        fun reloadActivityManager(context: Context?) {
            mSystemUIActivityManager =
                context!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        }

        private class ViewHolder(taskInfoLayout: ViewGroup) :
            RecyclerView.ViewHolder(taskInfoLayout) {
            val iconIV: ImageView = taskInfoLayout.findViewById(R.id.iv_task_info_icon)
            val highLightLineTV: ImageView = taskInfoLayout.findViewById(R.id.iv_highlight_line)
        }

        companion object {
            private const val TAG_TASK_ICON = "task_icon"
        }

        init {
            // We will use reloadActivityManager to update mSystemUIActivityManager with
            // systemui context.
            mSystemUIActivityManager =
                mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            mPackageManager = mContext.packageManager
            mDragCloseThreshold = dragCloseThreshold
        }
    }

    private class DragDropCloseListener(
        private val mWidth: Int,
        private val mHeight: Int,
        private val mEndCallback: Consumer<Int?>?
    ) : OnDragListener {
        private var mStartX = 0f
        private var mStartY = 0f
        override fun onDrag(v: View, event: DragEvent): Boolean {
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val locations = IntArray(2)
                    v.getLocationOnScreen(locations)
                    mStartX = locations[0].toFloat()
                    mStartY = locations[1].toFloat()
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    val x = event.x
                    val y = event.y
                    if (abs(x - mStartX) < mWidth && abs(y - mStartY) < mHeight) {
                        // Do nothing
                    } else {
                        v.setOnDragListener(null)
                        if (mEndCallback != null && v.tag is Int) {
                            mEndCallback.accept(v.tag as Int)
                        }
                    }
                }
            }
            return true
        }
    }

    private class DragDropShadowBuilder(v: View?) : DragShadowBuilder(v) {
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            val width = view.width
            val height = view.height
            mShadow.setBounds(0, 0, width, height)
            size[width] = height
            touch[width / 2] = height / 2
        }

        override fun onDrawShadow(canvas: Canvas) {
            mShadow.draw(canvas)
        }

        companion object {
            private lateinit var mShadow: Drawable
        }

        init {
            mShadow = if (v is ImageView && v.drawable != null) {
                v.drawable.mutate().constantState!!.newDrawable()
            } else {
                ColorDrawable(Color.LTGRAY)
            }
        }
    }

    companion object {
        private const val TAG = "AppStateLayout"
        private val AM_WRAPPER = ActivityManagerWrapper.getInstance()
        private const val MAX_RUNNING_TASKS = 50
    }

    init {
        mActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        mLaunchApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        mUserManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val manager = LinearLayoutManager(context, HORIZONTAL, false)
        layoutManager = manager
        setHasFixedSize(true)
        val appInfoIconWidth = context.resources.getDimensionPixelSize(R.dimen.app_info_icon_width)
        mAdapter = TaskAdapter(context, (appInfoIconWidth * 5))
        adapter = mAdapter
    }
}
