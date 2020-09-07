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

import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.SafeCloseable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/**
 * Handles updates due to changes in package manager (app installed/updated/removed)
 * or when a user availability changes.
 */
public class PackageUpdatedTask extends BaseModelUpdateTask {

    private static final boolean DEBUG = false;
    private static final String TAG = "PackageUpdatedTask";

    public static final int OP_ADD = 1;
    public static final int OP_UPDATE = 2;
    public static final int OP_REMOVE = 3; // uninstalled
    public static final int OP_UNAVAILABLE = 4; // external media unmounted
    public static final int OP_SUSPEND = 5; // package suspended
    public static final int OP_UNSUSPEND = 6; // package unsuspended
    public static final int OP_USER_AVAILABILITY_CHANGE = 7; // user available/unavailable

    private final int mOp;
    private final UserHandle mUser;
    private final String[] mPackages;

    public PackageUpdatedTask(int op, UserHandle user, String... packages) {
        mOp = op;
        mUser = user;
        mPackages = packages;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList appsList) {
        final Context context = app.getContext();
        final IconCache iconCache = app.getIconCache();

        final String[] packages = mPackages;
        final int N = packages.length;
        FlagOp flagOp = FlagOp.NO_OP;
        final HashSet<String> packageSet = new HashSet<>(Arrays.asList(packages));
        ItemInfoMatcher matcher = ItemInfoMatcher.ofPackages(packageSet, mUser);
        final HashSet<ComponentName> removedComponents = new HashSet<>();

        switch (mOp) {
            case OP_ADD: {
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]);
                    iconCache.updateIconsForPkg(packages[i], mUser);
                    if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
                        appsList.removePackage(packages[i], mUser);
                    }
                    appsList.addPackage(context, packages[i], mUser);

                    // Automatically add homescreen icon for work profile apps for below O device.
                    if (!Utilities.ATLEAST_OREO && !Process.myUserHandle().equals(mUser)) {
                        SessionCommitReceiver.queueAppIconAddition(context, packages[i], mUser);
                    }
                }
                break;
            }
            case OP_UPDATE:
                try (SafeCloseable t =
                             appsList.trackRemoves(a -> removedComponents.add(a.componentName))) {
                    for (int i = 0; i < N; i++) {
                        if (DEBUG) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                        iconCache.updateIconsForPkg(packages[i], mUser);
                        appsList.updatePackage(context, packages[i], mUser);
                    }
                }
                break;
            case OP_REMOVE: {
                for (int i = 0; i < N; i++) {
                    iconCache.removeIconsForPkg(packages[i], mUser);
                }
                // Fall through
            }
            case OP_UNAVAILABLE:
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                    appsList.removePackage(packages[i], mUser);
                }
                break;
            case OP_SUSPEND:
            case OP_UNSUSPEND:
                if (DEBUG) Log.d(TAG, "mAllAppsList.(un)suspend " + N);
                appsList.updateDisabledFlags(matcher, flagOp);
                break;
            case OP_USER_AVAILABILITY_CHANGE:
                // We want to update all packages for this user.
                matcher = ItemInfoMatcher.ofUser(mUser);
                appsList.updateDisabledFlags(matcher, flagOp);
                break;
        }

        bindApplicationsIfNeeded();

        final IntSparseArrayMap<Boolean> removedShortcuts = new IntSparseArrayMap<>();

        // Update shortcut infos
        if (mOp == OP_ADD || flagOp != FlagOp.NO_OP) {
            if (!removedShortcuts.isEmpty()) {
                deleteAndBindComponentsRemoved(ItemInfoMatcher.ofItemIds(removedShortcuts, false));
            }
        }

        final HashSet<String> removedPackages = new HashSet<>();
        if (mOp == OP_REMOVE) {
            // Mark all packages in the broadcast to be removed
            Collections.addAll(removedPackages, packages);

            // No need to update the removedComponents as
            // removedPackages is a super-set of removedComponents
        } else if (mOp == OP_UPDATE) {
            // Mark disabled packages in the broadcast to be removed
            final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
            for (int i=0; i<N; i++) {
                if (!launcherApps.isPackageEnabledForProfile(packages[i], mUser)) {
                    removedPackages.add(packages[i]);
                }
            }
        }

        if (!removedPackages.isEmpty() || !removedComponents.isEmpty()) {
            ItemInfoMatcher removeMatch = ItemInfoMatcher.ofPackages(removedPackages, mUser)
                    .or(ItemInfoMatcher.ofComponents(removedComponents, mUser))
                    .and(ItemInfoMatcher.ofItemIds(removedShortcuts, true));
            deleteAndBindComponentsRemoved(removeMatch);
        }
    }
}
