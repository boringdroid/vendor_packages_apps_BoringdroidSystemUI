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

package com.android.quickstep.views;

import static android.provider.Settings.ACTION_APP_USAGE_SETTINGS;

import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.quickstep.UserHandleHelper;
import com.android.systemui.shared.recents.model.Task;

@TargetApi(Build.VERSION_CODES.Q)
public final class DigitalWellBeingToast {
    static final Intent OPEN_APP_USAGE_SETTINGS_TEMPLATE = new Intent(ACTION_APP_USAGE_SETTINGS);

    private static final String TAG = DigitalWellBeingToast.class.getSimpleName();

    private final BaseDraggingActivity mActivity;
    private final TaskView mTaskView;

    private Task mTask;
    private boolean mHasLimit;

    public DigitalWellBeingToast(BaseDraggingActivity activity, TaskView taskView) {
        mActivity = activity;
        mTaskView = taskView;
    }

    private void setTaskFooter(View view) {
        View oldFooter = mTaskView.setFooter(TaskView.INDEX_DIGITAL_WELLBEING_TOAST, view);
        if (oldFooter != null) {
            oldFooter.setOnClickListener(null);
            mActivity.getViewCache().recycleView(R.layout.digital_wellbeing_toast, oldFooter);
        }
    }

    private void setNoLimit() {
        mHasLimit = false;
        mTaskView.setContentDescription(mTask.titleDescription);
        setTaskFooter(null);
    }

    public boolean hasLimit() {
        return mHasLimit;
    }

    public void initialize(Task task) {
        mTask = task;

        if (task.key.userId != UserHandleHelper.myUserId()) {
            setNoLimit();
            return;
        }

        THREAD_POOL_EXECUTOR.execute(() -> mTaskView.post(() -> setNoLimit()));
    }

    public void openAppUsageSettings(View view) {
        final Intent intent = new Intent(OPEN_APP_USAGE_SETTINGS_TEMPLATE)
                .putExtra(Intent.EXTRA_PACKAGE_NAME,
                        mTask.getTopComponent().getPackageName()).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            final BaseActivity activity = BaseActivity.fromContext(view.getContext());
            final ActivityOptions options = ActivityOptions.makeScaleUpAnimation(
                    view, 0, 0,
                    view.getWidth(), view.getHeight());
            activity.startActivity(intent, options.toBundle());
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to open app usage settings for task "
                    + mTask.getTopComponent().getPackageName(), e);
        }
    }

}
