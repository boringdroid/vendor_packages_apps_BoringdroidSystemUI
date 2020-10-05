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

package com.android.quickstep;

import android.app.Activity;
import android.app.ActivityOptions;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.popup.SystemShortcut;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.Collections;
import java.util.List;

import static android.view.Display.DEFAULT_DISPLAY;

/**
 * Represents a system shortcut that can be shown for a recent task.
 */
public class TaskSystemShortcut<T extends SystemShortcut> extends SystemShortcut {

    private static final String TAG = "TaskSystemShortcut";

    protected T mSystemShortcut;

    public TaskSystemShortcut(T systemShortcut) {
        super(systemShortcut);
        mSystemShortcut = systemShortcut;
    }

    protected TaskSystemShortcut(int iconResId, int labelResId) {
        super(iconResId, labelResId);
    }

    @Override
    public View.OnClickListener getOnClickListener(
            BaseDraggingActivity activity, Task task) {
        return null;
    }

    public View.OnClickListener getOnClickListener(BaseDraggingActivity activity, TaskView view) {
        Task task = view.getTask();
        return getOnClickListenerForTask(activity, task);
    }

    protected View.OnClickListener getOnClickListenerForTask(
            BaseDraggingActivity activity, Task task) {
        return mSystemShortcut.getOnClickListener(activity, task);
    }

    public static class AppInfo extends TaskSystemShortcut<SystemShortcut.AppInfo> {
        public AppInfo() {
            super(new SystemShortcut.AppInfo());
        }
    }

    public static class CloseTask extends TaskSystemShortcut {
        public CloseTask() {
            super(R.drawable.ic_close_task, R.string.recent_task_option_split_screen);
        }

        @Override
        public View.OnClickListener getOnClickListener(BaseDraggingActivity activity,
                                                       TaskView taskView) {
            return v -> {
                dismissTaskMenuView(activity);
                if (taskView == null) {
                    return;
                }
                RecentsView recentsView = taskView.getRecentsView();
                if (recentsView != null) {
                    recentsView.removeView(taskView);
                }
            };
        }
    }

    public static abstract class MultiWindow extends TaskSystemShortcut {

        private Handler mHandler;

        public MultiWindow(int iconRes, int textRes) {
            super(iconRes, textRes);
            mHandler = new Handler(Looper.getMainLooper());
        }

        protected abstract boolean isAvailable(BaseDraggingActivity activity, int displayId);

        protected abstract ActivityOptions makeLaunchOptions(Activity activity);

        protected abstract boolean onActivityStarted(BaseDraggingActivity activity);

        @Override
        public View.OnClickListener getOnClickListener(
                BaseDraggingActivity activity, TaskView taskView) {
            final Task task = taskView.getTask();
            final int taskId = task.key.id;
            final int displayId = task.key.displayId;
            if (!task.isDockable) {
                return null;
            }
            if (!isAvailable(activity, displayId)) {
                return null;
            }

            final TaskThumbnailView thumbnailView = taskView.getThumbnail();
            return (v -> {
                dismissTaskMenuView(activity);

                ActivityOptions options = makeLaunchOptions(activity);
                if (options != null
                        && ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId,
                        options)) {
                    if (!onActivityStarted(activity)) {
                        return;
                    }
                    final Runnable animStartedListener = () -> {
                        // Hide the task view and wait for the window to be resized
                        // TODO: Consider animating in launcher and do an in-place start activity
                        //       afterwards
                        taskView.setAlpha(0f);
                    };

                    final int[] position = new int[2];
                    thumbnailView.getLocationOnScreen(position);
                    final int width = (int) (thumbnailView.getWidth() * taskView.getScaleX());
                    final int height = (int) (thumbnailView.getHeight() * taskView.getScaleY());
                    final Rect taskBounds = new Rect(position[0], position[1],
                            position[0] + width, position[1] + height);

                    // Take the thumbnail of the task without a scrim and apply it back after
                    float alpha = thumbnailView.getDimAlpha();
                    thumbnailView.setDimAlpha(0);
                    Bitmap thumbnail = RecentsTransition.drawViewIntoHardwareBitmap(
                            taskBounds.width(), taskBounds.height(), thumbnailView, 1f,
                            Color.BLACK);
                    thumbnailView.setDimAlpha(alpha);

                    AppTransitionAnimationSpecsFuture future =
                            new AppTransitionAnimationSpecsFuture(mHandler) {
                                @Override
                                public List<AppTransitionAnimationSpecCompat> composeSpecs() {
                                    return Collections.singletonList(new AppTransitionAnimationSpecCompat(
                                            taskId, thumbnail, taskBounds));
                                }
                            };
                    WindowManagerWrapper.getInstance().overridePendingAppTransitionMultiThumbFuture(
                            future, animStartedListener, mHandler, true /* scaleUp */, displayId);
                }
            });
        }
    }

    public static class SplitScreen extends MultiWindow {
        public SplitScreen() {
            super(R.drawable.ic_split_screen, R.string.recent_task_option_split_screen);
        }

        @Override
        protected boolean isAvailable(BaseDraggingActivity activity, int displayId) {
            // Don't show menu-item if already in multi-window and the task is from
            // the secondary display.
            // TODO(b/118266305): Temporarily disable splitscreen for secondary display while new
            // implementation is enabled
            return !activity.getDeviceProfile().isMultiWindowMode
                    && (displayId == -1 || displayId == DEFAULT_DISPLAY);
        }

        @Override
        protected ActivityOptions makeLaunchOptions(Activity activity) {
            final ActivityCompat act = new ActivityCompat(activity);
            final int navBarPosition =
                    WindowManagerWrapper.getInstance().getNavBarPosition(act.getDisplayId());
            if (navBarPosition == WindowManagerWrapper.NAV_BAR_POS_INVALID) {
                return null;
            }
            boolean dockTopOrLeft = navBarPosition != WindowManagerWrapper.NAV_BAR_POS_LEFT;
            return ActivityOptionsCompat.makeSplitScreenOptions(dockTopOrLeft);
        }

        @Override
        protected boolean onActivityStarted(BaseDraggingActivity activity) {
            ISystemUiProxy sysUiProxy = RecentsModel.INSTANCE.get(activity).getSystemUiProxy();
            try {
                sysUiProxy.onSplitScreenInvoked();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to notify SysUI of split screen: ", e);
                return false;
            }
            return true;
        }
    }
}
