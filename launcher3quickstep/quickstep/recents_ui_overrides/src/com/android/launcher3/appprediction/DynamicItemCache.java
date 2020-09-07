/**
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
package com.android.launcher3.appprediction;

import android.content.Context;

import androidx.annotation.UiThread;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class which loads and caches predicted items like instant apps and shortcuts, before
 * they can be displayed on the UI
 */
public class DynamicItemCache {

    private final Map<String, InstantAppItemInfo> mInstantApps;

    public DynamicItemCache() {
        mInstantApps = new HashMap<>();
    }

    @UiThread
    public InstantAppItemInfo getInstantApp(String pkgName) {
        return mInstantApps.get(pkgName);
    }
}
