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

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import com.android.launcher3.R;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Factory class to create and add an overlays on the TaskView
 */
public class TaskOverlayFactory implements ResourceBasedOverride {
    public static final MainThreadInitializedObject<TaskOverlayFactory> INSTANCE =
            forOverride(TaskOverlayFactory.class, R.string.task_overlay_factory_class);

    public TaskOverlay createOverlay() {
        return new TaskOverlay();
    }

    public static class TaskOverlay {

        /**
         * Called when the current task is interactive for the user
         */
        public void initOverlay() { }

        /**
         * Called when the overlay is no longer used.
         */
        public void reset() { }
    }
}
