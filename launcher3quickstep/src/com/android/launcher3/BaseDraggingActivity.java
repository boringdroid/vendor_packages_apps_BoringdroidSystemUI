/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.ActionMode;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.uioverrides.DisplayRotationListener;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Themes;

/**
 * Extension of BaseActivity allowing support for drag-n-drop
 */
public abstract class BaseDraggingActivity extends BaseActivity
        implements WallpaperColorInfo.OnChangeListener {

    private static final String TAG = "BaseDraggingActivity";

    // When starting an action mode, setting this tag will cause the action mode to be cancelled
    // automatically when user interacts with the launcher.
    public static final Object AUTO_CANCEL_ACTION_MODE = new Object();

    private ActionMode mCurrentActionMode;
    protected boolean mIsSafeModeEnabled;

    private OnStartCallback mOnStartCallback;

    private int mThemeRes = R.style.AppTheme;

    private DisplayRotationListener mRotationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIsSafeModeEnabled = getPackageManager().isSafeMode();
        mRotationListener = new DisplayRotationListener(this, this::onDeviceRotationChanged);

        // Update theme
        WallpaperColorInfo wallpaperColorInfo = WallpaperColorInfo.getInstance(this);
        wallpaperColorInfo.addOnChangeListener(this);
        int themeRes = Themes.getActivityThemeRes(this);
        if (themeRes != mThemeRes) {
            mThemeRes = themeRes;
            setTheme(themeRes);
        }
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        updateTheme();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTheme();
    }

    private void updateTheme() {
        if (mThemeRes != Themes.getActivityThemeRes(this)) {
            recreate();
        }
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        mCurrentActionMode = mode;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        mCurrentActionMode = null;
    }

    @Override
    public boolean finishAutoCancelActionMode() {
        if (mCurrentActionMode != null && AUTO_CANCEL_ACTION_MODE == mCurrentActionMode.getTag()) {
            mCurrentActionMode.finish();
            return true;
        }
        return false;
    }

    public abstract <T extends View> T getOverviewPanel();

    public Rect getViewBounds(View v) {
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        return new Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1] + v.getHeight());
    }

    public final Bundle getActivityLaunchOptionsAsBundle(View v) {
        ActivityOptions activityOptions = getActivityLaunchOptions(v);
        return activityOptions == null ? null : activityOptions.toBundle();
    }

    public abstract ActivityOptions getActivityLaunchOptions(View v);

    public boolean startActivitySafely(View v, Intent intent, @Nullable ItemInfo item,
            @Nullable String sourceContainer) {
        if (mIsSafeModeEnabled && !PackageManagerHelper.isSystemApp(this, intent)) {
            Toast.makeText(this, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }

        Bundle optsBundle = (v != null) ? getActivityLaunchOptionsAsBundle(v) : null;
        UserHandle user = item == null ? null : item.user;

        // Prepare intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v != null) {
            intent.setSourceBounds(getViewBounds(v));
        }
        try {
            if (user == null || user.equals(Process.myUserHandle())) {
                // Could be launching some bookkeeping activity
                startActivity(intent, optsBundle);
            } else {
                LauncherAppsCompat.getInstance(this).startActivityForProfile(
                        intent.getComponent(), user, intent.getSourceBounds(), optsBundle);
            }
            return true;
        } catch (NullPointerException|ActivityNotFoundException|SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + item + " intent=" + intent, e);
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mOnStartCallback != null) {
            mOnStartCallback.onActivityStart(this);
            mOnStartCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WallpaperColorInfo.getInstance(this).removeOnChangeListener(this);
        mRotationListener.disable();
    }

    protected void onDeviceProfileInitiated() {
        if (mDeviceProfile.isVerticalBarLayout()) {
            mRotationListener.enable();
            mDeviceProfile.updateIsSeascape(this);
        } else {
            mRotationListener.disable();
        }
    }

    private void onDeviceRotationChanged() {
        if (mDeviceProfile.updateIsSeascape(this)) {
            reapplyUi();
        }
    }

    protected abstract void reapplyUi();

    /**
     * Callback for listening for onStart
     */
    public interface OnStartCallback<T extends BaseDraggingActivity> {

        void onActivityStart(T activity);
    }
}
