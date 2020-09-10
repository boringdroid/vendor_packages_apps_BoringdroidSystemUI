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

import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_RECENTS_FADE_ANIM;
import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.WindowTransformSwipeHandler.RECENTS_ATTACH_DURATION;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState;
import com.android.quickstep.views.LauncherRecentsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for the in-launcher recents.
 */
public final class LauncherActivityControllerHelper implements ActivityControlHelper<Launcher> {

    private Runnable mAdjustInterpolatorsRunnable;

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
    public void onTransitionCancelled(Launcher activity, boolean activityVisible) {
        LauncherState startState = activity.getStateManager().getRestState();
        activity.getStateManager().goToState(startState, activityVisible);
    }

    @Override
    public void onSwipeUpToRecentsComplete(Launcher activity) {
        // Re apply state in case we did something funky during the transition.
        activity.getStateManager().reapplyState();
    }

    @Override
    public void onSwipeUpToHomeComplete(Launcher activity) {
        // Ensure recents is at the correct position for NORMAL state. For example, when we detach
        // recents, we assume the first task is invisible, making translation off by one task.
        activity.getStateManager().reapplyState();
    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
    }

    @NonNull
    @Override
    public HomeAnimationFactory prepareHomeUI(Launcher activity) {
        final DeviceProfile dp = activity.getDeviceProfile();

        return new HomeAnimationFactory() {
            @Nullable
            @Override
            public View getFloatingView() {
                return null;
            }

            @NonNull
            @Override
            public RectF getWindowTargetRect() {
                return HomeAnimationFactory.getDefaultWindowTargetRect(dp);
            }

            @NonNull
            @Override
            public AnimatorPlaybackController createActivityAnimationToHome() {
                // Return an empty APC here since we have an non-user controlled animation to home.
                long accuracy = 2 * Math.max(dp.widthPx, dp.heightPx);
                return activity.getStateManager().createAnimationToNewWorkspace(NORMAL, accuracy,
                        0 /* animComponents */);
            }

            @Override
            public void playAtomicAnimation(float velocity) {
            }
        };
    }

    @Override
    public AnimationFactory prepareRecentsUI(Launcher activity, boolean activityVisible,
            boolean animateActivity, Consumer<AnimatorPlaybackController> callback) {
        final LauncherState startState = activity.getStateManager().getState();

        LauncherState resetState = startState;
        if (startState.disableRestore) {
            resetState = activity.getStateManager().getRestState();
        }
        activity.getStateManager().setRestState(resetState);

        final LauncherState fromState = animateActivity ? BACKGROUND_APP : OVERVIEW;
        activity.getStateManager().goToState(fromState, false);

        return new AnimationFactory() {
            private boolean mIsAttachedToWindow;

            @Override
            public void createActivityController(long transitionLength) {
                createActivityControllerInternal(activity, fromState, transitionLength, callback);
                // Creating the activity controller animation sometimes reapplies the launcher state
                // (because we set the animation as the current state animation), so we reapply the
                // attached state here as well to ensure recents is shown/hidden appropriately.
                if (SysUINavigationMode.getMode(activity) == Mode.NO_BUTTON) {
                    setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
                }
            }

            @Override
            public void adjustActivityControllerInterpolators() {
                if (mAdjustInterpolatorsRunnable != null) {
                    mAdjustInterpolatorsRunnable.run();
                }
            }

            @Override
            public void onTransitionCancelled() {
                activity.getStateManager().goToState(startState, false /* animate */);
            }

            @Override
            public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator,
                    long duration) {
            }

            @Override
            public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
                if (mIsAttachedToWindow == attached && animate) {
                    return;
                }
                mIsAttachedToWindow = attached;
                LauncherRecentsView recentsView = activity.getOverviewPanel();
                Animator fadeAnim = activity.getStateManager()
                        .createStateElementAnimation(
                        INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);

                int runningTaskIndex = recentsView.getRunningTaskIndex();
                if (runningTaskIndex == recentsView.getTaskViewStartIndex()) {
                    // If we are on the first task (we haven't quick switched), translate recents in
                    // from the side. Calculate the start translation based on current scale/scroll.
                    float scrollOffsetX = recentsView.getScrollOffset();
                    float offscreenX = recentsView.getOffscreenTranslationX();

                    float fromTranslationX = attached ? offscreenX - scrollOffsetX : 0;
                    float toTranslationX = attached ? 0 : offscreenX - scrollOffsetX;
                    activity.getStateManager()
                            .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);

                    if (!recentsView.isShown() && animate) {
                        recentsView.setTranslationX(fromTranslationX);
                    } else {
                        fromTranslationX = recentsView.getTranslationX();
                    }

                    if (!animate) {
                        recentsView.setTranslationX(toTranslationX);
                    } else {
                        activity.getStateManager().createStateElementAnimation(
                                INDEX_RECENTS_TRANSLATE_X_ANIM,
                                fromTranslationX, toTranslationX).start();
                    }

                    fadeAnim.setInterpolator(attached ? INSTANT : ACCEL_2);
                } else {
                    fadeAnim.setInterpolator(ACCEL_DEACCEL);
                }
                fadeAnim.setDuration(animate ? RECENTS_ATTACH_DURATION : 0).start();
            }
        };
    }

    private void createActivityControllerInternal(Launcher activity, LauncherState fromState,
            long transitionLength, Consumer<AnimatorPlaybackController> callback) {
        LauncherState endState = OVERVIEW;
        if (fromState == endState) {
            return;
        }

        AnimatorSet anim = new AnimatorSet();
        playScaleDownAnim(activity);

        anim.setDuration(transitionLength * 2);
        anim.setInterpolator(LINEAR);
        AnimatorPlaybackController controller =
                AnimatorPlaybackController.wrap(anim, transitionLength * 2);
        activity.getStateManager().setCurrentUserControlledAnimation(controller);

        // Since we are changing the start position of the UI, reapply the state, at the end
        controller.setEndAction(() -> {
            activity.getStateManager().goToState(
                    controller.getInterpolatedProgress() > 0.5 ? endState : fromState, false);
        });
        callback.accept(controller);
    }

    /**
     * Scale down recents from the center task being full screen to being in overview.
     */
    private void playScaleDownAnim(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        if (recentsView.getCurrentPageTaskView() == null) {
            return;
        }

        mAdjustInterpolatorsRunnable = () -> {
            // Adjust the translateY interpolator to account for the running task's top inset.
            // When progress <= 1, this is handled by each task view as they set their fullscreen
            // progress. However, once we go to progress > 1, fullscreen progress stays at 0, so
            // recents as a whole needs to translate further to keep up with the app window.
            TaskView runningTaskView = recentsView.getRunningTaskView();
            if (runningTaskView == null) {
                runningTaskView = recentsView.getCurrentPageTaskView();
                if (runningTaskView == null) {
                    // There are no task views in LockTask mode when Overview is enabled.
                    return;
                }
            }
        };
    }

    @Nullable
    @Override
    public Launcher getCreatedActivity() {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            return null;
        }
        return (Launcher) app.getModel().getCallback();
    }

    @Nullable
    @UiThread
    private Launcher getVisibleLauncher() {
        Launcher launcher = getCreatedActivity();
        return (launcher != null) && launcher.isStarted() && launcher.hasWindowFocus() ?
                launcher : null;
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        Launcher launcher = getVisibleLauncher();
        return launcher != null && launcher.getStateManager().getState().overviewUi
                ? launcher.getOverviewPanel() : null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        Launcher launcher = getVisibleLauncher();
        if (launcher == null) {
            return false;
        }

        launcher.getStateManager().goToState(OVERVIEW,
                launcher.getStateManager().shouldAnimateStateChange(), onCompleteCallback);
        return true;
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
    public int getContainerType() {
        final Launcher launcher = getVisibleLauncher();
        return launcher != null ? launcher.getStateManager().getState().containerType
                : LauncherLogProto.ContainerType.APP;
    }

    @Override
    public boolean isInLiveTileMode() {
        Launcher launcher = getCreatedActivity();
        return launcher != null && launcher.getStateManager().getState() == OVERVIEW &&
                launcher.isStarted();
    }

    @Override
    public void onLaunchTaskFailed(Launcher launcher) {
        launcher.getStateManager().goToState(OVERVIEW);
    }

    @Override
    public void onLaunchTaskSuccess(Launcher launcher) {
        launcher.getStateManager().moveToRestState();
    }
}
