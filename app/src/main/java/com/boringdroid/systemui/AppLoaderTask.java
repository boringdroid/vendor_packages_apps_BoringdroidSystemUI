package com.boringdroid.systemui;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.UserManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AppLoaderTask implements Runnable {
    private static final HandlerThread WORK_THREAD = new HandlerThread("app-loader-thread");

    static {
        WORK_THREAD.start();
    }

    private final Handler sHandler = new Handler(WORK_THREAD.getLooper());

    private final WeakReference<Context> mContext;
    private final WeakReference<Handler> mTarget;

    private final List<AppData> mAllApps = new ArrayList<>();
    private boolean mStopped = false;

    public AppLoaderTask(Context context, Handler target) {
        mContext = new WeakReference<>(context);
        mTarget = new WeakReference<>(target);
    }

    @Override
    public void run() {
        if (mStopped) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<UserHandle> userHandles = userManager.getUserProfiles();
        List<LauncherActivityInfo> activityInfoList = new ArrayList<>();
        LauncherApps launcherApps =
                (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        for (UserHandle userHandle : userHandles) {
            activityInfoList.addAll(launcherApps.getActivityList(null, userHandle));
        }
        mAllApps.clear();
        for (LauncherActivityInfo info : activityInfoList) {
            AppData appInfo = new AppData();
            appInfo.setName((String) info.getLabel());
            appInfo.setComponentName(info.getComponentName());
            appInfo.setPackageName(info.getApplicationInfo().packageName);
            appInfo.setIcon(info.getIcon(0));
            mAllApps.add(appInfo);
        }
        mAllApps.sort(
                (appDataOne, appDataTwo) -> appDataOne.getName().compareTo(appDataTwo.getName()));
        Handler target = getTarget();
        if (target != null) {
            target.sendEmptyMessage(HandlerConstant.H_LOAD_SUCCEED);
        }
    }

    public synchronized void start() {
        mStopped = false;
        sHandler.post(this);
    }

    public synchronized void stop() {
        mStopped = true;
        notify();
    }

    public List<AppData> getAllApps() {
        return new ArrayList<>(mAllApps);
    }

    private Handler getTarget() {
        return mTarget.get();
    }

    private Context getContext() {
        return mContext.get();
    }
}