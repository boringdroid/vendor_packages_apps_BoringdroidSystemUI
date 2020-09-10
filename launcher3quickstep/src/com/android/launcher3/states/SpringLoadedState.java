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
package com.android.launcher3.states;

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_TRANSITION_MS;

import com.android.launcher3.LauncherState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Definition for spring loaded state used during drag and drop.
 */
public class SpringLoadedState extends LauncherState {
    private static final int STATE_FLAGS = FLAG_MULTI_PAGE |
            FLAG_DISABLE_ACCESSIBILITY | FLAG_DISABLE_RESTORE | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED |
            FLAG_DISABLE_PAGE_CLIPPING | FLAG_PAGE_BACKGROUNDS | FLAG_HIDE_BACK_BUTTON;

    public SpringLoadedState(int id) {
        super(id, ContainerType.OVERVIEW, SPRING_LOADED_TRANSITION_MS, STATE_FLAGS);
    }
}
