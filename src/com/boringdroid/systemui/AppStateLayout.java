package com.boringdroid.systemui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.List;

import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_UNDEFINED;

public class AppStateLayout extends RecyclerView {
    private static final String TAG = "AppStateLayout";

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
        mLaunchApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        LinearLayoutManager manager =
                new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        setLayoutManager(manager);
        setHasFixedSize(true);
        mAdapter = new TaskAdapter(context);
        setAdapter(mAdapter);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        ActivityManagerWrapper.getInstance().unregisterTaskStackListener(mListener);
        super.onDetachedFromWindow();
    }

    private void removeTask(int taskId) {
        mTasks.removeIf(taskInfo -> taskInfo.getId() == taskId);
        mAdapter.setData(mTasks);
        mAdapter.notifyDataSetChanged();
    }

    private void topTask(ActivityManager.RunningTaskInfo runningTaskInfo) {
        String packageName = runningTaskInfo.baseActivity == null ?
                null : runningTaskInfo.baseActivity.getPackageName();
        if (isLauncher(getContext(), packageName)) {
            Log.d(TAG, "Ignore launcher " + packageName);
            mAdapter.setTopTaskId(-1);
            mAdapter.notifyDataSetChanged();
            return;
        }
        if (packageName != null && packageName.startsWith("com.android.systemui")) {
            Log.d(TAG, "Ignore systemui " + packageName);
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
            List<LauncherActivityInfo> infoList = mLaunchApps.getActivityList(packageName, userHandle);
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
        mAdapter.notifyDataSetChanged();
    }

    private boolean isLauncher(Context context, String packageName) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        return res != null && res.activityInfo != null
                && packageName.equals(res.activityInfo.packageName);
    }

    private class AppStateListener extends TaskStackChangeListener {
        @Override
        public void onTaskStackChanged() {
            super.onTaskStackChanged();
            ActivityManager.RunningTaskInfo info =
                    ActivityManagerWrapper
                            .getInstance()
                            .getRunningTask(ACTIVITY_TYPE_UNDEFINED);
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
        private final List<TaskInfo> mTasks = new ArrayList<>();
        private final Context mContext;
        private final ActivityManager mActivityManager;
        private int mTopTaskId = -1;

        public TaskAdapter(@NonNull Context context) {
            mContext = context;
            mActivityManager =
                    (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        }

        @NonNull
        @Override
        public TaskAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewGroup taskInfoLayout =
                    (ViewGroup) LayoutInflater
                            .from(mContext)
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
            holder.iconIV.setOnClickListener(
                    v -> mActivityManager.moveTaskToFront(taskInfo.getId(), 0)
            );
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
}
