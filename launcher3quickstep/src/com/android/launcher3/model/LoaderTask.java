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

package com.android.launcher3.model;

import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.AppInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherActivityCachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.TraceHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 */
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";

    private final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;

    private final LoaderResults mResults;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final PackageInstallerCompat mPackageInstaller;
    private final IconCache mIconCache;

    private boolean mStopped;

    public LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList,
                      LoaderResults results) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mResults = results;

        mLauncherApps = LauncherAppsCompat.getInstance(mApp.getContext());
        mUserManager = UserManagerCompat.getInstance(mApp.getContext());
        mPackageInstaller = PackageInstallerCompat.getInstance(mApp.getContext());
        mIconCache = mApp.getIconCache();
    }

    protected synchronized void waitForIdle() {
        // Wait until the either we're stopped or the other threads are done.
        // This way we don't start loading all apps until the workspace has settled
        // down.
        LooperIdleLock idleLock = mResults.newIdleLock(this);
        // Just in case mFlushingWorkerThread changes but we aren't woken up,
        // wait no longer than 1sec at a time
        while (!mStopped && idleLock.awaitLocked(1000));
    }

    private synchronized void verifyNotStopped() throws CancellationException {
        if (mStopped) {
            throw new CancellationException("Loader stopped");
        }
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.beginSection(TAG);
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            TraceHelper.partitionSection(TAG, "step 1.1: loading workspace");

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 1.2: bind workspace workspace");

            // Take a break
            TraceHelper.partitionSection(TAG, "step 1 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // second step
            TraceHelper.partitionSection(TAG, "step 2.1: loading all apps");
            List<LauncherActivityInfo> allActivityList = loadAllApps();

            TraceHelper.partitionSection(TAG, "step 2.2: Binding all apps");
            verifyNotStopped();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 2.3: Update icon cache");
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    (updatedPackages, updatedPackages2) -> mApp.getModel().onPackageIconsUpdated());

            // Take a break
            TraceHelper.partitionSection(TAG, "step 2 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // third step
            TraceHelper.partitionSection(TAG, "step 3.1: loading deep shortcuts");

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 3.2: bind deep shortcuts");

            // Take a break
            TraceHelper.partitionSection(TAG, "step 3 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            TraceHelper.partitionSection(TAG, "step 5: Finish icon cache update");
            updateHandler.finish();

            transaction.commit();
        } catch (CancellationException e) {
            // Loader stopped, ignore
            TraceHelper.partitionSection(TAG, "Cancelled");
        }
        TraceHelper.endSection(TAG);
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    private void setIgnorePackages(IconCacheUpdateHandler updateHandler) {
        // Ignore packages which have a promise icon.
        HashSet<String> packagesToIgnore = new HashSet<>();
        updateHandler.setPackagesToIgnore(Process.myUserHandle(), packagesToIgnore);
    }

    private List<LauncherActivityInfo> loadAllApps() {
        final List<UserHandle> profiles = mUserManager.getUserProfiles();
        List<LauncherActivityInfo> allActivityList = new ArrayList<>();
        // Clear the list of apps
        mBgAllAppsList.clear();
        for (UserHandle user : profiles) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return allActivityList;
            }
            boolean quietMode = mUserManager.isQuietModeEnabled(user);
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                // This builds the icon bitmaps.
                mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
            }
            allActivityList.addAll(apps);
        }

        if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mPackageInstaller.getAllVerifiedSessions()) {
                mBgAllAppsList.addPromiseApp(mApp.getContext(),
                        PackageInstallerCompat.PackageInstallInfo.fromInstallingState(info));
            }
        }

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }
}
