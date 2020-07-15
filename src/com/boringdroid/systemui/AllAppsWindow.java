package com.boringdroid.systemui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.List;

public class AllAppsWindow implements View.OnClickListener {
    private static final String TAG = "AppAppsWindow";
    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mWindowContentView;
    private AllAppsLayout mAllAppsLayout;
    private boolean mShown = false;

    private final AppLoaderTask mAppLoaderTask;
    private final H mHandler = new H(this);

    public AllAppsWindow(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mAppLoaderTask = new AppLoaderTask(mContext, mHandler);
    }

    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    @Override
    public void onClick(View v) {
        if (mShown) {
            dismiss();
            return;
        }
        WindowManager.LayoutParams layoutParams = generateLayoutParams(mContext, mWindowManager);
        mWindowContentView = LayoutInflater.from(mContext).inflate(R.layout.layout_all_apps, null);
        mAllAppsLayout = mWindowContentView.findViewById(R.id.all_apps_layout);
        mAllAppsLayout.setHandler(mHandler);
        int elevation = mContext.getResources().getInteger(R.integer.all_apps_elevation);
        mWindowContentView.setElevation(elevation);
        mWindowContentView.setOnTouchListener(
                (windowView, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                        dismiss();
                    }
                    return false;
                }
        );
        mWindowManager.addView(mWindowContentView, layoutParams);
        mAppLoaderTask.start();
        mShown = true;
    }

    private WindowManager.LayoutParams generateLayoutParams(Context context,
                                                            WindowManager windowManager) {
        int windowWidth =
                context.getResources().getDimensionPixelSize(R.dimen.all_apps_window_width);
        int windowHeight =
                context.getResources().getDimensionPixelSize(R.dimen.all_apps_window_height);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.RGB_565
        );
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 0;
        layoutParams.y = displayMetrics.heightPixels - windowHeight;
        return layoutParams;
    }

    private void dismiss() {
        try {
            mWindowManager.removeViewImmediate(mWindowContentView);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Catch exception when remove all apps window", e);
        }
        mWindowContentView = null;
        mShown = false;
    }

    private void notifyLoadSucceed() {
        mAllAppsLayout.setData(mAppLoaderTask.getAllApps());
    }

    private static final class H extends Handler {
        private final WeakReference<AllAppsWindow> mAllAppsWindow;

        public H(AllAppsWindow allAppsWindow) {
            mAllAppsWindow = new WeakReference<>(allAppsWindow);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerConstant.H_LOAD_SUCCEED:
                    runMethodSafely(AllAppsWindow::notifyLoadSucceed);
                    break;
                case HandlerConstant.H_DISMISS_ALL_APPS_WINDOW:
                    runMethodSafely(AllAppsWindow::dismiss);
                    break;
                default:
                    break;
            }
        }

        private void runMethodSafely(RunAllAppsWindowMethod method) {
            if (mAllAppsWindow.get() != null) {
                method.run(mAllAppsWindow.get());
            }
        }

        private interface RunAllAppsWindowMethod {
            void run(AllAppsWindow allAppsWindow);
        }
    }
}
