/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * {@link ActivityControlHelper} for the in-launcher recents.
 */
public final class LauncherActivityControllerHelper implements ActivityControlHelper<BaseDraggingActivity> {

    @Override
    public void onTransitionCancelled(BaseDraggingActivity activity, boolean activityVisible) {

    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(context, dp, outRect);
        if (dp.isVerticalBarLayout() && SysUINavigationMode.getMode(context) != Mode.NO_BUTTON) {
            return 0;
        } else {
            return LayoutUtils.getShelfTrackingDistance(context, dp);
        }
    }

    @Override
    public void onSwipeUpToRecentsComplete(BaseDraggingActivity activity) {

    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
    }

    @NonNull
    @Override
    public HomeAnimationFactory prepareHomeUI(BaseDraggingActivity activity) {
        return null;
    }

    @Nullable
    @Override
    public BaseDraggingActivity getCreatedActivity() {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            return null;
        }
        return (BaseDraggingActivity) app.getModel().getCallback();
    }

    @Nullable
    @UiThread
    private BaseDraggingActivity getVisibleLauncher() {
        BaseDraggingActivity launcher = getCreatedActivity();
        return (launcher != null) && launcher.isStarted() && launcher.hasWindowFocus() ?
                launcher : null;
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        // TODO recheck it
        BaseDraggingActivity launcher = getVisibleLauncher();
        return launcher != null ? launcher.getOverviewPanel() : null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        BaseDraggingActivity launcher = getVisibleLauncher();
        return launcher != null;
    }

    @Override
    public boolean deferStartingActivity(Region activeNavBarRegion, MotionEvent ev) {
        return activeNavBarRegion.contains((int) ev.getX(), (int) ev.getY());
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        return homeBounds;
    }

    @Override
    public boolean shouldMinimizeSplitScreen() {
        return true;
    }

    @Override
    public boolean isInLiveTileMode() {
        BaseDraggingActivity launcher = getCreatedActivity();
        // TODO recheck it
        return launcher != null && launcher.isStarted();
    }

    @Override
    public void onLaunchTaskFailed(BaseDraggingActivity activity) {

    }

    @Override
    public void onLaunchTaskSuccess(BaseDraggingActivity activity) {

    }
}
