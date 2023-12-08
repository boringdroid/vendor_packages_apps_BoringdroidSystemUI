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
import com.android.systemui.shared.system.TaskStackChangeListeners
import java.util.function.Consumer
import kotlin.math.abs

class AppStateLayout
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {
    private val activityManager: ActivityManager
    private val appStateListener = AppStateListener()
    private val launchApps: LauncherApps
    private val userManager: UserManager
    private val tasks: MutableList<TaskInfo> = ArrayList()
    private val taskAdapter: TaskAdapter?

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TC_WRAPPER.registerTaskStackListener(appStateListener)
    }

    override fun onDetachedFromWindow() {
        TC_WRAPPER.unregisterTaskStackListener(appStateListener)
        super.onDetachedFromWindow()
    }

    fun initTasks() {
        val runningTaskInfos = activityManager.getRunningTasks(MAX_RUNNING_TASKS)
        for (i in runningTaskInfos.indices.reversed()) {
            val runningTaskInfo = runningTaskInfos[i]
            if (shouldIgnoreTopTask(runningTaskInfo.topActivity)) {
                continue
            }
            topTask(runningTaskInfo, true)
        }
    }

    private fun removeTask(taskId: Int) {
        tasks.removeIf { taskInfo: TaskInfo -> taskInfo.id == taskId }
        taskAdapter!!.setData(tasks)
        taskAdapter.notifyDataSetChanged()
    }

    private fun getRunningTaskInfoPackageName(runningTaskInfo: RunningTaskInfo): String? {
        return if (runningTaskInfo.baseActivity == null) {
            null
        } else {
            runningTaskInfo.baseActivity!!.packageName
        }
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

    private fun topTask(
        runningTaskInfo: RunningTaskInfo,
        skipIgnoreCheck: Boolean = false,
    ) {
        val packageName = getRunningTaskInfoPackageName(runningTaskInfo)
        if (!skipIgnoreCheck && shouldIgnoreTopTask(runningTaskInfo.topActivity)) {
            taskAdapter!!.setTopTaskId(-1)
            taskAdapter.notifyDataSetChanged()
            return
        }
        val taskInfo = TaskInfo()
        taskInfo.id = runningTaskInfo.id
        taskInfo.setBaseActivityComponentName(runningTaskInfo.baseActivity)
        taskInfo.setRealActivityComponentName(runningTaskInfo.topActivity)
        taskInfo.packageName = packageName
        val userHandles = userManager.userProfiles
        for (userHandle in userHandles) {
            val infoList = launchApps.getActivityList(packageName, userHandle)
            if (infoList.size > 0 && infoList[0] != null) {
                taskInfo.icon = infoList[0]!!.getIcon(0)
                break
            }
        }
        var icon = taskInfo.icon
        icon =
            if (icon == null && context != null) {
                context.getDrawable(R.mipmap.default_icon_round)
            } else {
                icon
            }
        if (icon == null) {
            Log.e(TAG, "$packageName's icon is null, context $context")
        }
        taskInfo.icon = icon
        val index = tasks.indexOf(taskInfo)
        tasks.remove(taskInfo)
        tasks.add(if (index >= 0) index else tasks.size, taskInfo)
        taskAdapter!!.setData(tasks)
        taskAdapter.setTopTaskId(taskInfo.id)
        Log.d(TAG, "Top task $taskInfo")
        taskAdapter.notifyDataSetChanged()
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
    fun isLauncher(
        context: Context,
        componentName: ComponentName?,
    ): Boolean {
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
        taskAdapter?.reloadActivityManager(context)
    }

    private inner class AppStateListener : TaskStackChangeListener {
        override fun onTaskCreated(
            taskId: Int,
            componentName: ComponentName?,
        ) {
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

    private class TaskAdapter(private val context: Context, dragCloseThreshold: Int) :
        Adapter<TaskAdapter.ViewHolder>() {
        private val tasks: MutableList<TaskInfo> = ArrayList()
        private var systemUIActivityManager: ActivityManager
        private val packageManager: PackageManager
        private var topTaskId = -1
        private val dragCloseThreshold: Int

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val taskInfoLayout =
                LayoutInflater.from(context).inflate(R.layout.layout_task_info, parent, false)
                    as ViewGroup
            return ViewHolder(taskInfoLayout)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val taskInfo = tasks[position]
            val packageName = taskInfo.packageName
            holder.iconIV.setImageDrawable(taskInfo.icon)
            if (taskInfo.id == topTaskId) {
                holder.highLightLineTV.setImageResource(R.drawable.line_long)
            } else {
                holder.highLightLineTV.setImageResource(R.drawable.line_short)
            }
            var label: CharSequence? = packageName
            try {
                label =
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(
                            packageName!!,
                            PackageManager.GET_META_DATA
                        ),
                    )
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Failed to get label for $packageName")
            }
            holder.iconIV.tag = taskInfo.id
            holder.iconIV.tooltipText = label
            holder.iconIV.setOnClickListener {
                systemUIActivityManager.moveTaskToFront(taskInfo.id, 0)
                context.sendBroadcast(
                    Intent("com.boringdroid.systemui.CLOSE_RECENTS"),
                )
            }
            holder.iconIV.setOnLongClickListener { v: View ->
                val item = ClipData.Item(TAG_TASK_ICON)
                val dragData = ClipData(TAG_TASK_ICON, arrayOf("unknown"), item)
                val shadow: DragShadowBuilder = DragDropShadowBuilder(v)
                holder.iconIV.setOnDragListener(
                    DragDropCloseListener(
                        dragCloseThreshold,
                        dragCloseThreshold,
                    ) { taskId: Int? ->
                        AM_WRAPPER.removeTask(
                            taskId!!,
                        )
                    },
                )
                v.startDragAndDrop(dragData, shadow, null, DRAG_FLAG_GLOBAL)
                true
            }
        }

        override fun getItemCount(): Int {
            return tasks.size
        }

        fun setData(tasks: List<TaskInfo>?) {
            this.tasks.clear()
            this.tasks.addAll(tasks!!)
        }

        fun setTopTaskId(id: Int) {
            topTaskId = id
        }

        fun reloadActivityManager(context: Context?) {
            systemUIActivityManager =
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
            systemUIActivityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            packageManager = context.packageManager
            this.dragCloseThreshold = dragCloseThreshold
        }
    }

    private class DragDropCloseListener(
        private val width: Int,
        private val height: Int,
        private val endcallback: Consumer<Int?>?,
    ) : OnDragListener {
        private var startX = 0f
        private var startY = 0f

        override fun onDrag(
            v: View,
            event: DragEvent,
        ): Boolean {
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val locations = IntArray(2)
                    v.getLocationOnScreen(locations)
                    startX = locations[0].toFloat()
                    startY = locations[1].toFloat()
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    val x = event.x
                    val y = event.y
                    if (abs(x - startX) < width && abs(y - startY) < height) {
                        // Do nothing
                    } else {
                        v.setOnDragListener(null)
                        if (endcallback != null && v.tag is Int) {
                            endcallback.accept(v.tag as Int)
                        }
                    }
                }
            }
            return true
        }
    }

    private class DragDropShadowBuilder(v: View?) : DragShadowBuilder(v) {
        override fun onProvideShadowMetrics(
            size: Point,
            touch: Point,
        ) {
            val width = view.width
            val height = view.height
            shadow.setBounds(0, 0, width, height)
            size[width] = height
            touch[width / 2] = height / 2
        }

        override fun onDrawShadow(canvas: Canvas) {
            shadow.draw(canvas)
        }

        companion object {
            private lateinit var shadow: Drawable
        }

        init {
            shadow =
                if (v is ImageView && v.drawable != null) {
                    v.drawable.mutate().constantState!!.newDrawable()
                } else {
                    ColorDrawable(Color.LTGRAY)
                }
        }
    }

    companion object {
        private const val TAG = "AppStateLayout"
        private val AM_WRAPPER = ActivityManagerWrapper.getInstance()
        private val TC_WRAPPER = TaskStackChangeListeners.getInstance()
        private const val MAX_RUNNING_TASKS = 50
    }

    init {
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        launchApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val manager = LinearLayoutManager(context, HORIZONTAL, false)
        layoutManager = manager
        setHasFixedSize(true)
        val appInfoIconWidth = context.resources.getDimensionPixelSize(R.dimen.app_info_icon_width)
        taskAdapter = TaskAdapter(context, (appInfoIconWidth * 5))
        adapter = taskAdapter
    }
}
