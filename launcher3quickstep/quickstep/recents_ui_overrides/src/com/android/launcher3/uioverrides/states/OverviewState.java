/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.util.LayoutUtils;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {
    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

    public OverviewState(int id) {
        this(id, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    protected OverviewState(int id, int transitionDuration, int stateFlags) {
        this(id, ContainerType.TASKSWITCHER, transitionDuration, stateFlags);
    }

    protected OverviewState(int id, int logContainer, int transitionDuration, int stateFlags) {
        super(id, logContainer, transitionDuration, stateFlags);
    }

    public static float getDefaultVerticalProgress(Launcher launcher) {
        return 1 - (getDefaultSwipeHeight(launcher));
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        return LayoutUtils.getDefaultSwipeHeight(launcher, launcher.getDeviceProfile());
    }

    public static OverviewState newBackgroundState(int id) {
        return new BackgroundAppState(id);
    }

    public static OverviewState newPeekState(int id) {
        return new OverviewState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new BackgroundAppState(id);
    }
}
