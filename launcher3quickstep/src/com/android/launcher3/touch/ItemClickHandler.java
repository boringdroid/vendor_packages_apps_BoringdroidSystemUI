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
package com.android.launcher3.touch;

import static com.android.launcher3.model.AppLaunchTracker.CONTAINER_ALL_APPS;

import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.Nullable;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.views.FloatingIconView;

/**
 * Class for handling clicks on workspace and all-apps items
 */
public class ItemClickHandler {

    /**
     * Instance used for click handling on items
     */
    public static final OnClickListener INSTANCE = getInstance(null);

    public static final OnClickListener getInstance(String sourceContainer) {
        return v -> onClick(v, sourceContainer);
    }

    private static void onClick(View v, String sourceContainer) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) return;

        Launcher launcher = Launcher.getLauncher(v.getContext());

        Object tag = v.getTag();
        if (tag instanceof AppInfo) {
            startAppShortcutOrInfoActivity(v, (AppInfo) tag, launcher,
                    sourceContainer == null ? CONTAINER_ALL_APPS: sourceContainer);
        }
    }

    private static void startAppShortcutOrInfoActivity(View v, ItemInfo item, Launcher launcher,
            @Nullable String sourceContainer) {
        Intent intent;
        if (item instanceof PromiseAppInfo) {
            PromiseAppInfo promiseAppInfo = (PromiseAppInfo) item;
            intent = promiseAppInfo.getMarketIntent(launcher);
        } else {
            intent = item.getIntent();
        }
        if (intent == null) {
            throw new IllegalArgumentException("Input must have a valid intent");
        }
        if (v != null && launcher.getAppTransitionManager().supportsAdaptiveIconAnimation()) {
            // Preload the icon to reduce latency b/w swapping the floating view with the original.
            FloatingIconView.fetchIcon(launcher, v, item, true /* isOpening */);
        }
        launcher.startActivitySafely(v, intent, item, sourceContainer);
    }
}
