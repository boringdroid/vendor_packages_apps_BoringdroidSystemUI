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

package com.android.launcher3.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.PackageManager;
import android.os.Parcelable;
import android.os.UserHandle;

@TargetApi(26)
public class LauncherAppsCompatVO extends LauncherAppsCompatVL {

    LauncherAppsCompatVO(Context context) {
        super(context);
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user) {
        try {
            ApplicationInfo info = mLauncherApps.getApplicationInfo(packageName, flags, user);
            return (info.flags & ApplicationInfo.FLAG_INSTALLED) == 0 || !info.enabled
                    ? null : info;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static PinItemRequest getPinItemRequest(Intent intent) {
        Parcelable extra = intent.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST);
        return extra instanceof PinItemRequest ? (PinItemRequest) extra : null;
    }
}
