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

import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_SHELF_ANIM;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;

import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;

/**
 * Animates the shelf between states HIDE, PEEK, and OVERVIEW.
 */

public class ShelfPeekAnim {

    public static final Interpolator INTERPOLATOR = OVERSHOOT_1_2;
    public static final long DURATION = 240;

    private final Launcher mLauncher;

    private ShelfAnimState mShelfState;

    public ShelfPeekAnim(Launcher launcher) {
        mLauncher = launcher;
    }

    /**
     * Animates to the given state, canceling the previous animation if it was still running.
     */
    public void setShelfState(ShelfAnimState shelfState) {
        if (mShelfState == shelfState) {
            return;
        }
        mLauncher.getStateManager().cancelStateElementAnimation(INDEX_SHELF_ANIM);
        mShelfState = shelfState;
    }

    /** The various shelf states we can animate to. */
    public enum ShelfAnimState {
        HIDE(true), PEEK(true), OVERVIEW(false), CANCEL(false);

        ShelfAnimState(boolean shouldPreformHaptic) {
            this.shouldPreformHaptic = shouldPreformHaptic;
        }

        public final boolean shouldPreformHaptic;
    }
}
