/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.model;


import com.android.launcher3.LauncherAppState;
import com.android.launcher3.WorkspaceItemInfo;

import java.util.ArrayList;

/**
 * Task to handle changing of lock state of the user
 */
public class UserLockStateChangedTask extends BaseModelUpdateTask {

    public UserLockStateChangedTask() {
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
        // Update the workspace to reflect the changes to updated shortcuts residing on it.
        ArrayList<WorkspaceItemInfo> updatedWorkspaceItemInfos = new ArrayList<>();
        bindUpdatedWorkspaceItems(updatedWorkspaceItemInfos);
    }
}
