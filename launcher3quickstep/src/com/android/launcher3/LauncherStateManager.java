/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.NORMAL;

/**
 * TODO: figure out what kind of tests we can write for this
 *
 * Things to test when changing the following class.
 *   - Home from workspace
 *          - from center screen
 *          - from other screens
 *   - Home from all apps
 *          - from center screen
 *          - from other screens
 *   - Back from all apps
 *          - from center screen
 *          - from other screens
 *   - Launch app from workspace and quit
 *          - with back
 *          - with home
 *   - Launch app from all apps and quit
 *          - with back
 *          - with home
 *   - Go to a screen that's not the default, then all
 *     apps, and launch and app, and go back
 *          - with back
 *          -with home
 *   - On workspace, long press power and go back
 *          - with back
 *          - with home
 *   - On all apps, long press power and go back
 *          - with back
 *          - with home
 *   - On workspace, power off
 *   - On all apps, power off
 *   - Launch an app and turn off the screen while in that app
 *          - Go back with home key
 *          - Go back with back key  TODO: make this not go to workspace
 *          - From all apps
 *          - From workspace
 *   - Enter and exit car mode (becase it causes an extra configuration changed)
 *          - From all apps
 *          - From the center workspace
 *          - From another workspace
 */
public class LauncherStateManager {

    public static final String TAG = "StateManager";

    private LauncherState mState = NORMAL;

    public LauncherStateManager() {
    }

    public LauncherState getState() {
        return mState;
    }

    public interface StateHandler {

        /**
         * Updates the UI to {@param state} without any animations
         */
        void setState(LauncherState state);
    }

    public interface StateListener {
    }
}
