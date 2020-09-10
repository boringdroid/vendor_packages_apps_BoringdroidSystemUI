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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;

import com.android.launcher3.userevent.nano.LauncherLogProto;

/**
 * State indicating that the Launcher is behind an app
 */
public class BackgroundAppState extends OverviewState {

    private static final int STATE_FLAGS =
            FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY
                    | FLAG_DISABLE_INTERACTION;

    public BackgroundAppState(int id) {
        this(id, LauncherLogProto.ContainerType.TASKSWITCHER);
    }

    protected BackgroundAppState(int id, int logContainer) {
        super(id, logContainer, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    @Override
    public float getOverviewFullscreenProgress() {
        return 1;
    }
}
