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
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.AppInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherActivityCachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.util.LooperIdleLock;

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
    private final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;

    private final BaseLoaderResults mResults;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final IconCache mIconCache;

    private boolean mStopped;

    public LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList,
                      BaseLoaderResults results) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mResults = results;

        mLauncherApps = LauncherAppsCompat.getInstance(mApp.getContext());
        mUserManager = UserManagerCompat.getInstance(mApp.getContext());
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

        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            verifyNotStopped();
            // Take a break
            waitForIdle();
            verifyNotStopped();

            // second step
            List<LauncherActivityInfo> allActivityList = loadAllApps();

            verifyNotStopped();

            verifyNotStopped();
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    (updatedPackages, updatedPackages2) -> mApp.getModel().onPackageIconsUpdated());

            // Take a break
            waitForIdle();
            verifyNotStopped();

            // third step
            verifyNotStopped();

            // Take a break
            waitForIdle();
            verifyNotStopped();

            updateHandler.finish();

            transaction.commit();
        } catch (CancellationException e) {
            // Loader stopped, ignore
        }
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

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }
}
