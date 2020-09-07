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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherStateManager.ANIM_ALL;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.anim.SpringObjectAnimator;
import com.android.launcher3.graphics.OverviewScrim;
import com.android.quickstep.views.RecentsView;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates an animation where all the workspace items are moved into their final location,
 * staggered row by row from the bottom up.
 * This is used in conjunction with the swipe up to home animation.
 */
public class StaggeredWorkspaceAnim {

    private static final int ALPHA_DURATION_MS = 250;

    private final float mVelocity;

    private final List<Animator> mAnimators = new ArrayList<>();

    public StaggeredWorkspaceAnim(Launcher launcher, float velocity, boolean animateOverviewScrim) {
        prepareToAnimate(launcher);

        mVelocity = velocity;

        // Scale the translationY based on the initial velocity to better sync the workspace items
        // with the floating view.

        Workspace workspace = launcher.getWorkspace();
        CellLayout cellLayout = (CellLayout) workspace.getChildAt(workspace.getCurrentPage());

        boolean workspaceClipChildren = workspace.getClipChildren();
        boolean workspaceClipToPadding = workspace.getClipToPadding();
        boolean cellLayoutClipChildren = cellLayout.getClipChildren();
        boolean cellLayoutClipToPadding = cellLayout.getClipToPadding();

        workspace.setClipChildren(false);
        workspace.setClipToPadding(false);
        cellLayout.setClipChildren(false);
        cellLayout.setClipToPadding(false);

        if (animateOverviewScrim) {
            addScrimAnimationForState(launcher, BACKGROUND_APP, 0);
            addScrimAnimationForState(launcher, NORMAL, ALPHA_DURATION_MS);
        }

        AnimatorListener resetClipListener = new AnimatorListenerAdapter() {
            int numAnimations = mAnimators.size();

            @Override
            public void onAnimationEnd(Animator animation) {
                numAnimations--;
                if (numAnimations > 0) {
                    return;
                }

                workspace.setClipChildren(workspaceClipChildren);
                workspace.setClipToPadding(workspaceClipToPadding);
                cellLayout.setClipChildren(cellLayoutClipChildren);
                cellLayout.setClipToPadding(cellLayoutClipToPadding);
            }
        };

        for (Animator a : mAnimators) {
            a.addListener(resetClipListener);
        }
    }

    /**
     * Setup workspace with 0 duration to prepare for our staggered animation.
     */
    private void prepareToAnimate(Launcher launcher) {
        LauncherStateManager stateManager = launcher.getStateManager();
        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        // setRecentsAttachedToAppWindow() will animate recents out.
        builder.addFlag(AnimatorSetBuilder.FLAG_DONT_ANIMATE_OVERVIEW);
        stateManager.createAtomicAnimation(BACKGROUND_APP, NORMAL, builder, ANIM_ALL, 0);
        builder.build().start();

        // Stop scrolling so that it doesn't interfere with the translation offscreen.
        launcher.<RecentsView>getOverviewPanel().getScroller().forceFinished(true);
    }

    /**
     * Starts the animation.
     */
    public void start() {
        for (Animator a : mAnimators) {
            if (a instanceof SpringObjectAnimator) {
                ((SpringObjectAnimator) a).startSpring(1f, mVelocity, null);
            } else {
                a.start();
            }
        }
    }

    private void addScrimAnimationForState(Launcher launcher, LauncherState state, long duration) {
        AnimatorSetBuilder scrimAnimBuilder = new AnimatorSetBuilder();
        AnimationConfig scrimAnimConfig = new AnimationConfig();
        scrimAnimConfig.duration = duration;
        PropertySetter scrimPropertySetter = scrimAnimConfig.getPropertySetter(scrimAnimBuilder);
        launcher.getWorkspace().getStateTransitionAnimation().setScrim(scrimPropertySetter, state);
        mAnimators.add(scrimAnimBuilder.build());
        Animator fadeOverviewScrim = ObjectAnimator.ofFloat(
                launcher.getDragLayer().getOverviewScrim(), OverviewScrim.SCRIM_PROGRESS,
                state.getOverviewScrimAlpha(launcher));
        fadeOverviewScrim.setDuration(duration);
        mAnimators.add(fadeOverviewScrim);
    }
}
