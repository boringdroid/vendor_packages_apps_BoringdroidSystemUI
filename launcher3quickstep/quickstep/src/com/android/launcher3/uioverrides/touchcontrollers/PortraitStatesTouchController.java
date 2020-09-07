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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.AbstractFloatingView.TYPE_ACCESSIBLE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.LayoutUtils;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    private static final String TAG = "PortraitStatesTouchCtrl";

    /**
     * The progress at which all apps content will be fully visible when swiping up from overview.
     */
    protected static final float ALL_APPS_CONTENT_FADE_THRESHOLD = 0.08f;

    private final PortraitOverviewStateTouchHelper mOverviewPortraitStateTouchHelper;

    private final InterpolatorWrapper mAllAppsInterpolatorWrapper = new InterpolatorWrapper();

    // If true, we will finish the current animation instantly on second touch.
    private boolean mFinishFastOnSecondTouch;

    public PortraitStatesTouchController(Launcher l, boolean allowDragToOverview) {
        super(l, SingleAxisSwipeDetector.VERTICAL);
        mOverviewPortraitStateTouchHelper = new PortraitOverviewStateTouchHelper(l);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            if (mFinishFastOnSecondTouch) {
                // TODO: Animate to finish instead.
                mCurrentAnimation.skipToEnd();
            }

            // Otherwise, make sure everything is settled and don't intercept so they can scroll
            // recents, dismiss a task, etc.
            if (mAtomicAnim != null) {
                mAtomicAnim.end();
            }
            return false;
        }
        if (mLauncher.isInState(OVERVIEW)) {
            if (!mOverviewPortraitStateTouchHelper.canInterceptTouch(ev)) {
                return false;
            }
        }  // If we are swiping to all apps instead of overview, allow it from anywhere.

        if (AbstractFloatingView.getTopOpenViewWithType(mLauncher, TYPE_ACCESSIBLE) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        return fromState;
    }

    @Override
    protected int getLogContainerTypeForNormalState(MotionEvent ev) {
        return ContainerType.WORKSPACE;
    }

    private AnimatorSetBuilder getNormalToOverviewAnimation() {
        mAllAppsInterpolatorWrapper.baseInterpolator = LINEAR;

        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        builder.setInterpolator(ANIM_VERTICAL_PROGRESS, mAllAppsInterpolatorWrapper);
        return builder;
    }

    @Override
    protected AnimatorSetBuilder getAnimatorSetBuilderForStates(LauncherState fromState,
            LauncherState toState) {
        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        if (fromState == NORMAL && toState == OVERVIEW) {
            builder = getNormalToOverviewAnimation();
        }
        return builder;
    }

    @Override
    protected float initCurrentAnimation(@AnimationComponents int animComponents) {
        long maxAccuracy = (long) (2);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher);
        float endVerticalShift = mToState.getVerticalProgress(mLauncher);

        float totalShift = endVerticalShift - startVerticalShift;

        final AnimatorSetBuilder builder = totalShift == 0 ? new AnimatorSetBuilder()
                : getAnimatorSetBuilderForStates(mFromState, mToState);
        updateAnimatorBuilderOnReinit(builder);

        cancelPendingAnim();

        if (mFromState == OVERVIEW && mToState == NORMAL
                && mOverviewPortraitStateTouchHelper.shouldSwipeDownReturnToApp()) {
            // Reset the state manager, when changing the interaction mode
            mLauncher.getStateManager().goToState(OVERVIEW, false /* animate */);
            mPendingAnimation = mOverviewPortraitStateTouchHelper
                    .createSwipeDownToTaskAppAnimation(maxAccuracy);
            mPendingAnimation.anim.setInterpolator(Interpolators.LINEAR);

            Runnable onCancelRunnable = () -> {
                cancelPendingAnim();
                clearState();
            };
            mCurrentAnimation = AnimatorPlaybackController.wrap(mPendingAnimation.anim, maxAccuracy,
                    onCancelRunnable);
            mLauncher.getStateManager().setCurrentUserControlledAnimation(mCurrentAnimation);
            totalShift = LayoutUtils.getShelfTrackingDistance(mLauncher,
                    mLauncher.getDeviceProfile());
        } else {
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, builder, maxAccuracy, this::clearState,
                            animComponents);
        }

        if (totalShift == 0) {
            totalShift = Math.signum(mFromState.ordinal - mToState.ordinal)
                    * OverviewState.getDefaultSwipeHeight(mLauncher);
        }
        return 1 / totalShift;
    }

    /**
     * Give subclasses the chance to update the animation when we re-initialize towards a new state.
     */
    protected void updateAnimatorBuilderOnReinit(AnimatorSetBuilder builder) {
    }

    private void cancelPendingAnim() {
        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false, Touch.SWIPE);
            mPendingAnimation = null;
        }
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState,
                velocity, isFling);
        handleFirstSwipeToOverview(animator, expectedDuration, targetState, velocity, isFling);
    }

    private void handleFirstSwipeToOverview(final ValueAnimator animator,
            final long expectedDuration, final LauncherState targetState, final float velocity,
            final boolean isFling) {
        if (mFromState == NORMAL && mToState == OVERVIEW && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;
            if (isFling && expectedDuration != 0) {
                // Update all apps interpolator to add a bit of overshoot starting from currFraction
                final float currFraction = mCurrentAnimation.getProgressFraction();
                mAllAppsInterpolatorWrapper.baseInterpolator = Interpolators.clampToProgress(
                        Interpolators.overshootInterpolatorForVelocity(velocity), currFraction, 1);
                animator.setDuration(Math.min(expectedDuration, ATOMIC_DURATION))
                        .setInterpolator(LINEAR);
            }
        } else {
            mFinishFastOnSecondTouch = false;
        }
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mStartState == NORMAL && targetState == OVERVIEW) {
            RecentsModel.INSTANCE.get(mLauncher).onOverviewShown(true, TAG);
        }
    }

    private static class InterpolatorWrapper implements Interpolator {

        public TimeInterpolator baseInterpolator = LINEAR;

        @Override
        public float getInterpolation(float v) {
            return baseInterpolator.getInterpolation(v);
        }
    }
}
