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

package com.android.launcher3.uioverrides;

import com.android.launcher3.graphics.RotationMode;
import com.android.quickstep.TouchInteractionService;

/**
 * Provides recents-related {@link UiFactory} logic and classes.
 */
public abstract class RecentsUiFactory {

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = false;

    public static RotationMode ROTATION_LANDSCAPE = new RotationMode(-90) {

    };

    public static RotationMode ROTATION_SEASCAPE = new RotationMode(90) {

    };

    /**
     * Clears the swipe shared state for the current swipe gesture.
     */
    public static void clearSwipeSharedState(boolean finishAnimation) {
        TouchInteractionService.getSwipeSharedState().clearAllState(finishAnimation);
    }
}
