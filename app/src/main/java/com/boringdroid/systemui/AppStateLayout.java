package com.boringdroid.systemui;

import static android.content.pm.PackageManager.GET_META_DATA;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AppStateLayout extends RecyclerView {
    private static final String TAG = "AppStateLayout";

    private static final ActivityManagerWrapper AM_WRAPPER = ActivityManagerWrapper.getInstance();
    private static final int MAX_RUNNING_TASKS = 50;

    private final ActivityManager mActivityManager;
    private final AppStateListener mListener = new AppStateListener();
    private final LauncherApps mLaunchApps;
    private final UserManager mUserManager;
    private final List<TaskInfo> mTasks = new ArrayList<>();
    private final TaskAdapter mAdapter;

    public AppStateLayout(Context context) {
        this(context, null);
    }

    public AppStateLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppStateLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mLaunchApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        LinearLayoutManager manager =
                new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        setLayoutManager(manager);
        setHasFixedSize(true);
        int appInfoIconWidth =
                context.getResources().getDimensionPixelSize(R.dimen.app_info_icon_width);
        int dragCloseThreshold = appInfoIconWidth * 5;
        mAdapter = new TaskAdapter(context, dragCloseThreshold);
        setAdapter(mAdapter);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AM_WRAPPER.registerTaskStackListener(mListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        AM_WRAPPER.unregisterTaskStackListener(mListener);
        super.onDetachedFromWindow();
    }

    public void initTasks() {
        List<ActivityManager.RunningTaskInfo> runningTaskInfos =
                mActivityManager.getRunningTasks(MAX_RUNNING_TASKS);
        for (int i = runningTaskInfos.size() - 1; i >= 0; i--) {
            ActivityManager.RunningTaskInfo runningTaskInfo = runningTaskInfos.get(i);
            if (shouldIgnoreTopTask(getRunningTaskInfoPackageName(runningTaskInfo))) {
                continue;
            }
            topTask(runningTaskInfo, true);
        }
    }

    private void removeTask(int taskId) {
        mTasks.removeIf(taskInfo -> taskInfo.getId() == taskId);
        mAdapter.setData(mTasks);
        mAdapter.notifyDataSetChanged();
    }

    private String getRunningTaskInfoPackageName(ActivityManager.RunningTaskInfo runningTaskInfo) {
        return runningTaskInfo.baseActivity == null
                ? null
                : runningTaskInfo.baseActivity.getPackageName();
    }

    public boolean shouldIgnoreTopTask(String packageName) {
        if (isSpecialLauncher(packageName)) {
            Log.d(TAG, "Ignore launcher " + packageName);
            return true;
        }
        if (packageName != null
                && getContext() != null
                && packageName.startsWith(getContext().getPackageName())) {
            Log.d(TAG, "Ignore self " + packageName);
            return true;
        }
        if (isLauncher(getContext(), packageName)) {
            Log.d(TAG, "Ignore launcher " + packageName);
            return true;
        }
        if (packageName != null && packageName.startsWith("com.android.systemui")) {
            Log.d(TAG, "Ignore systemui " + packageName);
            return true;
        }
        return false;
    }

    private void topTask(ActivityManager.RunningTaskInfo runningTaskInfo) {
        topTask(runningTaskInfo, false);
    }

    private void topTask(ActivityManager.RunningTaskInfo runningTaskInfo, boolean skipIgnoreCheck) {
        String packageName = getRunningTaskInfoPackageName(runningTaskInfo);
        if (!skipIgnoreCheck && shouldIgnoreTopTask(packageName)) {
            mAdapter.setTopTaskId(-1);
            mAdapter.notifyDataSetChanged();
            return;
        }
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setId(runningTaskInfo.id);
        taskInfo.setBaseActivityComponentName(runningTaskInfo.baseActivity);
        taskInfo.setRealActivityComponentName(runningTaskInfo.topActivity);
        taskInfo.setPackageName(packageName);
        List<UserHandle> userHandles = mUserManager.getUserProfiles();
        for (UserHandle userHandle : userHandles) {
            List<LauncherActivityInfo> infoList =
                    mLaunchApps.getActivityList(packageName, userHandle);
            if (infoList.size() > 0 && infoList.get(0) != null) {
                taskInfo.setIcon(infoList.get(0).getIcon(0));
                break;
            }
        }
        int index = mTasks.indexOf(taskInfo);
        mTasks.remove(taskInfo);
        mTasks.add(index >= 0 ? index : mTasks.size(), taskInfo);
        mAdapter.setData(mTasks);
        mAdapter.setTopTaskId(taskInfo.getId());
        Log.d(TAG, "Top task " + taskInfo);
        mAdapter.notifyDataSetChanged();
    }

    private boolean isSpecialLauncher(String packageName) {
        if ("com.farmerbb.taskbar".equals(packageName)) {
            return true;
        }
        if ("com.teslacoilsw.launcher".equals(packageName)) {
            return true;
        }
        if ("ch.deletescape.lawnchair.plah".equals(packageName)) {
            return true;
        }
        return false;
    }

    @VisibleForTesting
    boolean isLauncher(Context context, String packageName) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        return res != null
                && res.activityInfo != null
                && packageName != null
                && packageName.equals(res.activityInfo.packageName);
    }

    private class AppStateListener extends TaskStackChangeListener {
        @Override
        public void onTaskStackChanged() {
            super.onTaskStackChanged();
            ActivityManager.RunningTaskInfo info =
                    ActivityManagerWrapper.getInstance().getRunningTask();
            Log.d(TAG, "onTaskStackChanged " + info);
            if (info != null) {
                topTask(info);
            }
        }

        @Override
        public void onTaskRemoved(int taskId) {
            super.onTaskRemoved(taskId);
            Log.d(TAG, "onTaskRemoved " + taskId);
            removeTask(taskId);
        }
    }

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
        private static final String TAG_TASK_ICON = "task_icon";

        private final List<TaskInfo> mTasks = new ArrayList<>();
        private final Context mContext;
        private ActivityManager mSystemUIActivityManager;
        private final PackageManager mPackageManager;
        private int mTopTaskId = -1;
        private final int mDragCloseThreshold;

        public TaskAdapter(@NonNull Context context, int dragCloseThreshold) {
            mContext = context;
            // We will use reloadActivityManager to update mSystemUIActivityManager with
            // systemui context.
            mSystemUIActivityManager =
                    (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            mPackageManager = mContext.getPackageManager();
            mDragCloseThreshold = dragCloseThreshold;
        }

        @NonNull
        @Override
        public TaskAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewGroup taskInfoLayout =
                    (ViewGroup)
                            LayoutInflater.from(mContext)
                                    .inflate(R.layout.layout_task_info, parent, false);
            return new ViewHolder(taskInfoLayout);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TaskInfo taskInfo = mTasks.get(position);
            holder.iconIV.setImageDrawable(taskInfo.getIcon());
            if (taskInfo.getId() == mTopTaskId) {
                holder.highLightLineTV.setImageResource(R.drawable.line_long);
            } else {
                holder.highLightLineTV.setImageResource(R.drawable.line_short);
            }
            String packageName = taskInfo.getPackageName();
            CharSequence label = packageName;
            try {
                label =
                        mPackageManager.getApplicationLabel(
                                mPackageManager.getApplicationInfo(packageName, GET_META_DATA));
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to get label for " + packageName);
            }
            holder.iconIV.setTag(taskInfo.getId());
            holder.iconIV.setTooltipText(label);
            holder.iconIV.setOnClickListener(
                    v -> {
                        mSystemUIActivityManager.moveTaskToFront(taskInfo.getId(), 0);
                        mContext.sendBroadcast(
                                new Intent("com.boringdroid.systemui.CLOSE_RECENTS"));
                    });
            holder.iconIV.setOnLongClickListener(
                    v -> {
                        ClipData.Item item = new ClipData.Item(TAG_TASK_ICON);
                        ClipData dragData =
                                new ClipData(TAG_TASK_ICON, new String[] {"unknown"}, item);
                        DragShadowBuilder shadow = new DragDropShadowBuilder(v);
                        holder.iconIV.setOnDragListener(
                                new DragDropCloseListener(
                                        mDragCloseThreshold,
                                        mDragCloseThreshold,
                                        AM_WRAPPER::removeTask));
                        v.startDragAndDrop(dragData, shadow, null, View.DRAG_FLAG_GLOBAL);
                        return true;
                    });
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }

        public void setData(List<TaskInfo> tasks) {
            mTasks.clear();
            mTasks.addAll(tasks);
        }

        public void setTopTaskId(int id) {
            mTopTaskId = id;
        }

        public void reloadActivityManager(Context context) {
            mSystemUIActivityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            public final ImageView iconIV;
            public final ImageView highLightLineTV;

            public ViewHolder(@NonNull ViewGroup taskInfoLayout) {
                super(taskInfoLayout);
                iconIV = taskInfoLayout.findViewById(R.id.iv_task_info_icon);
                highLightLineTV = taskInfoLayout.findViewById(R.id.iv_highlight_line);
            }
        }
    }

    private static final class DragDropCloseListener implements OnDragListener {
        private final Consumer<Integer> mEndCallback;
        private final int mWidth;
        private final int mHeight;
        private float mStartX;
        private float mStartY;

        public DragDropCloseListener(int width, int height, Consumer<Integer> endCallback) {
            mWidth = width;
            mHeight = height;
            mEndCallback = endCallback;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    int[] locations = new int[2];
                    v.getLocationOnScreen(locations);
                    mStartX = locations[0];
                    mStartY = locations[1];
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    float x = event.getX();
                    float y = event.getY();
                    if (Math.abs(x - mStartX) < mWidth && Math.abs(y - mStartY) < mHeight) {
                        break;
                    }
                    v.setOnDragListener(null);
                    if (mEndCallback != null && v.getTag() instanceof Integer) {
                        mEndCallback.accept((Integer) v.getTag());
                    }
            }
            return true;
        }
    }

    private static final class DragDropShadowBuilder extends View.DragShadowBuilder {
        private static Drawable mShadow;

        public DragDropShadowBuilder(View v) {
            super(v);
            if (v instanceof ImageView) {
                Drawable drawable = ((ImageView) v).getDrawable();
                if (drawable != null) {
                    mShadow = drawable.mutate().getConstantState().newDrawable();
                    return;
                }
            }
            mShadow = new ColorDrawable(Color.LTGRAY);
        }

        @Override
        public void onProvideShadowMetrics(Point size, Point touch) {
            int width = getView().getWidth();
            int height = getView().getHeight();
            mShadow.setBounds(0, 0, width, height);
            size.set(width, height);
            touch.set(width / 2, height / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            mShadow.draw(canvas);
        }
    }
}
