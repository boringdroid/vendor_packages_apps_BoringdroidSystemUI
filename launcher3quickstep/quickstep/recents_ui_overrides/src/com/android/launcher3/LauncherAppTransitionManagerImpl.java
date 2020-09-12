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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * A {@link QuickstepAppTransitionManagerImpl} that also implements recents transitions from
 * {@link RecentsView}.
 */
public final class LauncherAppTransitionManagerImpl extends QuickstepAppTransitionManagerImpl {

    public static final int INDEX_SHELF_ANIM = 0;
    public static final int INDEX_RECENTS_FADE_ANIM = 1;
    public static final int INDEX_RECENTS_TRANSLATE_X_ANIM = 2;

    public LauncherAppTransitionManagerImpl(Context context) {
        super(context);
    }

    @Override
    protected boolean isLaunchingFromRecents(@NonNull View v,
            @Nullable RemoteAnimationTargetCompat[] targets) {
        // TODO recheck it
        return true;
    }

    @Override
    protected void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] targets, boolean launcherClosing) {
    }

    @Override
    protected Runnable composeViewContentAnimator(@NonNull AnimatorSet anim, float[] alphas,
            float[] trans) {
        return () -> { };
    }

    @Override
    public Animator createStateElementAnimation(int index, float... values) {
        return super.createStateElementAnimation(index, values);
    }
}
